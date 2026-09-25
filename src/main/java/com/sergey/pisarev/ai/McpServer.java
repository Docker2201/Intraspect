package com.sergey.pisarev.ai;

import com.sergey.pisarev.util.I18n;
import com.sergey.pisarev.util.MiniJson;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Сервер MCP внутри программы: нейросеть подключается и работает с этой программой.
 *
 * <p>Почему HTTP, а не stdio. Обычный сервер MCP клиент запускает сам как дочерний
 * процесс и говорит с ним через stdin/stdout. Здесь наоборот: программа уже открыта,
 * в ней лежит чертёж и настройки наладчика, и подключаться надо к ней, а не поднимать
 * второй экземпляр. Поэтому сервер слушает HTTP — на обычных сокетах, см. {@link HttpLoop}.
 *
 * <p>Безопасность. Сервер слушает только 127.0.0.1 — из сети к нему не подключиться.
 * Плюс ключ: без верного {@code Authorization: Bearer} или {@code ?key=} запрос
 * отклоняется. Ключ постоянный (лежит в настройках рядом с программой), чтобы адрес
 * в клиенте прописывался один раз; сменить его можно кнопкой в окне. Сервер выключен,
 * включает его человек в окне «МСП», и там же виден режим доступа: только чтение или
 * полный. Это не мелочь: через MCP можно переписать программу, по которой будут резать.
 */
public final class McpServer {
    /** Что нейросети разрешено делать. */
    public enum Access {
        /** Только смотреть: читать программу, настройки, инструменты, картинки. */
        READ_ONLY,
        /** Смотреть и менять: программа, настройки, инструменты. */
        FULL
    }

    public static final int DEFAULT_PORT = 8722;
    private static final String PATH = "/mcp";
    private static final String SERVER_NAME = "intraspect";
    /** Версия протокола по умолчанию; если клиент просит другую известную — отвечаем его. */
    private static final String PROTOCOL = "2024-11-05";
    private static final List<String> KNOWN_PROTOCOLS = List.of("2024-11-05", "2025-03-26", "2025-06-18");
    private static final List<String> WRITING_TOOLS =
            List.of("set_program", "set_settings", "save_tool", "remove_tool", "control_simulation");
    private static final int LOG_LINES = 200;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String JSON = "application/json; charset=utf-8";
    private static final String CRLF = "\r\n";

    private static final Deque<String> journal = new ArrayDeque<>();
    private static volatile McpServer running;

    /** Ставится сразу после запуска сокета: экземпляр один, поэтому поля не final. */
    private volatile HttpLoop http;
    private volatile int port;
    private final AppAccess app;
    private final String token;
    private final String appVersion;
    private volatile Access access;
    private volatile int calls;
    private volatile String lastTool = "";

    private McpServer(AppAccess app, String token, Access access, String appVersion) {
        this.app = app;
        this.token = token;
        this.access = access;
        this.appVersion = appVersion == null ? "" : appVersion;
    }

    // ------------------------------------------------------------------
    // Запуск и остановка
    // ------------------------------------------------------------------

    public static synchronized McpServer current() {
        return running;
    }

    public static synchronized boolean isRunning() {
        return running != null;
    }

    /**
     * Поднимает сервер. Порт 0 — любой свободный.
     *
     * @throws IOException порт занят или закрыт политикой — сообщение показывается человеку
     */
    public static synchronized McpServer start(AppAccess app, int port, Access access, String appVersion)
            throws IOException {
        if (running != null) {
            return running;
        }
        String token = key();
        // Экземпляр ровно один: раньше обработчик висел на одном объекте, а режим доступа
        // и счётчик вызовов жили на другом, и переключение «только чтение / полный»
        // ничего не меняло. Поймано проверкой CheckMcp.
        McpServer server = new McpServer(app, token, access, appVersion);
        HttpLoop http = HttpLoop.start(port, server::handle, McpServer::note);
        server.http = http;
        server.port = http.port();
        running = server;
        note(I18n.format("mcp.log.on", server.port(), label(access)));
        return server;
    }

    public static synchronized void stop() {
        if (running == null) {
            return;
        }
        if (running.http != null) {
            running.http.close();
        }
        note(I18n.text("mcp.log.off"));
        running = null;
    }

    // ------------------------------------------------------------------
    // Состояние для окна
    // ------------------------------------------------------------------

    public int port() {
        return port;
    }

    public String token() {
        return token;
    }

    public Access access() {
        return access;
    }

    public void setAccess(Access value) {
        this.access = value == null ? Access.READ_ONLY : value;
        note(I18n.format("mcp.log.access", label(this.access)));
    }

    public int calls() {
        return calls;
    }

    public String lastTool() {
        return lastTool;
    }

    /** Адрес для клиента, ключ уже внутри. */
    public String url() {
        return "http://127.0.0.1:" + port + PATH + "?key=" + token;
    }

    /** Готовая команда для Claude Code. */
    public String claudeCodeCommand() {
        return "claude mcp add --transport http intraspect " + url();
    }

    /** Готовый кусок конфигурации для клиентов, которые читают JSON. */
    public String clientConfigJson() {
        return new JsonOut().obj().key("mcpServers").obj().key(SERVER_NAME).obj()
                .field("type", "http")
                .field("url", url())
                .end().end().end().done();
    }

    public static synchronized List<String> journal() {
        return List.copyOf(journal);
    }

    public static synchronized void note(String line) {
        journal.addLast(LocalTime.now().format(STAMP) + "  " + line);
        while (journal.size() > LOG_LINES) {
            journal.removeFirst();
        }
    }

    /** Как называется режим доступа на языке интерфейса. */
    public static String label(Access access) {
        return I18n.text(access == Access.FULL ? "mcp.access.full.short" : "mcp.access.read.short");
    }

    // ------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------

    /** Один запрос от клиента MCP. */
    private HttpLoop.Response handle(HttpLoop.Request request, OutputStream raw) throws IOException {
        if (!PATH.equals(request.path())) {
            return HttpLoop.Response.text(404, "не тот адрес: сервер отвечает на " + PATH);
        }
        if (!authorized(request)) {
            note(I18n.text("mcp.log.badkey"));
            return new HttpLoop.Response(401, JSON, errorBody(null, -32001, "неверный ключ доступа"));
        }
        switch (request.method()) {
            case "POST":
                return handlePost(request);
            case "GET":
                keepAlive(raw);
                return null;
            case "DELETE":
                return HttpLoop.Response.empty(200);
            case "OPTIONS":
                return HttpLoop.Response.empty(204);
            default:
                return HttpLoop.Response.text(405, "поддерживается POST");
        }
    }

    private HttpLoop.Response handlePost(HttpLoop.Request request) {
        String body = request.body();
        if (body == null || body.isBlank()) {
            return new HttpLoop.Response(400, JSON, errorBody(null, -32700, "пустой запрос"));
        }
        Map<String, Object> parsed;
        try {
            parsed = MiniJson.parseObject(body);
        } catch (RuntimeException broken) {
            return new HttpLoop.Response(400, JSON,
                    errorBody(null, -32700, "не JSON: " + broken.getMessage()));
        }
        String answer = dispatch(MiniJson.string(parsed, "method", ""), parsed, parsed.get("id"));
        if (answer == null) {
            // Уведомление: по протоколу ответа нет, только подтверждение приёма.
            return HttpLoop.Response.empty(202);
        }
        return new HttpLoop.Response(200, JSON, answer);
    }

    /**
     * Поток событий для клиентов, которые открывают GET и ждут.
     *
     * <p>Своих сообщений сервер не рассылает, поэтому в поток идут только двоеточия —
     * это комментарии SSE, они держат соединение и ничего не значат.
     */
    private void keepAlive(OutputStream raw) throws IOException {
        raw.write(("HTTP/1.1 200 OK" + CRLF
                + "Content-Type: text/event-stream" + CRLF
                + "Cache-Control: no-cache" + CRLF
                + "Connection: close" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        raw.flush();
        try {
            while (isRunning()) {
                raw.write(": keep-alive\n\n".getBytes(StandardCharsets.UTF_8));
                raw.flush();
                Thread.sleep(15_000L);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean authorized(HttpLoop.Request request) {
        String header = request.header("authorization");
        if (header != null && header.startsWith("Bearer ") && token.equals(header.substring(7).trim())) {
            return true;
        }
        String query = request.query();
        if (query != null && !query.isEmpty()) {
            for (String part : query.split("&")) {
                int eq = part.indexOf('=');
                if (eq > 0 && "key".equals(part.substring(0, eq)) && token.equals(part.substring(eq + 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Ответ или null, если это уведомление. */
    private String dispatch(String method, Map<String, Object> request, Object id) {
        if (method.startsWith("notifications/")) {
            return null;
        }
        switch (method) {
            case "initialize": {
                Map<String, Object> params = objectOf(request.get("params"));
                String wanted = MiniJson.string(params, "protocolVersion", PROTOCOL);
                String protocol = KNOWN_PROTOCOLS.contains(wanted) ? wanted : PROTOCOL;
                Map<String, Object> client = objectOf(params.get("clientInfo"));
                note(I18n.format("mcp.log.client",
                        MiniJson.string(client, "name", "?") + " "
                                + MiniJson.string(client, "version", "")));
                String result = new JsonOut().obj()
                        .field("protocolVersion", protocol)
                        .key("capabilities").obj().key("tools").obj().end().end()
                        .key("serverInfo").obj()
                        .field("name", SERVER_NAME)
                        .field("version", appVersion.isEmpty() ? "3.80" : appVersion)
                        .end()
                        .field("instructions",
                                "Это Intraspect — проверка и симуляция токарных программ Siemens SINUMERIK. "
                                        + "Начни с get_program, get_settings и get_tool_library: там текущая "
                                        + "программа, заготовка и инструменты магазина. Программа режет только "
                                        + "тем инструментом, который заведён в библиотеке. Правь через "
                                        + "set_program, после каждой правки вызывай check_program, а результат "
                                        + "смотри через render_model_3d и render_graph_2d.")
                        .end().done();
                return resultBody(id, result);
            }
            case "ping":
                return resultBody(id, "{}");
            case "tools/list":
                return resultBody(id, new JsonOut().obj().fieldRaw("tools", McpTools.listJson()).end().done());
            case "resources/list":
                return resultBody(id, new JsonOut().obj().key("resources").arr().end().end().done());
            case "prompts/list":
                return resultBody(id, new JsonOut().obj().key("prompts").arr().end().end().done());
            case "tools/call": {
                Map<String, Object> params = objectOf(request.get("params"));
                String name = MiniJson.string(params, "name", "");
                Map<String, Object> arguments = objectOf(params.get("arguments"));
                calls++;
                lastTool = name;
                if (access != Access.FULL && WRITING_TOOLS.contains(name)) {
                    note(I18n.format("mcp.log.denied", name));
                    return resultBody(id, refusal(name));
                }
                note(I18n.format("mcp.log.call", name + describe(name, arguments)));
                app.log("MCP: " + name + describe(name, arguments));
                return resultBody(id, McpTools.call(app, name, arguments));
            }
            default:
                return errorBody(id, -32601, "метод не поддержан: " + method);
        }
    }

    /** Коротко, что именно попросили: строка журнала должна быть читаемой наладчиком. */
    private static String describe(String name, Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }
        switch (name) {
            case "set_program": {
                String reason = MiniJson.string(arguments, "reason", "");
                String text = MiniJson.string(arguments, "text", "");
                int lines = text.isEmpty() ? 0 : text.split("\r?\n", -1).length;
                return " (" + lines + " строк" + (reason.isBlank() ? "" : ", " + reason) + ")";
            }
            case "save_tool":
                return " (ячейка " + (int) MiniJson.number(arguments, "location", 0)
                        + " " + MiniJson.string(arguments, "name", "") + ")";
            case "remove_tool":
                return " (ячейка " + (int) MiniJson.number(arguments, "location", 0) + ")";
            case "set_settings":
                return " (" + String.join(", ", arguments.keySet()) + ")";
            default:
                return "";
        }
    }

    private static String refusal(String name) {
        return new JsonOut().obj().key("content").arr().obj()
                .field("type", "text")
                .field("text", "Инструмент " + name + " меняет программу, а доступ сейчас «только чтение». "
                        + "Человек может переключить режим в окне «МСП».")
                .end().end().field("isError", true).end().done();
    }

    private static String resultBody(Object id, String result) {
        return new JsonOut().obj()
                .field("jsonrpc", "2.0")
                .fieldRaw("id", idJson(id))
                .fieldRaw("result", result)
                .end().done();
    }

    private static String errorBody(Object id, int code, String message) {
        return new JsonOut().obj()
                .field("jsonrpc", "2.0")
                .fieldRaw("id", idJson(id))
                .key("error").obj().field("code", code).field("message", message).end()
                .end().done();
    }

    private static String idJson(Object id) {
        if (id == null) {
            return "null";
        }
        if (id instanceof Double number) {
            return JsonOut.number(number);
        }
        return JsonOut.string(String.valueOf(id));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectOf(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    /**
     * Постоянный ключ из настроек программы.
     *
     * <p>Если настройки почему-то недоступны (флешка «только чтение»), ключ будет
     * случайным на этот запуск: работать можно, но адрес придётся взять из окна «МСП».
     */
    private static String key() {
        try {
            String saved = com.sergey.pisarev.util.UserSettings.getMcpKey();
            if (saved != null && !saved.isBlank()) {
                return saved;
            }
        } catch (RuntimeException ignored) {
            // Настройки не прочитались - ниже случайный ключ.
        }
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
