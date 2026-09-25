package com.sergey.pisarev.service;

import com.sergey.pisarev.util.I18n;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Чей это файл: какой канал, какая сторона, или это программа параметров.
 *
 * <p>Двухканальная программа колеса лежит пятью файлами: параметры и четыре
 * обрабатывающих — два канала по две стороны. Раскладывать их руками по окнам
 * незачем: в каждом файле написано, чей он, а если заголовок потеряли, видно по
 * осям. Канал 2 на таком станке ходит по осям U/W, канал 1 — по X/Z.
 */
public final class ProgramFileRole {
    private static final Pattern PARAMETER_HEADER = Pattern.compile(
            ";\\s*Program-type\\s*:?\\s*Parameter\\s+Program", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHANNEL_HEADER = Pattern.compile(
            ";\\s*Channel\\s*:?\\s*([12])\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SIDE_HEADER = Pattern.compile(
            ";\\s*Machining\\s+side\\s*:?\\s*([12])\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHECK_CHANNEL = Pattern.compile(
            "\\bN_CHKCH\\s*\\(\\s*([12])\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_CHANNEL_SIDE = Pattern.compile(
            "_C([12])(?:_S([12]))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_SIDE = Pattern.compile("_S([12])\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SIDE_VARIABLE = Pattern.compile(
            "N_(?:CHUCK_HEIGHT|WP_ZP)_[XZUW]_S([12])\\b", Pattern.CASE_INSENSITIVE);

    private ProgramFileRole() {
    }

    public enum Kind {
        /** Программа параметров: одни размеры, ни одного хода. */
        PARAMETER,
        /** Обрабатывающая программа. */
        MACHINING
    }

    /**
     * @param channel 1 или 2; 0 — определить не удалось
     * @param side    1 (внутренняя) или 2 (наружная); 0 — определить не удалось
     */
    public record Role(Kind kind, int channel, int side, char horizontalAxis, char verticalAxis) {
        public boolean isParameter() {
            return kind == Kind.PARAMETER;
        }

        /** Подпись для окна и списка: «Канал 2, сторона 1». */
        public String shortLabel() {
            if (kind == Kind.PARAMETER) {
                return I18n.text("file.role.parameters");
            }
            StringBuilder text = new StringBuilder();
            text.append(I18n.format("file.role.channel", channel > 0 ? String.valueOf(channel) : "?"));
            if (side > 0) {
                text.append(I18n.format("file.role.side", side));
            }
            return text.toString();
        }
    }

    /** Разбирает роль по имени файла и тексту. Имя может быть пустым. */
    public static Role detect(String fileName, String text) {
        String name = fileName == null ? "" : fileName;
        String body = text == null ? "" : text;
        boolean parameter = PARAMETER_HEADER.matcher(body).find()
                || name.toUpperCase(Locale.US).contains("PARAMETER")
                || MachineParameterProgram.looksLikeParameterProgram(body);
        int channel = readChannel(name, body);
        int side = readSide(name, body);
        if (parameter) {
            return new Role(Kind.PARAMETER, 0, 0, 'X', 'Z');
        }
        char horizontal = channel == 2 ? 'U' : 'X';
        char vertical = channel == 2 ? 'W' : 'Z';
        if (channel == 0) {
            // Заголовка нет: смотрим, по каким осям программа реально ходит.
            char[] axes = detectAxisLetters(body);
            horizontal = axes[0];
            vertical = axes[1];
            channel = horizontal == 'U' ? 2 : 1;
        }
        return new Role(Kind.MACHINING, channel, side, horizontal, vertical);
    }

    /**
     * По каким осям написана программа: X/Z или U/W.
     *
     * <p>Считаем только исполняемые строки: в комментариях шапки букв хватает и без
     * ходов, а решать надо по движению.
     */
    public static char[] detectAxisLetters(String programText) {
        if (programText == null || programText.isBlank()) {
            return new char[]{'X', 'Z'};
        }
        int primary = 0;
        int secondary = 0;
        for (String rawLine : programText.split("\\R")) {
            if (GCodeProgramParser.isNonExecutableProgramLine(rawLine)) {
                continue;
            }
            String line = GCodeProgramParser.stripFrameNumberForParse(
                    GCodeProgramParser.stripCommentsForParse(rawLine)).toUpperCase(Locale.US);
            if (GCodeProgramParser.indexOfAxisToken(line, 'X') >= 0
                    || GCodeProgramParser.indexOfAxisToken(line, 'Z') >= 0) {
                primary++;
            }
            if (GCodeProgramParser.indexOfAxisToken(line, 'U') >= 0
                    || GCodeProgramParser.indexOfAxisToken(line, 'W') >= 0) {
                secondary++;
            }
        }
        return secondary > primary ? new char[]{'U', 'W'} : new char[]{'X', 'Z'};
    }

    private static int readChannel(String name, String body) {
        Matcher header = CHANNEL_HEADER.matcher(body);
        if (header.find()) {
            return Integer.parseInt(header.group(1));
        }
        Matcher check = CHECK_CHANNEL.matcher(body);
        if (check.find()) {
            return Integer.parseInt(check.group(1));
        }
        Matcher fromName = NAME_CHANNEL_SIDE.matcher(name);
        if (fromName.find()) {
            return Integer.parseInt(fromName.group(1));
        }
        return 0;
    }

    private static int readSide(String name, String body) {
        Matcher header = SIDE_HEADER.matcher(body);
        if (header.find()) {
            return Integer.parseInt(header.group(1));
        }
        Matcher fromName = NAME_CHANNEL_SIDE.matcher(name);
        if (fromName.find() && fromName.group(2) != null) {
            return Integer.parseInt(fromName.group(2));
        }
        Matcher sideOnly = NAME_SIDE.matcher(name);
        if (sideOnly.find()) {
            return Integer.parseInt(sideOnly.group(1));
        }
        // Последний признак — имя нулевой точки: N_WP_ZP_X_S2, N_CHUCK_HEIGHT_W_S1.
        Matcher variable = SIDE_VARIABLE.matcher(body);
        if (variable.find()) {
            return Integer.parseInt(variable.group(1));
        }
        return 0;
    }
}
