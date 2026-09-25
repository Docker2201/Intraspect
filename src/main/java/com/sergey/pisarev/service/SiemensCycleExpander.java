package com.sergey.pisarev.service;

import com.sergey.pisarev.model.GCodeMoveData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Развёртка типовых циклов SINUMERIK (CYCLE) в сегменты G00/G01 для симуляции.
 * Расширяется по мере поддержки новых циклов.
 */
public final class SiemensCycleExpander {
    private static final Pattern CYCLE_CALL = Pattern.compile(
            "CYCLE(\\d+)(?:\\s*\\(([^)]*)\\))?",
            Pattern.CASE_INSENSITIVE);

    private SiemensCycleExpander() {
    }

    public static List<GCodeMoveData> expand(String programText, List<GCodeMoveData> baseMoves) {
        if (programText == null || !programText.toUpperCase(Locale.US).contains("CYCLE")) {
            return baseMoves;
        }
        List<CycleSegment> cycles = parseCycles(programText);
        if (cycles.isEmpty()) {
            return baseMoves;
        }
        ArrayList<GCodeMoveData> merged = new ArrayList<>(baseMoves);
        for (CycleSegment cycle : cycles) {
            merged.addAll(expandCycle(cycle));
        }
        merged.sort(java.util.Comparator.comparingInt(GCodeMoveData::sourceLine));
        return merged;
    }

    private static List<CycleSegment> parseCycles(String programText) {
        ArrayList<CycleSegment> cycles = new ArrayList<>();
        String[] lines = programText.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = GCodeProgramParser.stripFrameNumberForParse(lines[i]).toUpperCase(Locale.US);
            Matcher matcher = CYCLE_CALL.matcher(line);
            if (matcher.find()) {
                int number = Integer.parseInt(matcher.group(1));
                List<Double> args = parseNumbers(matcher.group(2));
                cycles.add(new CycleSegment(i + 1, number, args, line));
            }
        }
        return cycles;
    }

    private static List<GCodeMoveData> expandCycle(CycleSegment cycle) {
        return switch (cycle.number()) {
            case 82, 840, 84 -> expandDrillCycle(cycle);
            case 95 -> expandTurningCycle95(cycle);
            default -> List.of();
        };
    }

    /** Упрощённый CYCLE84: подвод по Z, сверление, отвод. */
    private static List<GCodeMoveData> expandDrillCycle(CycleSegment cycle) {
        if (cycle.args().size() < 4) {
            return List.of();
        }
        double retract = cycle.args().get(0);
        double depth = cycle.args().get(1);
        double diameter = cycle.args().size() > 3 ? cycle.args().get(3) : 0.0;
        int line = cycle.sourceLine();
        double surfaceZ = 0.0;
        ArrayList<GCodeMoveData> moves = new ArrayList<>();
        moves.add(segment(diameter, surfaceZ, diameter, retract, true, line));
        moves.add(segment(diameter, retract, diameter, depth, false, line));
        moves.add(segment(diameter, depth, diameter, retract, true, line));
        return moves;
    }

    /** Упрощённый CYCLE95: черновая проточка по Z с шагом. */
    private static List<GCodeMoveData> expandTurningCycle95(CycleSegment cycle) {
        if (cycle.args().size() < 5) {
            return List.of();
        }
        double xFinish = cycle.args().get(0);
        double zStart = cycle.args().get(1);
        double zEnd = cycle.args().get(2);
        double step = Math.max(0.5, cycle.args().get(3));
        double xAllowance = cycle.args().get(4);
        int line = cycle.sourceLine();
        ArrayList<GCodeMoveData> moves = new ArrayList<>();
        double z = zStart;
        double xRough = xFinish + xAllowance * 2.0;
        while (z >= zEnd - 0.001) {
            moves.add(segment(xRough, z, xFinish, z, false, line));
            z -= step;
        }
        return moves;
    }

    private static GCodeMoveData segment(
            double startX,
            double startZ,
            double endX,
            double endZ,
            boolean rapid,
            int line
    ) {
        return new GCodeMoveData(startX, startZ, endX, endZ, rapid, line, false, 0);
    }

    private static List<Double> parseNumbers(String args) {
        ArrayList<Double> numbers = new ArrayList<>();
        if (args == null || args.isBlank()) {
            return numbers;
        }
        for (String token : args.split("[,;\\s]+")) {
            if (token.isBlank()) {
                continue;
            }
            Double value = GCodeExpressionEvaluator.evaluate(token, Map.of());
            if (value != null) {
                numbers.add(value);
            }
        }
        return numbers;
    }

    private record CycleSegment(int sourceLine, int number, List<Double> args, String rawLine) {
    }
}
