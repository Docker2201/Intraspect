package com.sergey.pisarev.service;

import com.sergey.pisarev.model.GCodeMoveData;
import com.sergey.pisarev.model.MachineConfiguration;
import com.sergey.pisarev.model.ProgramWorkOffsetScan;
import com.sergey.pisarev.model.WorkOffsetValues;
import com.sergey.pisarev.model.WorkpieceDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор служебных команд SINUMERIK (WORKPIECE, T) из текста программы.
 */
public final class GCodeProgramParser {
    // M17 — конец подпрограммы (.SPF). Двухканальные программы станка написаны
    // подпрограммами и заканчиваются им, а не M30: проверка зря требовала M30, а
    // разбор шёл дальше по файлу.
    private static final java.util.regex.Pattern PROGRAM_END =
            java.util.regex.Pattern.compile("\\bM0*(?:2|17|30)\\b");
    private static final Pattern WORKPIECE_PATTERN = Pattern.compile(
            "WORKPIECE\\s*\\(([^)]*)\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CUP_RADIUS_PATTERN = Pattern.compile(
            "(?i)(?:CUP[_\\s-]*R(?:ADIUS)?|GRIP[_\\s-]*R|R[_\\s-]*CHUCK|RADIUS[_\\s-]*CUP"
                    + "|CHUCK[_\\s-]*R|FUTTER[_\\s-]*R|GRUNDK[_\\s-]*R|SPANN(?:UNG)?[_\\s-]*R)"
                    + "\\s*[=:;]?\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern DMG_MACHINE_PATTERN = Pattern.compile(
            "(?i)\\b(?:DMG|MORI|NLX|CTX|CELOS)\\b");
    private static final Pattern T_NUMBER = Pattern.compile("T(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern T_NAME = Pattern.compile("T\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern D_NUMBER = Pattern.compile("D(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORK_OFFSET_CODE = Pattern.compile("G(5[4-9]|[5-9]\\d{2})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern G500_PATTERN = Pattern.compile("G500\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMENT_OFFSET = Pattern.compile("\\(G(5[4-9])\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FRAME_NUMBER = Pattern.compile("^N(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    // Границу слова () использовать нельзя: в записи без пробелов «G0X40Z5» между
    // G0 и X нет границы, и строка считалась неисполняемой — программа давала ноль ходов.
    // Проверяем вместо этого, что слева не буква (чтобы не ловить MAX, ANG и т.п.).
    private static final Pattern MOTION_G = Pattern.compile(
            "(?<![A-Za-z0-9])G0?([0-3])(?![0-9])", Pattern.CASE_INSENSITIVE);
    // Координата может быть задана именем: «X=BORE_DIAM-20», «Z=WHEEL_HEIGHT+12».
    // Раньше признаком оси была только цифра, и такие кадры считались неисполняемыми:
    // на программе колеса из траектории пропадала вся торцовка ступицы.
    private static final Pattern AXIS_TOKEN = Pattern.compile(
            "(?<![A-Za-z])[XYZABCUVW]\\s*(?:=\\s*[-+]?[\\w.$(]|[-+.]?\\d)",
            Pattern.CASE_INSENSITIVE);

    private GCodeProgramParser() {
    }

    /**
     * Ищет G54–G59 в программе. X/Z на той же строке или последний G500 X/Z перед кадром (Siemens).
     */
    public static ProgramWorkOffsetScan scanWorkOffsets(String programText) {
        if (programText == null || programText.isBlank()) {
            return new ProgramWorkOffsetScan(-1, Set.of(), Map.of());
        }
        Set<Integer> codes = new LinkedHashSet<>();
        Map<Integer, WorkOffsetValues> values = new TreeMap<>();
        int active = -1;
        Double pendingG500X = null;
        Double pendingG500Z = null;
        String[] lines = programText.split("\\R");
        Map<String, Double> rVariables = new TreeMap<>();
        for (String rawLine : lines) {
            GCodeExpressionEvaluator.collectRDefinitions(rawLine, rVariables);
            String line = stripFrameNumber(stripComment(rawLine)).toUpperCase(Locale.US);
            if (line.isBlank()) {
                continue;
            }
            if (G500_PATTERN.matcher(line).find()) {
                Double x = readAxisOnLine(line, 'X', rVariables);
                Double z = readAxisOnLine(line, 'Z', rVariables);
                if (x != null) {
                    pendingG500X = x;
                }
                if (z != null) {
                    pendingG500Z = z;
                }
                active = 500;
            }
            Matcher commentMatcher = COMMENT_OFFSET.matcher(rawLine);
            while (commentMatcher.find()) {
                int code = Integer.parseInt(commentMatcher.group(1));
                if (code >= 54 && code <= 73) {
                    codes.add(code);
                }
            }
            Matcher matcher = WORK_OFFSET_CODE.matcher(line);
            while (matcher.find()) {
                int code = Integer.parseInt(matcher.group(1));
                if (!isSettableWorkOffsetCode(code)) {
                    continue;
                }
                codes.add(code);
                active = code;
                Double x = readAxisOnLine(line, 'X', rVariables);
                Double z = readAxisOnLine(line, 'Z', rVariables);
                WorkOffsetValues previous = values.getOrDefault(code, WorkOffsetValues.ZERO);
                if (x != null || z != null) {
                    values.put(
                            code,
                            new WorkOffsetValues(
                                    x != null ? x : previous.xMm(),
                                    z != null ? z : previous.zMm()));
                } else if (pendingG500X != null || pendingG500Z != null) {
                    values.put(
                            code,
                            new WorkOffsetValues(
                                    pendingG500X != null ? pendingG500X : previous.xMm(),
                                    pendingG500Z != null ? pendingG500Z : previous.zMm()));
                }
            }
        }
        return new ProgramWorkOffsetScan(active, codes, values);
    }

    private static boolean isSettableWorkOffsetCode(int code) {
        return (code >= 54 && code <= 73) || (code >= 501 && code <= 599);
    }

    private static Double readAxisOnLine(String line, char axis) {
        return readAxisOnLine(line, axis, Map.of());
    }

    static Double readAxisOnLine(String line, char axis, Map<String, Double> rVariables) {
        int index = indexOfAxisToken(line, axis);
        if (index < 0) {
            return null;
        }
        int start = index + 1;
        if (start < line.length() && line.charAt(start) == '=') {
            start++;
        }
        int end = start;
        while (end < line.length()) {
            char ch = line.charAt(end);
            if (Character.isDigit(ch) || ch == '-' || ch == '+' || ch == '.' || ch == ','
                    || ch == 'R' || ch == 'r') {
                end++;
                continue;
            }
            break;
        }
        if (end == start) {
            return null;
        }
        return GCodeExpressionEvaluator.evaluate(line.substring(start, end), rVariables);
    }

    /** Кадр завершения программы: M30 или M02/M2 (комментарии должны быть уже убраны). */
    public static boolean isProgramEndLine(String line) {
        return line != null && PROGRAM_END.matcher(line).find();
    }

    public static int indexOfAxisToken(String line, char axis) {
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) != axis) {
                continue;
            }
            // Перед адресным словом цифра — это нормальная запись без пробелов (G0X40Z5).
            // Отбрасываем только случай, когда буква стоит внутри слова (MAX, CR=...),
            // иначе такие программы давали ноль ходов и пустой график.
            if (i > 0 && Character.isLetter(line.charAt(i - 1))) {
                continue;
            }
            if (i + 1 < line.length()) {
                char next = line.charAt(i + 1);
                if (next == '=' || next == '+' || next == '-' || next == '.' || Character.isDigit(next)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static int extractFrameLineNumber(String rawLine, int fallbackLine) {
        if (rawLine == null) {
            return fallbackLine;
        }
        Matcher matcher = Pattern.compile("^N(\\d+)\\b", Pattern.CASE_INSENSITIVE).matcher(rawLine.trim());
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return fallbackLine;
            }
        }
        return fallbackLine;
    }

    public static Optional<WorkpieceDefinition> findWorkpiece(String programText) {
        if (programText == null || programText.isBlank()) {
            return Optional.empty();
        }
        Matcher fullTextMatcher = WORKPIECE_PATTERN.matcher(programText);
        while (fullTextMatcher.find()) {
            int lineNumber = lineNumberForIndex(programText, fullTextMatcher.start());
            WorkpieceDefinition parsed = parseWorkpieceArguments(fullTextMatcher.group(1), lineNumber);
            if (parsed != null && parsed.isValid()) {
                return Optional.of(parsed);
            }
        }
        String[] lines = programText.split("\\R");
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = stripFrameNumber(stripComment(lines[lineIndex]));
            int workpieceAt = line.toUpperCase(Locale.US).indexOf("WORKPIECE");
            if (workpieceAt > 0) {
                line = line.substring(workpieceAt);
            }
            Matcher matcher = WORKPIECE_PATTERN.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            WorkpieceDefinition parsed = parseWorkpieceArguments(matcher.group(1), lineIndex + 1);
            if (parsed != null && parsed.isValid()) {
                return Optional.of(parsed);
            }
        }
        return Optional.empty();
    }

    /** Радиус чашки из комментария или служебной строки программы (мм). */
    public static OptionalDouble findCupRadiusMm(String programText) {
        if (programText == null || programText.isBlank()) {
            return OptionalDouble.empty();
        }
        Matcher matcher = CUP_RADIUS_PATTERN.matcher(programText);
        if (matcher.find()) {
            Double value = parseDouble(matcher.group(1));
            if (value != null && value > 0.0) {
                return OptionalDouble.of(value);
            }
        }
        return OptionalDouble.empty();
    }

    private static int lineNumberForIndex(String text, int index) {
        int line = 1;
        for (int i = 0; i < index && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** DMG MORI / SinuTrain с Siemens — по заголовку программы. */
    public static boolean looksLikeDmgSiemensLathe(String programText) {
        return programText != null && !programText.isBlank() && DMG_MACHINE_PATTERN.matcher(programText).find();
    }

    private static final Pattern DIAMOF_PATTERN = Pattern.compile("\\bDIAMOF\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIAMON_PATTERN = Pattern.compile("\\bDIAM(?:ON|90)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Переключение режима X в кадре: DIAMON/DIAM90 — диаметр, DIAMOF — радиус.
     *
     * <p>Режим модальный, поэтому разбирать его надо построчно, а не «что встретилось
     * в файле первым»: одна программа может точить обод в радиусах, а потом вернуться
     * в диаметры. Возвращает null, если в кадре переключения нет.
     */
    public static Boolean readDiameterModeSwitch(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        Matcher onMatcher = DIAMON_PATTERN.matcher(line);
        Matcher offMatcher = DIAMOF_PATTERN.matcher(line);
        int lastOn = -1;
        while (onMatcher.find()) {
            lastOn = onMatcher.start();
        }
        int lastOff = -1;
        while (offMatcher.find()) {
            lastOff = offMatcher.start();
        }
        if (lastOn < 0 && lastOff < 0) {
            return null;
        }
        return lastOn > lastOff;
    }

    /**
     * Программа явно использует диаметральный режим X (Siemens DIAMON/DIAM90)
     * и не отменяет его до первого движения. Если есть DIAMOF — режим радиусный.
     * Иначе — эвристика по WORKPIECE: если max|X| абсолютных перемещений ближе к D, чем к D/2.
     */
    public static boolean detectInitialDiameterMode(String programText, double workpieceDiameter) {
        if (programText == null || programText.isBlank()) {
            return false;
        }
        // Сначала проверяем явные токены DIAMON/DIAMOF в их порядке появления.
        Matcher onMatcher = DIAMON_PATTERN.matcher(programText);
        Matcher offMatcher = DIAMOF_PATTERN.matcher(programText);
        Integer firstOn = onMatcher.find() ? onMatcher.start() : null;
        Integer firstOff = offMatcher.find() ? offMatcher.start() : null;
        if (firstOn != null && (firstOff == null || firstOn < firstOff)) {
            return true;
        }
        if (firstOff != null && (firstOn == null || firstOff < firstOn)) {
            return false;
        }
        // Эвристика по WORKPIECE: max|X| ≥ 60% D → диаметральный режим.
        if (workpieceDiameter > 1.0E-6) {
            double maxAbsX = scanMaxAbsoluteX(programText);
            if (maxAbsX > 0.55 * workpieceDiameter) {
                return true;
            }
        }
        return false;
    }

    /** Максимум |X| в абсолютных G0/G1/G2/G3 строках (без R-радиуса дуги, без CR/RND и т.п.). */
    private static double scanMaxAbsoluteX(String programText) {
        double maxAbs = 0.0;
        boolean absoluteMode = true;
        for (String rawLine : programText.split("\\R")) {
            String line = stripFrameNumber(stripComment(rawLine)).toUpperCase(Locale.US);
            if (line.isBlank()) {
                continue;
            }
            if (line.contains("G90")) {
                absoluteMode = true;
            }
            if (line.contains("G91")) {
                absoluteMode = false;
            }
            if (!absoluteMode) {
                continue;
            }
            Double x = readAxisOnLine(line, 'X');
            if (x != null) {
                maxAbs = Math.max(maxAbs, Math.abs(x));
            }
        }
        return maxAbs;
    }

    private static WorkpieceDefinition parseWorkpieceArguments(String args, int sourceLine) {
        List<String> tokens = splitArguments(args);
        if (tokens.size() < 3) {
            return null;
        }
        String shapeToken = null;
        int numericStart = 0;
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i).replace("\"", "").trim();
            if (token.equalsIgnoreCase("CYLINDER") || token.equalsIgnoreCase("BOX")) {
                shapeToken = token;
                numericStart = i + 1;
                break;
            }
        }
        if (shapeToken == null) {
            return null;
        }
        WorkpieceDefinition.Shape shape = shapeToken.equalsIgnoreCase("CYLINDER")
                ? WorkpieceDefinition.Shape.CYLINDER
                : (shapeToken.equalsIgnoreCase("BOX") ? WorkpieceDefinition.Shape.BOX : WorkpieceDefinition.Shape.UNKNOWN);
        if (shape == WorkpieceDefinition.Shape.BOX) {
            return parseBoxArguments(tokens, numericStart, sourceLine);
        }
        if (shape != WorkpieceDefinition.Shape.CYLINDER) {
            return null;
        }
        // Siemens NC programming, table "Blank shape parameters":
        // CYLINDER, bit mask, Z0, Z1, ZB, outside diameter, ...
        // Bit 6: Z1 absolute; otherwise Z1 is relative to Z0. ZB is the
        // machining/chucking limit, NOT another endpoint of the blank.
        // Keep argument positions: omitted optional fields must not shift d0.
        if (tokens.size() - numericStart >= 5) {
            Double outsideDiameter = parseDouble(tokens.get(numericStart + 4));
            if (outsideDiameter != null && outsideDiameter > 0.0) {
                String bitToken = tokens.get(numericStart);
                Double bits = bitToken.isBlank() ? 0.0 : parseDouble(bitToken);
                Double z0 = parseDouble(tokens.get(numericStart + 1));
                Double z1 = parseDouble(tokens.get(numericStart + 2));
                if (bits == null || z0 == null || z1 == null
                        || !Double.isFinite(bits) || bits < 0 || bits > 16383
                        || bits != Math.rint(bits) || !Double.isFinite(outsideDiameter)) {
                    return null;
                }
                double endZ = (bits.intValue() & 0x40) != 0 ? z1 : z0 + z1;
                if (!Double.isFinite(z0) || !Double.isFinite(endZ) || endZ == z0) {
                    return null;
                }
                return new WorkpieceDefinition(shape, outsideDiameter, Math.abs(endZ - z0),
                        Math.min(z0, endZ), Math.max(z0, endZ), sourceLine);
            }
            // Compatibility with old Chekator auto-fixes: D,L,ZA,ZA,0.
            // A zero final diameter cannot describe a valid Siemens cylinder.
            if (outsideDiameter == null || outsideDiameter != 0.0) {
                return null;
            }
        }
        List<Double> numbers = new ArrayList<>();
        for (int i = numericStart; i < tokens.size(); i++) {
            Double value = parseDouble(tokens.get(i));
            if (value != null) {
                numbers.add(value);
            }
        }
        if (numbers.isEmpty()) {
            return null;
        }
        double diameter = numbers.get(0);
        double length = numbers.size() >= 2 ? numbers.get(1) : diameter;
        if (length <= 0.0) {
            length = Math.max(10.0, diameter);
        }
        double zMin;
        double zMax;
        boolean zAnchorMissing = false;
        if (numbers.size() >= 5) {
            // Only the historical Chekator D,L,ZA,ZA,0 output is retained.
            if (numbers.size() != 5 || numbers.get(4) != 0.0
                    || numbers.get(1) <= 0.0 || !numbers.get(2).equals(numbers.get(3))) {
                return null;
            }
            zMin = numbers.get(2);
            zMax = zMin + length;
        } else if (numbers.size() >= 3) {
            double zA = numbers.get(1);
            double zI = numbers.get(2);
            zMin = Math.min(zA, zI);
            zMax = Math.max(zA, zI);
            length = zMax - zMin;
            if (length <= 0.0) {
                length = Math.max(10.0, diameter);
                zMax = zMin + length;
            }
        } else {
            // Только диаметр и длина: где пруток стоит вдоль Z, запись не говорит.
            // По умолчанию кладём торец в Z0 и металл в минус — так читаются все
            // остальные формы, — но помечаем заготовку как непривязанную, чтобы
            // отображение поставило её по тому, что программа реально режет.
            zMin = -length;
            zMax = 0.0;
            zAnchorMissing = true;
        }
        if (zMax <= zMin && length > 0.0) {
            zMax = zMin + length;
        }
        WorkpieceDefinition parsed =
                new WorkpieceDefinition(shape, diameter, length, zMin, zMax, sourceLine);
        return zAnchorMissing ? parsed.withoutZAnchor() : parsed;
    }

    /**
     * Прямоугольная заготовка SINUMERIK: {@code WORKPIECE(,,,"BOX",n,ZA,ZI,ZB,X0,Y0,X1,Y1)}.
     *
     * <p>Углы X0/Y0 и X1/Y1 всегда стоят последними четырьмя числами — на них и опираемся,
     * а всё, что перед ними (кроме ведущего служебного числа полной записи), считаем Z.
     * Так разбор переживает укороченные варианты, которые пишут разные постпроцессоры.
     */
    private static WorkpieceDefinition parseBoxArguments(
            List<String> tokens, int numericStart, int sourceLine) {
        List<Double> numbers = new ArrayList<>();
        for (int i = numericStart; i < tokens.size(); i++) {
            Double value = parseDouble(tokens.get(i));
            if (value != null) {
                numbers.add(value);
            }
        }
        if (numbers.size() < 4) {
            return null;
        }
        int corner = numbers.size() - 4;
        double widthX = Math.abs(numbers.get(corner + 2) - numbers.get(corner));
        double widthY = Math.abs(numbers.get(corner + 3) - numbers.get(corner + 1));
        if (widthX <= 0.0 || widthY <= 0.0) {
            return null;
        }
        // В полной записи первым идёт служебное число (0/64/112), а не координата Z.
        int zFrom = numbers.size() >= 8 ? 1 : 0;
        double zMin = Double.POSITIVE_INFINITY;
        double zMax = Double.NEGATIVE_INFINITY;
        for (int i = zFrom; i < corner; i++) {
            zMin = Math.min(zMin, numbers.get(i));
            zMax = Math.max(zMax, numbers.get(i));
        }
        if (!Double.isFinite(zMin) || !Double.isFinite(zMax) || zMax <= zMin) {
            // Высоту не сказали: берём тонкую плиту, чтобы заготовка осталась пригодной,
            // а границы по Z потом уточнит расчёт по траектории.
            zMin = Double.isFinite(zMin) ? zMin : 0.0;
            zMax = zMin + Math.max(1.0, Math.min(widthX, widthY) * 0.25);
        }
        return new WorkpieceDefinition(
                WorkpieceDefinition.Shape.BOX,
                Math.hypot(widthX, widthY),
                zMax - zMin,
                zMin,
                zMax,
                sourceLine,
                widthX,
                widthY);
    }

    public static boolean looksLikeLatheProgram(List<GCodeMoveData> moves) {
        if (moves == null || moves.isEmpty()) {
            return false;
        }
        double zSpan = 0.0;
        double maxAbsX = 0.0;
        for (GCodeMoveData move : moves) {
            zSpan = Math.max(zSpan, Math.abs(move.endZ() - move.startZ()));
            maxAbsX = Math.max(maxAbsX, Math.max(Math.abs(move.startX()), Math.abs(move.endX())));
        }
        return maxAbsX > 1.0;
    }

    public static WorkpieceDefinition inferWorkpiece(
            String programText,
            List<GCodeMoveData> moves,
            MachineConfiguration configuration
    ) {
        Optional<WorkpieceDefinition> parsed = findWorkpiece(programText);
        if (parsed.isPresent()) {
            return parsed.get();
        }
        if (programText != null && programText.toUpperCase(Locale.US).contains("WORKPIECE")) {
            return null;
        }
        if (moves == null || moves.isEmpty()) {
            return null;
        }
        double zMin = Double.POSITIVE_INFINITY;
        double zMax = Double.NEGATIVE_INFINITY;
        double maxFeedDiam = 0.0;
        // Длину заготовки задают рабочие ходы. Быстрые уходят на смену инструмента и
        // в отводы: на программе колеса это Z=335 при детали шириной 178 мм, и рядом с
        // деталью вырастал необработанный цилиндр вдвое длиннее себя.
        for (GCodeMoveData move : moves) {
            if (move.rapid()) {
                continue;
            }
            zMin = Math.min(zMin, Math.min(move.startZ(), move.endZ()));
            zMax = Math.max(zMax, Math.max(move.startZ(), move.endZ()));
            maxFeedDiam = Math.max(maxFeedDiam, Math.max(Math.abs(move.startX()), Math.abs(move.endX())));
        }
        if (!Double.isFinite(zMin)) {
            return null;
        }
        double diameter = 0.0;
        if (configuration != null && configuration.isUseBlankDiameter() && configuration.getBlankDiameterMm() > 0.0) {
            diameter = configuration.getBlankDiameterMm();
        } else if (maxFeedDiam > 0.0) {
            diameter = maxFeedDiam * 1.02;
            if (diameter > 800.0) {
                return null;
            }
        }
        if (diameter <= 0.0) {
            return null;
        }
        double length = Math.max(1.0, zMax - zMin);
        return new WorkpieceDefinition(
                WorkpieceDefinition.Shape.CYLINDER,
                diameter,
                length,
                zMin,
                zMax,
                0);
    }

    /** Numeric tool state. Explicit M6/L6 programs preselect T until the change block. */
    public static Map<Integer, Integer> parseToolNumberByLine(String programText) {
        HashMap<Integer,Integer> result=new HashMap<>();
        if(programText==null || programText.isBlank()) return result;
        String marker="\n"+MultiChannelSimulation.CHANNEL_BOUNDARY+"\n";
        int boundary=programText.indexOf(marker);
        if(boundary>=0) {
            String first=programText.substring(0,boundary);
            result.putAll(parseToolNumberByLine(first));
            int offset=MultiChannelSimulation.secondChannelLineOffset(first);
            result.put(offset,0);
            parseToolNumberByLine(programText.substring(boundary+marker.length()))
                    .forEach((line,tool)->result.put(line+offset,tool));
            return result;
        }
        String[] lines=programText.split("\\R");
        boolean explicitChange=java.util.Arrays.stream(lines).anyMatch(GCodeProgramParser::isToolChangeBlock);
        int active=0,selected=0,lineNumber=1;
        for(String raw:lines) {
            String line=toolControlBlock(raw);
            Matcher token=TOOL_ADDRESS.matcher(line);
            if(token.find()) {selected=Integer.parseInt(token.group(1)); if(!explicitChange)active=selected;}
            if(isToolChangeBlock(raw)) active=selected;
            result.put(lineNumber++,active);
        }
        return result;
    }

    private static final Pattern TOOL_ADDRESS=Pattern.compile("(?<![A-Z_])T\\s*=?\\s*(\\d+)(?![\\d.])");
    private static final Pattern TOOL_CHANGE=Pattern.compile("(?<![A-Z_])(?:M0*6|L0*6)(?![\\d.A-Z_])");
    static String toolControlBlock(String raw) {
        return stripFrameNumber(stripComment(raw)).replaceAll("\"[^\"]*\"", " ").toUpperCase(Locale.US);
    }
    static boolean isToolChangeBlock(String raw) {
        return TOOL_CHANGE.matcher(toolControlBlock(raw)).find();
    }

    public static List<GCodeMoveData> enrichMovesWithTools(
            String programText,
            List<GCodeMoveData> moves
    ) {
        if (moves == null || moves.isEmpty()) {
            return List.of();
        }
        Map<Integer, Integer> toolByLine = parseToolNumberByLine(programText);
        Map<Integer, Integer> edgeByLine = LatheCompensationProcessor.parseDNumberByLine(programText);
        ArrayList<GCodeMoveData> enriched = new ArrayList<>(moves.size());
        for (GCodeMoveData move : moves) {
            int tool = toolByLine.getOrDefault(move.sourceLine(), 0);
            int edge = Math.max(1, edgeByLine.getOrDefault(move.sourceLine(), move.edgeNumber()));
            enriched.add(new GCodeMoveData(
                    move.startX(),
                    move.startZ(),
                    move.endX(),
                    move.endZ(),
                    move.rapid(),
                    move.sourceLine(),
                    move.arcSegment(),
                    tool,
                    edge,
                    move.arcEnd()));
        }
        return enriched;
    }

    private static List<String> splitArguments(String args) {
        ArrayList<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < args.length(); i++) {
            char ch = args.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
                current.append(ch);
                continue;
            }
            if (ch == ',' && !inQuotes) {
                tokens.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        if (current.length() > 0) {
            tokens.add(current.toString().trim());
        }
        return tokens;
    }

    private static Double parseDouble(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String cleaned = token.replace("\"", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String stripFrameNumber(String line) {
        if (line == null) {
            return "";
        }
        return FRAME_NUMBER.matcher(line.trim()).replaceFirst("").trim();
    }

    public static String stripFrameNumberForParse(String line) {
        return stripFrameNumber(line);
    }

    /** Удаляет ; и (...) из строки G-code перед разбором координат. */
    public static String stripCommentsForParse(String line) {
        return stripComment(line);
    }

    /**
     * Строка без исполняемого G-code (комментарий, MSG, IF, метаданные Siemens).
     * Координаты из таких строк не должны попадать в траекторию.
     */
    public static boolean isNonExecutableProgramLine(String line) {
        if (line == null || line.isBlank()) {
            return true;
        }
        String trimmed = line.trim();
        if (trimmed.startsWith(";") || trimmed.startsWith("%")) {
            return true;
        }
        String upper = stripFrameNumber(stripComment(line)).toUpperCase(Locale.US);
        if (upper.isBlank()) {
            return true;
        }
        if (WORKPIECE_PATTERN.matcher(upper).find()) {
            return true;
        }
        if (upper.startsWith("MSG")
                || upper.startsWith("STOPRE")
                || upper.startsWith("GOTOF")
                || upper.startsWith("GOTO")
                || upper.startsWith("STOP")
                || upper.startsWith("IF ")
                || upper.equals("ENDIF")
                || upper.startsWith("DEF ")
                || upper.startsWith("EXTERN")
                || upper.startsWith("SET(")
                || upper.startsWith("SETPIECE")) {
            return true;
        }
        return !MOTION_G.matcher(upper).find() && !AXIS_TOKEN.matcher(upper).find();
    }

    private static String stripComment(String line) {
        if (line == null) {
            return "";
        }
        String text = line;
        int semi = text.indexOf(';');
        if (semi >= 0) {
            text = text.substring(0, semi);
        }
        // Круглые скобки — комментарий только у ISO/Fanuc. У Siemens это вызов:
        // Z=IC(+27), LIMS=RTOI(...), N_WAITM(1), M=QU(100). Вырезать их нельзя:
        // от кадра «Z=IC(+27)» оставалось «Z=IC», и инструмент уезжал в Z=0 через
        // всю деталь, а отвод по X терялся совсем.
        if (parenthesesAreComments()) {
            while (true) {
                int open = text.indexOf('(');
                if (open < 0) {
                    break;
                }
                int close = text.indexOf(')', open);
                if (close < 0) {
                    text = text.substring(0, open);
                    break;
                }
                text = text.substring(0, open) + text.substring(close + 1);
            }
        }
        return text.trim();
    }

    /** Считать ли «(...)» комментарием: да у Fanuc/ISO, нет у Siemens. */
    private static boolean parenthesesAreComments() {
        try {
            String system = com.sergey.pisarev.util.UserSettings.getControlSystem();
            return system != null && system.toUpperCase(Locale.US).contains("FANUC");
        } catch (RuntimeException error) {
            return false;
        }
    }
}
