package com.sergey.pisarev.util;

import com.sergey.pisarev.model.CncToolDefinition;
import com.sergey.pisarev.model.MachineConfiguration.MachineType;
import com.sergey.pisarev.model.ToolCutDirection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Сохранение списка инструментов между запусками (JSON в data рядом с программой).
 */
public final class ToolLibraryStore {
    private static final Logger LOGGER = Logger.getLogger(ToolLibraryStore.class.getName());
    private ToolLibraryStore() {
    }

    public static List<CncToolDefinition> load() {
        return load(savedMachineType());
    }

    public static MachineType savedMachineType() {
        try {
            return MachineType.valueOf(UserSettings.getMachineType());
        } catch (IllegalArgumentException exception) {
            return MachineType.GENERIC_LATHE;
        }
    }

    private static Path storePath(MachineType machine) {
        return PortableStorage.file("tools-" + machine.name().toLowerCase(Locale.ROOT) + ".json");
    }

    /** Each machine owns its T/D namespace. The old shared library is a read-only
     * migration seed, so changing a wheel tool never changes an axle tool. */
    public static List<CncToolDefinition> load(MachineType machine) {
        Path source = PortableStorage.readablePath(storePath(machine));
        if (!Files.exists(source)) {
            List<CncToolDefinition> tools = read(PortableStorage.readablePath(PortableStorage.file("tools.json")));
            save(machine, tools);
            return tools;
        }
        return read(source);
    }

    private static List<CncToolDefinition> read(Path source) {
        List<CncToolDefinition> tools;
        if (!Files.isRegularFile(source)) {
            tools = defaultTools();
        } else {
            try {
                String json = Files.readString(source, StandardCharsets.UTF_8);
                tools = parseJson(json);
            } catch (IOException exception) {
                LOGGER.log(Level.WARNING, "Failed to load tool library", exception);
                tools = defaultTools();
            }
        }
        return tools;
    }

    public static void save(List<CncToolDefinition> tools) {
        save(savedMachineType(), tools);
    }

    public static void save(MachineType machine, List<CncToolDefinition> tools) {
        try {
            PortableStorage.writeAtomically(storePath(machine), toJson(tools), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "Failed to save tool library", exception);
        }
    }

    // Имена — на языке интерфейса: раньше были вшиты по-русски и так и
    // оставались в английском и украинском интерфейсе.
    public static List<CncToolDefinition> defaultTools() {
        ArrayList<CncToolDefinition> tools = new ArrayList<>();
        tools.add(new CncToolDefinition(1, I18n.text("tooltype.030"), "turning", 1, 1, 124.0, -12.0, 0.8, 500, "Roughing tool", 1, ToolCutDirection.BOTH));
        tools.add(new CncToolDefinition(2, I18n.text("tooltype.031"), "turning", 2, 1, 98.0, -10.0, 0.35, 510, "Finishing tool", 1, ToolCutDirection.BOTH));
        tools.add(new CncToolDefinition(3, I18n.text("tooltype.022"), "drill", 3, 1, 110.0, -8.0, 2.5, 200, "Twist drill", 1, ToolCutDirection.BOTH));
        tools.add(new CncToolDefinition(8, I18n.text("tool.default.centering"), "turning", 8, 1, 140.0, 0.0, 6.0, 500, "Centering / reference", 1, ToolCutDirection.BOTH));
        tools.add(new CncToolDefinition(20, I18n.text("tooltype.033"), "turning", 20, 1, 0.0, 38.0, 8.0, 530, "Cutting tool", 1, ToolCutDirection.BOTH));
        return tools;
    }

    private static List<CncToolDefinition> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return defaultTools();
        }
        try {
            List<Map<String, Object>> objects = MiniJson.parseObjectArray(json);
            ArrayList<CncToolDefinition> tools = new ArrayList<>(objects.size());
            for (Map<String, Object> object : objects) {
                tools.add(toolFrom(object));
            }
            return tools;
        } catch (MiniJson.JsonException exception) {
            // Повреждённый файл не должен молча превращаться в обрезанный список:
            // возвращаем набор по умолчанию и пишем причину в лог.
            LOGGER.log(Level.WARNING, "Библиотека инструментов повреждена: " + exception.getMessage());
            return defaultTools();
        }
    }

    private static CncToolDefinition toolFrom(Map<String, Object> object) {
        int location = (int) MiniJson.number(object, "location", 1);
        String name = MiniJson.string(object, "name", "");
        String type = MiniJson.string(object, "type", "turning");
        int toolNumber = (int) MiniJson.number(object, "toolNumber", location);
        int edgeNumber = (int) MiniJson.number(object, "edgeNumber", 1);
        double lengthX = MiniJson.number(object, "lengthX", 0.0);
        double lengthZ = MiniJson.number(object, "lengthZ", 0.0);
        double radius = MiniJson.number(object, "radius", 0.0);
        int typeCode = (int) MiniJson.number(object, "typeCode", 500);
        String typeIdentifier = MiniJson.string(object, "typeIdentifier", "Roughing tool");
        int toolPosition = (int) MiniJson.number(object, "toolPosition", 1);
        ToolCutDirection cutDirection =
                ToolCutDirection.fromKey(MiniJson.string(object, "cutDirection", "BOTH"));
        double holderAngleDeg = MiniJson.number(object, "holderAngleDeg", 0.0);
        double insertAngleDeg = MiniJson.number(object, "insertAngleDeg", 0.0);
        double plateLength = MiniJson.number(object, "plateLength", 0.0);
        double cutWidth = MiniJson.number(object, "cutWidth", 0.0);
        var tool = new CncToolDefinition(
                location, name, type, toolNumber, edgeNumber, lengthX, lengthZ, radius,
                typeCode, typeIdentifier, toolPosition, cutDirection,
                holderAngleDeg, insertAngleDeg, plateLength, cutWidth);
        String modelId=MiniJson.string(object,"modelId","auto");
        try { tool.setModelId(modelId); }
        catch (IllegalArgumentException exception) {
            LOGGER.log(Level.WARNING,"Unknown tool model {0}; keeping tool geometry with automatic model",modelId);
        }
        return tool;
    }

    private static String toJson(List<CncToolDefinition> tools) {
        StringBuilder builder = new StringBuilder();
        builder.append("[\n");
        for (int i = 0; i < tools.size(); i++) {
            CncToolDefinition tool = tools.get(i);
            builder.append("  {");
            builder.append("\"location\":").append(tool.getLocation()).append(",");
            builder.append("\"name\":\"").append(escape(tool.getName())).append("\",");
            builder.append("\"type\":\"").append(escape(tool.getType())).append("\",");
            builder.append("\"modelId\":\"").append(escape(tool.getModelId())).append("\",");
            builder.append("\"toolNumber\":").append(tool.getToolNumber()).append(",");
            builder.append("\"edgeNumber\":").append(tool.getEdgeNumber()).append(",");
            builder.append(String.format(Locale.US, "\"lengthX\":%.3f,", tool.getLengthX()));
            builder.append(String.format(Locale.US, "\"lengthZ\":%.3f,", tool.getLengthZ()));
            builder.append(String.format(Locale.US, "\"radius\":%.3f,", tool.getRadius()));
            builder.append("\"typeCode\":").append(tool.getTypeCode()).append(",");
            builder.append("\"typeIdentifier\":\"").append(escape(tool.getTypeIdentifier())).append("\",");
            builder.append("\"toolPosition\":").append(tool.getToolPosition()).append(",");
            ToolCutDirection direction = tool.getCutDirection() != null
                    ? tool.getCutDirection()
                    : ToolCutDirection.BOTH;
            builder.append("\"cutDirection\":\"").append(direction.getKey()).append("\",");
            builder.append(String.format(Locale.US, "\"holderAngleDeg\":%.3f,", tool.getHolderAngleDeg()));
            builder.append(String.format(Locale.US, "\"insertAngleDeg\":%.3f,", tool.getInsertAngleDeg()));
            builder.append(String.format(Locale.US, "\"plateLength\":%.3f,", tool.getPlateLength()));
            builder.append(String.format(Locale.US, "\"cutWidth\":%.3f", tool.getCutWidth()));
            builder.append("}");
            if (i < tools.size() - 1) {
                builder.append(",");
            }
            builder.append("\n");
        }
        builder.append("]\n");
        return builder.toString();
    }

    private static String escape(String value) {
        return MiniJson.escape(value);
    }
}
