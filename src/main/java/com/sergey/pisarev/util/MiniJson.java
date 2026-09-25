package com.sergey.pisarev.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Минимальный разбор JSON под нужды локального хранения (массив плоских объектов).
 *
 * <p>Появился вместо поиска через {@code indexOf}: тот обрывал строку на первой кавычке
 * и считал объект до первой закрывающей скобки, поэтому имя инструмента с кавычкой или
 * фигурной скобкой рушило всю библиотеку. Здесь разбор идёт по символам, с учётом
 * экранирования, поэтому такие значения читаются обратно как записаны.
 *
 * <p>Значения возвращаются как {@link String}, {@link Double}, {@link Boolean} или null.
 */
public final class MiniJson {

    /** Ошибка разбора: текст не является ожидаемым JSON. */
    public static final class JsonException extends RuntimeException {
        JsonException(String message) {
            super(message);
        }
    }

    private final String source;
    private int pos;

    private MiniJson(String source) {
        this.source = source;
    }

    /** Разбирает массив объектов: {@code [ { ... }, { ... } ]}. */
    public static List<Map<String, Object>> parseObjectArray(String json) {
        MiniJson parser = new MiniJson(json == null ? "" : json);
        parser.skipWhitespace();
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos < parser.source.length()) {
            throw new JsonException("лишние символы после JSON на позиции " + parser.pos);
        }
        if (!(value instanceof List<?> list)) {
            throw new JsonException("ожидался массив объектов");
        }
        List<Map<String, Object>> objects = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> typed = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    typed.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                objects.add(typed);
            }
        }
        return objects;
    }

    /**
     * Разбирает один объект: {@code { ... }}. Нужен для запросов JSON-RPC от MCP.
     *
     * <p>Пустая карта вместо исключения не годится: неразобранный запрос надо отличать
     * от запроса без полей, поэтому здесь бросается {@link JsonException}.
     */
    public static Map<String, Object> parseObject(String json) {
        MiniJson parser = new MiniJson(json == null ? "" : json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos < parser.source.length()) {
            throw new JsonException("лишние символы после JSON на позиции " + parser.pos);
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new JsonException("ожидался объект JSON");
        }
        Map<String, Object> typed = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            typed.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return typed;
    }

    /** Экранирование строки для записи в JSON. */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    /** Строковое поле объекта или значение по умолчанию. */
    public static String string(Map<String, Object> object, String key, String fallback) {
        Object value = object.get(key);
        if (value instanceof String text) {
            return text;
        }
        return value != null ? String.valueOf(value) : fallback;
    }

    /** Числовое поле объекта или значение по умолчанию. */
    public static double number(Map<String, Object> object, String key, double fallback) {
        Object value = object.get(key);
        if (value instanceof Double number) {
            return number;
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    // ------------------------------------------------------------------
    // Разбор
    // ------------------------------------------------------------------

    private Object parseValue() {
        skipWhitespace();
        if (pos >= source.length()) {
            throw new JsonException("неожиданный конец JSON");
        }
        char c = source.charAt(pos);
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            default -> parseNumber();
        };
    }

    private Map<String, Object> parseObject() {
        expect('{');
        Map<String, Object> result = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            ++pos;
            return result;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            result.put(key, parseValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                ++pos;
                continue;
            }
            expect('}');
            return result;
        }
    }

    private List<Object> parseArray() {
        expect('[');
        List<Object> result = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            ++pos;
            return result;
        }
        while (true) {
            result.add(parseValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                ++pos;
                continue;
            }
            expect(']');
            return result;
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            if (pos >= source.length()) {
                throw new JsonException("строка не закрыта");
            }
            char c = source.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (pos >= source.length()) {
                throw new JsonException("оборванное экранирование");
            }
            char escaped = source.charAt(pos++);
            switch (escaped) {
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (pos + 4 > source.length()) {
                        throw new JsonException("короткая \\u-последовательность");
                    }
                    out.append((char) Integer.parseInt(source.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> throw new JsonException("неизвестное экранирование \\" + escaped);
            }
        }
    }

    private Double parseNumber() {
        int start = pos;
        if (peek() == '-' || peek() == '+') {
            ++pos;
        }
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (Character.isDigit(c) || c == '.' || c == 'e' || c == 'E' || c == '-' || c == '+') {
                ++pos;
            } else {
                break;
            }
        }
        String text = source.substring(start, pos);
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException exception) {
            throw new JsonException("не число: " + text);
        }
    }

    private Boolean parseBoolean() {
        if (source.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (source.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw new JsonException("ожидалось true/false на позиции " + pos);
    }

    private Object parseNull() {
        if (source.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new JsonException("ожидалось null на позиции " + pos);
    }

    private char peek() {
        if (pos >= source.length()) {
            throw new JsonException("неожиданный конец JSON");
        }
        return source.charAt(pos);
    }

    private void expect(char expected) {
        skipWhitespace();
        if (pos >= source.length() || source.charAt(pos) != expected) {
            throw new JsonException("ожидался '" + expected + "' на позиции " + pos);
        }
        ++pos;
    }

    private void skipWhitespace() {
        while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) {
            ++pos;
        }
    }
}
