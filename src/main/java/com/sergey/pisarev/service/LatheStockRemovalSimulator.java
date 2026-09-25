package com.sergey.pisarev.service;

import com.sergey.pisarev.model.GCodeMoveData;
import com.sergey.pisarev.model.MachineConfiguration;
import com.sergey.pisarev.model.SimulationContext;
import com.sergey.pisarev.model.WorkpieceDefinition;
import com.sergey.pisarev.service.LatheCompensationProcessor.CompensationMode;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Токарное снятие: непрерывная огибающая min(R) по Z из сегментов G-code.
 * Сохраняет торцы заготовки, пересечения проходов и форму дуговых сегментов.
 */
public final class LatheStockRemovalSimulator {
    private static final double RADIUS_EPS = 0.02;

    private LatheStockRemovalSimulator() {
    }

    /**
     * Готовый профиль [Z, R] для LatheGeometry / TriangleMesh — без сглаживающей выборки.
     */
    public static List<double[]> buildRevolveProfile(SimulationContext context) {
        return buildRevolveProfile(context, -1);
    }

    /**
     * @param maxContourMoveIndexInclusive -1 = все ходы; иначе симуляция нарезки до этого индекса включительно
     */
    public static List<double[]> buildRevolveProfile(SimulationContext context, int maxContourMoveIndexInclusive) {
        if (AxialStockSection.enabled(context)) {
            var moves = context.getContourMoves();
            int end = maxContourMoveIndexInclusive < 0 ? moves.size() : Math.min(moves.size(), maxContourMoveIndexInclusive+1);
            return AxialStockSection.build(context, moves.subList(0,end));
        }
        TreeMap<Double, Double> radiusByZ = buildRadiusEnvelope(context, maxContourMoveIndexInclusive);
        if (radiusByZ == null || radiusByZ.size() < 2) {
            return List.of();
        }
        double stockRadius = resolveStockRadius(context);
        List<double[]> smooth = StockRemovalMeshBuilder.smoothProfileFromTreeMap(radiusByZ, stockRadius);
        return StockRemovalMeshBuilder.applyCupConstraint(context, smooth);
    }

    public static List<double[]> buildRevolveProfile(SimulationContext context, List<GCodeMoveData> moves) {
        if (AxialStockSection.enabled(context)) return AxialStockSection.build(context,moves);
        TreeMap<Double, Double> radiusByZ = buildRadiusEnvelope(context, moves);
        if (radiusByZ == null || radiusByZ.size() < 2) {
            return List.of();
        }
        double stockRadius = resolveStockRadius(context);
        List<double[]> smooth = StockRemovalMeshBuilder.smoothProfileFromTreeMap(radiusByZ, stockRadius);
        return StockRemovalMeshBuilder.applyCupConstraint(context, smooth);
    }

    /**
     * Стартовая огибающая заготовки (радиус по Z) для инкрементального обновления
     * меша цикла; null, если заготовка не определена.
     */
    public static TreeMap<Double, Double> newStockEnvelope(SimulationContext context) {
        if (context == null) {
            return null;
        }
        double stockRadius = resolveStockRadius(context);
        if (stockRadius <= 0.0) {
            return null;
        }
        TreeMap<Double, Double> radiusByZ = new TreeMap<>();
        initializeStock(radiusByZ, context, stockRadius);
        return radiusByZ;
    }

    /** Применяет ходы [fromInclusive, toExclusive) к огибающей — без пересчёта префикса с нуля. */
    public static void applyMovesToEnvelope(
            TreeMap<Double, Double> radiusByZ,
            SimulationContext context,
            LatheCuttingEnvelope envelope,
            List<GCodeMoveData> moves,
            int fromInclusive,
            int toExclusive
    ) {
        if (radiusByZ == null || context == null || envelope == null || moves == null) {
            return;
        }
        double stockRadius = resolveStockRadius(context);
        if (stockRadius <= 0.0) {
            return;
        }
        WorkpieceDefinition workpiece = context.getWorkpiece();
        int from = Math.max(0, fromInclusive);
        int to = Math.min(moves.size(), toExclusive);
        for (int i = from; i < to; i++) {
            applyCuttingMove(radiusByZ, envelope, stockRadius, workpiece, moves.get(i));
        }
    }

    /** Гладкий профиль [Z, R] из готовой огибающей (как buildRevolveProfile). */
    public static List<double[]> envelopeToProfile(SimulationContext context, TreeMap<Double, Double> radiusByZ) {
        if (context == null || radiusByZ == null || radiusByZ.size() < 2) {
            return List.of();
        }
        double stockRadius = resolveStockRadius(context);
        List<double[]> smooth = StockRemovalMeshBuilder.smoothProfileFromTreeMap(radiusByZ, stockRadius);
        return StockRemovalMeshBuilder.applyCupConstraint(context, smooth);
    }

    /**
     * Ступенчатый профиль [Z,R] для 3D-вращения (SinuTrain / OCCT): ортогональные ступени 90°.
     */
    public static List<double[]> buildMeshRevolveProfile(SimulationContext context) {
        return buildMeshRevolveProfile(context, -1);
    }

    public static List<double[]> buildMeshRevolveProfile(
            SimulationContext context,
            int maxContourMoveIndexInclusive
    ) {
        if (AxialStockSection.enabled(context)) return buildRevolveProfile(context,maxContourMoveIndexInclusive);
        TreeMap<Double, Double> radiusByZ = buildRadiusEnvelope(context, maxContourMoveIndexInclusive);
        if (radiusByZ == null || radiusByZ.size() < 2) {
            return List.of();
        }
        List<double[]> stepped = buildOrthogonalStepProfile(radiusByZ);
        return StockRemovalMeshBuilder.applyCupConstraint(context, stepped);
    }

    private static TreeMap<Double, Double> buildRadiusEnvelope(
            SimulationContext context,
            int maxContourMoveIndexInclusive
    ) {
        double stockRadius = resolveStockRadius(context);
        if (stockRadius <= 0.0 || context == null || context.getContourMoves().isEmpty()) {
            return null;
        }
        TreeMap<Double, Double> radiusByZ = new TreeMap<>();
        LatheCuttingEnvelope envelope = new LatheCuttingEnvelope(context);
        initializeStock(radiusByZ, context, stockRadius);
        applyCuttingEnvelope(radiusByZ, context, envelope, stockRadius, maxContourMoveIndexInclusive);
        return radiusByZ.size() < 2 ? null : radiusByZ;
    }

    private static TreeMap<Double, Double> buildRadiusEnvelope(
            SimulationContext context,
            List<GCodeMoveData> moves
    ) {
        if (context == null || moves == null || moves.isEmpty()) {
            return null;
        }
        double stockRadius = resolveStockRadius(context);
        if (stockRadius <= 0.0) {
            return null;
        }
        TreeMap<Double, Double> radiusByZ = new TreeMap<>();
        LatheCuttingEnvelope envelope = new LatheCuttingEnvelope(context);
        initializeStock(radiusByZ, context, stockRadius);
        WorkpieceDefinition workpiece = context.getWorkpiece();
        for (GCodeMoveData move : moves) {
            applyCuttingMove(radiusByZ, envelope, stockRadius, workpiece, move);
        }
        return radiusByZ.size() < 2 ? null : radiusByZ;
    }

    /** Ступенчатый профиль 90° только для 2D-вида «Сбоку» / «Срез». */
    public static List<double[]> buildSteppedSideViewProfile(SimulationContext context, int maxContourMoveIndexInclusive) {
        List<double[]> smooth = buildRevolveProfile(context, maxContourMoveIndexInclusive);
        if (smooth.size() < 2) {
            return smooth;
        }
        return StockRemovalMeshBuilder.profileForSideView(smooth);
    }

    private static void initializeStock(
            TreeMap<Double, Double> radiusByZ,
            SimulationContext context,
            double stockRadius
    ) {
        WorkpieceDefinition workpiece = context.getWorkpiece();
        double zMin;
        double zMax;
        if (workpiece != null && workpiece.isValid()) {
            zMin = workpiece.getZMin();
            zMax = workpiece.getZMax();
        } else {
            zMin = Double.POSITIVE_INFINITY;
            zMax = Double.NEGATIVE_INFINITY;
            for (GCodeMoveData move : context.getMoves()) {
                if (move.rapid()) {
                    continue;
                }
                zMin = Math.min(zMin, Math.min(move.startZ(), move.endZ()));
                zMax = Math.max(zMax, Math.max(move.startZ(), move.endZ()));
            }
            if (!Double.isFinite(zMin)) {
                zMin = -50.0;
                zMax = 50.0;
            }
            double pad = Math.max(5.0, (zMax - zMin) * 0.02);
            zMin -= pad;
            zMax += pad;
        }
        // A cylinder is a straight segment, not a grid. Exact ends must survive
        // every cut, including when its length is not a multiple of a grid step.
        radiusByZ.put(zMin, stockRadius);
        radiusByZ.put(zMax, stockRadius);
    }

    private static void applyCuttingEnvelope(
            TreeMap<Double, Double> radiusByZ,
            SimulationContext context,
            LatheCuttingEnvelope envelope,
            double stockRadius,
            int maxContourMoveIndexInclusive
    ) {
        List<GCodeMoveData> moves = context.getContourMoves();
        int last = maxContourMoveIndexInclusive < 0
                ? moves.size() - 1
                : Math.min(maxContourMoveIndexInclusive, moves.size() - 1);
        WorkpieceDefinition workpiece = context.getWorkpiece();
        for (int i = 0; i <= last; i++) {
            applyCuttingMove(radiusByZ, envelope, stockRadius, workpiece, moves.get(i));
        }
    }

    private static void applyCuttingMove(
            TreeMap<Double, Double> radiusByZ,
            LatheCuttingEnvelope envelope,
            double stockRadius,
            WorkpieceDefinition workpiece,
            GCodeMoveData move
    ) {
        if (move.rapid() || isParkingMove(move, stockRadius * 2.0)) {
            return;
        }
        if (!envelope.canCutMove(move.sourceLine(), move.startX(), move.startZ(), move.endX(), move.endZ())) {
            return;
        }
        if (move.startX() == move.endX() && move.startZ() == move.endZ()) {
            return;
        }
        applySweptCut(radiusByZ, envelope, move);
    }

    private static List<GCodeMoveData> selectDominantCompensatedContour(
            SimulationContext context,
            List<GCodeMoveData> moves,
            LatheCuttingEnvelope envelope,
            double stockRadius,
            int last,
            WorkpieceDefinition workpiece
    ) {
        if (context == null || moves == null || moves.isEmpty() || last < 0 || stockRadius <= 0.0) {
            return List.of();
        }
        TreeMap<Integer, CompensationMode> modeByLine =
                new TreeMap<>(LatheCompensationProcessor.scanCompensationByLine(context.getProgramText()));
        if (modeByLine.isEmpty()) {
            return List.of();
        }
        double workpieceSpan = workpiece != null && workpiece.isValid()
                ? Math.max(0.0, workpiece.getZMax() - workpiece.getZMin())
                : moveZSpan(moves, last);
        double minDominantSpan = Math.max(150.0, workpieceSpan * 0.20);
        ContourChain current = null;
        ContourChain best = null;
        for (int i = 0; i <= last && i < moves.size(); i++) {
            GCodeMoveData move = moves.get(i);
            CompensationMode mode = modeAt(modeByLine, move.sourceLine());
            if (mode == CompensationMode.NONE) {
                best = betterChain(best, current, stockRadius, minDominantSpan);
                current = null;
                continue;
            }
            if (move.rapid() || isParkingMove(move, stockRadius * 2.0) || isOutsideWorkpiece(move, workpiece)) {
                continue;
            }
            if (!envelope.canCutMove(move.sourceLine(), move.startX(), move.startZ(), move.endX(), move.endZ())) {
                continue;
            }
            double r1 = envelope.removalRadiusAt(move.sourceLine(), move.startX(), stockRadius);
            double r2 = envelope.removalRadiusAt(move.sourceLine(), move.endX(), stockRadius);
            if (Math.min(r1, r2) >= stockRadius - RADIUS_EPS
                    && Math.abs(move.startZ() - move.endZ()) < RADIUS_EPS) {
                continue;
            }
            if (current == null || current.mode != mode) {
                best = betterChain(best, current, stockRadius, minDominantSpan);
                current = new ContourChain(mode);
            }
            current.add(move, Math.min(r1, r2));
        }
        best = betterChain(best, current, stockRadius, minDominantSpan);
        return best != null ? best.moves : List.of();
    }

    private static CompensationMode modeAt(TreeMap<Integer, CompensationMode> modeByLine, int sourceLine) {
        var entry = modeByLine.floorEntry(sourceLine);
        return entry != null ? entry.getValue() : CompensationMode.NONE;
    }

    private static double moveZSpan(List<GCodeMoveData> moves, int last) {
        double zMin = Double.POSITIVE_INFINITY;
        double zMax = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= last && i < moves.size(); i++) {
            GCodeMoveData move = moves.get(i);
            zMin = Math.min(zMin, Math.min(move.startZ(), move.endZ()));
            zMax = Math.max(zMax, Math.max(move.startZ(), move.endZ()));
        }
        return Double.isFinite(zMin) && Double.isFinite(zMax) ? zMax - zMin : 0.0;
    }

    private static ContourChain betterChain(
            ContourChain best,
            ContourChain candidate,
            double stockRadius,
            double minDominantSpan
    ) {
        if (candidate == null || !candidate.isDominant(stockRadius, minDominantSpan)) {
            return best;
        }
        if (best == null || candidate.score(stockRadius) > best.score(stockRadius)) {
            return candidate;
        }
        return best;
    }

    private static boolean isOutsideWorkpiece(GCodeMoveData move, WorkpieceDefinition workpiece) {
        if (workpiece == null || !workpiece.isValid()) {
            return false;
        }
        double zLo = Math.min(move.startZ(), move.endZ());
        double zHi = Math.max(move.startZ(), move.endZ());
        return zHi < workpiece.getZMin() - 0.5 || zLo > workpiece.getZMax() + 0.5;
    }

    private static boolean isParkingMove(GCodeMoveData move, double stockDiameterMm) {
        if (!move.rapid()) {
            return false;
        }
        double maxDiameter = Math.max(Math.abs(move.startX()), Math.abs(move.endX()));
        return maxDiameter > stockDiameterMm * 1.35 + 20.0;
    }

    private static void applySweptCut(
            TreeMap<Double, Double> radiusByZ,
            LatheCuttingEnvelope envelope,
            GCodeMoveData move
    ) {
        int line = move.sourceLine();
        double z1 = move.startZ(), z2 = move.endZ();
        double r1 = LatheMeshBuilder.toRadius(move.startX());
        double r2 = LatheMeshBuilder.toRadius(move.endX());
        if (envelope.isCompensated(line)) {
            // G41/G42 describes the finished contour. Spreading this contour
            // sideways by R (or by a guessed plate width) cuts it a second time
            // and moves shoulders away from their programmed coordinates.
            if (z2 < z1) {
                double swap = z1; z1 = z2; z2 = swap;
                swap = r1; r1 = r2; r2 = swap;
            }
            LatheRadiusEnvelope.cut(radiusByZ, z1, r1, z2, r2);
            return;
        }
        double radius = envelope.noseRadiusMm(line);
        double zOffset = envelope.noseCenterZOffsetMm(line);
        double rOffset = envelope.noseCenterRadialOffsetMm(line);
        LatheNoseSweep.cut(radiusByZ, z1 + zOffset, r1 + rOffset,
                z2 + zOffset, r2 + rOffset, radius);
    }

    /**
     * Ступени 90°: при смене R — сначала Z, потом R (как контур SinuTrain).
     */
    private static List<double[]> buildOrthogonalStepProfile(TreeMap<Double, Double> radiusByZ) {
        ArrayList<double[]> sorted = new ArrayList<>();
        for (var entry : radiusByZ.entrySet()) {
            sorted.add(new double[]{entry.getKey(), Math.max(0.0, entry.getValue())});
        }
        if (sorted.size() < 2) {
            return sorted;
        }
        ArrayList<double[]> out = new ArrayList<>();
        out.add(new double[]{sorted.get(0)[0], sorted.get(0)[1]});
        for (int i = 1; i < sorted.size(); i++) {
            double zB = sorted.get(i)[0];
            double rB = sorted.get(i)[1];
            double[] last = out.get(out.size() - 1);
            double dz = zB - last[0];
            double dr = rB - last[1];
            if (Math.abs(dz) < 0.02 && Math.abs(dr) < RADIUS_EPS) {
                continue;
            }
            if (Math.abs(dr) < RADIUS_EPS) {
                last[0] = zB;
            } else if (Math.abs(dz) < 0.02) {
                out.add(new double[]{last[0], rB});
            } else {
                out.add(new double[]{zB, last[1]});
                out.add(new double[]{zB, rB});
            }
        }
        return collapsePlateaus(out);
    }

    /** Сливает длинные участки с постоянным R в одно кольцо. */
    private static List<double[]> collapsePlateaus(List<double[]> profile) {
        if (profile.size() < 2) {
            return profile;
        }
        ArrayList<double[]> out = new ArrayList<>();
        out.add(profile.get(0));
        for (int i = 1; i < profile.size(); i++) {
            double[] cur = profile.get(i);
            double[] last = out.get(out.size() - 1);
            boolean sameR = Math.abs(cur[1] - last[1]) < RADIUS_EPS;
            if (sameR) {
                out.set(out.size() - 1, new double[]{cur[0], cur[1]});
            } else {
                out.add(cur);
            }
        }
        return out;
    }

    private static final class ContourChain {
        private final CompensationMode mode;
        private final ArrayList<GCodeMoveData> moves = new ArrayList<>();
        private double zMin = Double.POSITIVE_INFINITY;
        private double zMax = Double.NEGATIVE_INFINITY;
        private double minRadius = Double.POSITIVE_INFINITY;

        private ContourChain(CompensationMode mode) {
            this.mode = mode;
        }

        private void add(GCodeMoveData move, double removalRadius) {
            this.moves.add(move);
            this.zMin = Math.min(this.zMin, Math.min(move.startZ(), move.endZ()));
            this.zMax = Math.max(this.zMax, Math.max(move.startZ(), move.endZ()));
            this.minRadius = Math.min(this.minRadius, removalRadius);
        }

        private double span() {
            return Double.isFinite(this.zMin) && Double.isFinite(this.zMax)
                    ? Math.max(0.0, this.zMax - this.zMin)
                    : 0.0;
        }

        private double depth(double stockRadius) {
            return Double.isFinite(this.minRadius)
                    ? Math.max(0.0, stockRadius - this.minRadius)
                    : 0.0;
        }

        private boolean isDominant(double stockRadius, double minDominantSpan) {
            return this.moves.size() >= 2
                    && this.span() >= minDominantSpan
                    && this.depth(stockRadius) >= 0.5;
        }

        private double score(double stockRadius) {
            return this.span() + this.depth(stockRadius) * 0.02;
        }
    }

    private static double resolveStockRadius(SimulationContext context) {
        WorkpieceDefinition workpiece = context.getWorkpiece();
        if (workpiece != null && workpiece.isValid()) {
            return workpiece.getStockRadiusMm();
        }
        MachineConfiguration machine = context.getMachineConfiguration();
        if (machine != null
                && machine.isUseBlankDiameter()
                && machine.getBlankDiameterMm() > 0.0) {
            return machine.getBlankDiameterMm() / 2.0;
        }
        double maxFeedRadius = 0.0;
        for (GCodeMoveData move : context.getMoves()) {
            if (move.rapid()) {
                continue;
            }
            maxFeedRadius = Math.max(maxFeedRadius, LatheMeshBuilder.toRadius(move.startX()));
            maxFeedRadius = Math.max(maxFeedRadius, LatheMeshBuilder.toRadius(move.endX()));
        }
        return maxFeedRadius > 0.0 ? maxFeedRadius * 1.02 : 0.0;
    }
}
