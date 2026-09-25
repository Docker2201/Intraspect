package com.sergey.pisarev.service;

import com.sergey.pisarev.model.CncToolDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Инструменты, перечисленные в шапке программы.
 *
 * <p>Такие программы начинаются со списка рабочего инструмента:
 * <pre>
 * ;Rabochij instrument
 *   ;T4 - krivoj chernovoj D=25 mm
 *   ;T6 - krivoj chistovoj D=12 mm
 *   ;T3 - kanavochnij romb
 *     ;ili T303 - kanavochnij
 *   ;T8 - rastochnoj kvadrat
 * </pre>
 *
 * <p>Здесь <em>D</em> — диаметр пластины, то есть радиус вершины вдвое меньше.
 * В библиотеке этот диаметр иногда оказывается в поле радиуса: тогда модель
 * садится на вдвое больший вылет и заходит в деталь, а круглая пластина выходит
 * вдвое крупнее. Программа — источник правды о своём инструменте, поэтому её
 * числа перекрывают библиотечные, а всё, чего в шапке нет (пластина, углы,
 * позиция вершины), берётся из библиотеки как есть.
 */
public final class ProgramToolTable {
    /** Что шапка говорит об одном инструменте; {@code null} — не сказано. */
    public record Entry(int toolNumber, String description, Integer typeCode, Double noseRadiusMm) { }

    // ";T4 - pryamoj chernovoj D=25 mm" и "  ;ili T303 - kanavochnij".
    private static final Pattern LINE = Pattern.compile(
            "(?im)^\\s*;\\s*(?:ili|или|or)?\\s*T(\\d{1,4})\\s*[-–—:]\\s*(.+?)\\s*$");
    private static final Pattern DIAMETER = Pattern.compile("(?i)\\bD\\s*=?\\s*(\\d+(?:[.,]\\d+)?)");

    private ProgramToolTable() { }

    /** Инструменты из шапки, по номеру. Пустая карта, если списка нет. */
    public static Map<Integer, Entry> parse(String programText) {
        var found = new LinkedHashMap<Integer, Entry>();
        if (programText == null || programText.isBlank()) {
            return found;
        }
        Matcher matcher = LINE.matcher(programText);
        while (matcher.find()) {
            int number = Integer.parseInt(matcher.group(1));
            String description = matcher.group(2).trim();
            // Строки кода вида "N190 T4" сюда не попадают: разбираются только
            // комментарии, а в них номер инструмента идёт с описанием через тире.
            if (description.isEmpty()) {
                continue;
            }
            found.putIfAbsent(number, new Entry(number, description,
                    typeCode(description), noseRadius(description)));
        }
        return found;
    }

    /**
     * Инструменты для симуляции: библиотека, поверх которой легли числа из шапки.
     *
     * <p>Библиотека на диске не меняется — правка живёт только в этом расчёте.
     */
    public static List<CncToolDefinition> apply(String programText, List<CncToolDefinition> library) {
        var table = parse(programText);
        if (table.isEmpty() || library == null || library.isEmpty()) {
            return library == null ? List.of() : library;
        }
        var result = new ArrayList<CncToolDefinition>(library.size());
        for (CncToolDefinition tool : library) {
            Entry entry = table.get(tool.getToolNumber());
            if (entry == null || (entry.typeCode() == null && entry.noseRadiusMm() == null)) {
                result.add(tool);
                continue;
            }
            result.add(merge(tool, entry));
        }
        return result;
    }

    /** Копия инструмента с числами из шапки. */
    private static CncToolDefinition merge(CncToolDefinition tool, Entry entry) {
        var copy = new CncToolDefinition();
        copy.setLocation(tool.getLocation());
        copy.setName(tool.getName());
        copy.setType(tool.getType());
        copy.setToolNumber(tool.getToolNumber());
        copy.setEdgeNumber(tool.getEdgeNumber());
        copy.setLengthX(tool.getLengthX());
        copy.setLengthZ(tool.getLengthZ());
        copy.setRadius(entry.noseRadiusMm() != null ? entry.noseRadiusMm() : tool.getRadius());
        copy.setTypeCode(entry.typeCode() != null ? entry.typeCode() : tool.getTypeCode());
        copy.setTypeIdentifier(tool.getTypeIdentifier());
        copy.setToolPosition(tool.getToolPosition());
        copy.setCutDirection(tool.getCutDirection());
        copy.setHolderAngleDeg(tool.getHolderAngleDeg());
        copy.setInsertAngleDeg(tool.getInsertAngleDeg());
        copy.setPlateLength(tool.getPlateLength());
        copy.setCutWidth(tool.getCutWidth());
        return copy;
    }

    /** Радиус вершины из диаметра пластины: «D=25 mm» — это радиус 12.5. */
    private static Double noseRadius(String description) {
        Matcher matcher = DIAMETER.matcher(description);
        if (!matcher.find()) {
            return null;
        }
        double diameter = Double.parseDouble(matcher.group(1).replace(',', '.'));
        return diameter > 0 ? diameter * .5 : null;
    }

    /** Тип инструмента по слову из описания; {@code null} — в шапке про тип ничего. */
    private static Integer typeCode(String description) {
        String text = description.toLowerCase(Locale.ROOT);
        if (text.contains("chernov") || text.contains("чернов")) return 500;
        if (text.contains("chistov") || text.contains("чистов")) return 510;
        if (text.contains("kanavoch") || text.contains("канавоч")) return 520;
        if (text.contains("otrezn") || text.contains("отрезн")) return 530;
        if (text.contains("rezb") || text.contains("резьб") || text.contains("thread")) return 540;
        return null;
    }
}
