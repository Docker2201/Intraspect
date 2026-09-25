package com.sergey.pisarev.ai;

import com.sergey.pisarev.util.MiniJson;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Инструменты MCP: что именно умеет делать нейросеть с программой.
 *
 * <p>Описания инструментов — это не комментарии, а часть работы: нейросеть читает
 * только их. Поэтому в них сказано, что станок токарный Siemens SINUMERIK, что размеры
 * в миллиметрах, что X при DIAMON задаётся диаметром и что заготовка объявляется
 * WORKPIECE. Без этого модель пишет G-code «вообще», а не под этот станок.
 */
public final class McpTools {
    /** Сколько ходов отдавать, если нейросеть не попросила иначе. */
    private static final int DEFAULT_MOVES = 200;

    private McpTools() {
    }

    /** Ответ на tools/list. */
    public static String listJson() {
        JsonOut out = new JsonOut();
        out.arr();

        tool(out, "get_program",
                "Читает текст G-code программы из редактора. Это токарная программа для стойки "
                        + "Siemens SINUMERIK: размеры в миллиметрах, X при DIAMON задаётся диаметром, "
                        + "Z идёт вдоль оси детали, минус в сторону шпинделя.",
                emptySchema());

        tool(out, "set_program",
                "Заменяет программу в редакторе целиком и сразу перестраивает график. Пиши в стиле "
                        + "SINUMERIK: WORKPIECE для заготовки, вызов инструмента T<ячейка> D<кромка>, "
                        + "G54..G57 для нулевой точки, G95 или G96/G97 по надобности, M30 в конце. "
                        + "Прежний текст остаётся в истории правок, человек может вернуть его.",
                schema(new String[]{
                        strField("text", "полный текст программы"),
                        strField("reason", "что и зачем меняешь - попадёт в журнал MCP, это увидит наладчик")
                }, new String[]{"text"}));

        tool(out, "check_program",
                "Прогоняет проверку программы и возвращает найденное: красные ошибки, из-за которых "
                        + "3D не построится, и жёлтые предупреждения. Коды E001..E007 и W101..W106. "
                        + "Вызывай после каждой правки программы.",
                emptySchema());

        tool(out, "get_settings",
                "Настройки программы: система ЧПУ и станок, диаметр заготовки, чашка, эквидистанта "
                        + "(радиус вершины и припуск), смещения нулевой точки G54.., коррекции подачи и "
                        + "шпинделя, выбранные оси.",
                emptySchema());

        tool(out, "set_settings",
                "Меняет настройки программы — те же, что человек правит в окне настроек. Передавай "
                        + "только те поля, которые нужно изменить; имена полей — как в get_settings, "
                        + "значения в миллиметрах и процентах. Изменение сразу видно в окне настроек и "
                        + "учитывается графиком и 3D. Смещения нулевой точки важны для программ, которые "
                        + "точат с двух концов: Z второго нуля ставит вторую половину детали на место.",
                schema(new String[]{
                        strField("controlSystem", "система ЧПУ: SIEMENS_TURNING, SIEMENS_MILLING, FANUC"),
                        strField("machineType", "станок: DMG_MORI_LATHE, GENERIC_LATHE, VERTICAL_LATHE (карусельный: деталь лежит, ось вертикальная), GENERIC_MILL, HAAS_CM1"),
                        numberField("programFontSize", "размер шрифта программы, 10..32"),
                        numberField("blankDiameterMm", "диаметр заготовки, мм"),
                        boolField("useBlankDiameter", "учитывать диаметр заготовки из настроек"),
                        numberField("cupRadiusMm", "радиус чашки, мм"),
                        boolField("useCupRadius", "учитывать чашку"),
                        boolField("equidistantEnabled", "строить эквидистанту по G41/G42"),
                        boolField("equidistantRadiusFromLibrary", "радиус вершины брать из библиотеки по T и D"),
                        numberField("equidistantRadiusMm", "радиус вершины вручную, мм"),
                        numberField("equidistantAllowanceMm", "припуск сверх OFFN, мм"),
                        numberField("feedOverridePercent", "коррекция подачи, 0..120 %"),
                        numberField("spindleOverridePercent", "коррекция шпинделя, 50..120 %"),
                        numberField("workOffsetCount", "сколько точек смещения показывать"),
                        arrayField("workOffsets",
                                "смещения нулевой точки, например [{code:54, x:0, z:-2192}]")
                }, new String[0]));

        tool(out, "get_tool_library",
                "Библиотека инструментов станка — всё, что видит человек в таблице настроек: ячейка "
                        + "магазина, название, тип по Siemens (500 черновой, 510 чистовой, 520 подрезной, "
                        + "530 отрезной, 540 резьбовой, 550 пластина, 200 сверло), номер T, кромка D, радиус "
                        + "вершины, длины по X и Z, положение вершины 1..9, направление резания, углы "
                        + "держателя и кромки, размеры пластины. Программа режет только тем инструментом, "
                        + "который заведён в библиотеке. Эти же поля принимает save_tool.",
                emptySchema());

        tool(out, "save_tool",
                "Правит уже заведённый инструмент или добавляет новый. Адрес: ячейка магазина "
                        + "(location) и кромка (edge) — в одной ячейке живут D1, D2; можно адресовать и "
                        + "номером T (toolNumber) с кромкой. Передавай только те поля, которые надо "
                        + "изменить: остальные останутся как были. Если под адрес подходит несколько "
                        + "кромок, инструмент попросит указать edge. Новому инструменту нужна ячейка. "
                        + "Изменение сразу видно в таблице настроек.",
                schema(new String[]{
                        numberField("location", "ячейка магазина — адрес инструмента"),
                        numberField("edge", "номер кромки D: какую именно кромку правим"),
                        numberField("toolNumber", "номер T в программе"),
                        strField("name", "название, например Черновой"),
                        strField("modelId", "Модель библиотеки: auto, round_straight (прямой), round_cranked (кривой). Для круглых пластин typeCode=550."),
                        numberField("typeCode", "тип по Siemens: 500 черновой, 510 чистовой, 520 подрезной, "
                                + "530 отрезной, 540 резьбовой, 550 пластина, 200 сверло"),
                        numberField("radiusMm", "радиус вершины, мм"),
                        numberField("lengthXmm", "длина по X, мм"),
                        numberField("lengthZmm", "длина по Z, мм"),
                        numberField("toolPosition", "положение вершины 1..9, как в данных инструмента SINUMERIK"),
                        strField("cutDirection", "направление резания: BOTH, Z_PLUS, Z_MINUS, X_PLUS, X_MINUS"),
                        numberField("holderAngleDeg", "угол держателя, градусы"),
                        numberField("insertAngleDeg", "угол кромки, градусы"),
                        numberField("plateLengthMm", "длина пластины, мм"),
                        numberField("cutWidthMm", "ширина реза, мм")
                }, new String[0]));

        tool(out, "remove_tool",
                "Убирает инструмент из ячейки магазина. Без кромки убирает ячейку целиком, "
                        + "с кромкой (edge) — только её.",
                schema(new String[]{
                        numberField("location", "ячейка магазина"),
                        numberField("edge", "номер кромки D; без него уберётся вся ячейка")
                }, new String[]{"location"}));

        tool(out, "get_toolpath",
                "Разобранная траектория: габариты по X и Z, максимальный диаметр, длина, сколько рабочих "
                        + "и холостых ходов, какие T вызываются, и сами ходы с номерами строк. Так видно, что "
                        + "стойка поняла из программы.",
                schema(new String[]{numberField("maxMoves", "сколько ходов вернуть, по умолчанию 200")},
                        new String[0]));

        tool(out, "render_graph_2d",
                "Рисует двумерный график программы — то же, что видит человек в окне: красное быстрый "
                        + "ход, зелёное рабочая подача. Возвращает PNG.",
                sizeSchema());

        tool(out, "render_model_3d",
                "Строит трёхмерную модель детали после снятия материала и возвращает PNG. Так можно "
                        + "посмотреть на результат своей программы глазами, как в SinuTrain.",
                sizeSchema());

        tool(out, "get_simulation_state",
                "Точное состояние открытого 3D-окна: сторона, время запрошенного и показанного кадра, "
                + "готовность меша, оба суппорта, строки NC, T/D, координаты контура и центра пластины в мм. "
                + "Читает остановленный цикл без его запуска. sourceLine относится к объединённому тексту, "
                + "channelLine и blockText — к программе канала. X — диаметр, radius — радиус.", emptySchema());
        tool(out, "render_simulation_view",
                "PNG текущего открытого 3D-окна с текущей камерой, инструментами и панелью. "
                + "Не перестраивает готовую деталь и не меняет положение цикла.", sizeSchema());
        tool(out, "control_simulation",
                "Управление открытым 3D-циклом: pause, play, seek. seek ставит на паузу и переходит "
                + "к абсолютному времени seconds выбранной стороны setupSide (1/2), независимо от скорости. "
                + "Либо sourceLine объединённой программы и fraction (0..1). Меш строится асинхронно: "
                + "перед снимком проверь get_simulation_state.meshPending. open открывает полный цикл.",
                schema(new String[]{strField("action", "open, pause, play, seek"),
                        numberField("seconds", "абсолютное время стороны, секунды"),
                        numberField("setupSide", "сторона 1 или 2"),
                        numberField("sourceLine", "строка объединённого NC, начиная с 1"),
                        numberField("fraction", "доля хода строки, 0..1")}, new String[]{"action"}));
        out.end();
        return out.done();
    }

    /** Выполняет tools/call и возвращает готовый result. */
    public static String call(AppAccess app, String name, Map<String, Object> args) {
        Map<String, Object> arguments = args == null ? Map.of() : args;
        try {
            switch (name) {
                case "get_simulation_state":
                    return text(jsonValue(app.simulationState()));
                case "control_simulation":
                    return text(jsonValue(app.controlSimulation(arguments)));
                case "render_simulation_view": {
                    var picture = app.simulationView((int)MiniJson.number(arguments,"width",1600),
                            (int)MiniJson.number(arguments,"height",1000));
                    return picture.ok() ? image(picture.png(),"image/png","Текущее окно 3D.") : error(picture.problem());
                }
                case "get_program":
                    return text(app.programText());
                case "set_program": {
                    String programText = MiniJson.string(arguments, "text", "");
                    if (programText.isBlank()) {
                        return error("текст программы пустой");
                    }
                    return text(app.setProgramText(programText, MiniJson.string(arguments, "reason", "")));
                }
                case "check_program":
                    return text(problemsText(app.checkProgram()));
                case "get_settings":
                    return text(mapText(app.settings()));
                case "set_settings": {
                    List<String> changed = app.applySettings(arguments);
                    if (changed.isEmpty()) {
                        return text("Ничего не изменилось: ни одно известное поле не пришло.");
                    }
                    return text("Изменено:" + lines(changed));
                }
                case "get_tool_library": {
                    StringBuilder body = new StringBuilder();
                    for (Map<String, Object> tool : app.toolLibrary()) {
                        body.append(System.lineSeparator()).append("- ").append(mapLine(tool));
                    }
                    return text(body.length() == 0
                            ? "Библиотека инструментов пуста."
                            : "Инструменты в магазине:" + body);
                }
                case "save_tool":
                    return text(app.saveTool(arguments));
                case "remove_tool":
                    return text(app.removeTool(
                            (int) MiniJson.number(arguments, "location", -1),
                            (int) MiniJson.number(arguments, "edge", 0)));
                case "get_toolpath":
                    return text(mapText(app.toolpath(
                            (int) MiniJson.number(arguments, "maxMoves", DEFAULT_MOVES))));
                case "render_graph_2d":
                    return image(app.renderGraph(
                                    (int) MiniJson.number(arguments, "width", 1200),
                                    (int) MiniJson.number(arguments, "height", 700)),
                            "image/png", "Двумерный график программы.");
                case "render_model_3d": {
                    AppAccess.Picture picture = app.render3d(
                            (int) MiniJson.number(arguments, "width", 1200),
                            (int) MiniJson.number(arguments, "height", 700));
                    if (!picture.ok()) {
                        return error("модель не построилась. " + picture.problem());
                    }
                    return image(picture.png(), "image/png",
                            "Трёхмерная модель детали после снятия материала.");
                }
                default:
                    return error("нет такого инструмента: " + name);
            }
        } catch (Exception failure) {
            String message = failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage();
            return error(message);
        }
    }

    // ------------------------------------------------------------------
    // Ответы
    // ------------------------------------------------------------------

    private static String text(String body) {
        JsonOut out = new JsonOut();
        out.obj().key("content").arr().obj()
                .field("type", "text")
                .field("text", body == null ? "" : body)
                .end().end().field("isError", false).end();
        return out.done();
    }

    private static String jsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> map) return map.entrySet().stream()
                .map(e -> JsonOut.string(String.valueOf(e.getKey())) + ":" + jsonValue(e.getValue()))
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        if (value instanceof List<?> list) return list.stream().map(McpTools::jsonValue)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        if (value instanceof Number number) return JsonOut.number(number.doubleValue());
        if (value instanceof Boolean) return value.toString();
        return JsonOut.string(value.toString());
    }

    private static String error(String message) {
        JsonOut out = new JsonOut();
        out.obj().key("content").arr().obj()
                .field("type", "text")
                .field("text", "Не получилось: " + message)
                .end().end().field("isError", true).end();
        return out.done();
    }

    private static String image(byte[] bytes, String mimeType, String caption) {
        if (bytes == null || bytes.length == 0) {
            return error("картинка не построилась");
        }
        JsonOut out = new JsonOut();
        out.obj().key("content").arr()
                .obj().field("type", "text").field("text", caption).end()
                .obj().field("type", "image")
                .field("data", Base64.getEncoder().encodeToString(bytes))
                .field("mimeType", mimeType)
                .end()
                .end().field("isError", false).end();
        return out.done();
    }

    // ------------------------------------------------------------------
    // Схемы
    // ------------------------------------------------------------------

    private static void tool(JsonOut out, String name, String description, String schema) {
        out.obj()
                .field("name", name)
                .field("description", description)
                .fieldRaw("inputSchema", schema)
                .end();
    }

    private static String emptySchema() {
        return new JsonOut().obj().field("type", "object").key("properties").obj().end().end().done();
    }

    private static String schema(String[] properties, String[] required) {
        JsonOut out = new JsonOut();
        out.obj().field("type", "object").key("properties").obj();
        for (String property : properties) {
            out.raw(property);
        }
        out.end();
        if (required.length > 0) {
            out.key("required").arr();
            for (String name : required) {
                out.val(name);
            }
            out.end();
        }
        out.end();
        return out.done();
    }

    private static String sizeSchema() {
        return schema(new String[]{
                numberField("width", "ширина картинки в точках, по умолчанию 1200"),
                numberField("height", "высота картинки в точках, по умолчанию 700")
        }, new String[0]);
    }

    /** Одно поле схемы; вставляется внутрь properties как есть. */
    private static String field(String name, String type, String description) {
        return JsonOut.string(name) + ":"
                + new JsonOut().obj().field("type", type).field("description", description).end().done();
    }

    private static String numberField(String name, String description) {
        return field(name, "number", description);
    }

    private static String boolField(String name, String description) {
        return field(name, "boolean", description);
    }

    private static String strField(String name, String description) {
        return field(name, "string", description);
    }

    private static String arrayField(String name, String description) {
        return field(name, "array", description);
    }

    // ------------------------------------------------------------------
    // Человеческий текст ответов
    // ------------------------------------------------------------------

    private static String problemsText(List<AppAccess.Problem> problems) {
        if (problems == null || problems.isEmpty()) {
            return "Проверка чистая: ни ошибок, ни предупреждений.";
        }
        StringBuilder body = new StringBuilder("Найдено:");
        for (AppAccess.Problem problem : problems) {
            body.append(System.lineSeparator())
                    .append("- ").append(problem.code()).append(' ')
                    .append("ERROR".equals(problem.severity()) ? "красная" : "жёлтая")
                    .append(": ").append(problem.title());
            if (problem.line() > 0) {
                body.append(" (строка ").append(problem.line()).append(')');
            }
            if (problem.details() != null && !problem.details().isBlank()) {
                body.append(System.lineSeparator()).append("  ").append(problem.details());
            }
            if (problem.fix() != null && !problem.fix().isBlank()) {
                body.append(System.lineSeparator()).append("  есть автоисправление: ").append(problem.fix());
            }
        }
        return body.toString();
    }

    private static String mapText(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return "Нечего показать.";
        }
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (body.length() > 0) {
                body.append(System.lineSeparator());
            }
            body.append(entry.getKey()).append(": ").append(valueText(entry.getValue()));
        }
        return body.toString();
    }

    private static String mapLine(Map<String, Object> values) {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (body.length() > 0) {
                body.append("  ");
            }
            body.append(entry.getKey()).append('=').append(valueText(entry.getValue()));
        }
        return body.toString();
    }

    private static String valueText(Object value) {
        if (value instanceof Double number) {
            return JsonOut.number(number);
        }
        if (value instanceof List<?> list) {
            StringBuilder body = new StringBuilder();
            for (Object item : list) {
                if (body.length() > 0) {
                    body.append("; ");
                }
                body.append(item instanceof Map<?, ?> map ? mapLine(cast(map)) : String.valueOf(item));
            }
            return body.toString();
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> map) {
        Map<String, Object> typed = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            typed.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return typed;
    }

    private static String lines(List<String> items) {
        StringBuilder body = new StringBuilder();
        for (String item : items) {
            body.append(System.lineSeparator()).append("- ").append(item);
        }
        return body.toString();
    }
}
