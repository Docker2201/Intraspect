package com.sergey.pisarev.service;

import com.sergey.pisarev.model.WorkpieceDefinition;
import com.sergey.pisarev.util.I18n;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор программы на проблемы — как подсветка ошибок в IDE.
 *
 * <p>Две степени критичности:
 * <ul>
 *   <li>{@link Severity#ERROR} (красная) — без исправления 3D-симуляция не запустится;</li>
 *   <li>{@link Severity#WARNING} (жёлтая) — запустится, но результат может быть не тем,
 *       что ожидает технолог.</li>
 * </ul>
 *
 * <p>Часть проблем умеет чинить себя сама: у диагностики есть {@code autoFix} —
 * функция «текст программы → исправленный текст». Правки, которые меняют не текст,
 * а настройки (например, добор инструментов в библиотеку), помечены кодом и
 * выполняются вызывающей стороной.
 */
public final class ProgramDiagnostics {

    public enum Severity {
        WARNING,
        ERROR
    }

    /** Код: нужные инструменты отсутствуют в библиотеке (правка не текстовая). */
    public static final String CODE_TOOLS_MISSING = "E004";

    /** Код: смещения нулевой точки из программы не заведены в настройках (правка не текстовая). */
    public static final String CODE_WORK_OFFSETS_MISSING = "W104";

    /** Код: заготовка в программе меньше траектории. */
    public static final String CODE_BLANK_TOO_SMALL = "W106";
    /**
     * Программа точит с двух концов, а второй ноль не задан.
     *
     * <p>Так написаны программы осей: одна половина от G54 (один торец), другая от G55
     * (другой торец). Пока Z второго нуля не вписан в настройки, обе половины ложатся
     * от одного нуля — деталь выходит длиннее себя и с перехватом посередине.
     */
    public static final String CODE_TWO_ENDS_NO_OFFSET = "W107";


    /** Код: в библиотеке нет точной пары «номер T + кромка D» (правка не текстовая). */
    public static final String CODE_TOOL_EDGES_MISSING = "E006";

    /** Код: рабочие ходы есть, но ни у одного нет активного инструмента. */
    public static final String CODE_NO_ACTIVE_TOOL = "E007";

    private static final Pattern TOOL_CALL = Pattern.compile("\\bT\\s*=?\\s*(\\d{1,4})\\b");
    // M17 — конец подпрограммы (.SPF). Двухканальные программы станка написаны
    // подпрограммами: требовать от них M30 неверно.
    private static final Pattern PROGRAM_END = Pattern.compile("\\bM0*(?:2|17|30)\\b");
    private static final Pattern COMP_ON = Pattern.compile("\\bG0*4[12]\\b");
    private static final Pattern COMP_OFF = Pattern.compile("\\bG0*40\\b");
    private static final Pattern CUTTING_MOVE = Pattern.compile("\\bG0*[123]\\b");
    /** G54…G73 и G505…G599 — коды смещений нулевой точки Siemens. */
    private static final Pattern WORK_OFFSET_CALL =
            Pattern.compile("(?<![A-Za-z0-9])G(\\d{2,3})(?![0-9])");
    private static final Pattern FEED_VALUE = Pattern.compile("\\bF\\s*=?\\s*\\d");

    /** Одна найденная проблема. */
    public static final class Diagnostic {
        private final String code;
        private final Severity severity;
        private final String title;
        private final String details;
        private final int line;
        private final String autoFixLabel;
        private final UnaryOperator<String> autoFix;

        Diagnostic(String code, Severity severity, String title, String details,
                   int line, String autoFixLabel, UnaryOperator<String> autoFix) {
            this.code = code;
            this.severity = severity;
            this.title = title;
            this.details = details;
            this.line = line;
            this.autoFixLabel = autoFixLabel;
            this.autoFix = autoFix;
        }

        public String code() {
            return this.code;
        }

        public Severity severity() {
            return this.severity;
        }

        public String title() {
            return this.title;
        }

        public String details() {
            return this.details;
        }

        /** Номер строки программы (с 1); 0 — проблема не привязана к строке. */
        public int line() {
            return this.line;
        }

        /** Подпись кнопки автоисправления или null, если чинить автоматически нечем. */
        public String autoFixLabel() {
            return this.autoFixLabel;
        }

        public boolean hasTextAutoFix() {
            return this.autoFix != null;
        }

        /** Применяет автоисправление к тексту программы. */
        public String applyAutoFix(String programText) {
            return this.autoFix == null ? programText : this.autoFix.apply(programText);
        }

        @Override
        public String toString() {
            return this.code + " " + this.severity + " " + this.title;
        }
    }

    private ProgramDiagnostics() {
    }

    /**
     * Проверяет программу.
     *
     * @param programText       текст G-code
     * @param moveCount         сколько перемещений разобралось (0 — строить нечего)
     * @param libraryToolNumbers номера инструментов, заведённые в библиотеке
     * @param inferredWorkpiece  заготовка, вычисленная по траектории (null — вычислить не вышло);
     *                           нужна, чтобы предложить автоподстановку WORKPIECE
     */
    public static List<Diagnostic> analyze(
            String programText,
            int moveCount,
            Collection<Integer> libraryToolNumbers,
            WorkpieceDefinition inferredWorkpiece) {
        return analyze(programText, moveCount, libraryToolNumbers, inferredWorkpiece, null);
    }

    /**
     * То же самое, но с проверкой смещений нулевой точки.
     *
     * @param configuredWorkOffsetCodes коды G54…, заведённые в настройках;
     *                                  null — смещения не проверять
     */
    public static List<Diagnostic> analyze(
            String programText,
            int moveCount,
            Collection<Integer> libraryToolNumbers,
            WorkpieceDefinition inferredWorkpiece,
            Collection<Integer> configuredWorkOffsetCodes) {

        List<Diagnostic> found = new ArrayList<>();
        String text = programText == null ? "" : programText;
        String upper = stripComments(text).toUpperCase(Locale.US);

        // --- Красные: без них 3D не поедет ---

        if (text.isBlank()) {
            found.add(new Diagnostic("E001", Severity.ERROR,
                    I18n.text("diag.001"),
                    I18n.text("diag.002"),
                    0, null, null));
            return found;
        }

        if (moveCount <= 0) {
            found.add(new Diagnostic("E001", Severity.ERROR,
                    I18n.text("diag.003"),
                    I18n.text("diag.004")
                            + I18n.text("diag.005")
                            + I18n.text("diag.006"),
                    0, null, null));
        }

        if (moveCount > 0 && GCodeProgramParser.findWorkpiece(text).isEmpty()) {
            UnaryOperator<String> fix = null;
            String fixLabel = null;
            if (inferredWorkpiece != null && inferredWorkpiece.isValid()) {
                String line = workpieceLine(inferredWorkpiece);
                fix = source -> insertLineAtTop(source, line);
                fixLabel = I18n.text("diag.007");
            }
            found.add(new Diagnostic("E002", Severity.ERROR,
                    I18n.text("diag.008"),
                    I18n.text("diag.009")
                            + (fix != null
                            ? I18n.text("diag.010")
                            : I18n.text("diag.011")),
                    0, fixLabel, fix));
        }

        // --- Инструменты ---

        Map<Integer, Integer> toolByLine = GCodeProgramParser.parseToolNumberByLine(text);
        Set<Integer> usedTools = new LinkedHashSet<>(toolByLine.values());
        usedTools.remove(0);
        Set<Integer> library = libraryToolNumbers == null
                ? Set.of()
                : new LinkedHashSet<>(libraryToolNumbers);

        if (usedTools.isEmpty()) {
            // Красная, а не жёлтая: без активного T проверка инструментов всё равно
            // не пропустит запуск, и «игнорировать» приводило бы в тупик.
            found.add(new Diagnostic("E005", Severity.ERROR,
                    I18n.text("diag.012"),
                    I18n.text("diag.013")
                            + I18n.text("diag.014")
                            + I18n.text("diag.015")
                            + I18n.text("diag.016"),
                    0, I18n.text("diag.017"), ProgramDiagnostics::insertDefaultToolCall));
        } else if (library.isEmpty()) {
            found.add(new Diagnostic("E003", Severity.ERROR,
                    I18n.text("diag.018"),
                    I18n.text("diag.019") + describeTools(usedTools)
                            + I18n.text("diag.020")
                            + I18n.text("diag.021"),
                    firstLineForTools(toolByLine, usedTools), null, null));
        } else {
            TreeSet<Integer> missing = new TreeSet<>();
            for (Integer tool : usedTools) {
                if (!library.contains(tool)) {
                    missing.add(tool);
                }
            }
            if (!missing.isEmpty()) {
                // Жёлтая, а не красная: стойка такую программу не останавливает.
                // Незаведённый инструмент означает пустые данные коррекции, то есть
                // нулевой радиус, и SINUMERIK просто идёт по запрограммированному
                // контуру. Мы делаем так же — но говорим, чем это обернётся.
                found.add(new Diagnostic(CODE_TOOLS_MISSING, Severity.WARNING,
                        I18n.text("diag.022") + describeTools(missing),
                        I18n.text("diag.023") + describeTools(missing)
                                + I18n.text("diag.024")
                                + I18n.text("diag.025")
                                + I18n.text("diag.026")
                                + I18n.text("diag.027")
                                + I18n.text("diag.028")
                                + I18n.text("diag.029"),
                        firstLineForTools(toolByLine, missing),
                        I18n.text("diag.030"), null));
            }
        }

        // --- Жёлтые: поедет, но стоит посмотреть ---

        if (!PROGRAM_END.matcher(upper).find()) {
            found.add(new Diagnostic("W101", Severity.WARNING,
                    I18n.text("diag.031"),
                    I18n.text("diag.032")
                            + I18n.text("diag.033"),
                    0, I18n.text("diag.034"), ProgramDiagnostics::appendProgramEnd));
        }

        Matcher compOn = COMP_ON.matcher(upper);
        if (compOn.find() && !COMP_OFF.matcher(upper).find()) {
            found.add(new Diagnostic("W102", Severity.WARNING,
                    I18n.text("diag.035"),
                    I18n.text("diag.036")
                            + I18n.text("diag.037"),
                    lineOfOffset(text, compOn.start()),
                    I18n.text("diag.038"), ProgramDiagnostics::appendCompensationOff));
        }

        if (CUTTING_MOVE.matcher(upper).find() && !FEED_VALUE.matcher(upper).find()) {
            found.add(new Diagnostic("W103", Severity.WARNING,
                    I18n.text("diag.039"),
                    I18n.text("diag.040")
                            + I18n.text("diag.041"),
                    0, null, null));
        }

        WorkpieceDefinition declared = GCodeProgramParser.findWorkpiece(text).orElse(null);
        if (declared != null && declared.getShape() == WorkpieceDefinition.Shape.BOX) {
            found.add(new Diagnostic("W105", Severity.WARNING,
                    I18n.text("diag.042") + declared.describeSize() + I18n.text("sim.044"),
                    I18n.text("diag.043") + declared.describeSize() + I18n.text("diag.044")
                            + I18n.text("diag.045")
                            + I18n.text("diag.046")
                            + String.format(Locale.US, "Ø%.0f", declared.getDiameterMm()) + I18n.text("diag.047")
                            + I18n.text("diag.048"),
                    declared.getSourceLine(), null, null));
        }

        if (configuredWorkOffsetCodes != null) {
            TreeSet<Integer> missingOffsets = new TreeSet<>(usedWorkOffsetCodes(text));
            missingOffsets.removeAll(configuredWorkOffsetCodes);
            if (!missingOffsets.isEmpty()) {
                found.add(new Diagnostic(CODE_WORK_OFFSETS_MISSING, Severity.WARNING,
                        I18n.text("diag.049") + describeOffsets(missingOffsets),
                        I18n.text("diag.050") + describeOffsets(missingOffsets)
                                + I18n.text("diag.051")
                                + I18n.text("diag.052")
                                + I18n.text("diag.053")
                                + I18n.text("diag.054")
                                + I18n.text("diag.055"),
                        firstLineForOffsets(text, missingOffsets),
                        I18n.text("diag.056"), null));
            }
        }

        return found;
    }

    /**
     * Коды, которые точно означают настраиваемое смещение нулевой точки.
     *
     * <p>Уже, чем {@link WorkOffsetStore#isSettableCode(int)}: тот пропускает весь диапазон
     * G54…G73, а там лежат G70/G71 (дюймы/миллиметры) и G58/G59 (программируемое смещение,
     * не строка в настройках). Ловить их как «незаведённую точку» — ложная тревога на
     * совершенно обычной программе.
     */
    static boolean isDiagnosableOffsetCode(int code) {
        return (code >= 54 && code <= 57) || (code >= 505 && code <= 599);
    }

    /** Коды смещений (G54…G57, G505…G599), которые вызывает программа. */
    public static SortedSet<Integer> usedWorkOffsetCodes(String programText) {
        TreeSet<Integer> codes = new TreeSet<>();
        if (programText == null) {
            return codes;
        }
        Matcher matcher = WORK_OFFSET_CALL.matcher(
                stripComments(programText).toUpperCase(Locale.US));
        while (matcher.find()) {
            try {
                int code = Integer.parseInt(matcher.group(1));
                if (isDiagnosableOffsetCode(code)) {
                    codes.add(code);
                }
            } catch (NumberFormatException ignored) {
                // не номер смещения — пропускаем
            }
        }
        return codes;
    }

    /**
     * В библиотеке нет точной пары «номер T + кромка D», которую режет программа.
     *
     * <p>Отдельно от {@link #CODE_TOOLS_MISSING}: там нет самого номера T, а здесь номер
     * есть, но вызвана другая кромка. 3D-срез не подбирает инструмент «примерно» —
     * нужны тот же T и та же D, иначе снятие пойдёт не той геометрией.
     *
     * @param pairs недостающие пары в виде «T5 D1»
     * @param where где они встречаются (кадр или строка), не более нескольких штук
     * @param line  первая строка программы с такой парой
     */
    public static Diagnostic toolEdgesMissing(List<String> pairs, List<String> where, int line) {
        String head = String.join(", ", pairs);
        StringBuilder details = new StringBuilder();
        details.append(I18n.text("diag.057")).append(head)
                .append(I18n.text("diag.058"))
                .append(I18n.text("diag.059"));
        if (where != null && !where.isEmpty()) {
            details.append(I18n.text("diag.060"));
            for (String item : where) {
                details.append("  • ").append(item).append('\n');
            }
        }
        details.append(I18n.text("diag.061"))
                .append(I18n.text("diag.062"))
                .append(I18n.text("diag.063"))
                .append(I18n.text("diag.064"));
        return new Diagnostic(CODE_TOOL_EDGES_MISSING, Severity.WARNING,
                I18n.text("diag.065") + head,
                details.toString(),
                line,
                I18n.text("diag.066"), null);
    }

    /** Рабочие ходы есть, но активного инструмента у них нет — резать нечем. */
    /**
     * Точение с двух концов, а Z второго нуля не задан.
     *
     * <p>Числа считает контроллер (у него есть разбор и настройки), текст собирается
     * здесь — рядом с остальными сообщениями проверки.
     */
    public static Diagnostic twoEndsNoOffset(String codes, double rawSpanMm, double blankLengthMm,
                                             int secondCode, double suggestedZmm) {
        return new Diagnostic(
                CODE_TWO_ENDS_NO_OFFSET,
                Severity.WARNING,
                String.format(Locale.US, I18n.text("diag.twoends.title"), codes),
                String.format(Locale.US, I18n.text("diag.twoends.details"),
                        codes, rawSpanMm, blankLengthMm, secondCode, suggestedZmm),
                0,
                String.format(Locale.US, I18n.text("diag.twoends.fix"), secondCode, suggestedZmm),
                null);
    }

    public static Diagnostic noActiveToolMoves() {
        return new Diagnostic(CODE_NO_ACTIVE_TOOL, Severity.ERROR,
                I18n.text("diag.067"),
                I18n.text("diag.068")
                        + I18n.text("diag.069")
                        + I18n.text("diag.070"),
                0, null, null);
    }

    /**
     * Заготовка из программы не сходится с тем, что реально режет инструмент.
     *
     * <p>Три случая, и все кончаются одинаково плохо: 3D-срез снимает материал именно
     * с заготовки, поэтому расхождение видно и на картинке, и в объёме снятого.
     * <ul>
     *   <li>заготовка тоньше траектории — часть контура режется по воздуху;</li>
     *   <li>заготовка не покрывает деталь по Z — деталь стоит рядом с заготовкой;</li>
     *   <li>заготовка в разы толще всего, что режется, — обычно неверно подставленный
     *       диаметр (например, взятый с позиции смены инструмента).</li>
     * </ul>
     *
     * @param declared заготовка из программы
     * @param needed   габариты по рабочим ходам
     * @return диагностика или null, если заготовка в порядке
     */
    public static Diagnostic blankMismatch(WorkpieceDefinition declared, WorkpieceDefinition needed) {
        if (declared == null || needed == null || !declared.isValid() || !needed.isValid()) {
            return null;
        }
        double tolerance = Math.max(1.0, needed.getLengthMm() * 0.02);
        boolean tooThin = needed.getDiameterMm() > declared.getDiameterMm() * 1.02 + 0.5;
        boolean missesZ = declared.getZMin() > needed.getZMin() + tolerance
                || declared.getZMax() < needed.getZMax() - tolerance;
        boolean tooThick = declared.getDiameterMm() > needed.getDiameterMm() * 2.0;
        if (!tooThin && !missesZ && !tooThick) {
            return null;
        }

        String title;
        StringBuilder details = new StringBuilder();
        if (tooThin) {
            title = String.format(Locale.US, I18n.text("diag.071"),
                    declared.getDiameterMm(), needed.getDiameterMm());
            details.append(String.format(Locale.US,
                    I18n.text("diag.072")
                            + I18n.text("diag.073"),
                    declared.getDiameterMm(), needed.getDiameterMm()));
        } else if (tooThick) {
            title = String.format(Locale.US, I18n.text("diag.074"),
                    declared.getDiameterMm(), needed.getDiameterMm());
            details.append(String.format(Locale.US,
                    I18n.text("diag.075")
                            + I18n.text("diag.076")
                            + I18n.text("diag.077"),
                    declared.getDiameterMm(), needed.getDiameterMm()));
        } else {
            title = I18n.text("diag.078");
            details.append(String.format(Locale.US,
                    I18n.text("diag.079")
                            + I18n.text("diag.080"),
                    declared.getZMin(), declared.getZMax(),
                    needed.getZMin(), needed.getZMax()));
        }
        // Подгонка может расширять границы, но не удалять необработанные концы.
        double fittedMin = Math.min(declared.getZMin(), needed.getZMin());
        double fittedMax = Math.max(declared.getZMax(), needed.getZMax());
        WorkpieceDefinition fitted = new WorkpieceDefinition(
                declared.getShape(),
                Math.max(declared.getDiameterMm(), needed.getDiameterMm()),
                fittedMax - fittedMin,
                fittedMin,
                fittedMax,
                declared.getSourceLine());
        if (tooThick) {
            fitted = new WorkpieceDefinition(
                    declared.getShape(),
                    needed.getDiameterMm(),
                    fittedMax - fittedMin,
                    fittedMin,
                    fittedMax,
                    declared.getSourceLine());
        }
        String replacement = workpieceLine(fitted);
        int line = declared.getSourceLine();
        UnaryOperator<String> fix = line > 0
                ? source -> replaceLine(source, line, replacement)
                : null;
        details.append(String.format(Locale.US,
                I18n.text("diag.081"), replacement));
        return new Diagnostic(CODE_BLANK_TOO_SMALL, Severity.WARNING,
                title, details.toString(), line,
                fix != null ? I18n.text("diag.082") : null, fix);
    }

    /** Заменяет одну строку программы (нумерация с 1), сохраняя остальные как есть. */
    private static String replaceLine(String programText, int lineNumber, String replacement) {
        String text = programText == null ? "" : programText;
        String[] lines = text.split("\\R", -1);
        if (lineNumber < 1 || lineNumber > lines.length) {
            return text;
        }
        lines[lineNumber - 1] = replacement;
        return String.join(System.lineSeparator(), lines);
    }

    /** Худшая из найденных проблем (для плашки у заголовка) или null. */
    public static Diagnostic worst(List<Diagnostic> diagnostics) {
        Diagnostic worst = null;
        for (Diagnostic diagnostic : diagnostics) {
            if (worst == null || isMoreImportant(diagnostic, worst)) {
                worst = diagnostic;
            }
        }
        return worst;
    }

    /**
     * Красная важнее жёлтой. При равной степени вперёд выходит та, у которой есть
     * автоисправление: диалог даёт кнопки только для главной проблемы, и без этого
     * правка жёлтой становилась недоступной, стоило рядом появиться второй жёлтой.
     */
    private static boolean isMoreImportant(Diagnostic candidate, Diagnostic current) {
        if (candidate.severity() != current.severity()) {
            return candidate.severity() == Severity.ERROR;
        }
        return candidate.autoFixLabel() != null && current.autoFixLabel() == null;
    }

    public static boolean hasErrors(List<Diagnostic> diagnostics) {
        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic.severity() == Severity.ERROR) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Автоисправления
    // ------------------------------------------------------------------

    /**
     * Строка заготовки в формате, который понимает {@link GCodeProgramParser#findWorkpiece}.
     *
     * <p>Формат Siemens: битовая маска, Z0, Z1, ZB, наружный диаметр.
     * 192 = 64 + 128: Z1 и ZB заданы абсолютно, без зависимости от G90/G91.
     */
    static String workpieceLine(WorkpieceDefinition workpiece) {
        return String.format(Locale.US, "WORKPIECE(,,,\"CYLINDER\",192,%.3f,%.3f,%.3f,%.3f)",
                workpiece.getZMax(), workpiece.getZMin(),
                workpiece.getZMin(), workpiece.getDiameterMm());
    }

    private static String insertLineAtTop(String programText, String line) {
        String text = programText == null ? "" : programText;
        return line + System.lineSeparator() + text;
    }

    private static String insertDefaultToolCall(String programText) {
        String text = programText == null ? "" : programText;
        return "T1 D1" + System.lineSeparator() + text;
    }

    private static String appendProgramEnd(String programText) {
        String text = programText == null ? "" : programText;
        String trimmed = stripTrailingNewlines(text);
        return trimmed + System.lineSeparator() + "M30" + System.lineSeparator();
    }

    private static String appendCompensationOff(String programText) {
        String text = programText == null ? "" : programText;
        String trimmed = stripTrailingNewlines(text);
        String[] lines = trimmed.split("\\R", -1);
        // G40 ставим перед кадром конца программы, если он есть, иначе в самый низ.
        for (int i = lines.length - 1; i >= 0; --i) {
            String upper = stripComments(lines[i]).toUpperCase(Locale.US);
            if (PROGRAM_END.matcher(upper).find()) {
                StringBuilder builder = new StringBuilder();
                for (int j = 0; j < lines.length; ++j) {
                    if (j == i) {
                        builder.append("G40").append(System.lineSeparator());
                    }
                    builder.append(lines[j]);
                    if (j < lines.length - 1) {
                        builder.append(System.lineSeparator());
                    }
                }
                return builder + System.lineSeparator();
            }
        }
        return trimmed + System.lineSeparator() + "G40" + System.lineSeparator();
    }

    // ------------------------------------------------------------------
    // Мелочи
    // ------------------------------------------------------------------

    private static String stripTrailingNewlines(String text) {
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r')) {
            --end;
        }
        return text.substring(0, end);
    }

    /** Убирает комментарии, чтобы не ловить G40 или M30 внутри пояснений. */
    private static String stripComments(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (String line : text.split("\\R", -1)) {
            String clean = line;
            int semicolon = clean.indexOf(';');
            if (semicolon >= 0) {
                clean = clean.substring(0, semicolon);
            }
            clean = clean.replaceAll("\\([^)]*\\)", " ");
            builder.append(clean).append('\n');
        }
        return builder.toString();
    }

    private static int lineOfOffset(String text, int offsetInStripped) {
        // stripComments сохраняет число строк, поэтому смещение переводится напрямую.
        int line = 1;
        int limit = Math.min(offsetInStripped, text.length());
        for (int i = 0; i < limit; ++i) {
            if (text.charAt(i) == '\n') {
                ++line;
            }
        }
        return line;
    }

    private static int firstLineForTools(Map<Integer, Integer> toolByLine, Set<Integer> tools) {
        int best = 0;
        for (Map.Entry<Integer, Integer> entry : toolByLine.entrySet()) {
            if (tools.contains(entry.getValue()) && (best == 0 || entry.getKey() < best)) {
                best = entry.getKey();
            }
        }
        return best;
    }

    /** Первая строка, где встречается одно из смещений (для «Исправить вручную»). */
    private static int firstLineForOffsets(String text, Set<Integer> codes) {
        String[] lines = stripComments(text).split("\n", -1);
        for (int i = 0; i < lines.length; ++i) {
            Matcher matcher = WORK_OFFSET_CALL.matcher(lines[i].toUpperCase(Locale.US));
            while (matcher.find()) {
                try {
                    if (codes.contains(Integer.parseInt(matcher.group(1)))) {
                        return i + 1;
                    }
                } catch (NumberFormatException ignored) {
                    // не номер смещения — пропускаем
                }
            }
        }
        return 0;
    }

    private static String describeOffsets(Set<Integer> codes) {
        StringBuilder builder = new StringBuilder();
        for (Integer code : codes) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append('G').append(code);
        }
        return builder.toString();
    }

    private static String describeTools(Set<Integer> tools) {
        StringBuilder builder = new StringBuilder();
        for (Integer tool : tools) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append('T').append(tool);
        }
        return builder.toString();
    }
}
