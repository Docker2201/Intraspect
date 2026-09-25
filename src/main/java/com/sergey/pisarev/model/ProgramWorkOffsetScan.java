package com.sergey.pisarev.model;

import java.util.Map;
import java.util.Set;

/**
 * Результат разбора команд G54–G59 в тексте программы.
 */
public record ProgramWorkOffsetScan(
        int activeCode,
        Set<Integer> codesFound,
        Map<Integer, WorkOffsetValues> valuesFromProgram
) {
    public boolean hasOffsets() {
        return !this.codesFound.isEmpty();
    }

    public int rowCount() {
        int maxIndex = 0;
        for (int code : this.codesFound) {
            if (code >= 54 && code <= 73) {
                maxIndex = Math.max(maxIndex, code - 53);
            }
        }
        if (maxIndex == 0 && !this.valuesFromProgram.isEmpty()) {
            for (int code : this.valuesFromProgram.keySet()) {
                if (code >= 54 && code <= 73) {
                    maxIndex = Math.max(maxIndex, code - 53);
                }
            }
        }
        return Math.max(1, maxIndex);
    }

    public boolean hasValues() {
        return !this.valuesFromProgram.isEmpty();
    }
}
