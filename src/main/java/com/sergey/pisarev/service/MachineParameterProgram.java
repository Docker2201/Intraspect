package com.sergey.pisarev.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Параметрическая программа станка: имена размеров и их значения.
 *
 * <p>На двухканальных станках Hegenscheidt обрабатывающая программа не содержит
 * размеров вообще. Она ходит по именам — {@code X=TREAD_DIAM+30},
 * {@code Z=WHEEL_HEIGHT+12} — а сами значения лежат в отдельной программе
 * параметров, которую оператор правит под исполнение колеса. Без неё траектория
 * читается как нулевая, поэтому значения нужно взять оттуда и подставить.
 *
 * <p>Порядок важен: строка вида {@code B=A+10} считается по уже прочитанным
 * строкам, как на станке.
 */
public final class MachineParameterProgram {
    /** Имя и выражение: «TREAD_DIAM=(800)/2», «DEF REAL V_CUT=120», «N_SYM_FACTOR=0.5». */
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "^\\s*(?:N\\d+\\s+)?(DEF\\s+(?:INT|REAL|BOOL|CHAR|STRING|AXIS|FRAME)\\s+)?"
                    + "([A-Za-z_$][A-Za-z0-9_$]*(?:\\[[^\\]]*\\])?)\\s*=\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);

    /** Заголовок, которым сам Siemens отмечает такую программу. */
    private static final Pattern PARAMETER_HEADER = Pattern.compile(
            ";\\s*Program-type\\s*:?\\s*Parameter\\s+Program", Pattern.CASE_INSENSITIVE);

    /** Слова кадра: имя переменной не может быть подачей или скоростью. */
    private static final Set<String> FRAME_WORDS = Set.of(
            "F", "S", "T", "D", "M", "H", "L", "P", "X", "Y", "Z", "A", "B", "C", "U", "V", "W",
            "I", "J", "K", "R", "CR", "ANG", "RND", "CHF", "RNDM", "LIMS", "OFFN", "ADISPOS",
            "ADIS", "FA", "FZ", "FAD", "SPOS", "SPOSA", "AX", "IC", "AC", "DC", "QU", "MSG",
            "FB", "FL", "OVR", "SCALE", "ORI", "SD", "STAT", "TU", "SF", "DITS", "DITE");

    private MachineParameterProgram() {
    }

    /** Значения параметров и имена, которые посчитать не удалось. */
    public record Variables(Map<String, Double> values, List<String> unresolved) {
        public boolean isEmpty() {
            return values.isEmpty();
        }

        public int size() {
            return values.size();
        }

        public Double value(String name) {
            return name == null ? null : values.get(name.toUpperCase(Locale.US));
        }

        public static Variables empty() {
            return new Variables(Map.of(), List.of());
        }
    }

    /** Похож ли текст на программу параметров, а не на обрабатывающую. */
    public static boolean looksLikeParameterProgram(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (PARAMETER_HEADER.matcher(text).find()) {
            return true;
        }
        // Заголовка может не быть. Тогда признак — присваивания имён и ни одного хода.
        int assignments = 0;
        for (String rawLine : text.split("\\R")) {
            String line = stripComment(rawLine);
            if (line.isBlank()) {
                continue;
            }
            if (GCodeProgramParser.indexOfAxisToken(line.toUpperCase(Locale.US), 'X') >= 0
                    || GCodeProgramParser.indexOfAxisToken(line.toUpperCase(Locale.US), 'U') >= 0) {
                return false;
            }
            Matcher matcher = ASSIGNMENT.matcher(line);
            if (matcher.matches() && isParameterName(matcher.group(2), matcher.group(1) != null, true)) {
                assignments++;
            }
        }
        return assignments >= 3;
    }

    /** Читает программу параметров сверху вниз. */
    public static Variables read(String parameterProgramText) {
        return read(parameterProgramText, Map.of());
    }

    /**
     * Читает программу параметров, начиная с уже известных значений.
     *
     * @param seed значения, известные до чтения (например, из настроек станка)
     */
    public static Variables read(String parameterProgramText, Map<String, Double> seed) {
        Map<String, Double> values = new TreeMap<>();
        if (seed != null) {
            seed.forEach((name, value) -> values.put(name.toUpperCase(Locale.US), value));
        }
        Set<String> unresolved = new LinkedHashSet<>();
        if (parameterProgramText == null || parameterProgramText.isBlank()) {
            return new Variables(values, List.of());
        }
        for (String rawLine : parameterProgramText.split("\\R")) {
            collectAssignments(rawLine, values, unresolved, true);
        }
        // Имя, которое в итоге посчиталось, непонятным считать нельзя: в параметрах
        // ссылка вперёд встречается (строка ниже задаёт то, что выше уже прочитали).
        unresolved.removeIf(values::containsKey);
        return new Variables(values, new ArrayList<>(unresolved));
    }

    /**
     * Забирает присваивания из одной строки.
     *
     * @param permissive в программе параметров имя может быть любым; в обрабатывающей
     *                   программе берутся только явные переменные (с подчёркиванием,
     *                   системные или объявленные через DEF), иначе {@code F=0.8} и
     *                   {@code LIMS=45} осели бы в таблице размеров
     */
    public static void collectAssignments(String rawLine, Map<String, Double> values,
                                          Set<String> unresolved, boolean permissive) {
        if (rawLine == null || values == null) {
            return;
        }
        String line = stripComment(rawLine);
        if (line.isBlank()) {
            return;
        }
        Matcher matcher = ASSIGNMENT.matcher(line);
        if (!matcher.matches()) {
            return;
        }
        boolean declared = matcher.group(1) != null;
        String name = matcher.group(2).toUpperCase(Locale.US);
        if (!isParameterName(name, declared, permissive)) {
            return;
        }
        String expression = matcher.group(3).trim();
        // Хвост кадра отсекаем: «N_CHUCK_JAWS=1 STOPRE» и «X=5 F=0.8» — одно значение.
        int space = expression.indexOf(' ');
        if (space > 0 && expression.indexOf('(') < 0 && expression.indexOf('[') < 0) {
            expression = expression.substring(0, space);
        }
        Set<String> unknownHere = new LinkedHashSet<>();
        Double value = GCodeExpressionEvaluator.evaluate(expression, values, unknownHere);
        if (value == null || !Double.isFinite(value)) {
            if (unresolved != null) {
                unresolved.add(name);
            }
            return;
        }
        if (!unknownHere.isEmpty() && unresolved != null) {
            unresolved.addAll(unknownHere);
        }
        values.put(name, value);
    }

    private static boolean isParameterName(String name, boolean declared, boolean permissive) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String upper = name.toUpperCase(Locale.US);
        int bracket = upper.indexOf('[');
        String bare = bracket > 0 ? upper.substring(0, bracket) : upper;
        if (FRAME_WORDS.contains(bare)) {
            return false;
        }
        if (permissive || declared) {
            return true;
        }
        return bare.indexOf('_') > 0 || bare.startsWith("$");
    }

    private static String stripComment(String line) {
        if (line == null) {
            return "";
        }
        int semi = line.indexOf(';');
        return semi >= 0 ? line.substring(0, semi) : line;
    }
}
