package com.sergey.pisarev.ai;

import com.sergey.pisarev.util.MiniJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Автонастройка клиентов нейросети: программа сама прописывает себя в их конфиги.
 *
 * <p>Иначе наладчику пришлось бы искать спрятанный файл настроек Claude Code или Codex
 * и править JSON и TOML руками — при живой работе у станка это не годится.
 *
 * <p>Правки делаются точечно: файл не переписывается заново, меняется только свой
 * раздел. Причина простая — в этих файлах лежат все остальные настройки человека и
 * другие серверы MCP, и потерять их из-за нашей записи нельзя. Поэтому порядок такой:
 * сначала копия рядом с файлом, потом точечная вставка, потом проверка, что файл всё
 * ещё читается; если не читается — копия возвращается на место.
 */
public final class ClientConfig {

    /**
     * Что стало с конфигом одного клиента.
     *
     * <p>Состояние, а не готовая фраза: текст подписывает окно на языке интерфейса.
     * Иначе русские строки отсюда попадали в английское окно — так и было.
     */
    public enum State {
        /** Файла настроек нет: клиент не установлен. */
        NO_FILE,
        /** Файл есть, нашей записи нет. */
        ABSENT,
        /** Записаны и адрес совпадает с текущим. */
        SAME,
        /** Записаны, но адрес другой — надо прописать заново. */
        OTHER,
        /** Только что прописано. */
        WRITTEN,
        /** Не получилось; подробность в detail. */
        FAILED
    }

    /** Итог по одному клиенту. detail — подробность для состояний WRITTEN и FAILED. */
    public record Result(String client, State state, String detail, Path path) {

        public boolean ok() {
            return state == State.SAME || state == State.WRITTEN;
        }
    }

    /** Имя, под которым программа видна нейросети. */
    private static final String NAME = "intraspect";

    private ClientConfig() {
    }

    // ------------------------------------------------------------------
    // Где живут конфиги
    // ------------------------------------------------------------------

    /** Claude Code: один файл в профиле пользователя. */
    public static Path claudeCodePath() {
        return home().resolve(".claude.json");
    }

    /** Codex CLI: config.toml в папке .codex. */
    public static Path codexPath() {
        String codexHome = System.getenv("CODEX_HOME");
        if (codexHome != null && !codexHome.isBlank()) {
            return Path.of(codexHome).resolve("config.toml");
        }
        return home().resolve(".codex").resolve("config.toml");
    }

    private static Path home() {
        String profile = System.getenv("USERPROFILE");
        if (profile != null && !profile.isBlank()) {
            return Path.of(profile);
        }
        return Path.of(System.getProperty("user.home", "."));
    }

    // ------------------------------------------------------------------
    // Что уже прописано
    // ------------------------------------------------------------------

    /**
     * Состояние обоих клиентов: прописана ли программа и тот ли адрес.
     *
     * <p>Показывается в окне сразу при открытии, чтобы человек видел, надо ли вообще
     * что-то нажимать. Файлы читаются только по запросу окна, а не постоянно.
     *
     * @param url текущий адрес сервера; пустой — сервер выключен, адрес сравнивать не с чем
     */
    public static List<Result> status(String url) {
        List<Result> found = new ArrayList<>();
        found.add(statusOf("Claude Code", claudeCodePath(), url, ClientConfig::urlInClaudeCode));
        found.add(statusOf("Codex", codexPath(), url, ClientConfig::urlInCodex));
        return found;
    }

    private interface UrlReader {
        String read(String text);
    }

    private static Result statusOf(String client, Path path, String url, UrlReader reader) {
        try {
            if (!Files.isRegularFile(path)) {
                return new Result(client, State.NO_FILE, "", path);
            }
            String written = reader.read(Files.readString(path, StandardCharsets.UTF_8));
            if (written == null) {
                return new Result(client, State.ABSENT, "", path);
            }
            if (url == null || url.isBlank() || written.equals(url)) {
                return new Result(client, State.SAME, "", path);
            }
            return new Result(client, State.OTHER, "", path);
        } catch (IOException | RuntimeException failure) {
            return new Result(client, State.FAILED, String.valueOf(failure.getMessage()), path);
        }
    }

    private static String urlInClaudeCode(String text) {
        Object servers = MiniJson.parseObject(text).get("mcpServers");
        if (servers instanceof Map<?, ?> map && map.get(NAME) instanceof Map<?, ?> own) {
            Object url = own.get("url");
            return url == null ? null : String.valueOf(url);
        }
        return null;
    }

    private static String urlInCodex(String text) {
        boolean inSection = false;
        for (String line : text.split("\r?\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[")) {
                inSection = trimmed.equals("[mcp_servers." + NAME + "]");
                continue;
            }
            if (inSection && trimmed.startsWith("url")) {
                int quote = trimmed.indexOf('"');
                int last = trimmed.lastIndexOf('"');
                if (quote >= 0 && last > quote) {
                    return trimmed.substring(quote + 1, last);
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Claude Code (JSON)
    // ------------------------------------------------------------------

    /**
     * Прописывает сервер в конфиг Claude Code.
     *
     * @param url адрес с ключом — тот же, что показан в окне
     */
    public static Result configureClaudeCode(String url) {
        Path path = claudeCodePath();
        try {
            if (!Files.isRegularFile(path)) {
                // Пустого конфига не бывает у работающего Claude Code: значит его тут нет.
                return new Result("Claude Code", State.NO_FILE, "", path);
            }
            String text = Files.readString(path, StandardCharsets.UTF_8);
            Map<String, Object> parsed = MiniJson.parseObject(text);
            String entry = "\"" + NAME + "\": {\"type\": \"http\", \"url\": \"" + escape(url) + "\"}";
            String updated;
            Object servers = parsed.get("mcpServers");
            if (servers instanceof Map<?, ?> map) {
                // Именно верхний уровень: слово mcpServers встречается в этом файле ещё и
                // внутри каждого проекта, и первое вхождение почти всегда чужое.
                int braceAt = openBraceAfter(text, indexOfTopLevelKey(text, "mcpServers"));
                int braceEnd = matchingBrace(text, braceAt);
                if (braceAt < 0 || braceEnd < 0) {
                    return new Result("Claude Code", State.FAILED, "нет раздела mcpServers", path);
                }
                if (map.containsKey(NAME)) {
                    // Свою запись ищем строго внутри раздела, а не по всему файлу.
                    int keyAt = indexOfKey(text.substring(braceAt, braceEnd + 1), NAME);
                    if (keyAt < 0) {
                        return new Result("Claude Code", State.FAILED, "своя запись не найдена", path);
                    }
                    keyAt += braceAt;
                    int valueStart = openBraceAfter(text, keyAt);
                    int valueEnd = matchingBrace(text, valueStart);
                    if (valueStart < 0 || valueEnd < 0 || valueEnd > braceEnd) {
                        return new Result("Claude Code", State.FAILED, "своя запись повреждена", path);
                    }
                    updated = text.substring(0, keyAt) + entry + text.substring(valueEnd + 1);
                } else {
                    updated = text.substring(0, braceAt + 1) + "\n    " + entry + ","
                            + text.substring(braceAt + 1);
                }
            } else {
                int rootBrace = text.indexOf('{');
                if (rootBrace < 0) {
                    return new Result("Claude Code", State.FAILED, "это не JSON", path);
                }
                updated = text.substring(0, rootBrace + 1)
                        + "\n  \"mcpServers\": {" + entry + "},"
                        + text.substring(rootBrace + 1);
            }
            String problem = verifyJson(updated, url);
            if (problem != null) {
                return new Result("Claude Code", State.FAILED, problem, path);
            }
            Path backup = backup(path);
            Files.writeString(path, updated, StandardCharsets.UTF_8);
            return new Result("Claude Code", State.WRITTEN, backup.getFileName().toString(), path);
        } catch (IOException | RuntimeException failure) {
            return new Result("Claude Code", State.FAILED, String.valueOf(failure.getMessage()), path);
        }
    }

    /** Файл должен остаться читаемым JSON, и наша запись должна в нём найтись. */
    private static String verifyJson(String text, String url) {
        try {
            Map<String, Object> parsed = MiniJson.parseObject(text);
            Object servers = parsed.get("mcpServers");
            if (!(servers instanceof Map<?, ?> map)) {
                return "после правки нет раздела mcpServers";
            }
            Object entry = map.get(NAME);
            if (!(entry instanceof Map<?, ?> own)) {
                return "после правки нет своей записи";
            }
            if (!url.equals(own.get("url"))) {
                return "после правки адрес не совпал";
            }
            return null;
        } catch (RuntimeException broken) {
            return "после правки файл перестал читаться: " + broken.getMessage();
        }
    }

    // ------------------------------------------------------------------
    // Codex CLI (TOML)
    // ------------------------------------------------------------------

    /**
     * Прописывает сервер в config.toml Codex.
     *
     * <p>Формат взят такой же, как у уже работающих у людей серверов по HTTP: раздел
     * {@code [mcp_servers.<имя>]} и один {@code url}. Для HTTP Codex-у нужен признак
     * {@code rmcp_client = true} в разделе {@code [features]} — если его нет, он
     * добавляется, иначе запись просто не сработает.
     */
    public static Result configureCodex(String url) {
        Path path = codexPath();
        try {
            String text = Files.isRegularFile(path)
                    ? Files.readString(path, StandardCharsets.UTF_8) : "";
            if (text.isEmpty() && !Files.isDirectory(path.getParent())) {
                return new Result("Codex", State.NO_FILE, "", path);
            }
            String block = "[mcp_servers." + NAME + "]" + System.lineSeparator()
                    + "url = \"" + escape(url) + "\"" + System.lineSeparator();
            String updated = replaceTomlSection(text, "[mcp_servers." + NAME + "]", block);
            boolean addedFeature = false;
            if (!hasRmcpClient(updated)) {
                updated = withRmcpClient(updated);
                addedFeature = true;
            }
            Path backup = text.isEmpty() ? null : backup(path);
            Files.createDirectories(path.getParent());
            Files.writeString(path, updated, StandardCharsets.UTF_8);
            String detail = backup == null ? "" : backup.getFileName().toString();
            if (addedFeature) {
                detail = detail.isEmpty() ? "rmcp_client" : detail + ", rmcp_client";
            }
            return new Result("Codex", State.WRITTEN, detail, path);
        } catch (IOException | RuntimeException failure) {
            return new Result("Codex", State.FAILED, String.valueOf(failure.getMessage()), path);
        }
    }

    /** Заменяет раздел TOML целиком или дописывает его в конец. */
    static String replaceTomlSection(String text, String header, String block) {
        String eol = System.lineSeparator();
        List<String> lines = new ArrayList<>(List.of(text.isEmpty() ? new String[0] : text.split("\r?\n", -1)));
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals(header)) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            StringBuilder out = new StringBuilder(text);
            if (out.length() > 0 && !text.endsWith("\n")) {
                out.append(eol);
            }
            if (out.length() > 0) {
                out.append(eol);
            }
            out.append(block);
            return out.toString();
        }
        int end = lines.size();
        for (int i = start + 1; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("[")) {
                end = i;
                break;
            }
        }
        List<String> result = new ArrayList<>(lines.subList(0, start));
        result.addAll(List.of(block.split("\r?\n", -1)));
        // Пустые хвосты блока не размножаем: между разделами хватает одной строки.
        while (!result.isEmpty() && result.get(result.size() - 1).isBlank()) {
            result.remove(result.size() - 1);
        }
        result.add("");
        result.addAll(lines.subList(end, lines.size()));
        return String.join(eol, result);
    }

    private static boolean hasRmcpClient(String text) {
        for (String line : text.split("\r?\n", -1)) {
            String trimmed = line.trim().replace(" ", "");
            if (trimmed.startsWith("rmcp_client=true")
                    || trimmed.startsWith("experimental_use_rmcp_client=true")) {
                return true;
            }
        }
        return false;
    }

    private static String withRmcpClient(String text) {
        String eol = System.lineSeparator();
        List<String> lines = new ArrayList<>(List.of(text.split("\r?\n", -1)));
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals("[features]")) {
                lines.add(i + 1, "rmcp_client = true");
                return String.join(eol, lines);
            }
        }
        StringBuilder out = new StringBuilder(text);
        if (out.length() > 0 && !text.endsWith("\n")) {
            out.append(eol);
        }
        out.append(eol).append("[features]").append(eol).append("rmcp_client = true").append(eol);
        return out.toString();
    }

    // ------------------------------------------------------------------
    // Мелочи
    // ------------------------------------------------------------------

    /** Копия рядом с файлом: правим чужие настройки, значит откат должен быть под рукой. */
    private static Path backup(Path path) throws IOException {
        String stamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path backup = path.resolveSibling(path.getFileName() + ".intraspect-" + stamp + ".bak");
        // Перезапись, а не отказ: две правки в одну секунду - обычное дело (нажали
        // «Подключить» дважды), и падать из-за существующей копии нельзя.
        Files.copy(path, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return backup;
    }

    /** Ищет строку-ключ {@code "имя"} — именно как ключ, а не как часть значения. */
    private static int indexOfKey(String text, String key) {
        String needle = "\"" + key + "\"";
        int at = text.indexOf(needle);
        while (at >= 0) {
            int after = at + needle.length();
            while (after < text.length() && Character.isWhitespace(text.charAt(after))) {
                after++;
            }
            if (after < text.length() && text.charAt(after) == ':') {
                return at;
            }
            at = text.indexOf(needle, at + 1);
        }
        return -1;
    }

    /**
     * Ищет ключ на верхнем уровне объекта, а не первое совпадение в тексте.
     *
     * <p>В конфиге Claude Code слово {@code mcpServers} встречается у каждого проекта,
     * поэтому поиск по тексту приводил к чужому разделу. Здесь считается глубина
     * вложенности, а строки в кавычках пропускаются.
     */
    private static int indexOfTopLevelKey(String text, String key) {
        String needle = "\"" + key + "\"";
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                if (depth == 1 && text.startsWith(needle, i)) {
                    int after = i + needle.length();
                    while (after < text.length() && Character.isWhitespace(text.charAt(after))) {
                        after++;
                    }
                    if (after < text.length() && text.charAt(after) == ':') {
                        return i;
                    }
                }
                inString = true;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
            }
        }
        return -1;
    }

    private static int openBraceAfter(String text, int from) {
        if (from < 0) {
            return -1;
        }
        return text.indexOf('{', from);
    }

    /** Парная закрывающая скобка с учётом вложенности и строк в кавычках. */
    private static int matchingBrace(String text, int open) {
        if (open < 0 || open >= text.length() || text.charAt(open) != '{') {
            return -1;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String escape(String value) {
        return MiniJson.escape(value == null ? "" : value);
    }

    /** Короткий путь для показа человеку: домашняя папка сжимается до ~. */
    public static String shortPath(Path path) {
        String text = path == null ? "" : path.toString();
        String home = home().toString();
        if (!home.isEmpty() && text.toLowerCase(Locale.ROOT).startsWith(home.toLowerCase(Locale.ROOT))) {
            return "~" + text.substring(home.length());
        }
        return text;
    }
}
