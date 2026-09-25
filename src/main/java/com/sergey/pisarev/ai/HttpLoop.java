package com.sergey.pisarev.ai;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

/**
 * Маленький HTTP-сервер на обычных сокетах.
 *
 * <p>Почему не {@code com.sun.net.httpserver}. На машине заказчика он не поднимается:
 * ему нужен {@code Selector}, а {@code Selector.open()} там падает с «Unable to
 * establish loopback connection» — это же ломает и {@code java.net.http.HttpClient}.
 * Проверено замером (ProbeSockets): обычный {@link ServerSocket} с приёмом соединений
 * и чтением/записью работает нормально, селектор — нет. Скорее всего мешает защитное ПО,
 * и просить наладчика чинить настройки антивируса — не вариант.
 *
 * <p>Поэтому здесь блокирующие сокеты и поток на соединение: запросов от нейросети
 * единицы в минуту, экономить потоки не на чем. Умеет ровно то, что нужно MCP:
 * строка запроса, заголовки, тело по {@code Content-Length} или по частям
 * ({@code chunked}), и ответ с {@code Connection: close}.
 */
public final class HttpLoop implements AutoCloseable {

    /** Разобранный запрос. */
    public record Request(String method, String path, String query,
                          Map<String, String> headers, String body) {

        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    /** Что вернуть клиенту. */
    public record Response(int status, String contentType, String body) {

        public static Response json(String body) {
            return new Response(200, "application/json; charset=utf-8", body);
        }

        public static Response text(int status, String body) {
            return new Response(status, "text/plain; charset=utf-8", body);
        }

        /** Ответ без тела: клиент по протоколу его и не ждёт. */
        public static Response empty(int status) {
            return new Response(status, "text/plain; charset=utf-8", "");
        }
    }

    /** Обработчик запроса; вернуть null, если ответ уже отправлен вручную. */
    public interface Handler {
        Response handle(Request request, OutputStream raw) throws IOException;
    }

    private static final int MAX_BODY = 24 * 1024 * 1024;
    private static final int READ_TIMEOUT_MS = 120_000;

    private final ServerSocket server;
    private final Handler handler;
    private final Thread acceptor;
    private final java.util.function.Consumer<String> log;
    private volatile boolean stopping;

    private HttpLoop(ServerSocket server, Handler handler, java.util.function.Consumer<String> log) {
        this.server = server;
        this.handler = handler;
        this.log = log;
        this.acceptor = new Thread(this::acceptLoop, "mcp-accept");
        this.acceptor.setDaemon(true);
    }

    /** Слушает только 127.0.0.1: из сети к серверу не подключиться. */
    public static HttpLoop start(int port, Handler handler, java.util.function.Consumer<String> log)
            throws IOException {
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), Math.max(0, port)), 16);
        HttpLoop loop = new HttpLoop(socket, handler, log);
        loop.acceptor.start();
        return loop;
    }

    public int port() {
        return server.getLocalPort();
    }

    @Override
    public void close() {
        stopping = true;
        try {
            server.close();
        } catch (IOException ignored) {
            // Уже закрыт.
        }
    }

    private void acceptLoop() {
        while (!stopping) {
            Socket client;
            try {
                client = server.accept();
            } catch (IOException closed) {
                if (!stopping) {
                    note("приём соединений прекращён: " + closed.getMessage());
                }
                return;
            }
            Thread worker = new Thread(() -> serve(client), "mcp-conn");
            worker.setDaemon(true);
            worker.start();
        }
    }

    private void serve(Socket client) {
        try (Socket socket = client) {
            socket.setSoTimeout(READ_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            Request request = read(in);
            if (request == null) {
                return;
            }
            Response response;
            try {
                response = handler.handle(request, out);
            } catch (RuntimeException failure) {
                note("сбой обработки: " + failure);
                response = Response.text(500, "внутренняя ошибка: " + failure.getMessage());
            }
            if (response != null) {
                write(out, response);
            }
            out.flush();
        } catch (IOException broken) {
            // Клиент отключился на середине - обычное дело, шумить не о чем.
        }
    }

    private Request read(InputStream in) throws IOException {
        String line = readLine(in);
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.split(" ");
        if (parts.length < 2) {
            return null;
        }
        String method = parts[0];
        String target = parts[1];
        String path = target;
        String query = "";
        int mark = target.indexOf('?');
        if (mark >= 0) {
            path = target.substring(0, mark);
            query = target.substring(mark + 1);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        String header;
        while ((header = readLine(in)) != null && !header.isEmpty()) {
            int colon = header.indexOf(':');
            if (colon > 0) {
                headers.put(header.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        header.substring(colon + 1).trim());
            }
        }
        String body = readBody(in, headers);
        return new Request(method, path, query, headers, body);
    }

    private static String readBody(InputStream in, Map<String, String> headers) throws IOException {
        String encoding = headers.get("transfer-encoding");
        if (encoding != null && encoding.toLowerCase(Locale.ROOT).contains("chunked")) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            while (true) {
                String sizeLine = readLine(in);
                if (sizeLine == null) {
                    break;
                }
                int semicolon = sizeLine.indexOf(';');
                String hex = (semicolon >= 0 ? sizeLine.substring(0, semicolon) : sizeLine).trim();
                int size;
                try {
                    size = Integer.parseInt(hex, 16);
                } catch (NumberFormatException broken) {
                    break;
                }
                if (size <= 0) {
                    readLine(in);
                    break;
                }
                if (buffer.size() + size > MAX_BODY) {
                    throw new IOException("тело запроса больше 24 МБ");
                }
                byte[] chunk = in.readNBytes(size);
                buffer.write(chunk);
                readLine(in);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }
        String length = headers.get("content-length");
        if (length == null) {
            return "";
        }
        int size;
        try {
            size = Integer.parseInt(length.trim());
        } catch (NumberFormatException broken) {
            return "";
        }
        if (size <= 0) {
            return "";
        }
        if (size > MAX_BODY) {
            throw new IOException("тело запроса больше 24 МБ");
        }
        return new String(in.readNBytes(size), StandardCharsets.UTF_8);
    }

    /** Строка запроса и заголовки читаются побайтно: они короткие, а тело идёт как есть. */
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
        int value;
        while ((value = in.read()) >= 0) {
            if (value == '\n') {
                break;
            }
            if (value != '\r') {
                buffer.write(value);
            }
        }
        if (value < 0 && buffer.size() == 0) {
            return null;
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static void write(OutputStream out, Response response) throws IOException {
        byte[] body = response.body() == null
                ? new byte[0] : response.body().getBytes(StandardCharsets.UTF_8);
        StringBuilder head = new StringBuilder(128);
        head.append("HTTP/1.1 ").append(response.status()).append(' ')
                .append(reason(response.status())).append("\r\n");
        head.append("Content-Type: ").append(response.contentType()).append("\r\n");
        head.append("Content-Length: ").append(body.length).append("\r\n");
        head.append("Connection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.UTF_8));
        if (body.length > 0) {
            out.write(body);
        }
    }

    private static String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            default -> "Status";
        };
    }

    private void note(String message) {
        if (log != null) {
            log.accept(message);
        }
    }
}
