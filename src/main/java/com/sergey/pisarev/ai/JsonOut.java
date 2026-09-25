package com.sergey.pisarev.ai;

import com.sergey.pisarev.util.MiniJson;

import java.util.Locale;

/**
 * Сборка JSON вручную, без сторонних библиотек.
 *
 * <p>Программа портативная и собирается без интернета, поэтому JSON для MCP пишется
 * своим кодом: разбор уже есть в {@link MiniJson}, здесь только запись.
 *
 * <p>Запятые расставляются по последнему записанному символу: после «{», «[» и «:»
 * запятая не нужна, во всех остальных случаях нужна. Это правило полное, поэтому
 * отдельного учёта «пустой ли контейнер» не требуется.
 */
public final class JsonOut {
    private final StringBuilder text = new StringBuilder(256);
    private final StringBuilder open = new StringBuilder(16);

    public static String string(String value) {
        return '"' + MiniJson.escape(value == null ? "" : value) + '"';
    }

    /** Целые числа пишутся без дробной части: так JSON читается глазами. */
    public static String number(double value) {
        if (!Double.isFinite(value)) {
            return "null";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1.0e15) {
            return Long.toString((long) value);
        }
        return String.format(Locale.US, "%.6f", value);
    }

    public JsonOut obj() {
        separate();
        text.append('{');
        open.append('}');
        return this;
    }

    public JsonOut arr() {
        separate();
        text.append('[');
        open.append(']');
        return this;
    }

    public JsonOut end() {
        if (open.length() == 0) {
            throw new IllegalStateException("нечего закрывать");
        }
        text.append(open.charAt(open.length() - 1));
        open.setLength(open.length() - 1);
        return this;
    }

    /** Имя поля объекта; значение пишется следующим вызовом. */
    public JsonOut key(String name) {
        separate();
        text.append(string(name)).append(':');
        return this;
    }

    public JsonOut val(String value) {
        separate();
        text.append(value == null ? "null" : string(value));
        return this;
    }

    public JsonOut val(double value) {
        separate();
        text.append(number(value));
        return this;
    }

    public JsonOut val(boolean value) {
        separate();
        text.append(value ? "true" : "false");
        return this;
    }

    /** Уже готовый JSON — вставляется как есть. */
    public JsonOut raw(String json) {
        separate();
        text.append(json == null || json.isBlank() ? "null" : json);
        return this;
    }

    public JsonOut field(String name, String value) {
        return key(name).val(value);
    }

    public JsonOut field(String name, double value) {
        return key(name).val(value);
    }

    public JsonOut field(String name, boolean value) {
        return key(name).val(value);
    }

    public JsonOut fieldRaw(String name, String json) {
        return key(name).raw(json);
    }

    public String done() {
        if (open.length() > 0) {
            throw new IllegalStateException("JSON не закрыт: осталось уровней " + open.length());
        }
        return text.toString();
    }

    @Override
    public String toString() {
        return text.toString();
    }

    private void separate() {
        if (text.length() == 0) {
            return;
        }
        char last = text.charAt(text.length() - 1);
        if (last == '{' || last == '[' || last == ':') {
            return;
        }
        text.append(',');
    }
}
