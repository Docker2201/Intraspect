package com.sergey.pisarev.service;

import com.sergey.pisarev.model.CncToolDefinition;
import com.sergey.pisarev.model.GCodeMoveData;
import com.sergey.pisarev.service.LatheCompensationProcessor.CompensationMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Эквидистанта G41/G42 для токарной траектории (SINUMERIK).
 *
 * Смещает ходы по нормали к контуру в плоскости Z/R на величину
 * «радиус пластины инструмента (или радиус чашки из настроек, если радиус
 * инструмента не задан) + модальное OFFN». Контурные координаты программы
 * не меняются по смыслу: результат — траектория центра радиуса резца.
 *
 * Список ходов сохраняется 1:1 (количество, порядок, sourceLine, флаги),
 * меняются только координаты — это позволяет накладывать смещение поверх
 * любых кэшей, индексированных по номеру хода.
 */
public final class LatheEquidistantPath {
    private static final double CONTIGUITY_EPS_MM = 0.05;
    private static final double MIN_SEGMENT_MM = 1.0e-6;
    private static final double MITER_LIMIT = 4.0;

    private LatheEquidistantPath() {
    }

    /**
     * Настройки эквидистанты из блока «Заготовка и чашка».
     *
     * @param enabled          строить ли эквидистанту вообще (иначе на экране контур программы)
     * @param radiusFromLibrary радиус вершины берём из библиотеки по активным T/D
     * @param manualRadiusMm   радиус вершины вручную; он же запасной для незаполненной библиотеки
     * @param extraAllowanceMm припуск сверх программного OFFN
     */
    public record Settings(
            boolean enabled,
            boolean radiusFromLibrary,
            double manualRadiusMm,
            double extraAllowanceMm
    ) {
        /** Поведение до появления настроек: радиус из библиотеки, ничего не добавляем. */
        public static final Settings DEFAULT = new Settings(true, true, 0.0, 0.0);
    }

    /**
     * @param fallbackToolRadiusMm радиус чашки/пластины из настроек, если у инструмента радиус не задан
     * @param diameterMode X в ходах хранится как диаметр (стандарт DIAMON)
     */
    public static List<GCodeMoveData> apply(
            String programText,
            List<GCodeMoveData> moves,
            List<CncToolDefinition> tools,
            double fallbackToolRadiusMm,
            boolean diameterMode
    ) {
        return apply(programText, moves, tools, fallbackToolRadiusMm, diameterMode, Settings.DEFAULT);
    }

    /** То же, но с настройками эквидистанты из окна настроек. */
    public static List<GCodeMoveData> apply(
            String programText,
            List<GCodeMoveData> moves,
            List<CncToolDefinition> tools,
            double fallbackToolRadiusMm,
            boolean diameterMode,
            Settings settings
    ) {
        return apply(programText, moves, tools, fallbackToolRadiusMm, diameterMode, settings, true);
    }

    /** Surface allowance without the nose radius; a resumed pass has no entry ramp. */
    public static List<GCodeMoveData> applyContourAllowance(String program, List<GCodeMoveData> moves, boolean hasEntry) {
        return apply(program, moves, List.of(), 0, true, new Settings(true, false, 0, 0), hasEntry);
    }

    private static List<GCodeMoveData> apply(String programText, List<GCodeMoveData> moves,
            List<CncToolDefinition> tools, double fallbackToolRadiusMm, boolean diameterMode,
            Settings settings, boolean rampEntry) {
        if (moves == null || moves.isEmpty()) {
            return moves == null ? List.of() : moves;
        }
        Settings options = settings != null ? settings : Settings.DEFAULT;
        if (!options.enabled()) {
            // Эквидистанта выключена: показываем ровно то, что записано в программе.
            return moves;
        }
        Map<Integer, CompensationMode> modeByLine =
                LatheCompensationProcessor.scanCompensationByLine(programText);
        Map<Integer, Integer> toolByLine = GCodeProgramParser.parseToolNumberByLine(programText);
        Map<Integer, Integer> dByLine = LatheCompensationProcessor.parseDNumberByLine(programText);
        TreeMap<Integer, Double> offnByLine = LatheCompensationProcessor.scanOffnByLine(programText);

        int count = moves.size();
        // Подписанное смещение: G41 — влево от направления движения (+), G42 — вправо (−).
        double[] signedOffset = new double[count];
        boolean anyOffset = false;
        for (int i = 0; i < count; i++) {
            GCodeMoveData move = moves.get(i);
            CompensationMode mode = modeAt(modeByLine, move.sourceLine());
            if (mode == CompensationMode.NONE) {
                continue;
            }
            double toolRadius = options.radiusFromLibrary()
                    ? LatheCompensationProcessor.toolRadiusForLine(
                            tools, toolByLine, dByLine, move.sourceLine())
                    : 0.0;
            double manual = Math.max(0.0, options.manualRadiusMm());
            double base = toolRadius > 0.0
                    ? toolRadius
                    : (manual > 0.0 ? manual : Math.max(0.0, fallbackToolRadiusMm));
            double offn = offnAt(offnByLine, move.sourceLine()) + options.extraAllowanceMm();
            double distance = base + offn;
            if (distance <= 1.0e-6) {
                continue;
            }
            signedOffset[i] = mode == CompensationMode.G41 ? distance : -distance;
            anyOffset = true;
        }
        if (!anyOffset) {
            return moves;
        }

        double xScale = diameterMode ? 0.5 : 1.0;
        ArrayList<GCodeMoveData> result = new ArrayList<>(moves);
        int i = 0;
        while (i < count) {
            if (signedOffset[i] == 0.0) {
                i++;
                continue;
            }
            int chainStart = i;
            int chainEnd = i;
            while (chainEnd + 1 < count
                    && signedOffset[chainEnd + 1] != 0.0
                    && Math.signum(signedOffset[chainEnd + 1]) == Math.signum(signedOffset[chainStart])
                    && isContiguous(moves.get(chainEnd), moves.get(chainEnd + 1), xScale)) {
                chainEnd++;
            }
            offsetChain(moves, result, signedOffset, chainStart, chainEnd, xScale, rampEntry);
            i = chainEnd + 1;
        }
        return result;
    }

    /**
     * Смещение цепочки ходов [a..b]. Первый ход цепочки — кадр включения G41/G42,
     * на нём смещение нарастает от нуля (подвод); следующий за цепочкой ход
     * получает скорректированную точку старта (сход с эквидистанты на кадре G40).
     */
    private static void offsetChain(
            List<GCodeMoveData> source,
            ArrayList<GCodeMoveData> result,
            double[] signedOffset,
            int a,
            int b,
            double xScale,
            boolean rampEntry
    ) {
        int segments = b - a + 1;
        // Вершины цепочки в плоскости (z, r): P[0] — старт первого хода.
        double[][] points = new double[segments + 1][2];
        points[0][0] = source.get(a).startZ();
        points[0][1] = source.get(a).startX() * xScale;
        for (int k = 0; k < segments; k++) {
            points[k + 1][0] = source.get(a + k).endZ();
            points[k + 1][1] = source.get(a + k).endX() * xScale;
        }
        // Нормали сегментов (влево от направления движения).
        double[][] normals = new double[segments][];
        for (int k = 0; k < segments; k++) {
            normals[k] = leftNormal(points[k], points[k + 1]);
        }
        for (int k = 0; k < segments; k++) {
            if (normals[k] == null) {
                normals[k] = neighborNormal(normals, k);
            }
        }
        double[][] shifted = new double[segments + 1][2];
        shifted[0] = rampEntry ? points[0] : offsetVertex(points[0], null, 0, normals[0], signedOffset[a]);
        for (int v = 1; v <= segments; v++) {
            // Вершина v лежит между сегментами (v-1) и v.
            double[] prevNormal = normals[v - 1];
            double[] nextNormal = v <= segments - 1 ? normals[v] : null;
            double prevD = signedOffset[a + v - 1];
            double nextD = v <= segments - 1 ? signedOffset[a + v] : 0.0;
            // Кадр включения G41/G42 — подвод: его собственная нормаль не участвует,
            // конец подвода ставится перпендикулярно первому контурному сегменту.
            if (rampEntry && v == 1 && segments > 1) {
                prevNormal = null;
            }
            shifted[v] = offsetVertex(points[v], prevNormal, prevD, nextNormal, nextD);
        }
        for (int k = 0; k < segments; k++) {
            GCodeMoveData move = source.get(a + k);
            result.set(a + k, new GCodeMoveData(
                    shifted[k][1] / xScale,
                    shifted[k][0],
                    shifted[k + 1][1] / xScale,
                    shifted[k + 1][0],
                    move.rapid(),
                    move.sourceLine(),
                    move.arcSegment(),
                    move.toolNumber(),
                    move.edgeNumber(),
                    move.arcEnd()));
        }
        // Сход: следующий ход начинается с конца эквидистанты.
        int next = b + 1;
        if (next < source.size() && isContiguous(source.get(b), source.get(next), xScale)) {
            GCodeMoveData move = source.get(next);
            result.set(next, new GCodeMoveData(
                    shifted[segments][1] / xScale,
                    shifted[segments][0],
                    move.endX(),
                    move.endZ(),
                    move.rapid(),
                    move.sourceLine(),
                    move.arcSegment(),
                    move.toolNumber(),
                    move.edgeNumber(),
                    move.arcEnd()));
        }
    }

    private static double[] offsetVertex(
            double[] point,
            double[] prevNormal,
            double prevD,
            double[] nextNormal,
            double nextD
    ) {
        double vz;
        double vx;
        double maxD;
        if (prevNormal == null && nextNormal == null) {
            return new double[]{point[0], point[1]};
        }
        if (prevNormal == null) {
            vz = nextNormal[0] * nextD;
            vx = nextNormal[1] * nextD;
            maxD = Math.abs(nextD);
        } else if (nextNormal == null) {
            vz = prevNormal[0] * prevD;
            vx = prevNormal[1] * prevD;
            maxD = Math.abs(prevD);
        } else {
            double dot = prevNormal[0] * nextNormal[0] + prevNormal[1] * nextNormal[1];
            maxD = Math.max(Math.abs(prevD), Math.abs(nextD));
            if (dot < -0.8) {
                // Разворот почти на 180° — митра не определена, берём нормаль следующего сегмента.
                vz = nextNormal[0] * nextD;
                vx = nextNormal[1] * nextD;
            } else {
                vz = (prevNormal[0] * prevD + nextNormal[0] * nextD) / (1.0 + dot);
                vx = (prevNormal[1] * prevD + nextNormal[1] * nextD) / (1.0 + dot);
            }
        }
        double length = Math.hypot(vz, vx);
        double limit = MITER_LIMIT * maxD;
        if (length > limit && length > 1.0e-9) {
            vz = vz / length * limit;
            vx = vx / length * limit;
        }
        return new double[]{point[0] + vz, point[1] + vx};
    }

    /** Нормаль слева от направления движения в плоскости (z, r); null для нулевого сегмента. */
    private static double[] leftNormal(double[] from, double[] to) {
        double dz = to[0] - from[0];
        double dx = to[1] - from[1];
        double length = Math.hypot(dz, dx);
        if (length < MIN_SEGMENT_MM) {
            return null;
        }
        return new double[]{-dx / length, dz / length};
    }

    private static double[] neighborNormal(double[][] normals, int index) {
        for (int step = 1; step < normals.length; step++) {
            if (index - step >= 0 && normals[index - step] != null) {
                return normals[index - step];
            }
            if (index + step < normals.length && normals[index + step] != null) {
                return normals[index + step];
            }
        }
        return new double[]{0.0, 1.0};
    }

    private static boolean isContiguous(GCodeMoveData first, GCodeMoveData second, double xScale) {
        double dz = second.startZ() - first.endZ();
        double dx = (second.startX() - first.endX()) * xScale;
        return Math.hypot(dz, dx) <= CONTIGUITY_EPS_MM;
    }

    private static CompensationMode modeAt(Map<Integer, CompensationMode> modeByLine, int sourceLine) {
        if (modeByLine instanceof TreeMap<Integer, CompensationMode> tree) {
            var entry = tree.floorEntry(sourceLine);
            return entry != null ? entry.getValue() : CompensationMode.NONE;
        }
        CompensationMode mode = CompensationMode.NONE;
        for (Map.Entry<Integer, CompensationMode> entry : modeByLine.entrySet()) {
            if (entry.getKey() > sourceLine) {
                break;
            }
            mode = entry.getValue();
        }
        return mode;
    }

    private static double offnAt(TreeMap<Integer, Double> offnByLine, int sourceLine) {
        if (offnByLine == null || offnByLine.isEmpty()) {
            return 0.0;
        }
        var entry = offnByLine.floorEntry(sourceLine);
        return entry != null ? Math.max(0.0, entry.getValue()) : 0.0;
    }
}
