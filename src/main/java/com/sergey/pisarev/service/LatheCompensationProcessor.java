package com.sergey.pisarev.service;

import com.sergey.pisarev.model.CncToolDefinition;
import com.sergey.pisarev.model.GCodeMoveData;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * G41/G42/G40 и номер T/D для траектории симуляции (SINUMERIK turning).
 * Программные X — контур; при G41/G42 смещаем траекторию центра резца.
 */
public final class LatheCompensationProcessor {
    private static final Pattern D_NUMBER = Pattern.compile("D(\\d+)", Pattern.CASE_INSENSITIVE);

    public enum CompensationMode {
        NONE,
        G41,
        G42
    }

    private LatheCompensationProcessor() {
    }

    public static List<GCodeMoveData> apply(
            String programText,
            List<GCodeMoveData> moves,
            List<CncToolDefinition> tools
    ) {
        return apply(programText, moves, tools, 0.0, true);
    }

    /**
     * Эквидистанта G41/G42 + OFFN. X смещается по нормали к контуру (не просто ±2R).
     *
     * @param fallbackToolRadiusMm радиус чашки из настроек — используется, если у инструмента радиус не задан
     * @param diameterMode X в ходах хранится как диаметр
     */
    public static List<GCodeMoveData> apply(
            String programText,
            List<GCodeMoveData> moves,
            List<CncToolDefinition> tools,
            double fallbackToolRadiusMm,
            boolean diameterMode
    ) {
        if (moves == null || moves.isEmpty()) {
            return List.of();
        }
        return LatheEquidistantPath.apply(programText, moves, tools, fallbackToolRadiusMm, diameterMode);
    }

    /** Resolve external allowance parameters without changing source line numbers. */
    public static String resolveOffnExpressions(String text, Map<String,Double> seed) {
        if(text==null || text.isBlank()) return text;
        var values=new TreeMap<String,Double>(seed);
        var out=new StringBuilder();
        var expression=Pattern.compile("\\bOFFN\\s*=\\s*([^;]+?)(?=\\s+[A-Z_$][A-Z0-9_$]*(?:\\s*=|\\s+[0-9])|\\s+[FGMSTD][0-9]|;|$)",Pattern.CASE_INSENSITIVE);
        String[] lines=text.split("\\R",-1);
        for(int i=0;i<lines.length;i++) {
            String line=lines[i];
            MachineParameterProgram.collectAssignments(line,values,null,false);
            GCodeExpressionEvaluator.collectRDefinitions(line,values);
            int semi=line.indexOf(';'); String code=semi<0?line:line.substring(0,semi);
            var matcher=expression.matcher(code);
            if(matcher.find() && !code.stripLeading().toUpperCase(Locale.US).startsWith("MSG")) {
                var unknown=new java.util.HashSet<String>();
                Double value=GCodeExpressionEvaluator.evaluate(matcher.group(1),values,unknown);
                if(value!=null && Double.isFinite(value) && unknown.isEmpty())
                    line=line.substring(0,matcher.start(1))+Double.toString(value)+line.substring(matcher.end(1));
            }
            if(i>0)out.append('\n');out.append(line);
        }
        return out.toString();
    }

    /**
     * Модальное значение OFFN (нормальное смещение контура Siemens) по строкам программы.
     * В карте — только строки, где OFFN меняется; до первого OFFN значение 0.
     */
    public static TreeMap<Integer, Double> scanOffnByLine(String programText) {
        TreeMap<Integer, Double> map = new TreeMap<>();
        if (programText == null || programText.isBlank()) {
            return map;
        }
        Map<String, Double> rVariables = new TreeMap<>();
        int lineNumber = 1;
        for (String rawLine : programText.split("\\R")) {
            GCodeExpressionEvaluator.collectRDefinitions(rawLine, rVariables);
            String line = GCodeProgramParser.stripFrameNumberForParse(stripComment(rawLine))
                    .toUpperCase(Locale.US);
            Matcher matcher = OFFN_PATTERN.matcher(line);
            if (matcher.find()) {
                Double value = GCodeExpressionEvaluator.evaluate(matcher.group(1), rVariables);
                if (value != null) {
                    map.put(lineNumber, value);
                }
            }
            lineNumber++;
        }
        return map;
    }

    private static final Pattern OFFN_PATTERN = Pattern.compile(
            "\\bOFFN\\s*=?\\s*([+-]?[\\d.,+\\-R]*\\d[\\d.,+\\-R]*|[+-]?R\\d+)",
            Pattern.CASE_INSENSITIVE);

    public static CompensationMode compensationModeAt(String programText, int sourceLine) {
        return modeAtLine(scanCompensationByLine(programText), sourceLine);
    }

    public static Map<Integer, CompensationMode> scanCompensationByLine(String programText) {
        TreeMap<Integer, CompensationMode> map = new TreeMap<>();
        if (programText == null || programText.isBlank()) {
            return map;
        }
        CompensationMode current = CompensationMode.NONE;
        int lineNumber = 1;
        String[] lines = programText.split("\\R");
        for (String rawLine : lines) {
            String line = GCodeProgramParser.stripFrameNumberForParse(stripComment(rawLine))
                    .toUpperCase(Locale.US);
            if (containsCode(line, "G40")) {
                current = CompensationMode.NONE;
            }
            if (containsCode(line, "G41")) {
                current = CompensationMode.G41;
            }
            if (containsCode(line, "G42")) {
                current = CompensationMode.G42;
            }
            map.put(lineNumber, current);
            lineNumber++;
        }
        return map;
    }

    public static Map<Integer, Integer> parseDNumberByLine(String programText) {
        HashMap<Integer, Integer> map = new HashMap<>();
        if (programText == null || programText.isBlank()) {
            return map;
        }
        var activeTools = GCodeProgramParser.parseToolNumberByLine(programText);
        int previousTool = 0;
        int currentD = 1;
        int lineNumber = 1;
        String[] lines = programText.split("\\R");
        for (String rawLine : lines) {
            String line = GCodeProgramParser.stripFrameNumberForParse(stripComment(rawLine))
                    .toUpperCase(Locale.US);
            line = GCodeProgramParser.toolControlBlock(rawLine);
            int activeTool = activeTools.getOrDefault(lineNumber, previousTool);
            if (activeTool != previousTool || GCodeProgramParser.isToolChangeBlock(rawLine)) currentD = 1;
            previousTool = activeTool;
            Matcher matcher = D_NUMBER.matcher(line);
            if (matcher.find()) {
                currentD = Integer.parseInt(matcher.group(1));
            }
            map.put(lineNumber, currentD);
            lineNumber++;
        }
        return map;
    }

    private static CompensationMode modeAtLine(Map<Integer, CompensationMode> modeByLine, int sourceLine) {
        if (modeByLine instanceof TreeMap<Integer, CompensationMode> tree) {
            var entry = tree.floorEntry(sourceLine);
            return entry != null ? entry.getValue() : CompensationMode.NONE;
        }
        CompensationMode mode = CompensationMode.NONE;
        for (Map.Entry<Integer, CompensationMode> entry : modeByLine.entrySet()) {
            if (entry.getKey() > sourceLine) {
                break;
            }
            mode = entry.getValue();
        }
        return mode;
    }

    public static double toolRadiusForLine(
            List<CncToolDefinition> tools,
            Map<Integer, Integer> toolByLine,
            Map<Integer, Integer> dByLine,
            int sourceLine
    ) {
        int toolNumber = toolByLine.getOrDefault(sourceLine, 0);
        if (toolNumber <= 0 || tools == null) {
            return 0.0;
        }
        int edge = dByLine.getOrDefault(sourceLine, 1);
        CncToolDefinition tool = toolForLine(tools, toolNumber, edge);
        return tool != null ? Math.max(0.0, tool.getRadius()) : 0.0;
    }

    public static CncToolDefinition toolForLine(
            List<CncToolDefinition> tools,
            Map<Integer, Integer> toolByLine,
            Map<Integer, Integer> dByLine,
            int sourceLine
    ) {
        int toolNumber = toolByLine.getOrDefault(sourceLine, 0);
        if (toolNumber <= 0 || tools == null) {
            return null;
        }
        int edge = dByLine.getOrDefault(sourceLine, 1);
        return toolForLine(tools, toolNumber, edge);
    }

    private static CncToolDefinition toolForLine(List<CncToolDefinition> tools, int toolNumber, int edge) {
        return resolveTool(tools, toolNumber, edge);
    }

    /**
     * Инструмент по номеру из кадра: сначала по номеру T, затем по номеру ячейки магазина.
     *
     * <p>У SINUMERIK с управлением инструментом «T5» выбирает инструмент, стоящий
     * в ячейке 5 магазина, а не инструмент с номером 5: в списке стойки первая колонка —
     * это как раз место (Platz). Список из SinuTrain переносят в приложение один в один,
     * и без этого правила программа с T3/T5 не находила ни одного инструмента.
     * Совпадение по номеру всегда в приоритете, поэтому программы без магазина
     * ведут себя ровно как раньше.
     */
    public static CncToolDefinition resolveTool(
            List<CncToolDefinition> tools, int toolNumber, int edge) {
        if (tools == null || toolNumber <= 0) {
            return null;
        }
        for (CncToolDefinition tool : tools) {
            if (tool.getToolNumber() == toolNumber && tool.getEdgeNumber() == edge) {
                return tool;
            }
        }
        for (CncToolDefinition tool : tools) {
            if (tool.getLocation() == toolNumber && tool.getEdgeNumber() == edge) {
                return tool;
            }
        }
        return null;
    }

    /** Номера, по которым программа может адресовать инструмент: номер T и ячейка. */
    public static java.util.Set<Integer> addressableToolNumbers(List<CncToolDefinition> tools) {
        java.util.Set<Integer> numbers = new java.util.HashSet<>();
        if (tools == null) {
            return numbers;
        }
        for (CncToolDefinition tool : tools) {
            if (tool.getToolNumber() > 0) {
                numbers.add(tool.getToolNumber());
            }
            if (tool.getLocation() > 0) {
                numbers.add(tool.getLocation());
            }
        }
        return numbers;
    }

    private static boolean containsCode(String line, String code) {
        for (String token : line.split("\\s+")) {
            if (token.equals(code)) {
                return true;
            }
            if (token.matches("G\\d+")) {
                try {
                    int value = Integer.parseInt(token.substring(1));
                    int expected = Integer.parseInt(code.substring(1));
                    if (value == expected) {
                        return true;
                    }
                }
                catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private static String stripComment(String line) {
        if (line == null) {
            return "";
        }
        String trimmed = line.trim();
        int semi = trimmed.indexOf(';');
        if (semi >= 0) {
            line = trimmed.substring(0, semi);
        } else {
            line = trimmed;
        }
        while (line.contains("(") && line.contains(")")) {
            int open = line.indexOf('(');
            int close = line.indexOf(')', open);
            if (close < 0) {
                break;
            }
            line = line.substring(0, open) + line.substring(close + 1);
        }
        return line.trim();
    }
}
