package com.sergey.pisarev.service;

import com.sergey.pisarev.model.WorkOffsetValues;
import com.sergey.pisarev.util.UserSettings;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Смещения G54–G59 из настроек (память + UserSettings).
 * Используется при разборе G-code для 2D-графика — как в ef1b080 19/05/26.
 */
public final class WorkOffsetStore {
    private static final Map<Integer, WorkOffsetValues> OFFSETS = new LinkedHashMap<>();

    private WorkOffsetStore() {
    }

    public static void clear() {
        OFFSETS.clear();
    }

    /** SinuTrain DEMO-Lathe: G54 Z=+2000, G55 Z=−200 (не X=2000). */
    public static void applySinuTrainDemoLathePresetsIfUnset() {
        if (!UserSettings.hasWorkOffsetMm(54)) {
            UserSettings.setWorkOffsetMm(54, 0.0, 2000.0);
        }
        if (!UserSettings.hasWorkOffsetMm(55)) {
            UserSettings.setWorkOffsetMm(55, 0.0, -200.0);
        }
    }

    /**
     * Частая ошибка: Z=2000 вписали в X (как в старых сборках). Переносим в Z.
     */
    public static void migrateG54ZFromMisplacedX() {
        for (int code = 54; code <= 55; code++) {
            double currentX = UserSettings.getWorkOffsetXmm(code);
            double currentZ = UserSettings.getWorkOffsetZmm(code);
            WorkOffsetValues normalized = normalizeLathePresetOffset(code, currentX, currentZ);
            if (Math.abs(normalized.xMm() - currentX) > 1.0E-6
                    || Math.abs(normalized.zMm() - currentZ) > 1.0E-6) {
                UserSettings.setWorkOffsetMm(code, normalized.xMm(), normalized.zMm());
            }
        }
    }

    public static WorkOffsetValues normalizeLathePresetOffset(int gCode, double xMm, double zMm) {
        if (Math.abs(zMm) < 0.5) {
            if (gCode == 54 && Math.abs(xMm - 2000.0) < 0.5) {
                return new WorkOffsetValues(0.0, 2000.0);
            }
            if (gCode == 55 && Math.abs(xMm + 200.0) < 0.5) {
                return new WorkOffsetValues(0.0, -200.0);
            }
        }
        if (gCode == 54 && Math.abs(xMm) < 0.5 && Math.abs(Math.abs(zMm) - 2000.0) < 0.5) {
            return new WorkOffsetValues(0.0, 2000.0);
        }
        if (gCode == 55 && Math.abs(xMm) < 0.5 && Math.abs(zMm - 200.0) < 0.5) {
            return new WorkOffsetValues(0.0, -200.0);
        }
        return new WorkOffsetValues(xMm, zMm);
    }

    /** Допустимые программируемые смещения Siemens: G54–G57 (+резерв до G73) и G505–G599. */
    public static boolean isSettableCode(int gCode) {
        return (gCode >= 54 && gCode <= 73) || (gCode >= 505 && gCode <= 599);
    }

    public static void loadFromUserSettings() {
        OFFSETS.clear();
        int count = UserSettings.getWorkOffsetCount();
        for (int i = 0; i < count; i++) {
            int code = 54 + i;
            OFFSETS.put(
                    code,
                    new WorkOffsetValues(
                            UserSettings.getWorkOffsetXmm(code),
                            UserSettings.getWorkOffsetZmm(code)));
        }
        for (Integer code : UserSettings.getWorkOffsetExtraCodes()) {
            if (code != null && isSettableCode(code)) {
                OFFSETS.put(
                        code,
                        new WorkOffsetValues(
                                UserSettings.getWorkOffsetXmm(code),
                                UserSettings.getWorkOffsetZmm(code)));
            }
        }
    }

    public static void set(int gCode, double xMm, double zMm) {
        if (!isSettableCode(gCode)) {
            return;
        }
        OFFSETS.put(gCode, new WorkOffsetValues(xMm, zMm));
    }

    public static WorkOffsetValues get(int gCode) {
        if (gCode == 500) {
            gCode = 54;
        }
        if (isSettableCode(gCode)) {
            WorkOffsetValues values = OFFSETS.get(gCode);
            if (values != null) {
                return values;
            }
            return new WorkOffsetValues(
                    UserSettings.getWorkOffsetXmm(gCode),
                    UserSettings.getWorkOffsetZmm(gCode));
        }
        return WorkOffsetValues.ZERO;
    }

    /** Расширенные коды (G505–G599), известные хранилищу. */
    public static java.util.SortedSet<Integer> extendedCodes() {
        java.util.TreeSet<Integer> codes = new java.util.TreeSet<>();
        for (Integer code : OFFSETS.keySet()) {
            if (code != null && code >= 505) {
                codes.add(code);
            }
        }
        for (Integer code : UserSettings.getWorkOffsetExtraCodes()) {
            if (code != null && isSettableCode(code) && code >= 505) {
                codes.add(code);
            }
        }
        return codes;
    }

    public static void persistToUserSettings() {
        int count = UserSettings.getWorkOffsetCount();
        UserSettings.setWorkOffsetCount(count);
        for (int i = 0; i < count; i++) {
            int code = 54 + i;
            WorkOffsetValues values = get(code);
            UserSettings.setWorkOffsetMm(code, values.xMm(), values.zMm());
        }
        java.util.SortedSet<Integer> extras = extendedCodes();
        for (Integer code : extras) {
            WorkOffsetValues values = get(code);
            UserSettings.setWorkOffsetMm(code, values.xMm(), values.zMm());
        }
        UserSettings.setWorkOffsetExtraCodes(extras);
        UserSettings.flush();
    }

    public static String fingerprint() {
        StringBuilder builder = new StringBuilder();
        builder.append(UserSettings.getWorkOffsetCount());
        for (int code = 54; code <= 73; code++) {
            WorkOffsetValues values = get(code);
            builder.append('|').append(code).append(':')
                    .append(values.xMm()).append(',').append(values.zMm());
        }
        for (Integer code : extendedCodes()) {
            WorkOffsetValues values = get(code);
            builder.append('|').append(code).append(':')
                    .append(values.xMm()).append(',').append(values.zMm());
        }
        return builder.toString();
    }
}
