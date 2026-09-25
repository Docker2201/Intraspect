package com.sergey.pisarev.service;

import com.sergey.pisarev.model.CncToolDefinition;

import java.util.List;

/**
 * Инструмент берётся из шапки программы, а не только из библиотеки.
 *
 * <p>В шапке стоит диаметр пластины, а в библиотеке — радиус вершины. Когда
 * диаметр попадает в поле радиуса, модель садится на вдвое больший вылет и
 * заходит в деталь, а эквидистанта считается не по той пластине.
 */
public final class ProgramToolTableRegressionTest {
    private static final String HEADER = """
            ;************************************************
            ;Channel       : 1 (right)
            ;************************************************
            ;Rabochij instrument
              ;T4 - krivoj chernovoj D=25 mm
              ;T6 - krivoj chistovoj D=12 mm
              ;T3 - kanavochnij romb
                ;ili T303 - kanavochnij
              ;T8 - rastochnoj kvadrat
            ;************************************************
            N190 T4
            N200 D1 G1 X=100 Z=5 F100
            """;

    public static void main(String[] args) {
        var table = ProgramToolTable.parse(HEADER);
        check("шапка разобрана целиком", table.size() == 5);
        near("D=25 — это радиус 12.5", table.get(4).noseRadiusMm(), 12.5);
        near("D=12 — это радиус 6", table.get(6).noseRadiusMm(), 6);
        check("черновой — тип 500", table.get(4).typeCode() == 500);
        check("чистовой — тип 510", table.get(6).typeCode() == 510);
        check("канавочный — тип 520", table.get(3).typeCode() == 520);
        check("запасной канавочный тоже в списке", table.get(303).typeCode() == 520);
        check("у канавочного диаметра нет", table.get(3).noseRadiusMm() == null);
        check("расточной: тип из шапки не следует", table.get(8).typeCode() == null);
        check("кадр «N190 T4» не путается с таблицей", !table.containsKey(190));

        // Библиотека с диаметром в поле радиуса и заниженной вершиной.
        var library = List.of(
                tool(4, 1, 510, 0.2, 3, 11),
                tool(6, 1, 510, 0.2, 4, 11),
                tool(3, 1, 550, 20, 3, 11),
                tool(209, 1, 510, 20, 3, 0));
        var applied = ProgramToolTable.apply(HEADER, library);
        near("радиус чернового взят из программы", applied.get(0).getRadius(), 12.5);
        check("тип чернового исправлен", applied.get(0).getTypeCode() == 500);
        near("радиус чистового взят из программы", applied.get(1).getRadius(), 6);
        check("канавочный больше не круглая пластина", applied.get(2).getTypeCode() == 520);
        near("у канавочного радиус остался библиотечный", applied.get(2).getRadius(), 20);
        near("инструмента вне шапки не трогаем", applied.get(3).getRadius(), 20);
        check("остальные поля сохранены",
                applied.get(0).getPlateLength() == 11 && applied.get(0).getToolPosition() == 3
                        && applied.get(1).getToolPosition() == 4);
        near("библиотека на диске не изменилась", library.get(0).getRadius(), 0.2);

        var noHeader = ProgramToolTable.apply("N10 G1 X=100 Z=5 F100\n", library);
        check("без шапки библиотека идёт как есть", noHeader == library);
        System.out.println("Program tool table regressions passed.");
    }

    private static CncToolDefinition tool(int number, int edge, int type, double radius,
            int position, double plate) {
        var tool = new CncToolDefinition();
        tool.setToolNumber(number);
        tool.setEdgeNumber(edge);
        tool.setTypeCode(type);
        tool.setRadius(radius);
        tool.setToolPosition(position);
        tool.setPlateLength(plate);
        return tool;
    }

    private static void near(String label, Double actual, double expected) {
        if (actual == null || Math.abs(actual - expected) > 1e-9) {
            throw new AssertionError(label + ": " + actual + " вместо " + expected);
        }
        System.out.println("PASS " + label);
    }

    private static void check(String label, boolean ok) {
        if (!ok) throw new AssertionError(label);
        System.out.println("PASS " + label);
    }
}
