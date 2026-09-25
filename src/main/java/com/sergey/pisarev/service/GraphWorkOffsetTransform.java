package com.sergey.pisarev.service;

import com.sergey.pisarev.model.WorkOffsetValues;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Смещения нулевой точки для графика (SinuTrain / Siemens).
 * Значения из настроек; активный G54–G59 / G500 — из программы.
 */
public final class GraphWorkOffsetTransform {

    private GraphWorkOffsetTransform() {
    }

    public static WorkOffsetValues resolveActiveOffset(
            int activeCodeFromProgram,
            Map<Integer, WorkOffsetValues> manualOffsets
    ) {
        if (manualOffsets == null || manualOffsets.isEmpty() || activeCodeFromProgram < 0) {
            return WorkOffsetValues.ZERO;
        }
        if (WorkOffsetStore.isSettableCode(activeCodeFromProgram)) {
            return manualOffsets.getOrDefault(activeCodeFromProgram, WorkOffsetValues.ZERO);
        }
        // G500 / G53 — машинные координаты, без смещения нулевой точки из настроек.
        return WorkOffsetValues.ZERO;
    }

    public static double machineX(double programX, WorkOffsetValues offset) {
        return programX + offset.xMm();
    }

    public static double machineZ(double programZ, WorkOffsetValues offset) {
        return programZ + offset.zMm();
    }

    /**
     * Для каждой строки УП — какое ручное смещение действует на ходы с этой строки и ниже
     * (до следующего G500/G53/G54…).
     */
    public static TreeMap<Integer, WorkOffsetValues> buildShiftAfterSourceLine(
            String programText,
            Map<Integer, WorkOffsetValues> manualOffsets,
            Function<String, Integer> workOffsetCodeOnLine
    ) {
        TreeMap<Integer, WorkOffsetValues> table = new TreeMap<>();
        if (programText == null || programText.trim().isEmpty()) {
            return table;
        }
        WorkOffsetValues activeShift = WorkOffsetValues.ZERO;
        int lineNumber = 1;
        for (String rawLine : programText.split("\\R")) {
            int sourceLine = lineNumber;
            // G54/G55… часто на строке без X/Z (например «T8 D1 G54») — isNonExecutable=true,
            // но смещение нулевой точки всё равно должно включаться.
            String line = GCodeProgramParser.stripFrameNumberForParse(
                    GCodeProgramParser.stripCommentsForParse(rawLine)).toUpperCase(Locale.US);
            if (!line.isBlank()) {
                Integer fixtureCode = workOffsetCodeOnLine.apply(line);
                if (fixtureCode != null) {
                    if (fixtureCode == 500 || fixtureCode == 53) {
                        activeShift = WorkOffsetValues.ZERO;
                    } else if (WorkOffsetStore.isSettableCode(fixtureCode)) {
                        activeShift = manualOffsets.getOrDefault(fixtureCode, WorkOffsetValues.ZERO);
                    }
                }
            }
            table.put(sourceLine, activeShift);
            ++lineNumber;
        }
        return table;
    }

    public static WorkOffsetValues shiftForSourceLine(
            TreeMap<Integer, WorkOffsetValues> shiftAfterLine,
            int sourceLine
    ) {
        if (sourceLine <= 0 || shiftAfterLine == null || shiftAfterLine.isEmpty()) {
            return WorkOffsetValues.ZERO;
        }
        Map.Entry<Integer, WorkOffsetValues> entry = shiftAfterLine.floorEntry(sourceLine);
        return entry != null ? entry.getValue() : WorkOffsetValues.ZERO;
    }
}
