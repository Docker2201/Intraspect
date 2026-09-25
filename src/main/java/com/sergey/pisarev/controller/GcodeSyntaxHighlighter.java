package com.sergey.pisarev.controller;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Раскраска самого текста программы: G — своим цветом, оси — своим, и так далее.
 *
 * <p>До этого весь текст был одного цвета, и в двухканальной программе на 300 строк
 * глазами ничего не находилось. Отдельным цветом выделены слова синхронизации каналов
 * ({@code N_WAITM}, {@code SETM}): в двухканальной программе именно по ним видно, где
 * головки ждут друг друга.
 *
 * <p>Порядок в выражении важен: сначала комментарий и строка, потом конкретные слова,
 * и только в конце имена и числа — иначе {@code G1} попало бы в «имя».
 */
final class GcodeSyntaxHighlighter {
    private static final String[] GROUPS = {
            "COMMENT", "STRING", "FRAME", "LABEL", "SYNC", "KEYWORD",
            "ARCCW", "ARCCCW", "GCODE", "MCODE", "TOOL", "FEED", "AXIS", "NAME", "NUMBER"
    };

    private static final Pattern PATTERN = Pattern.compile(
            "(?<COMMENT>;[^\\n]*)"
                    + "|(?<STRING>\"[^\"\\n]*\")"
                    + "|(?<FRAME>^[ \\t]*N\\d+)"
                    + "|(?<LABEL>^[ \\t]*[A-Za-z_][A-Za-z0-9_]*[ \\t]*:)"
                    + "|(?<SYNC>\\b(?:N_WAITM|WAITM|SETM|CLEARM|N_CHKCH|N_TWOCHANNELS|N_ENABLE"
                    + "|N_DISABLE|N_CHKWP)\\b)"
                    + "|(?<KEYWORD>\\b(?:GOTOF|GOTOB|GOTO|IF|ELSE|ENDIF|WHILE|ENDWHILE|REPEAT"
                    + "|FOR|ENDFOR|DEF|EXTERN|STOPRE|SUPA|MSG|DIAMON|DIAMOF|DIAM90|WORKPIECE"
                    + "|TRANS|ATRANS|ROT|SCALE|MIRROR|NOT|AND|OR|TRUE|FALSE|RET)\\b)"
                    // Дуги отдельными цветами: по часовой и против часовой путают
                    // чаще всего, а в кадре они отличаются одной цифрой.
                    + "|(?<ARCCW>(?<![A-Za-z0-9_$])G0?2(?![0-9]))"
                    + "|(?<ARCCCW>(?<![A-Za-z0-9_$])G0?3(?![0-9]))"
                    + "|(?<GCODE>(?<![A-Za-z0-9_$])G\\d{1,3}(?![0-9]))"
                    + "|(?<MCODE>(?<![A-Za-z0-9_$])M\\d{1,4}(?![0-9]))"
                    + "|(?<TOOL>(?<![A-Za-z0-9_$])[TD]\\d{1,4}(?![0-9]))"
                    + "|(?<FEED>(?<![A-Za-z0-9_$])(?:LIMS|OFFN|ADISPOS|ADIS|FA|[FS])(?=\\s*=?\\s*[-+.\\d]))"
                    + "|(?<AXIS>(?<![A-Za-z0-9_$])[XYZUVWABCIJK](?=\\s*=?\\s*[-+.\\dA-Za-z($]))"
                    + "|(?<NAME>[A-Za-z_$][A-Za-z0-9_$]*)"
                    + "|(?<NUMBER>[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private GcodeSyntaxHighlighter() {
    }

    /** Разметка для текста программы. Пустой текст — пустая разметка. */
    static StyleSpans<Collection<String>> highlight(String text) {
        StyleSpansBuilder<Collection<String>> spans = new StyleSpansBuilder<>();
        if (text == null || text.isEmpty()) {
            spans.add(Collections.emptyList(), 0);
            return spans.create();
        }
        Matcher matcher = PATTERN.matcher(text);
        int last = 0;
        while (matcher.find()) {
            String group = matchedGroup(matcher);
            if (group == null) {
                continue;
            }
            spans.add(Collections.emptyList(), matcher.start() - last);
            spans.add(List.of("gcode-" + group.toLowerCase(java.util.Locale.US)),
                    matcher.end() - matcher.start());
            last = matcher.end();
        }
        spans.add(Collections.emptyList(), text.length() - last);
        return spans.create();
    }

    private static String matchedGroup(Matcher matcher) {
        for (String group : GROUPS) {
            if (matcher.group(group) != null) {
                return group;
            }
        }
        return null;
    }
}
