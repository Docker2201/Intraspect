package com.sergey.pisarev.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Пользовательские настройки, сохраняемые между запусками.
 */
public final class UserSettings {
    private static final Logger LOGGER = Logger.getLogger(UserSettings.class.getName());
    /** Настройки хранятся файлом рядом с программой (портативность), а не в реестре Windows. */
    private static final PortablePrefs PREFS = PortablePrefs.open("settings.properties");
    private static final String LAST_PROGRAM_FILE = "last_program.nc";
    private static final Path LAST_PROGRAM_PATH = PortableStorage.file(LAST_PROGRAM_FILE);
    private static final Path RIGHT_PROGRAM_PATH = PortableStorage.file("last_program_right.nc");
    private static final Path PARAMETER_PROGRAM_PATH = PortableStorage.file("parameters.spf");
    private static final String KEY_RIGHT_PANE_VISIBLE = "rightPaneVisible";
    private static final String KEY_ACTIVE_SIDE = "activeMachiningSide";

    private static final String KEY_PROGRAM_FONT_SIZE = "programFontSize";
    private static final String KEY_DARK_THEME = "darkTheme";
    private static final String KEY_LANGUAGE = "interfaceLanguage";
    private static final String KEY_MCP_AUTOSTART = "mcpAutoStart";
    private static final String KEY_MCP_PORT = "mcpPort";
    private static final String KEY_MCP_ACCESS = "mcpAccess";
    private static final String KEY_MCP_KEY = "mcpKey";
    private static final String KEY_CONTROL_SYSTEM = "controlSystem";
    private static final String KEY_MACHINE_TYPE = "machineType";
    private static final String KEY_FEED_OVERRIDE = "feedOverridePercent";
    private static final String KEY_SPINDLE_OVERRIDE = "spindleOverridePercent";
    private static final String KEY_BLANK_DIAMETER = "blankDiameterMm";
    private static final String KEY_USE_BLANK = "useBlankDiameter";
    private static final String KEY_CUP_RADIUS = "cupRadiusMm";
    private static final String KEY_USE_CUP = "useCupRadius";
    private static final String KEY_EQUIDISTANT_ON = "equidistantEnabled";
    private static final String KEY_EQUIDISTANT_FROM_LIBRARY = "equidistantRadiusFromLibrary";
    private static final String KEY_EQUIDISTANT_RADIUS = "equidistantRadiusMm";
    private static final String KEY_EQUIDISTANT_ALLOWANCE = "equidistantAllowanceMm";
    private static final String KEY_WORK_OFFSET_COUNT = "workOffsetCount";
    private static final String KEY_WORK_OFFSET_PREFIX = "workOffset.";
    private static final int DEFAULT_FEED_OVERRIDE = 100;
    private static final int DEFAULT_SPINDLE_OVERRIDE = 100;
    private static final int DEFAULT_PROGRAM_FONT_SIZE = 12;
    private static final int MIN_PROGRAM_FONT_SIZE = 10;
    private static final int MAX_PROGRAM_FONT_SIZE = 32;

    private UserSettings() {
    }

    public static int getProgramFontSize() {
        return clampProgramFontSize(PREFS.getInt(KEY_PROGRAM_FONT_SIZE, DEFAULT_PROGRAM_FONT_SIZE));
    }

    public static void setProgramFontSize(int size) {
        PREFS.putInt(KEY_PROGRAM_FONT_SIZE, clampProgramFontSize(size));
        flush();
    }

    public static String getLastProgramText() {
        try {
            Path source = PortableStorage.readablePath(LAST_PROGRAM_PATH);
            if (!Files.isRegularFile(source)) {
                return "";
            }
            return Files.readString(source, StandardCharsets.UTF_8);
        }
        catch (IOException | RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Failed to read last program text", exception);
            return "";
        }
    }

    public static void setLastProgramText(String text) {
        try {
            PortableStorage.writeAtomically(
                    LAST_PROGRAM_PATH, text != null ? text : "", StandardCharsets.UTF_8);
        }
        catch (IOException | RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Failed to save last program text", exception);
        }
    }

    /**
     * Программа правого окна кода. На двухканальном станке слева канал 2, справа
     * канал 1, как стоят головки. Лежит рядом с основной, чтобы после перезапуска
     * оба окна были на месте.
     */
    public static String getRightProgramText() {
        return readTextFile(RIGHT_PROGRAM_PATH, "right pane program");
    }

    public static void setRightProgramText(String text) {
        writeTextFile(RIGHT_PROGRAM_PATH, text, "right pane program");
    }

    /**
     * Программа канала и стороны из набора двухканальной программы.
     *
     * <p>Колесо точат с двух сторон: сначала сторона 1 в обоих каналах, потом деталь
     * переворачивают и идёт сторона 2. В окнах кода видно одну сторону, остальные лежат
     * здесь — иначе половина открытого набора просто пропадала.
     */
    public static String getChannelSideProgram(int channel, int side) {
        return readTextFile(channelSidePath(channel, side), "channel program");
    }

    public static void setChannelSideProgram(int channel, int side, String text) {
        writeTextFile(channelSidePath(channel, side), text, "channel program");
    }

    private static Path channelSidePath(int channel, int side) {
        int safeChannel = channel == 2 ? 2 : 1;
        int safeSide = side == 2 ? 2 : 1;
        return PortableStorage.file("program_c" + safeChannel + "_s" + safeSide + ".nc");
    }

    /** Какая сторона детали сейчас в окнах кода: 1 или 2. */
    public static int getActiveSide() {
        int side = PREFS.getInt(KEY_ACTIVE_SIDE, 1);
        return side == 2 ? 2 : 1;
    }

    public static void setActiveSide(int side) {
        PREFS.putInt(KEY_ACTIVE_SIDE, side == 2 ? 2 : 1);
        flush();
    }

    public static double getTurnoverValue(String name, double fallback) {
        return PREFS.getDouble("turnover." + name, fallback);
    }

    public static void setTurnoverValues(double zSum, double diameter, double zMin, double length) {
        PREFS.putDouble("turnover.zSum", zSum);
        PREFS.putDouble("turnover.diameter", diameter);
        PREFS.putDouble("turnover.zMin", zMin);
        PREFS.putDouble("turnover.length", length);
        flush();
    }

    public static boolean isApproximatePreformEnabled() { return PREFS.getBoolean("turnover.preform.enabled", false); }
    public static void setApproximatePreform(boolean enabled, double allowance) {
        if (!Double.isFinite(allowance) || allowance < 0 || (enabled && allowance == 0))
            throw new IllegalArgumentException("Invalid preform allowance");
        PREFS.putBoolean("turnover.preform.enabled", enabled);
        PREFS.putDouble("turnover.preform.allowance", allowance);
        flush();
    }

    public static void setTurnoverBoreDiameter(double diameter) {
        PREFS.putDouble("turnover.initialBoreDiameter", diameter);
        flush();
    }

    /** Программа параметров: размеры, по именам которых ходит обрабатывающая программа. */
    public static String getParameterProgramText() {
        return readTextFile(PARAMETER_PROGRAM_PATH, "parameter program");
    }

    public static void setParameterProgramText(String text) {
        writeTextFile(PARAMETER_PROGRAM_PATH, text, "parameter program");
    }

    /** Развёрнут ли правый блок кода (второй канал). */
    public static boolean isRightPaneVisible() {
        return PREFS.getBoolean(KEY_RIGHT_PANE_VISIBLE, false);
    }

    public static void setRightPaneVisible(boolean visible) {
        PREFS.putBoolean(KEY_RIGHT_PANE_VISIBLE, visible);
        flush();
    }

    private static String readTextFile(Path path, String what) {
        try {
            Path source = PortableStorage.readablePath(path);
            if (!Files.isRegularFile(source)) {
                return "";
            }
            return Files.readString(source, StandardCharsets.UTF_8);
        }
        catch (IOException | RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Failed to read " + what, exception);
            return "";
        }
    }

    private static void writeTextFile(Path path, String text, String what) {
        try {
            PortableStorage.writeAtomically(path, text != null ? text : "", StandardCharsets.UTF_8);
        }
        catch (IOException | RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Failed to save " + what, exception);
        }
    }

    public static int clampProgramFontSize(int size) {
        return Math.max(MIN_PROGRAM_FONT_SIZE, Math.min(MAX_PROGRAM_FONT_SIZE, size));
    }

    public static boolean isDarkTheme() {
        return PREFS.getBoolean(KEY_DARK_THEME, false);
    }

    public static void setDarkTheme(boolean darkTheme) {
        PREFS.putBoolean(KEY_DARK_THEME, darkTheme);
        flush();
    }

    /**
     * Язык интерфейса: "ru", "en", "uk" или пустая строка — «как в системе Windows».
     * Читается один раз при запуске, см. {@link I18n}.
     */
    public static String getInterfaceLanguage() {
        return PREFS.get(KEY_LANGUAGE, "");
    }

    public static void setInterfaceLanguage(String language) {
        PREFS.put(KEY_LANGUAGE, language == null ? "" : language.trim());
        flush();
    }

    /**
     * Поднимать ли сервер MCP сразу при запуске программы.
     *
     * <p>По умолчанию нет: сервер даёт нейросети доступ к программе, и включать его
     * человек должен осознанно. Кто работает с ИИ каждый день — ставит галочку и больше
     * к ней не возвращается.
     */
    public static boolean isMcpAutoStart() {
        return PREFS.getBoolean(KEY_MCP_AUTOSTART, false);
    }

    public static void setMcpAutoStart(boolean autoStart) {
        PREFS.putBoolean(KEY_MCP_AUTOSTART, autoStart);
        flush();
    }

    public static int getMcpPort() {
        int port = PREFS.getInt(KEY_MCP_PORT, 8722);
        return port >= 0 && port <= 65535 ? port : 8722;
    }

    public static void setMcpPort(int port) {
        PREFS.putInt(KEY_MCP_PORT, port >= 0 && port <= 65535 ? port : 8722);
        flush();
    }

    /** Режим доступа для MCP: "READ_ONLY" или "FULL". */
    public static String getMcpAccess() {
        return PREFS.get(KEY_MCP_ACCESS, "READ_ONLY");
    }

    public static void setMcpAccess(String access) {
        PREFS.put(KEY_MCP_ACCESS, "FULL".equals(access) ? "FULL" : "READ_ONLY");
        flush();
    }

    /**
     * Ключ доступа к серверу MCP.
     *
     * <p>Ключ постоянный, а не новый на каждый запуск. Иначе после каждого включения
     * программы приходилось бы заново прописывать адрес в клиенте нейросети — а адрес
     * прописывают один раз. Создаётся при первом обращении и лежит в том же файле
     * настроек рядом с программой; сменить можно кнопкой в окне «МСП».
     *
     * <p>Файл настроек не шифруется, но и сервер слушает только 127.0.0.1: чтобы
     * воспользоваться ключом, надо уже сидеть за этим компьютером.
     */
    public static synchronized String getMcpKey() {
        String value = PREFS.get(KEY_MCP_KEY, "");
        if (value == null || value.isBlank()) {
            value = newMcpKey();
            PREFS.put(KEY_MCP_KEY, value);
            flush();
        }
        return value;
    }

    /** Выдаёт новый ключ: старые подключения после этого не работают. */
    public static synchronized String resetMcpKey() {
        String value = newMcpKey();
        PREFS.put(KEY_MCP_KEY, value);
        flush();
        return value;
    }

    private static String newMcpKey() {
        byte[] bytes = new byte[18];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String getControlSystem() {
        return PREFS.get(KEY_CONTROL_SYSTEM, "SIEMENS_MILLING");
    }

    public static void setControlSystem(String controlSystem) {
        PREFS.put(KEY_CONTROL_SYSTEM, controlSystem);
        flush();
    }

    public static String getMachineType() {
        String stored = PREFS.get(KEY_MACHINE_TYPE, "GENERIC_LATHE");
        if ("DEMO_LATHE_840D".equals(stored)) {
            return "GENERIC_LATHE";
        }
        return stored;
    }

    public static void setMachineType(String machineType) {
        PREFS.put(KEY_MACHINE_TYPE, machineType);
        flush();
    }

    public static int getFeedOverridePercent() {
        return clampFeedOverride(PREFS.getInt(KEY_FEED_OVERRIDE, DEFAULT_FEED_OVERRIDE));
    }

    public static void setFeedOverridePercent(int percent) {
        PREFS.putInt(KEY_FEED_OVERRIDE, clampFeedOverride(percent));
        flush();
    }

    public static int getSpindleOverridePercent() {
        return clampSpindleOverride(PREFS.getInt(KEY_SPINDLE_OVERRIDE, DEFAULT_SPINDLE_OVERRIDE));
    }

    public static void setSpindleOverridePercent(int percent) {
        PREFS.putInt(KEY_SPINDLE_OVERRIDE, clampSpindleOverride(percent));
        flush();
    }

    public static int clampFeedOverride(int percent) {
        if (percent <= 0) {
            return 0;
        }
        return Math.min(120, percent);
    }

    public static int clampSpindleOverride(int percent) {
        return Math.max(50, Math.min(120, percent));
    }

    public static double getBlankDiameterMm() {
        return Math.max(0.0, PREFS.getDouble(KEY_BLANK_DIAMETER, 0.0));
    }

    public static void setBlankDiameterMm(double mm) {
        PREFS.putDouble(KEY_BLANK_DIAMETER, Math.max(0.0, mm));
        flush();
    }

    public static boolean isUseBlankDiameter() {
        return PREFS.getBoolean(KEY_USE_BLANK, false);
    }

    public static void setUseBlankDiameter(boolean use) {
        PREFS.putBoolean(KEY_USE_BLANK, use);
        flush();
    }

    /** Масштаб окна, как в браузере; 0 — не задан. */
    public static double getUiScale() {
        return PREFS.getDouble("ui.scale", 0.0);
    }

    public static void setUiScale(double factor) {
        PREFS.putDouble("ui.scale", factor);
        flush();
    }

    public static double getCupRadiusMm() {
        return Math.max(0.0, PREFS.getDouble(KEY_CUP_RADIUS, 0.0));
    }

    public static void setCupRadiusMm(double mm) {
        PREFS.putDouble(KEY_CUP_RADIUS, Math.max(0.0, mm));
        flush();
    }

    public static boolean isUseCupRadius() {
        return PREFS.getBoolean(KEY_USE_CUP, false);
    }

    public static void setUseCupRadius(boolean use) {
        PREFS.putBoolean(KEY_USE_CUP, use);
        flush();
    }

    // --- Эквидистанта (коррекция на радиус вершины резца, G41/G42) ---

    /** Строить ли эквидистанту: выключено — на экране остаётся программный контур. */
    public static boolean isEquidistantEnabled() {
        return PREFS.getBoolean(KEY_EQUIDISTANT_ON, true);
    }

    public static void setEquidistantEnabled(boolean enabled) {
        PREFS.putBoolean(KEY_EQUIDISTANT_ON, enabled);
        flush();
    }

    /** true — радиус вершины берём из библиотеки по активным T/D; false — из поля ниже. */
    public static boolean isEquidistantRadiusFromLibrary() {
        return PREFS.getBoolean(KEY_EQUIDISTANT_FROM_LIBRARY, true);
    }

    public static void setEquidistantRadiusFromLibrary(boolean fromLibrary) {
        PREFS.putBoolean(KEY_EQUIDISTANT_FROM_LIBRARY, fromLibrary);
        flush();
    }

    /** Радиус вершины вручную; он же запасной, если в библиотеке радиус не заполнен. */
    public static double getEquidistantRadiusMm() {
        return Math.max(0.0, PREFS.getDouble(KEY_EQUIDISTANT_RADIUS, 0.0));
    }

    public static void setEquidistantRadiusMm(double radiusMm) {
        PREFS.putDouble(KEY_EQUIDISTANT_RADIUS, Math.max(0.0, radiusMm));
        flush();
    }

    /** Припуск сверх программного OFFN, мм: наладчик оставляет запас под чистовой проход. */
    public static double getEquidistantAllowanceMm() {
        return PREFS.getDouble(KEY_EQUIDISTANT_ALLOWANCE, 0.0);
    }

    public static void setEquidistantAllowanceMm(double allowanceMm) {
        PREFS.putDouble(KEY_EQUIDISTANT_ALLOWANCE, allowanceMm);
        flush();
    }

    public static int getWorkOffsetCount() {
        return Math.max(0, Math.min(20, PREFS.getInt(KEY_WORK_OFFSET_COUNT, 3)));
    }

    public static void setWorkOffsetCount(int count) {
        PREFS.putInt(KEY_WORK_OFFSET_COUNT, Math.max(0, Math.min(20, count)));
        flush();
    }

    public static double getWorkOffsetXmm(int gCode) {
        return PREFS.getDouble(KEY_WORK_OFFSET_PREFIX + gCode + ".x", 0.0);
    }

    public static double getWorkOffsetZmm(int gCode) {
        return PREFS.getDouble(KEY_WORK_OFFSET_PREFIX + gCode + ".z", 0.0);
    }

    public static boolean hasWorkOffsetMm(int gCode) {
        return PREFS.get(KEY_WORK_OFFSET_PREFIX + gCode + ".x", null) != null
                || PREFS.get(KEY_WORK_OFFSET_PREFIX + gCode + ".z", null) != null;
    }

    public static void setWorkOffsetMm(int gCode, double xMm, double zMm) {
        PREFS.putDouble(KEY_WORK_OFFSET_PREFIX + gCode + ".x", xMm);
        PREFS.putDouble(KEY_WORK_OFFSET_PREFIX + gCode + ".z", zMm);
        flush();
    }

    /** Расширенные коды смещений Siemens (G505–G599), сохранённые пользователем. */
    public static java.util.List<Integer> getWorkOffsetExtraCodes() {
        String raw = PREFS.get(KEY_WORK_OFFSET_PREFIX + "extraCodes", "");
        java.util.ArrayList<Integer> codes = new java.util.ArrayList<>();
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                codes.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {
            }
        }
        return codes;
    }

    public static void setWorkOffsetExtraCodes(java.util.Collection<Integer> codes) {
        StringBuilder builder = new StringBuilder();
        if (codes != null) {
            for (Integer code : codes) {
                if (code == null) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(',');
                }
                builder.append(code);
            }
        }
        PREFS.put(KEY_WORK_OFFSET_PREFIX + "extraCodes", builder.toString());
        flush();
    }

    public static void flush() {
        PREFS.flush();
    }
}
