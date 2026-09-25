package com.sergey.pisarev.controller;

import com.sergey.pisarev.ai.AppAccess;
import com.sergey.pisarev.ai.McpServer;
import com.sergey.pisarev.ai.ModelSnapshot;
import com.sergey.pisarev.model.CncToolDefinition;
import com.sergey.pisarev.model.GCodeMoveData;
import com.sergey.pisarev.model.MachineConfiguration;
import com.sergey.pisarev.model.ToolCutDirection;
import com.sergey.pisarev.model.SimulationContext;
import com.sergey.pisarev.service.ProgramDiagnostics;
import com.sergey.pisarev.util.MiniJson;
import com.sergey.pisarev.util.UserSettings;

import javafx.application.Platform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Что нейросеть видит и меняет в программе.
 *
 * <p>Сервер MCP работает в своих потоках, а интерфейс JavaFX можно трогать только из
 * его собственного потока, поэтому каждый вызов переносится сюда через
 * {@link Platform#runLater} и ждёт результата. Ждём с ограничением по времени: если
 * поток интерфейса чем-то занят надолго, нейросеть получит понятный отказ, а не
 * зависнет молча.
 */
final class AiAppAccess implements AppAccess {
    /** Дольше этого не ждём поток интерфейса: лучше отказ, чем зависший клиент. */
    private static final long TIMEOUT_SECONDS = 25;

    private final MainController controller;

    AiAppAccess(MainController controller) {
        this.controller = controller;
    }

    // ------------------------------------------------------------------
    // Программа
    // ------------------------------------------------------------------

    @Override
    public String programText() {
        return onFx(this.controller::aiProgramText, "");
    }

    @Override
    public String setProgramText(String text, String reason) {
        return onFxText(() -> {
            this.controller.aiSetProgramText(text);
            List<Problem> problems = problems(this.controller.aiCheckProgram());
            int lines = text.isEmpty() ? 0 : text.split("\r?\n", -1).length;
            int red = 0;
            int yellow = 0;
            for (Problem problem : problems) {
                if ("ERROR".equals(problem.severity())) {
                    red++;
                } else {
                    yellow++;
                }
            }
            this.controller.aiStatus("Программу заменила нейросеть"
                    + (reason == null || reason.isBlank() ? "" : ": " + reason));
            return "Программа заменена: " + lines + " строк. Проверка: "
                    + red + " красных, " + yellow + " жёлтых."
                    + (red > 0 ? " Красные надо убрать, иначе 3D не построится." : "");
        }, "не удалось подставить программу");
    }

    @Override
    public List<Problem> checkProgram() {
        return onFx(() -> problems(this.controller.aiCheckProgram()), List.of());
    }

    private static List<Problem> problems(List<ProgramDiagnostics.Diagnostic> found) {
        List<Problem> result = new ArrayList<>();
        for (ProgramDiagnostics.Diagnostic diagnostic : found) {
            result.add(new Problem(
                    diagnostic.code(),
                    String.valueOf(diagnostic.severity()),
                    diagnostic.title(),
                    diagnostic.details(),
                    diagnostic.line(),
                    diagnostic.autoFixLabel()));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Настройки
    // ------------------------------------------------------------------

    @Override
    public Map<String, Object> settings() {
        return onFx(() -> {
            Map<String, Object> values = new LinkedHashMap<>();
            MachineConfiguration machine = this.controller.aiMachineConfiguration();
            values.put("controlSystem", machine.getControlSystem().getDisplayName());
            values.put("machineType", machine.getMachineType().getDisplayName());
            values.put("axisMode", this.controller.aiAxisMode());
            values.put("blankDiameterMm", UserSettings.getBlankDiameterMm());
            values.put("useBlankDiameter", UserSettings.isUseBlankDiameter());
            values.put("cupRadiusMm", UserSettings.getCupRadiusMm());
            values.put("useCupRadius", UserSettings.isUseCupRadius());
            values.put("equidistantEnabled", UserSettings.isEquidistantEnabled());
            values.put("equidistantRadiusFromLibrary", UserSettings.isEquidistantRadiusFromLibrary());
            values.put("equidistantRadiusMm", UserSettings.getEquidistantRadiusMm());
            values.put("equidistantAllowanceMm", UserSettings.getEquidistantAllowanceMm());
            values.put("feedOverridePercent", (double) UserSettings.getFeedOverridePercent());
            values.put("spindleOverridePercent", (double) UserSettings.getSpindleOverridePercent());
            values.put("workOffsetCount", (double) UserSettings.getWorkOffsetCount());
            List<Object> offsets = new ArrayList<>();
            for (int code : offsetCodes()) {
                if (!UserSettings.hasWorkOffsetMm(code)) {
                    continue;
                }
                Map<String, Object> offset = new LinkedHashMap<>();
                offset.put("code", (double) code);
                offset.put("x", UserSettings.getWorkOffsetXmm(code));
                offset.put("z", UserSettings.getWorkOffsetZmm(code));
                offsets.add(offset);
            }
            values.put("workOffsets", offsets);
            return values;
        }, Map.of());
    }

    @Override
    public List<String> applySettings(Map<String, Object> input) {
        return onFx(() -> {
            List<String> changed = new ArrayList<>();
            if (input.containsKey("blankDiameterMm")) {
                double value = MiniJson.number(input, "blankDiameterMm", -1);
                if (value > 0.0) {
                    UserSettings.setBlankDiameterMm(value);
                    changed.add(String.format(Locale.US, "диаметр заготовки %.1f мм", value));
                }
            }
            if (input.containsKey("useBlankDiameter")) {
                boolean value = flag(input, "useBlankDiameter");
                UserSettings.setUseBlankDiameter(value);
                changed.add("учитывать диаметр заготовки: " + (value ? "да" : "нет"));
            }
            if (input.containsKey("cupRadiusMm")) {
                double value = MiniJson.number(input, "cupRadiusMm", -1);
                if (value >= 0.0) {
                    UserSettings.setCupRadiusMm(value);
                    changed.add(String.format(Locale.US, "радиус чашки %.1f мм", value));
                }
            }
            if (input.containsKey("useCupRadius")) {
                boolean value = flag(input, "useCupRadius");
                UserSettings.setUseCupRadius(value);
                changed.add("учитывать чашку: " + (value ? "да" : "нет"));
            }
            if (input.containsKey("equidistantEnabled")) {
                boolean value = flag(input, "equidistantEnabled");
                UserSettings.setEquidistantEnabled(value);
                changed.add("эквидистанта: " + (value ? "строить" : "не строить"));
            }
            if (input.containsKey("equidistantRadiusFromLibrary")) {
                boolean value = flag(input, "equidistantRadiusFromLibrary");
                UserSettings.setEquidistantRadiusFromLibrary(value);
                changed.add("радиус вершины: " + (value ? "из библиотеки" : "вручную"));
            }
            if (input.containsKey("equidistantRadiusMm")) {
                double value = MiniJson.number(input, "equidistantRadiusMm", -1);
                if (value >= 0.0) {
                    UserSettings.setEquidistantRadiusMm(value);
                    changed.add(String.format(Locale.US, "радиус вершины %.2f мм", value));
                }
            }
            if (input.containsKey("equidistantAllowanceMm")) {
                double value = MiniJson.number(input, "equidistantAllowanceMm", -1);
                if (value >= 0.0) {
                    UserSettings.setEquidistantAllowanceMm(value);
                    changed.add(String.format(Locale.US, "припуск %.2f мм", value));
                }
            }
            if (input.containsKey("feedOverridePercent")) {
                int value = UserSettings.clampFeedOverride(
                        (int) MiniJson.number(input, "feedOverridePercent", 100));
                UserSettings.setFeedOverridePercent(value);
                changed.add("коррекция подачи " + value + " %");
            }
            if (input.containsKey("spindleOverridePercent")) {
                int value = UserSettings.clampSpindleOverride(
                        (int) MiniJson.number(input, "spindleOverridePercent", 100));
                UserSettings.setSpindleOverridePercent(value);
                changed.add("коррекция шпинделя " + value + " %");
            }
            if (input.containsKey("workOffsetCount")) {
                int value = Math.max(1, Math.min(46, (int) MiniJson.number(input, "workOffsetCount", 3)));
                UserSettings.setWorkOffsetCount(value);
                changed.add("точек смещения: " + value);
            }
            if (input.containsKey("controlSystem")) {
                MachineConfiguration.ControlSystem system =
                        matchControlSystem(MiniJson.string(input, "controlSystem", ""));
                if (system == null) {
                    changed.add("система ЧПУ не распознана: "
                            + MiniJson.string(input, "controlSystem", ""));
                } else {
                    UserSettings.setControlSystem(system.name());
                    changed.add("система ЧПУ " + system.getDisplayName());
                }
            }
            if (input.containsKey("machineType")) {
                MachineConfiguration.MachineType machine =
                        matchMachineType(MiniJson.string(input, "machineType", ""));
                if (machine == null) {
                    changed.add("станок не распознан: " + MiniJson.string(input, "machineType", ""));
                } else {
                    UserSettings.setMachineType(machine.name());
                    changed.add("станок " + machine.getDisplayName());
                }
            }
            if (input.containsKey("programFontSize")) {
                int size = UserSettings.clampProgramFontSize(
                        (int) MiniJson.number(input, "programFontSize", 12));
                UserSettings.setProgramFontSize(size);
                changed.add("размер шрифта программы " + size);
            }
            if (input.get("workOffsets") instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> raw)) {
                        continue;
                    }
                    Map<String, Object> offset = cast(raw);
                    int code = (int) MiniJson.number(offset, "code", -1);
                    if (code < 54) {
                        continue;
                    }
                    double x = MiniJson.number(offset, "x", 0.0);
                    double z = MiniJson.number(offset, "z", 0.0);
                    UserSettings.setWorkOffsetMm(code, x, z);
                    changed.add(String.format(Locale.US, "G%d: X=%.3f Z=%.3f", code, x, z));
                }
            }
            if (!changed.isEmpty()) {
                UserSettings.flush();
                this.controller.aiRefreshSettingsUi();
                this.controller.aiStatus("Настройки изменила нейросеть");
            }
            return changed;
        }, List.of());
    }

    /** Система ЧПУ по названию перечисления или по подписи в окне настроек. */
    private static MachineConfiguration.ControlSystem matchControlSystem(String wanted) {
        String text = wanted == null ? "" : wanted.trim();
        for (MachineConfiguration.ControlSystem system : MachineConfiguration.ControlSystem.values()) {
            if (system.name().equalsIgnoreCase(text)
                    || system.getDisplayName().equalsIgnoreCase(text)) {
                return system;
            }
        }
        return null;
    }

    private static MachineConfiguration.MachineType matchMachineType(String wanted) {
        String text = wanted == null ? "" : wanted.trim();
        for (MachineConfiguration.MachineType machine : MachineConfiguration.MachineType.values()) {
            if (machine.name().equalsIgnoreCase(text)
                    || machine.getDisplayName().equalsIgnoreCase(text)) {
                return machine;
            }
        }
        return null;
    }

    /** Коды, которые вообще может держать программа: G54..G57 и G505..G599. */
    private static List<Integer> offsetCodes() {
        List<Integer> codes = new ArrayList<>();
        for (int code = 54; code <= 57; code++) {
            codes.add(code);
        }
        for (int code = 505; code <= 599; code++) {
            codes.add(code);
        }
        return codes;
    }

    // ------------------------------------------------------------------
    // Инструменты
    // ------------------------------------------------------------------

    @Override
    public List<Map<String, Object>> toolLibrary() {
        return onFx(() -> {
            List<Map<String, Object>> result = new ArrayList<>();
            for (CncToolDefinition tool : this.controller.aiToolLibrary()) {
                // Все поля, которые видит и правит человек в таблице инструментов:
                // иначе нейросеть не знает, что именно можно исправить.
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("location", (double) tool.getLocation());
                row.put("name", tool.getName());
                row.put("modelId", tool.getModelId());
                row.put("typeCode", (double) tool.getTypeCode());
                row.put("toolNumber", (double) tool.getToolNumber());
                row.put("edge", (double) tool.getEdgeNumber());
                row.put("radiusMm", tool.getRadius());
                row.put("lengthXmm", tool.getLengthX());
                row.put("lengthZmm", tool.getLengthZ());
                row.put("toolPosition", (double) tool.getToolPosition());
                // name(), а не toString(): у перечисления подпись для окна («Влево (-Z)»),
                // а save_tool принимает именно имя.
                row.put("cutDirection", tool.getCutDirection() == null
                        ? ToolCutDirection.BOTH.name() : tool.getCutDirection().name());
                row.put("holderAngleDeg", tool.getHolderAngleDeg());
                row.put("insertAngleDeg", tool.getInsertAngleDeg());
                row.put("plateLengthMm", tool.getPlateLength());
                row.put("cutWidthMm", tool.getCutWidth());
                result.add(row);
            }
            return result;
        }, List.of());
    }

    @Override
    public String saveTool(Map<String, Object> input) {
        return onFxText(() -> {
            List<CncToolDefinition> library = new ArrayList<>(this.controller.aiToolLibrary());
            boolean hasLocation = input.containsKey("location");
            boolean hasNumber = input.containsKey("toolNumber");
            boolean hasEdge = input.containsKey("edge");
            int location = (int) MiniJson.number(input, "location", -1);
            int number = (int) MiniJson.number(input, "toolNumber", -1);
            int edge = (int) MiniJson.number(input, "edge", -1);
            if (!hasLocation && !hasNumber) {
                return "Нужна ячейка магазина (location) или номер инструмента (toolNumber).";
            }

            // Ищем, что править. В магазине одна ячейка держит несколько кромок
            // (D1, D2), поэтому адрес — ячейка И кромка. Раньше искали только по
            // ячейке и всегда попадали в первую кромку: изменить вторую было нельзя.
            List<CncToolDefinition> matches = new ArrayList<>();
            for (CncToolDefinition tool : library) {
                boolean sameLocation = hasLocation && tool.getLocation() == location;
                boolean sameNumber = hasNumber && tool.getToolNumber() == number;
                if (!(sameLocation || (!hasLocation && sameNumber))) {
                    continue;
                }
                if (hasEdge && tool.getEdgeNumber() != edge) {
                    continue;
                }
                matches.add(tool);
            }
            if (matches.size() > 1) {
                StringBuilder edges = new StringBuilder();
                for (CncToolDefinition tool : matches) {
                    if (edges.length() > 0) {
                        edges.append(", ");
                    }
                    edges.append('D').append(tool.getEdgeNumber());
                }
                return "Под этот адрес подходит несколько инструментов (" + edges
                        + "). Укажите кромку (edge), какой именно менять.";
            }

            CncToolDefinition tool = matches.isEmpty() ? new CncToolDefinition() : matches.get(0);
            boolean isNew = matches.isEmpty();
            if (isNew) {
                if (!hasLocation || location <= 0) {
                    return "Новому инструменту нужна ячейка магазина (location) больше нуля.";
                }
                tool.setLocation(location);
                tool.setToolNumber(hasNumber ? number : location);
                tool.setEdgeNumber(hasEdge ? edge : 1);
                tool.setName("Инструмент " + location);
            } else {
                if (hasLocation && location > 0) {
                    tool.setLocation(location);
                }
                if (hasNumber && number > 0) {
                    tool.setToolNumber(number);
                }
                if (hasEdge && edge > 0) {
                    tool.setEdgeNumber(edge);
                }
            }

            List<String> changed = new ArrayList<>();
            if (input.containsKey("name")) {
                tool.setName(MiniJson.string(input, "name", tool.getName()));
                changed.add("название " + tool.getName());
            }
            if (input.containsKey("typeCode")) {
                tool.setTypeCode((int) MiniJson.number(input, "typeCode", tool.getTypeCode()));
                changed.add("тип " + tool.getTypeCode());
            }
            if (input.containsKey("radiusMm")) {
                tool.setRadius(MiniJson.number(input, "radiusMm", tool.getRadius()));
                changed.add(String.format(Locale.US, "радиус %.3f", tool.getRadius()));
            }
            if (input.containsKey("lengthXmm")) {
                tool.setLengthX(MiniJson.number(input, "lengthXmm", tool.getLengthX()));
                changed.add(String.format(Locale.US, "длина X %.3f", tool.getLengthX()));
            }
            if (input.containsKey("lengthZmm")) {
                tool.setLengthZ(MiniJson.number(input, "lengthZmm", tool.getLengthZ()));
                changed.add(String.format(Locale.US, "длина Z %.3f", tool.getLengthZ()));
            }
            if (input.containsKey("toolPosition")) {
                int position = (int) MiniJson.number(input, "toolPosition", tool.getToolPosition());
                tool.setToolPosition(Math.max(1, Math.min(9, position)));
                changed.add("позиция вершины " + tool.getToolPosition());
            }
            if (input.containsKey("cutDirection")) {
                String wanted = MiniJson.string(input, "cutDirection", "").trim().toUpperCase(Locale.ROOT);
                ToolCutDirection direction = null;
                for (ToolCutDirection candidate : ToolCutDirection.values()) {
                    if (candidate.name().equals(wanted)) {
                        direction = candidate;
                        break;
                    }
                }
                if (direction == null) {
                    return "Направление резания не распознано: " + wanted
                            + ". Допустимо BOTH, Z_PLUS, Z_MINUS, X_PLUS, X_MINUS.";
                }
                tool.setCutDirection(direction);
                changed.add("направление " + direction.name());
            }
            if (input.containsKey("holderAngleDeg")) {
                tool.setHolderAngleDeg(MiniJson.number(input, "holderAngleDeg", tool.getHolderAngleDeg()));
                changed.add(String.format(Locale.US, "держатель %.1f°", tool.getHolderAngleDeg()));
            }
            if (input.containsKey("insertAngleDeg")) {
                tool.setInsertAngleDeg(MiniJson.number(input, "insertAngleDeg", tool.getInsertAngleDeg()));
                changed.add(String.format(Locale.US, "кромка %.1f°", tool.getInsertAngleDeg()));
            }
            if (input.containsKey("plateLengthMm")) {
                tool.setPlateLength(MiniJson.number(input, "plateLengthMm", tool.getPlateLength()));
                changed.add(String.format(Locale.US, "пластина %.2f", tool.getPlateLength()));
            }
            if (input.containsKey("cutWidthMm")) {
                tool.setCutWidth(MiniJson.number(input, "cutWidthMm", tool.getCutWidth()));
                changed.add(String.format(Locale.US, "ширина реза %.2f", tool.getCutWidth()));
            }

            if (input.containsKey("modelId")) {
                tool.setModelId(MiniJson.string(input,"modelId",tool.getModelId()));
                changed.add("3D: " + tool.getModelId());
            }

            if (isNew) {
                library.add(tool);
            }
            library.sort((left, right) -> {
                int byLocation = Integer.compare(left.getLocation(), right.getLocation());
                return byLocation != 0 ? byLocation
                        : Integer.compare(left.getEdgeNumber(), right.getEdgeNumber());
            });
            this.controller.aiSetToolLibrary(library);
            this.controller.aiStatus((isNew ? "Инструмент добавила" : "Инструмент изменила") + " нейросеть");
            String head = (isNew ? "Добавлен инструмент: " : "Изменён инструмент: ")
                    + "ячейка " + tool.getLocation() + ", T" + tool.getToolNumber()
                    + " D" + tool.getEdgeNumber() + ", " + tool.getName();
            return changed.isEmpty() ? head : head + " — " + String.join(", ", changed);
        }, "не удалось записать инструмент");
    }

    @Override
    public String removeTool(int location, int edge) {
        return onFxText(() -> {
            List<CncToolDefinition> library = new ArrayList<>(this.controller.aiToolLibrary());
            // Кромка не задана — убираем всю ячейку целиком, со всеми кромками.
            boolean removed = library.removeIf(tool -> tool.getLocation() == location
                    && (edge <= 0 || tool.getEdgeNumber() == edge));
            if (!removed) {
                return edge > 0
                        ? "В ячейке " + location + " нет кромки D" + edge + "."
                        : "В ячейке " + location + " инструмента нет.";
            }
            this.controller.aiSetToolLibrary(library);
            this.controller.aiStatus("Инструмент убрала нейросеть");
            return edge > 0
                    ? "Убрана кромка D" + edge + " из ячейки " + location + "."
                    : "Ячейка " + location + " освобождена.";
        }, "не удалось убрать инструмент");
    }

    // ------------------------------------------------------------------
    // Траектория и картинки
    // ------------------------------------------------------------------

    @Override
    public Map<String, Object> toolpath(int maxMoves) {
        return onFx(() -> {
            List<GCodeMoveData> moves = this.controller.aiContourMoves();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("moveCount", (double) moves.size());
            double minZ = Double.POSITIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            double maxDiameter = 0.0;
            double minDiameter = Double.POSITIVE_INFINITY;
            int cutting = 0;
            int rapid = 0;
            for (GCodeMoveData move : moves) {
                minZ = Math.min(minZ, Math.min(move.startZ(), move.endZ()));
                maxZ = Math.max(maxZ, Math.max(move.startZ(), move.endZ()));
                maxDiameter = Math.max(maxDiameter, Math.max(Math.abs(move.startX()), Math.abs(move.endX())));
                if (move.rapid()) {
                    rapid++;
                } else {
                    cutting++;
                    minDiameter = Math.min(minDiameter,
                            Math.min(Math.abs(move.startX()), Math.abs(move.endX())));
                }
            }
            if (moves.isEmpty()) {
                result.put("note", "Ходов нет: программа пустая или стойка ничего не разобрала.");
                return result;
            }
            result.put("cuttingMoves", (double) cutting);
            result.put("rapidMoves", (double) rapid);
            result.put("zFromMm", minZ);
            result.put("zToMm", maxZ);
            result.put("lengthMm", maxZ - minZ);
            result.put("maxDiameterMm", maxDiameter);
            result.put("minCuttingDiameterMm", Double.isFinite(minDiameter) ? minDiameter : 0.0);
            int limit = maxMoves <= 0 ? 200 : Math.min(maxMoves, 4000);
            List<Object> listed = new ArrayList<>();
            for (int i = 0; i < moves.size() && i < limit; i++) {
                GCodeMoveData move = moves.get(i);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("line", (double) move.sourceLine());
                row.put("kind", move.rapid() ? "быстрый" : "рабочий");
                row.put("fromX", move.startX());
                row.put("fromZ", move.startZ());
                row.put("toX", move.endX());
                row.put("toZ", move.endZ());
                listed.add(row);
            }
            result.put("moves", listed);
            if (moves.size() > limit) {
                result.put("shown", "первые " + limit + " из " + moves.size());
            }
            return result;
        }, Map.of());
    }

    @Override
    public byte[] renderGraph(int width, int height) {
        return onFx(() -> this.controller.aiRenderGraphPng(width, height), new byte[0]);
    }

    @Override
    public Picture render3d(int width, int height) {
        return onFx(() -> {
            SimulationContext context = this.controller.aiSimulationContext();
            return ModelSnapshot.render(context, this.controller.aiProgramText(),
                    width, height, this.controller.aiDarkTheme());
        }, Picture.failed("поток интерфейса не ответил"));
    }

    @Override
    public void log(String line) {
        Platform.runLater(() -> this.controller.aiStatus(line));
    }

    @Override public Map<String,Object> simulationState() {
        return onFx(() -> controller.aiSimulationWindow().liveState(), Map.of("error","FX timeout"));
    }
    @Override public Picture simulationView(int width,int height) {
        return onFx(() -> controller.aiSimulationWindow().livePicture(width,height), Picture.failed("FX timeout"));
    }
    @Override public Map<String,Object> controlSimulation(Map<String,Object> args) {
        return onFx(() -> controller.aiControlSimulation(args), Map.of("error","FX timeout"));
    }

    // ------------------------------------------------------------------
    // Переход в поток JavaFX
    // ------------------------------------------------------------------

    private <T> T onFx(Callable<T> work, T fallback) {
        if (Platform.isFxApplicationThread()) {
            try {
                return work.call();
            } catch (Exception failure) {
                McpServer.note("сбой в потоке интерфейса: " + failure);
                return fallback;
            }
        }
        AtomicReference<T> box = new AtomicReference<>(fallback);
        AtomicReference<Exception> broken = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                box.set(work.call());
            } catch (Exception failure) {
                broken.set(failure);
            } finally {
                done.countDown();
            }
        });
        try {
            if (!done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                McpServer.note("поток интерфейса не ответил за " + TIMEOUT_SECONDS + " с");
                return fallback;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return fallback;
        }
        if (broken.get() != null) {
            McpServer.note("сбой в потоке интерфейса: " + broken.get());
            throw new IllegalStateException(String.valueOf(broken.get().getMessage()), broken.get());
        }
        return box.get();
    }

    /**
     * То же, но с внятным текстом вместо пустого ответа, когда что-то сломалось.
     *
     * <p>Имя другое не для красоты: пока метод назывался {@code onFx}, вызов
     * {@code onFx(work, (String) null)} попадал в сам себя — перегрузка совпадала
     * точнее, чем обобщённая, и получалось бесконечное самовызывание вместо перехода
     * в поток интерфейса. Поймано сквозной проверкой CheckMcpApp.
     */
    private String onFxText(Callable<String> work, String failureText) {
        try {
            String value = this.<String>onFx(work, null);
            return value == null ? failureText : value;
        } catch (RuntimeException failure) {
            return failureText + ": " + failure.getMessage();
        }
    }

    private static boolean flag(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value instanceof Boolean state) {
            return state;
        }
        if (value instanceof Double number) {
            return number != 0.0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> map) {
        Map<String, Object> typed = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            typed.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return typed;
    }
}
