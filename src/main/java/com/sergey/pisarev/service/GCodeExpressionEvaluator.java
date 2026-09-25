package com.sergey.pisarev.service;

import com.sergey.pisarev.util.I18n;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Вычисление выражений Siemens/Fanuc: 205+6.5, -1751+R1, Z=-429-13.4.
 */
public final class GCodeExpressionEvaluator {
    private static final Pattern R_VARIABLE = Pattern.compile("R(\\d+)", Pattern.CASE_INSENSITIVE);
    // Определение R-переменной: «R1=5», «R1 = 5» или табличная форма «R30 0.034».
    // Разделитель обязателен (= или пробел), иначе «X=...+R30 ANG=...» ошибочно
    // распознавалось как «R3 = 0» и затирало настоящие R-переменные.
    private static final Pattern R_DEFINITION = Pattern.compile(
            "(?<![A-Za-z0-9_])R(\\d+)(?:\\s*=\\s*|\\s+)([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)",
            Pattern.CASE_INSENSITIVE);
    /** Встроенные величины Siemens. DOUBLE_PI — 2π: радиус на станке колеса, а не диаметр. */
    private static final Map<String, Double> CONSTANTS = Map.of(
            "PI", Math.PI,
            "DOUBLE_PI", 2.0 * Math.PI,
            "HALF_PI", Math.PI / 2.0);

    private GCodeExpressionEvaluator() {
    }

    public static void collectRDefinitions(String rawLine, Map<String, Double> rVariables) {
        if (rawLine == null || rVariables == null) {
            return;
        }
        Matcher matcher = R_DEFINITION.matcher(rawLine);
        while (matcher.find()) {
            Double value = parseNumber(matcher.group(2));
            if (value != null) {
                rVariables.put("R" + matcher.group(1), value);
            }
        }
    }

    public static Double evaluate(String expression, Map<String, Double> rVariables) {
        return evaluate(expression, rVariables, null);
    }

    /**
     * Вычисляет выражение с нормальным приоритетом операций.
     *
     * <p>Раньше здесь была просто сумма слагаемых, поэтому умножение и деление молча
     * терялись: {@code R1*2} при R1=10 давало 10 вместо 20. Теперь разбор рекурсивный:
     * {@code + -} ниже, {@code * /} выше, поддержаны скобки и унарный минус.
     *
     * @param unknownVariables если задан, сюда складываются имена неизвестных R-переменных
     *                         (их значение по-прежнему считается нулевым, но факт виден)
     */
    public static Double evaluate(String expression, Map<String, Double> rVariables,
                                  java.util.Set<String> unknownVariables) {
        if (expression == null) {
            return null;
        }
        String normalized = expression.replace(',', '.').trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            Parser parser = new Parser(normalized, rVariables, unknownVariables);
            double value = parser.parseSum();
            parser.skipSpaces();
            if (!parser.atEnd()) {
                return null;
            }
            return value;
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }

    /** Рекурсивный разбор: сумма → произведение → множитель. */
    private static final class Parser {
        private final String text;
        private final Map<String, Double> variables;
        private final java.util.Set<String> unknown;
        private int pos;

        Parser(String text, Map<String, Double> variables, java.util.Set<String> unknown) {
            this.text = text;
            this.variables = variables;
            this.unknown = unknown;
        }

        double parseSum() {
            double value = parseProduct();
            while (true) {
                skipSpaces();
                if (atEnd()) {
                    return value;
                }
                char c = text.charAt(pos);
                if (c == '+') {
                    ++pos;
                    value += parseProduct();
                } else if (c == '-') {
                    ++pos;
                    value -= parseProduct();
                } else {
                    return value;
                }
            }
        }

        private double parseProduct() {
            double value = parseFactor();
            while (true) {
                skipSpaces();
                if (atEnd()) {
                    return value;
                }
                char c = text.charAt(pos);
                if (c == '*') {
                    ++pos;
                    value *= parseFactor();
                } else if (c == '/') {
                    ++pos;
                    double divisor = parseFactor();
                    if (divisor == 0.0) {
                        throw new ArithmeticException(I18n.text("expr.error.divzero"));
                    }
                    value /= divisor;
                } else {
                    return value;
                }
            }
        }

        private double parseFactor() {
            skipSpaces();
            if (atEnd()) {
                throw new NumberFormatException(I18n.text("expr.error.cut"));
            }
            char c = text.charAt(pos);
            if (c == '+') {
                ++pos;
                return parseFactor();
            }
            if (c == '-') {
                ++pos;
                return -parseFactor();
            }
            if (c == '(') {
                ++pos;
                double inner = parseSum();
                skipSpaces();
                if (atEnd() || text.charAt(pos) != ')') {
                    throw new NumberFormatException(I18n.text("expr.error.paren"));
                }
                ++pos;
                return inner;
            }
            // Имя: R-переменная (R1), переменная параметрической программы
            // (TREAD_DIAM), системная ($P_TOOLR), функция (RTOI(...)) или элемент
            // массива (N_CHUCK_HEIGHT_Z_S1[N_CHUCK_JAWS]). До этого читались только
            // R-переменные, и вся двухканальная программа колеса, где размеры заданы
            // именами, давала нулевые координаты.
            if (c == '$' || c == '_' || Character.isLetter(c)) {
                int start = pos;
                ++pos;
                while (!atEnd()) {
                    char ch = text.charAt(pos);
                    if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '$') {
                        ++pos;
                        continue;
                    }
                    break;
                }
                String name = text.substring(start, pos).toUpperCase(Locale.US);
                if (!atEnd() && text.charAt(pos) == '(') {
                    ++pos;
                    double argument = parseSum();
                    skipSpaces();
                    if (atEnd() || text.charAt(pos) != ')') {
                        throw new NumberFormatException(I18n.format("expr.error.funcparen", name));
                    }
                    ++pos;
                    return applyFunction(name, argument);
                }
                if (!atEnd() && text.charAt(pos) == '[') {
                    ++pos;
                    double index = parseSum();
                    skipSpaces();
                    if (atEnd() || text.charAt(pos) != ']') {
                        throw new NumberFormatException(I18n.format("expr.error.arrayparen", name));
                    }
                    ++pos;
                    name = name + "[" + Math.round(index) + "]";
                }
                if (name.equals("TRUE")) {
                    return 1.0;
                }
                if (name.equals("FALSE")) {
                    return 0.0;
                }
                Double constant = CONSTANTS.get(name);
                if (constant != null) {
                    return constant;
                }
                Double value = variables != null ? variables.get(name) : null;
                if (value == null) {
                    // Значение неизвестно. Поведение прежнее (ноль), но факт фиксируем,
                    // чтобы диагностика могла сказать об этом вслух.
                    if (unknown != null) {
                        unknown.add(name);
                    }
                    return 0.0;
                }
                return value;
            }
            int start = pos;
            while (!atEnd()) {
                char digit = text.charAt(pos);
                if (Character.isDigit(digit) || digit == '.') {
                    ++pos;
                } else {
                    break;
                }
            }
            if (start == pos) {
                throw new NumberFormatException(I18n.format("expr.error.number", pos));
            }
            return Double.parseDouble(text.substring(start, pos));
        }

        /**
         * Функции Siemens внутри выражения.
         *
         * <p>IC/AC/DC и родня — не вычисления, а признак «размер инкрементный или
         * абсолютный». Значение возвращается как есть, а признак читает тот, кто
         * разбирает кадр: иначе {@code X=IC(...)} уехал бы в абсолютную координату.
         */
        private double applyFunction(String name, double argument) {
            switch (name) {
                case "IC":
                case "AC":
                case "DC":
                case "ACP":
                case "ACN":
                case "CIC":
                case "CAC":
                    return argument;
                case "RTOI":
                case "TRUNC":
                    return (double) (long) argument;
                case "ITOR":
                    return argument;
                case "ROUND":
                    return Math.round(argument);
                case "ABS":
                    return Math.abs(argument);
                case "SQRT":
                    return Math.sqrt(Math.max(0.0, argument));
                case "POT":
                    return argument * argument;
                case "SIN":
                    return Math.sin(Math.toRadians(argument));
                case "COS":
                    return Math.cos(Math.toRadians(argument));
                case "TAN":
                    return Math.tan(Math.toRadians(argument));
                case "ASIN":
                    return Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, argument))));
                case "ACOS":
                    return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, argument))));
                case "ATAN":
                    return Math.toDegrees(Math.atan(argument));
                case "LN":
                    return argument > 0.0 ? Math.log(argument) : 0.0;
                case "EXP":
                    return Math.exp(argument);
                default:
                    // Неизвестная функция: значение аргумента — ближе к правде, чем ноль,
                    // и имя попадает в список непонятного.
                    if (unknown != null) {
                        unknown.add(name + "()");
                    }
                    return argument;
            }
        }

        void skipSpaces() {
            while (!atEnd() && Character.isWhitespace(text.charAt(pos))) {
                ++pos;
            }
        }

        boolean atEnd() {
            return pos >= text.length();
        }
    }

    private static Double parseNumber(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(token.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
