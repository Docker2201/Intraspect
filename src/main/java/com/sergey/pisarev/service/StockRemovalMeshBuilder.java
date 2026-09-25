package com.sergey.pisarev.service;

import com.sergey.pisarev.model.GCodeMoveData;
import com.sergey.pisarev.model.MachineConfiguration;
import com.sergey.pisarev.model.MeshBuildResult;
import com.sergey.pisarev.model.SimulationContext;
import com.sergey.pisarev.model.WorkpieceDefinition;
import com.sergey.pisarev.occt.OcctLatheMesher;
import com.sergey.pisarev.occt.OcctNative;
import com.sergey.pisarev.util.I18n;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;

/**
 * Построение 3D-детали после снятия материала (только оставшийся объём).
 */
public final class StockRemovalMeshBuilder {
    // Баланс детализации и скорости: достаточно кругло при приближении, но без лишней нагрузки в 3D-окне.
    private static final int SLICES = 192;
    private static final int CYCLE_PREVIEW_SLICES = 96;
    private static final double MESH_PROFILE_TOLERANCE_MM = 0.002;
    private static final double Z_STEP_MM = 0.35;
    private static final double RADIUS_EPS = 0.02;

    private StockRemovalMeshBuilder() {
    }

    public static MeshBuildResult buildFinishedPart(SimulationContext context, String programText) {
        return buildFinishedPart(context, programText, -1);
    }

    public static MeshBuildResult buildFinishedPart(
            SimulationContext context,
            String programText,
            int maxContourMoveIndexInclusive
    ) {
        if (context == null) {
            return MeshBuildResult.failure(I18n.text("mesh.001"));
        }
        List<GCodeMoveData> contourMoves = context.getContourMoves();
        if (contourMoves.isEmpty()) {
            return MeshBuildResult.failure(I18n.text("mesh.002"));
        }
        if (programText != null && programText.toUpperCase(Locale.US).contains("CYCLE")
                && countCuttingMoves(contourMoves) == 0) {
            return MeshBuildResult.failure(
                    I18n.text("mesh.003")
                            + I18n.text("mesh.004"));
        }
        double stockRadius = resolveStockRadius(context);
        if (stockRadius <= 0.0) {
            return MeshBuildResult.failure(
                    I18n.text("mesh.005")
                            + I18n.text("mesh.006"));
        }
        LatheCuttingEnvelope envelope = new LatheCuttingEnvelope(context);
        List<double[]> profile = LatheStockRemovalSimulator.buildRevolveProfile(context, maxContourMoveIndexInclusive);
        int cuttingMoves = countCuttingMovesUpTo(contourMoves, maxContourMoveIndexInclusive);
        if (profile.size() < 2) {
            TreeMap<Double, Double> radiusByZ = new TreeMap<>();
            initializeStock(radiusByZ, context, stockRadius);
            cuttingMoves = applyCuts(radiusByZ, context, envelope, contourMoves, stockRadius);
            if (cuttingMoves == 0) {
                return MeshBuildResult.failure(
                        I18n.text("mesh.007")
                                + I18n.text("mesh.008"));
            }
            profile = applyCupConstraint(
                    context,
                    expandOrthogonalSteps(toFinishedProfile(radiusByZ, stockRadius)));
        }
        if (profile.size() < 2) {
            return MeshBuildResult.failure(I18n.text("mesh.009"));
        }
        if (!profileHasMaterialRemoval(profile, stockRadius)) {
            if (maxContourMoveIndexInclusive >= 0) {
                return MeshBuildResult.failure(
                        I18n.text("mesh.010"));
            }
            return MeshBuildResult.failure(
                    I18n.text("mesh.011")
                            + String.format(Locale.US, "%.1f", stockRadius * 2.0)
                            + I18n.text("mesh.012"));
        }
        try {
            List<double[]> meshProfile = buildFinishedMeshProfile(context, profile, stockRadius);
            TriangleMesh mesh = buildRevolvedTriangleMesh(meshProfile);
            validateMesh(mesh);
            return MeshBuildResult.success(
                    mesh,
                    I18n.text("mesh.013")
                            + String.format(Locale.US, "%.1f", maxDiameter(profile))
                            + I18n.text("mesh.014")
                            + cuttingMoves
                            + I18n.text("mesh.015"),
                    cuttingMoves,
                    profile.size());
        } catch (IllegalArgumentException exception) {
            return MeshBuildResult.failure(I18n.text("sim.049") + exception.getMessage());
        }
    }

    public static List<double[]> buildFinishedProfile(SimulationContext context) {
        List<double[]> simulated = LatheStockRemovalSimulator.buildRevolveProfile(context);
        if (simulated.size() >= 2) {
            return simulated;
        }
        double stockRadius = resolveStockRadius(context);
        if (stockRadius <= 0.0) {
            return List.of();
        }
        TreeMap<Double, Double> radiusByZ = new TreeMap<>();
        LatheCuttingEnvelope envelope = new LatheCuttingEnvelope(context);
        initializeStock(radiusByZ, context, stockRadius);
        applyCuts(radiusByZ, context, envelope, context.getContourMoves(), stockRadius);
        return applyCupConstraint(
                context,
                expandOrthogonalSteps(toFinishedProfile(radiusByZ, stockRadius)));
    }

    private static double resolveStockRadius(SimulationContext context) {
        WorkpieceDefinition workpiece = context.getWorkpiece();
        if (workpiece != null && workpiece.isValid()
                && (context == null || context.isWorkpieceFromProgram())) {
            return workpiece.getStockRadiusMm();
        }
        MachineConfiguration machine = context != null ? context.getMachineConfiguration() : null;
        if (machine != null
                && machine.isUseBlankDiameter()
                && machine.getBlankDiameterMm() > 0.0) {
            return machine.getBlankDiameterMm() / 2.0;
        }
        if (workpiece != null && workpiece.isValid()) {
            return workpiece.getStockRadiusMm();
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
                zMin = Math.min(zMin, Math.min(move.startZ(), move.endZ()));
                zMax = Math.max(zMax, Math.max(move.startZ(), move.endZ()));
            }
            if (!Double.isFinite(zMin)) {
                return;
            }
        }
        fillStockRange(radiusByZ, zMin, zMax, stockRadius);
    }

    private static void fillStockRange(TreeMap<Double, Double> radiusByZ, double zMin, double zMax, double stockRadius) {
        double step = Math.max(0.5, Z_STEP_MM);
        for (double z = zMin; z <= zMax + 0.001; z += step) {
            radiusByZ.put(z, stockRadius);
        }
        radiusByZ.put(zMin, stockRadius);
        radiusByZ.put(zMax, stockRadius);
    }

    private static int applyCuts(
            TreeMap<Double, Double> radiusByZ,
            SimulationContext context,
            LatheCuttingEnvelope envelope,
            List<GCodeMoveData> contourMoves,
            double stockRadius
    ) {
        int count = 0;
        double stockDiameter = stockRadius * 2.0;
        WorkpieceDefinition workpiece = context.getWorkpiece();
        for (GCodeMoveData move : contourMoves) {
            if (!isCuttingMove(move)) {
                continue;
            }
            if (isParkingMove(move, stockDiameter)) {
                continue;
            }
            if (isOutsideWorkpiece(move, workpiece)) {
                continue;
            }
            double r1 = envelope.removalRadiusAt(move.sourceLine(), move.startX(), stockRadius);
            double r2 = envelope.removalRadiusAt(move.sourceLine(), move.endX(), stockRadius);
            if (Math.abs(r1 - r2) < RADIUS_EPS
                    && Math.abs(move.startZ() - move.endZ()) < RADIUS_EPS) {
                continue;
            }
            applyCutSegment(radiusByZ, envelope, move, stockRadius);
            count++;
        }
        return count;
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

    private static boolean isCuttingMove(GCodeMoveData move) {
        if (move.rapid()) {
            return false;
        }
        double r1 = LatheMeshBuilder.toRadius(move.startX());
        double r2 = LatheMeshBuilder.toRadius(move.endX());
        return Math.abs(r1 - r2) >= RADIUS_EPS
                || Math.abs(move.startZ() - move.endZ()) >= RADIUS_EPS;
    }

    private static int countCuttingMoves(List<GCodeMoveData> moves) {
        return countCuttingMovesUpTo(moves, -1);
    }

    private static int countCuttingMovesUpTo(List<GCodeMoveData> moves, int maxIndexInclusive) {
        int count = 0;
        int last = maxIndexInclusive < 0 ? moves.size() - 1 : Math.min(maxIndexInclusive, moves.size() - 1);
        for (int i = 0; i <= last; i++) {
            if (isCuttingMove(moves.get(i))) {
                count++;
            }
        }
        return count;
    }

    /**
     * Ходы только нарезки (контур, без G00 и парковок) — как Tool Path в SinuTrain.
     */
    /**
     * Траектория для 3D-оверлея: рабочие ходы (зелёные) + укороченные G00 в зоне детали (красные).
     */
    public static List<GCodeMoveData> filterToolpathFor3dOverlay(SimulationContext context) {
        return filterToolpathFor3dOverlay(context, List.of());
    }

    public static List<GCodeMoveData> filterToolpathFor3dOverlay(
            SimulationContext context,
            List<double[]> finishedProfile
    ) {
        if (context == null || context.getContourMoves().isEmpty()) {
            return List.of();
        }
        double stockDiameter = resolveStockRadius(context) * 2.0;
        double stockRadius = resolveStockRadius(context);
        LinkedHashSet<GCodeMoveData> ordered = new LinkedHashSet<>();
        ordered.addAll(filterCuttingToolpathMoves(context));
        double zMin = Double.POSITIVE_INFINITY;
        double zMax = Double.NEGATIVE_INFINITY;
        double maxR = stockRadius * 1.05;
        if (finishedProfile != null && finishedProfile.size() >= 2) {
            for (double[] point : finishedProfile) {
                zMin = Math.min(zMin, point[0]);
                zMax = Math.max(zMax, point[0]);
                maxR = Math.max(maxR, point[1] * 1.08);
            }
        } else {
            for (GCodeMoveData move : context.getContourMoves()) {
                if (!isCuttingMove(move)) {
                    continue;
                }
                zMin = Math.min(zMin, Math.min(move.startZ(), move.endZ()));
                zMax = Math.max(zMax, Math.max(move.startZ(), move.endZ()));
            }
        }
        if (!Double.isFinite(zMin)) {
            WorkpieceDefinition workpiece = context.getWorkpiece();
            if (workpiece != null && workpiece.isValid()) {
                zMin = workpiece.getZMin();
                zMax = workpiece.getZMax();
                maxR = workpiece.getStockRadiusMm() * 1.05;
            }
        }
        double padZ = Math.max(5.0, (zMax - zMin) * 0.02);
        zMin -= padZ;
        zMax += padZ;
        LatheCuttingEnvelope envelope = new LatheCuttingEnvelope(context);
        for (GCodeMoveData move : context.getContourMoves()) {
            if (!move.rapid() || isParkingMove(move, stockDiameter)) {
                continue;
            }
            double zLo = Math.min(move.startZ(), move.endZ());
            double zHi = Math.max(move.startZ(), move.endZ());
            if (zHi < zMin || zLo > zMax) {
                continue;
            }
            double r = Math.max(
                    envelope.toolPathRadiusAt(move.sourceLine(), move.startX()),
                    envelope.toolPathRadiusAt(move.sourceLine(), move.endX()));
            if (r > maxR + 8.0) {
                continue;
            }
            ordered.add(move);
        }
        return new ArrayList<>(ordered);
    }

    public static List<GCodeMoveData> filterCuttingToolpathMoves(SimulationContext context) {
        if (context == null || context.getContourMoves().isEmpty()) {
            return List.of();
        }
        double stockDiameter = resolveStockRadius(context) * 2.0;
        ArrayList<GCodeMoveData> out = new ArrayList<>();
        for (GCodeMoveData move : context.getContourMoves()) {
            if (!isCuttingMove(move) || isParkingMove(move, stockDiameter)) {
                continue;
            }
            out.add(move);
        }
        return out;
    }

    /**
     * Профиль для 3D-вращения: гладкий min(R) по Z, без ступеней 90° (иначе кольца на меше).
     */
    public static List<double[]> buildFinishedMeshProfile(
            SimulationContext context,
            List<double[]> finishedProfile,
            double stockRadius
    ) {
        if (finishedProfile == null || finishedProfile.size() < 2) {
            return finishedProfile;
        }
        List<double[]> profile = buildSmoothMeshProfile(context, finishedProfile, stockRadius);
        return profile;
    }

    /** Гладкий профиль для OCCT/JavaFX-вращения (без ступеней 90° — иначе «кольца» на меше). */
    public static List<double[]> buildSmoothMeshProfile(
            SimulationContext context,
            List<double[]> finishedProfile,
            double stockRadius
    ) {
        if (AxialStockSection.isSection(finishedProfile)) return finishedProfile;
        if (finishedProfile == null || finishedProfile.size() < 2) {
            return finishedProfile;
        }
        List<double[]> profile = mergeProfileByZ(finishedProfile);
        profile = applyCupConstraint(context, profile);
        profile = trimToWorkpieceBounds(context, profile);
        return simplifyProfile(profile, MESH_PROFILE_TOLERANCE_MM);
    }

    public static List<double[]> buildSolidMeshProfile(
            SimulationContext context,
            List<double[]> finishedProfile,
            double stockRadius
    ) {
        return buildFinishedMeshProfile(context, finishedProfile, stockRadius);
    }

    /**
     * Сплошная 3D-деталь: стек усечённых конусов/цилиндров (закрытый объём, не «кольца»).
     */
    /** Профиль цельного цилиндра заготовки [Z, R]. */
    public static List<double[]> buildStockRevolveProfile(SimulationContext context) {
        if (AxialStockSection.enabled(context) && context.getPrecedingSetup() != null)
            return AxialStockSection.initialSection(context);
        double stockR = resolveStockRadius(context);
        if (stockR <= 0.0) {
            return List.of();
        }
        WorkpieceDefinition workpiece = context != null ? context.getWorkpiece() : null;
        double zMin = -50.0;
        double zMax = 50.0;
        if (workpiece != null && workpiece.isValid()) {
            zMin = workpiece.getZMin();
            zMax = workpiece.getZMax();
        } else if (context != null) {
            for (GCodeMoveData move : context.getMoves()) {
                zMin = Math.min(zMin, Math.min(move.startZ(), move.endZ()));
                zMax = Math.max(zMax, Math.max(move.startZ(), move.endZ()));
            }
            if (!Double.isFinite(zMin)) {
                zMin = -50.0;
                zMax = 50.0;
            }
        }
        return List.of(new double[]{zMin, stockR}, new double[]{zMax, stockR});
    }

    public static MeshView buildStockRevolvedMeshView(SimulationContext context, Color diffuseColor) {
        return buildStockRevolvedMeshView(context, diffuseColor, false);
    }

    public static MeshView buildStockRevolvedMeshView(
            SimulationContext context,
            Color diffuseColor,
            boolean halfSection
    ) {
        TriangleMesh mesh = buildStockRevolvedTriangleMesh(context, halfSection);
        if (mesh == null) {
            return null;
        }
        return createStockMeshView(mesh, diffuseColor);
    }

    public static TriangleMesh buildStockRevolvedTriangleMesh(SimulationContext context, boolean halfSection) {
        List<double[]> stockProfile = buildStockRevolveProfile(context);
        if (stockProfile.size() < 2) {
            return null;
        }
        TriangleMesh mesh = buildRevolveMesh(stockProfile, halfSection);
        validateMesh(mesh);
        return mesh;
    }

    public static MeshView createStockMeshView(TriangleMesh mesh, Color diffuseColor) {
        if (mesh == null) {
            return null;
        }
        PhongMaterial material = new PhongMaterial(diffuseColor);
        // Блик тусклый и узкий. Почти белый цвет при степени 8 давал широкое пятно
        // по всей заготовке, и при повороте оно ползло по поверхности как засвет.
        material.setSpecularColor(Color.web("#4a5766"));
        material.setSpecularPower(30.0);
        MeshView view = new MeshView(mesh);
        view.setMaterial(material);
        view.setCullFace(CullFace.NONE);
        view.setDrawMode(DrawMode.FILL);
        return view;
    }

    /**
     * 3D-деталь: сегменты усечённых конусов (форма УП), без артефактов «кольца» от одного revolve.
     */
    public static javafx.scene.Node buildFinishedPartNode(
            SimulationContext context,
            List<double[]> profile,
            double stockRadius,
            Color diffuseColor,
            boolean halfSection
    ) {
        if (profile == null || profile.size() < 2) {
            return null;
        }
        MeshView meshView = buildRevolvedSolidMeshView(
                context, profile, stockRadius, diffuseColor, halfSection);
        if (meshView != null) {
            return meshView;
        }
        List<double[]> meshProfile = buildFinishedMeshProfile(context, profile, stockRadius);
        if (meshProfile == null || meshProfile.size() < 2) {
            return null;
        }
        OcctNative.markMeshBuildUsedOcct(false);
        TriangleMesh solid = revolveSolidLathe(meshProfile, SLICES);
        if (solid == null || solid.getPoints().size() < 9) {
            return null;
        }
        validateMesh(solid);
        PhongMaterial material = sinuTrainPartMaterial(diffuseColor);
        MeshView view = new MeshView(solid);
        view.setMaterial(material);
        view.setCullFace(CullFace.NONE);
        view.setDrawMode(DrawMode.FILL);
        return view;
    }

    /** Одно тело вращения с закрытыми торцами (без «пустоты» между сегментами). */
    public static MeshView buildRevolvedSolidMeshView(
            SimulationContext context,
            List<double[]> profile,
            double stockRadius,
            Color diffuseColor
    ) {
        return buildRevolvedSolidMeshView(context, profile, stockRadius, diffuseColor, false);
    }

    /** Полутело вращения (плоскость среза X=0) — вид как в SinuTrain. */
    public static MeshView buildRevolvedSolidMeshView(
            SimulationContext context,
            List<double[]> profile,
            double stockRadius,
            Color diffuseColor,
            boolean halfSection
    ) {
        if (profile == null || profile.size() < 2) {
            return null;
        }
        TriangleMesh mesh = buildFinishedPartTriangleMesh(context, profile, stockRadius, halfSection);
        if (mesh == null) {
            return null;
        }
        return createFinishedMeshView(mesh, diffuseColor);
    }

    public static TriangleMesh buildFinishedPartTriangleMesh(
            SimulationContext context,
            List<double[]> profile,
            double stockRadius,
            boolean halfSection
    ) {
        List<double[]> meshProfile = buildSmoothMeshProfile(context, profile, stockRadius);
        if (meshProfile == null || meshProfile.size() < 2) {
            return null;
        }
        TriangleMesh mesh = buildRevolveMesh(meshProfile, halfSection);
        validateMesh(mesh);
        return mesh;
    }

    public static TriangleMesh buildCyclePreviewTriangleMesh(
            SimulationContext context,
            List<double[]> profile,
            double stockRadius
    ) {
        // Столько же срезов, сколько у токарного предпросмотра: 144 давали в сцене
        // почти полмиллиона вершин на колесе, а разницы на экране нет.
        if (AxialStockSection.isSection(profile))
            return CycleSectionMesh.build(profile, CYCLE_PREVIEW_SLICES);
        List<double[]> meshProfile = buildCyclePreviewMeshProfile(context, profile, stockRadius);
        if (meshProfile == null || meshProfile.size() < 2) {
            return null;
        }
        OcctNative.markMeshBuildUsedOcct(false);
        TriangleMesh mesh = LatheMeshNormals.apply(revolveSolidLathe(meshProfile, CYCLE_PREVIEW_SLICES), meshProfile);
        validateMesh(mesh);
        return mesh;
    }

    public static TriangleMesh buildFinalCycleTriangleMesh(List<double[]> profile) {
        return buildRevolvedTriangleMesh(profile);
    }

    private static List<double[]> buildCyclePreviewMeshProfile(
            SimulationContext context,
            List<double[]> finishedProfile,
            double stockRadius
    ) {
        if (finishedProfile == null || finishedProfile.size() < 2) {
            return finishedProfile;
        }
        List<double[]> profile = mergeProfileByZ(finishedProfile);
        profile = applyCupConstraint(context, profile);
        profile = trimToWorkpieceBounds(context, profile);
        return simplifyProfile(profile, MESH_PROFILE_TOLERANCE_MM);
    }

    public static MeshView createFinishedMeshView(TriangleMesh mesh, Color diffuseColor) {
        if (mesh == null) {
            return null;
        }
        PhongMaterial material = sinuTrainPartMaterial(diffuseColor);
        MeshView view = new MeshView(mesh);
        view.setMaterial(material);
        view.setCullFace(CullFace.NONE);
        view.setDrawMode(DrawMode.FILL);
        return view;
    }

    private static PhongMaterial sinuTrainPartMaterial(Color diffuseColor) {
        Color base = diffuseColor != null ? diffuseColor : Color.web("#7ec8e3");
        PhongMaterial material = new PhongMaterial(base);
        // См. заготовку: узкий тусклый блик вместо широкого светлого пятна.
        material.setSpecularColor(Color.web("#4f5e6d"));
        material.setSpecularPower(30.0);
        return material;
    }

    /**
     * Каким движком построен ПОСЛЕДНИЙ меш.
     *
     * <p>Раньше метка отвечала «OCCT», если библиотека просто доступна, — даже когда
     * построение реально шло через JavaFX (режим цикла, половинный разрез, откат после
     * ошибки OCCT). Теперь возвращается зафиксированный факт, а не предположение.
     */
    public static String activeMeshEngineLabel() {
        OcctNative.ensureLoaded();
        if (OcctNative.lastMeshBuildUsedOcct()) {
            return "OCCT";
        }
        if (!OcctNative.isAvailable()) {
            return I18n.text("mesh.016");
        }
        return OcctNative.PREFER_OCCT_MESH ? I18n.text("mesh.017") : "JavaFX";
    }

    private static TriangleMesh buildRevolvedTriangleMesh(List<double[]> meshProfile) {
        if (meshProfile == null || meshProfile.size() < 2) {
            return null;
        }
        OcctNative.markMeshBuildUsedOcct(false);
        if (OcctNative.PREFER_OCCT_MESH && OcctNative.isAvailable()) {
            TriangleMesh occt = OcctLatheMesher.buildRevolvedSolid(meshProfile);
            if (occt != null) {
                OcctNative.markMeshBuildUsedOcct(true);
                return LatheMeshNormals.apply(occt, meshProfile);
            }
            String reason = OcctNative.meshErrorMessage();
            if (reason == null || reason.isBlank()) {
                reason = OcctNative.loadErrorMessage();
            }
            System.err.println("OCCT mesh fallback to JavaFX: " + reason);
        }
        if (AxialStockSection.isSection(meshProfile)) {
            throw new IllegalArgumentException(I18n.format("mesh.error.occt", OcctNative.meshErrorMessage()));
        }
        return LatheMeshNormals.apply(revolveSolidLathe(meshProfile, SLICES), meshProfile);
    }

    private static TriangleMesh buildRevolveMesh(List<double[]> meshProfile, boolean halfSection) {
        if (!halfSection) {
            return buildRevolvedTriangleMesh(meshProfile);
        }
        // Половинный разрез строится средствами JavaFX — отмечаем, иначе метка
        // движка осталась бы от предыдущего построения через OCCT.
        OcctNative.markMeshBuildUsedOcct(false);
        try {
            return LatheMeshNormals.apply(revolveHalfSolidLathe(meshProfile, SLICES), meshProfile);
        } catch (RuntimeException exception) {
            return LatheMeshNormals.apply(revolveSolidLathe(meshProfile, SLICES), meshProfile);
        }
    }

    public static Group buildSolidPartGroup(List<double[]> profile, Color diffuseColor) {
        Group group = new Group();
        if (profile == null || profile.size() < 2) {
            return group;
        }
        PhongMaterial material = new PhongMaterial(diffuseColor);
        material.setSpecularColor(Color.web("#48545f"));
        material.setSpecularPower(30.0);
        for (int i = 0; i + 1 < profile.size(); i++) {
            double z0 = profile.get(i)[0];
            double z1 = profile.get(i + 1)[0];
            double r0 = Math.max(0.05, profile.get(i)[1]);
            double r1 = Math.max(0.05, profile.get(i + 1)[1]);
            double height = Math.abs(z1 - z0);
            if (height < 0.02) {
                continue;
            }
            boolean capBottom = i == 0;
            boolean capTop = i + 1 == profile.size() - 1;
            MeshView segment = new MeshView(buildFrustumMesh(r0, r1, z0, z1, 40, capBottom, capTop));
            segment.setMaterial(material);
            segment.setCullFace(CullFace.BACK);
            segment.setDrawMode(DrawMode.FILL);
            group.getChildren().add(segment);
        }
        return group;
    }

    public static double resolveStockRadiusMm(SimulationContext context) {
        return resolveStockRadius(context);
    }

    /**
     * Display profile: smoothed and clipped to WORKPIECE, with full blank ends preserved.
     */
    public static List<double[]> prepareDisplayProfile(
            SimulationContext context,
            List<double[]> finishedProfile,
            double stockRadius
    ) {
        if (AxialStockSection.isSection(finishedProfile)) return finishedProfile;
        if (finishedProfile == null || finishedProfile.size() < 2) {
            return finishedProfile;
        }
        List<double[]> profile = mergeProfileByZ(finishedProfile);
        profile = trimToWorkpieceBounds(context, profile);
        return simplifyProfile(profile, MESH_PROFILE_TOLERANCE_MM);
    }

    /** Цельный цилиндр заготовки (до снятия). */
    public static javafx.scene.shape.Cylinder buildStockCylinder(SimulationContext context) {
        WorkpieceDefinition workpiece = context != null ? context.getWorkpiece() : null;
        double stockR = resolveStockRadius(context);
        if (stockR <= 0.0) {
            stockR = 50.0;
        }
        double zMin = -50.0;
        double zMax = 50.0;
        if (workpiece != null && workpiece.isValid()) {
            zMin = workpiece.getZMin();
            zMax = workpiece.getZMax();
        }
        double height = Math.max(10.0, zMax - zMin);
        javafx.scene.shape.Cylinder cylinder = new javafx.scene.shape.Cylinder(stockR, height);
        cylinder.setTranslateY((zMin + zMax) / 2.0);
        return cylinder;
    }

    static List<double[]> simplifyPlateauProfile(List<double[]> profile) {
        if (profile.size() < 3) {
            return profile;
        }
        ArrayList<double[]> out = new ArrayList<>();
        out.add(profile.get(0));
        for (int i = 1; i < profile.size() - 1; i++) {
            double[] prev = out.get(out.size() - 1);
            double[] cur = profile.get(i);
            double[] next = profile.get(i + 1);
            boolean flatR = Math.abs(cur[1] - prev[1]) < 0.05 && Math.abs(cur[1] - next[1]) < 0.05;
            boolean tinyStep = Math.abs(cur[0] - prev[0]) < 0.5 && flatR;
            if (!tinyStep) {
                out.add(cur);
            }
        }
        out.add(profile.get(profile.size() - 1));
        return out;
    }

    private static void applyCutSegment(
            TreeMap<Double, Double> radiusByZ,
            LatheCuttingEnvelope envelope,
            GCodeMoveData move,
            double stockRadius
    ) {
        double z1 = move.startZ();
        double z2 = move.endZ();
        double span = Math.max(0.001, Math.abs(z2 - z1));
        double step = move.arcSegment() ? 0.06 : Z_STEP_MM;
        int steps = Math.max(1, (int) Math.ceil(span / step));
        double r1 = envelope.removalRadiusAt(move.sourceLine(), move.startX(), stockRadius);
        double r2 = envelope.removalRadiusAt(move.sourceLine(), move.endX(), stockRadius);
        double radialSpan = Math.abs(r2 - r1);
        if (radialSpan > 0.02) {
            double radialStep = move.arcSegment() ? 0.12 : 0.16;
            steps = Math.max(steps, (int) Math.ceil(Math.hypot(span, radialSpan) / radialStep));
            if (span < 0.25) {
                steps = Math.max(steps, (int) Math.ceil(radialSpan / 0.08));
            }
            steps = Math.min(1200, steps);
        }
        int line = move.sourceLine();
        double zMinOffset = envelope.zContactMinOffsetMm(line);
        double zMaxOffset = envelope.zContactMaxOffsetMm(line);
        if (Math.abs(z2 - z1) < 0.01 && Math.abs(move.endX() - move.startX()) > 0.02) {
            double radialFootprint = Math.max(0.35, Math.min(1.4, envelope.zContactHalfWidthMm(line)));
            zMinOffset = Math.min(zMinOffset, -radialFootprint);
            zMaxOffset = Math.max(zMaxOffset, radialFootprint);
        }
        double previousMinZ = Double.NaN;
        double previousMaxZ = Double.NaN;
        double previousFinished = Double.NaN;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            double z = z1 + (z2 - z1) * t;
            double x = move.startX() + (move.endX() - move.startX()) * t;
            double finishedRadius = envelope.removalRadiusAt(line, x, stockRadius);
            mergeToolContact(radiusByZ, z, finishedRadius, zMinOffset, zMaxOffset);
            double currentMinZ = z + Math.min(zMinOffset, zMaxOffset);
            double currentMaxZ = z + Math.max(zMinOffset, zMaxOffset);
            if (Double.isFinite(previousMinZ)
                    && Double.isFinite(previousMaxZ)
                    && Double.isFinite(previousFinished)) {
                mergeSweptToolContact(
                        radiusByZ,
                        previousMinZ,
                        previousMaxZ,
                        currentMinZ,
                        currentMaxZ,
                        Math.min(previousFinished, finishedRadius));
            }
            previousMinZ = currentMinZ;
            previousMaxZ = currentMaxZ;
            previousFinished = finishedRadius;
        }
    }

    private static void mergeSweptToolContact(
            TreeMap<Double, Double> radiusByZ,
            double firstMinZ,
            double firstMaxZ,
            double secondMinZ,
            double secondMaxZ,
            double finishedRadius
    ) {
        double zMin = Math.min(Math.min(firstMinZ, firstMaxZ), Math.min(secondMinZ, secondMaxZ));
        double zMax = Math.max(Math.max(firstMinZ, firstMaxZ), Math.max(secondMinZ, secondMaxZ));
        if (!Double.isFinite(zMin) || !Double.isFinite(zMax) || !Double.isFinite(finishedRadius)) {
            return;
        }
        double span = Math.max(0.0, zMax - zMin);
        double step = Math.max(0.04, Math.min(Z_STEP_MM * 0.5, Math.max(0.04, span * 0.12)));
        radiusByZ.merge(zMin, finishedRadius, Math::min);
        for (double z = zMin + step; z < zMax; z += step) {
            radiusByZ.merge(z, finishedRadius, Math::min);
        }
        radiusByZ.merge(zMax, finishedRadius, Math::min);
    }

    private static void mergeToolContact(
            TreeMap<Double, Double> radiusByZ,
            double zCenter,
            double finishedRadius,
            double zMinOffset,
            double zMaxOffset
    ) {
        if (Math.abs(zMinOffset) <= 0.01 && Math.abs(zMaxOffset) <= 0.01) {
            radiusByZ.merge(zCenter, finishedRadius, Math::min);
            return;
        }
        double zMin = zCenter + Math.min(zMinOffset, zMaxOffset);
        double zMax = zCenter + Math.max(zMinOffset, zMaxOffset);
        double span = Math.max(0.0, zMax - zMin);
        double step = Math.max(0.05, Math.min(Z_STEP_MM * 0.5, span * 0.18));
        radiusByZ.merge(zMin, finishedRadius, Math::min);
        for (double z = zMin + step; z < zMax; z += step) {
            radiusByZ.merge(z, finishedRadius, Math::min);
        }
        radiusByZ.merge(zMax, finishedRadius, Math::min);
    }

    /** Гладкий профиль [Z,R] из огибающей — для 3D, не для 2D-ступеней. */
    public static List<double[]> smoothProfileFromTreeMap(TreeMap<Double, Double> radiusByZ, double stockRadius) {
        return toFinishedProfile(radiusByZ, stockRadius);
    }

    private static List<double[]> toFinishedProfile(TreeMap<Double, Double> radiusByZ, double stockRadius) {
        ArrayList<double[]> profile = new ArrayList<>();
        for (var entry : radiusByZ.entrySet()) {
            double radius = entry.getValue();
            if (radius > stockRadius + RADIUS_EPS) {
                continue;
            }
            profile.add(new double[]{entry.getKey(), Math.max(0.0, radius)});
        }
        profile.sort(Comparator.comparingDouble(a -> a[0]));
        return simplifyProfile(profile, 1e-8);
    }

    /** Есть ли на профиле участки уже меньше радиуса заготовки. */
    public static boolean profileHasMaterialRemoval(List<double[]> profile, double stockRadius) {
        if (profile == null || stockRadius <= 0.0) {
            return false;
        }
        for (double[] point : profile) {
            if (point[1] < stockRadius - RADIUS_EPS) {
                return true;
            }
        }
        return false;
    }

    private static double maxDiameter(List<double[]> profile) {
        double maxR = 0.0;
        for (double[] point : profile) {
            maxR = Math.max(maxR, point[1]);
        }
        return maxR * 2.0;
    }

    /** Ступени 90° (Z, затем R) — как контур детали в SinuTrain (вид «Срез»). */
    public static List<double[]> profileForSideView(List<double[]> profile) {
        if (AxialStockSection.isSection(profile)) return profile;
        if (profile == null || profile.size() < 2) {
            return profile == null ? List.of() : profile;
        }
        return expandOrthogonalSteps(profile);
    }

    /** Ступени 90° (Z, затем R) — как контур детали в SinuTrain. */
    private static List<double[]> expandOrthogonalSteps(List<double[]> profile) {
        if (profile.size() < 2) {
            return profile;
        }
        ArrayList<double[]> out = new ArrayList<>();
        out.add(new double[]{profile.get(0)[0], profile.get(0)[1]});
        for (int i = 1; i < profile.size(); i++) {
            double zB = profile.get(i)[0];
            double rB = profile.get(i)[1];
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
        if (out.size() < 2) {
            return profile;
        }
        ArrayList<double[]> merged = new ArrayList<>();
        merged.add(out.get(0));
        for (int i = 1; i < out.size(); i++) {
            double[] cur = out.get(i);
            double[] prev = merged.get(merged.size() - 1);
            if (Math.abs(cur[1] - prev[1]) < RADIUS_EPS) {
                merged.set(merged.size() - 1, new double[]{cur[0], cur[1]});
            } else {
                merged.add(cur);
            }
        }
        return merged;
    }

    static List<double[]> applyCupConstraint(SimulationContext context, List<double[]> profile) {
        if (profile.size() < 2 || context == null) {
            return profile;
        }
        MachineConfiguration machine = context.getMachineConfiguration();
        if (machine == null || !machine.isUseCupRadius() || machine.getCupRadiusMm() <= 0.0) {
            return profile;
        }
        double cupR = machine.getCupRadiusMm();
        double zMin = profile.get(0)[0];
        double zMax = profile.get(profile.size() - 1)[0];
        WorkpieceDefinition workpiece = context.getWorkpiece();
        if (workpiece != null && workpiece.isValid()) {
            zMin = workpiece.getZMin();
            zMax = workpiece.getZMax();
        }
        double span = Math.max(1.0, zMax - zMin);
        double zBand = Math.min(60.0, Math.max(8.0, span * 0.06));
        double chuckZ = zMin;
        ArrayList<double[]> out = new ArrayList<>(profile.size());
        for (double[] point : profile) {
            double r = point[1];
            if (point[0] <= chuckZ + zBand) {
                r = Math.max(r, cupR);
            }
            out.add(new double[]{point[0], r});
        }
        if (!out.isEmpty() && out.get(0)[0] > chuckZ + 0.01) {
            out.add(0, new double[]{chuckZ, Math.max(out.get(0)[1], cupR)});
        }
        return out;
    }

    private static List<double[]> mergeProfile(List<double[]> points) {
        ArrayList<double[]> merged = new ArrayList<>();
        for (double[] point : points) {
            if (merged.isEmpty()) {
                merged.add(new double[]{point[0], point[1]});
                continue;
            }
            double[] last = merged.get(merged.size() - 1);
            if (Math.abs(last[0] - point[0]) < 0.001) {
                last[1] = Math.min(last[1], point[1]);
                continue;
            }
            if (Math.abs(last[1] - point[1]) < RADIUS_EPS
                    && Math.abs(last[0] - point[0]) < Z_STEP_MM * 1.5) {
                continue;
            }
            merged.add(new double[]{point[0], point[1]});
        }
        return merged;
    }

    /** Remove redundant points without moving shoulders or inventing axial bins.
     * The bound is radial error in mm, independent of the overall part length. */
    private static List<double[]> simplifyProfile(List<double[]> profile, double tolerance) {
        if (profile.size() < 3) {
            return profile;
        }
        boolean[] keep = new boolean[profile.size()];
        keep[0] = keep[keep.length - 1] = true;
        var pending = new java.util.ArrayDeque<int[]>();
        pending.push(new int[]{0, keep.length - 1});
        while (!pending.isEmpty()) {
            int[] range = pending.pop();
            double[] a = profile.get(range[0]), b = profile.get(range[1]);
            double span = b[0] - a[0];
            double worst = tolerance;
            int split = -1;
            for (int i = range[0] + 1; i < range[1]; i++) {
                double[] p = profile.get(i);
                double error = Math.abs(span) > 1e-12
                        ? Math.abs(p[1] - (a[1] + (b[1] - a[1]) * (p[0] - a[0]) / span))
                        : Math.abs(p[0] - a[0]);
                if (error > worst) { worst = error; split = i; }
            }
            if (split >= 0) {
                keep[split] = true;
                pending.push(new int[]{range[0], split});
                pending.push(new int[]{split, range[1]});
            }
        }
        ArrayList<double[]> reduced = new ArrayList<>();
        for (int i = 0; i < keep.length; i++) if (keep[i]) reduced.add(profile.get(i));
        return reduced;
    }

    private static void validateMesh(TriangleMesh mesh) {
        if (mesh == null || mesh.getPoints().size() < 9) {
            throw new IllegalArgumentException(I18n.text("mesh.error.empty"));
        }
        if (mesh.getFaces().size() < 6) {
            throw new IllegalArgumentException(I18n.text("mesh.error.nofaces"));
        }
        for (int i = 0; i < mesh.getPoints().size(); i++) {
            float value = mesh.getPoints().get(i);
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException(I18n.text("mesh.error.vertex"));
            }
        }
    }

    /**
     * Цельный цилиндр заготовки по Z, затем min(R) после снятия — один R на каждый Z.
     */
    static List<double[]> densifyStockCylinderProfile(
            SimulationContext context,
            List<double[]> finishedProfile,
            double stockRadius
    ) {
        double zMin = Double.POSITIVE_INFINITY;
        double zMax = Double.NEGATIVE_INFINITY;
        WorkpieceDefinition workpiece = context.getWorkpiece();
        if (workpiece != null && workpiece.isValid()) {
            zMin = workpiece.getZMin();
            zMax = workpiece.getZMax();
        }
        TreeMap<Double, Double> radiusByZ = new TreeMap<>();
        for (double[] point : finishedProfile) {
            zMin = Math.min(zMin, point[0]);
            zMax = Math.max(zMax, point[0]);
            radiusByZ.put(point[0], point[1]);
        }
        if (!Double.isFinite(zMin)) {
            return finishedProfile;
        }
        double pad = Math.max(0.5, (zMax - zMin) * 0.002);
        zMin -= pad;
        zMax += pad;
        double step = Math.max(2.0, Math.min(5.0, (zMax - zMin) / 120.0));
        for (double z = zMin; z <= zMax + step * 0.5; z += step) {
            double cutR = stockRadius;
            var floor = radiusByZ.floorEntry(z);
            var ceil = radiusByZ.ceilingEntry(z);
            if (floor != null && ceil != null && ceil.getKey() > floor.getKey() + 1.0e-6) {
                double t = (z - floor.getKey()) / (ceil.getKey() - floor.getKey());
                cutR = floor.getValue() + (ceil.getValue() - floor.getValue()) * t;
            } else if (floor != null) {
                cutR = floor.getValue();
            } else if (ceil != null) {
                cutR = ceil.getValue();
            }
            radiusByZ.put(z, Math.max(0.05, Math.min(stockRadius, cutR)));
        }
        ArrayList<double[]> out = new ArrayList<>();
        for (var entry : radiusByZ.entrySet()) {
            out.add(new double[]{entry.getKey(), entry.getValue()});
        }
        return out;
    }

    public static List<double[]> trimToCuttingZRange(
            SimulationContext context,
            List<double[]> profile,
            double stockRadius
    ) {
        if (context == null || profile.size() < 2) {
            return profile;
        }
        double zMinCut = Double.POSITIVE_INFINITY;
        double zMaxCut = Double.NEGATIVE_INFINITY;
        double stockBand = stockRadius * 0.985;
        LatheCuttingEnvelope envelope = new LatheCuttingEnvelope(context);
        for (GCodeMoveData move : context.getContourMoves()) {
            if (!isCuttingMove(move)) {
                continue;
            }
            double r1 = envelope.removalRadiusAt(move.sourceLine(), move.startX(), stockRadius);
            double r2 = envelope.removalRadiusAt(move.sourceLine(), move.endX(), stockRadius);
            if (r1 >= stockBand && r2 >= stockBand) {
                continue;
            }
            zMinCut = Math.min(zMinCut, Math.min(move.startZ(), move.endZ()));
            zMaxCut = Math.max(zMaxCut, Math.max(move.startZ(), move.endZ()));
        }
        if (!Double.isFinite(zMinCut)) {
            return profile;
        }
        double pad = Math.max(0.5, (zMaxCut - zMinCut) * 0.01);
        zMinCut -= pad;
        zMaxCut += pad;
        ArrayList<double[]> clipped = new ArrayList<>();
        for (double[] point : profile) {
            if (point[0] >= zMinCut && point[0] <= zMaxCut) {
                clipped.add(point);
            }
        }
        if (clipped.size() < 2) {
            return profile;
        }
        ensureEndpoint(clipped, zMinCut, profile);
        ensureEndpoint(clipped, zMaxCut, profile);
        return clipped;
    }

    static List<double[]> densifyProfileUniform(List<double[]> profile, double stepMm) {
        if (profile == null || profile.size() < 2 || stepMm <= 0.0) {
            return profile;
        }
        double zMin = profile.get(0)[0];
        double zMax = profile.get(profile.size() - 1)[0];
        if (zMax - zMin < stepMm) {
            return profile;
        }
        ArrayList<double[]> out = new ArrayList<>();
        for (double z = zMin; z <= zMax + stepMm * 0.5; z += stepMm) {
            out.add(new double[]{z, interpolateRadiusAtZ(profile, z)});
        }
        double[] last = profile.get(profile.size() - 1);
        if (out.isEmpty() || Math.abs(out.get(out.size() - 1)[0] - last[0]) > 0.05) {
            out.add(last);
        }
        return out;
    }

    public static List<double[]> trimToWorkpieceBounds(SimulationContext context, List<double[]> profile) {
        if (context == null || profile.size() < 2) {
            return profile;
        }
        WorkpieceDefinition workpiece = context.getWorkpiece();
        if (workpiece == null || !workpiece.isValid()) {
            return profile;
        }
        double zMin = workpiece.getZMin();
        double zMax = workpiece.getZMax();
        ArrayList<double[]> clipped = new ArrayList<>();
        for (double[] point : profile) {
            if (point[0] >= zMin - 0.02 && point[0] <= zMax + 0.02) {
                clipped.add(point);
            }
        }
        if (clipped.size() < 2) {
            return profile;
        }
        ensureEndpoint(clipped, zMin, profile);
        ensureEndpoint(clipped, zMax, profile);
        return clipped;
    }

    private static void ensureEndpoint(ArrayList<double[]> clipped, double z, List<double[]> source) {
        if (clipped.isEmpty()) {
            return;
        }
        double radius = interpolateRadiusAtZ(source, z);
        if (clipped.get(0)[0] > z + 0.02) {
            clipped.add(0, new double[]{z, radius});
        } else if (Math.abs(clipped.get(0)[0] - z) <= 0.02) {
            clipped.set(0, new double[]{z, Math.min(clipped.get(0)[1], radius)});
        }
        double[] last = clipped.get(clipped.size() - 1);
        if (last[0] < z - 0.02) {
            clipped.add(new double[]{z, radius});
        } else if (Math.abs(last[0] - z) <= 0.02) {
            clipped.set(clipped.size() - 1, new double[]{z, Math.min(last[1], radius)});
        }
    }

    private static double interpolateRadiusAtZ(List<double[]> profile, double z) {
        double radius = profile.get(0)[1];
        for (int i = 1; i < profile.size(); i++) {
            double[] prev = profile.get(i - 1);
            double[] cur = profile.get(i);
            if (z <= cur[0]) {
                double span = cur[0] - prev[0];
                double t = span > 1.0e-6 ? (z - prev[0]) / span : 0.0;
                return prev[1] + (cur[1] - prev[1]) * t;
            }
            radius = cur[1];
        }
        return radius;
    }

    static List<double[]> trimStockOnlyEnds(List<double[]> profile, double stockRadius) {
        if (profile.size() < 3 || stockRadius <= 0.0) {
            return profile;
        }
        int start = 0;
        int end = profile.size() - 1;
        double stockBand = stockRadius * 0.992;
        while (start < end - 1 && profile.get(start)[1] >= stockBand) {
            start++;
        }
        while (end > start + 1 && profile.get(end)[1] >= stockBand) {
            end--;
        }
        return new ArrayList<>(profile.subList(start, end + 1));
    }

    static List<double[]> trimMicroRadiusSpikes(List<double[]> profile, double minRadiusMm) {
        if (profile.size() < 3) {
            return profile;
        }
        ArrayList<double[]> out = new ArrayList<>();
        out.add(profile.get(0));
        for (int i = 1; i < profile.size() - 1; i++) {
            double[] prev = out.get(out.size() - 1);
            double[] cur = profile.get(i);
            double[] next = profile.get(i + 1);
            boolean spike = cur[1] < minRadiusMm
                    && prev[1] > minRadiusMm * 3.0
                    && next[1] > minRadiusMm * 3.0;
            if (!spike) {
                out.add(cur);
            }
        }
        out.add(profile.get(profile.size() - 1));
        return out.size() >= 2 ? out : profile;
    }

    static List<double[]> mergeProfileByZ(List<double[]> profile) {
        var sorted = new ArrayList<>(profile);
        if (sorted.size() > 1 && sorted.get(0)[0] > sorted.get(sorted.size() - 1)[0]) {
            java.util.Collections.reverse(sorted);
        }
        sorted.sort(Comparator.comparingDouble(p -> p[0])); // stable: keep shoulder order
        ArrayList<double[]> out = new ArrayList<>();
        for (double[] p : sorted) {
            if (!out.isEmpty()) {
                double[] last = out.get(out.size() - 1);
                if (Math.abs(last[0] - p[0]) < 1e-10 && Math.abs(last[1] - p[1]) < 1e-10) continue;
            }
            out.add(new double[]{p[0], p[1]});
        }
        return out;
    }

    /**
     * Тело вращения: плотный цилиндр/деталь — боковая поверхность + закрытые торцы (без «веера»).
     */
    /**
     * Полусфера вращения: угол от −90° до +90° в плоскости X–Z, торец на плоскости X=0 (продольный срез).
     */
    private static TriangleMesh revolveHalfSolidLathe(List<double[]> profile, int slices) {
        int rings = profile.size();
        if (rings < 2) {
            throw new IllegalArgumentException(I18n.text("mesh.error.profile"));
        }
        int halfSlices = Math.max(8, slices / 2);
        int pointsPerRing = halfSlices + 1;
        int vertexCount = rings * pointsPerRing;
        float[] points = new float[vertexCount * 3];
        int vertex = 0;
        for (int ring = 0; ring < rings; ring++) {
            double z = profile.get(ring)[0];
            double radius = Math.max(0.05, profile.get(ring)[1]);
            for (int slice = 0; slice <= halfSlices; slice++) {
                double angle = -Math.PI / 2.0 + Math.PI * slice / halfSlices;
                points[vertex * 3] = (float)(radius * Math.cos(angle));
                points[vertex * 3 + 1] = (float)z;
                points[vertex * 3 + 2] = (float)(radius * Math.sin(angle));
                vertex++;
            }
        }
        int sideTriangles = (rings - 1) * halfSlices * 2;
        int cutCapTriangles = (rings - 1) * 2;
        int endCapTriangles = Math.max(0, halfSlices - 1) * 2;
        int[] faces = new int[(sideTriangles + cutCapTriangles + endCapTriangles) * 6];
        int faceIndex = 0;
        for (int ring = 0; ring < rings - 1; ring++) {
            for (int slice = 0; slice < halfSlices; slice++) {
                int a = ring * pointsPerRing + slice;
                int b = a + 1;
                int c = a + pointsPerRing;
                int d = c + 1;
                faceIndex = addQuad(faces, faceIndex, a, c, b);
                faceIndex = addQuad(faces, faceIndex, b, c, d);
            }
        }
        for (int ring = 0; ring < rings - 1; ring++) {
            int a = ring * pointsPerRing;
            int b = (ring + 1) * pointsPerRing;
            int c = b + 1;
            int d = a + 1;
            faceIndex = addTriangle(faces, faceIndex, a, b, c);
            faceIndex = addTriangle(faces, faceIndex, a, c, d);
        }
        int ringStart = 0;
        for (int slice = 1; slice < halfSlices; slice++) {
            faceIndex = addTriangle(faces, faceIndex, ringStart, ringStart + slice, ringStart + slice + 1);
        }
        int ringEnd = (rings - 1) * pointsPerRing;
        for (int slice = 1; slice < halfSlices; slice++) {
            faceIndex = addTriangle(faces, faceIndex, ringEnd, ringEnd + slice + 1, ringEnd + slice);
        }
        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().setAll(points);
        mesh.getFaces().setAll(faces);
        mesh.getTexCoords().addAll(0f, 0f);
        return mesh;
    }

    private static TriangleMesh revolveSolidLathe(List<double[]> profile, int slices) {
        int rings = profile.size();
        if (rings < 2) {
            throw new IllegalArgumentException(I18n.text("mesh.error.profile"));
        }
        int pointsPerRing = slices + 1;
        int vertexCount = rings * pointsPerRing;
        float[] points = new float[vertexCount * 3];
        int vertex = 0;
        for (int ring = 0; ring < rings; ring++) {
            double z = profile.get(ring)[0];
            double radius = Math.max(0.05, profile.get(ring)[1]);
            for (int slice = 0; slice <= slices; slice++) {
                double angle = Math.PI * 2.0 * slice / slices;
                points[vertex * 3] = (float)(radius * Math.cos(angle));
                points[vertex * 3 + 1] = (float)z;
                points[vertex * 3 + 2] = (float)(radius * Math.sin(angle));
                vertex++;
            }
        }

        int sideFaceCount = (rings - 1) * slices * 2;
        int capFaceCount = Math.max(0, slices - 1) * 2;
        int[] faces = new int[(sideFaceCount + capFaceCount) * 6];
        int faceIndex = 0;
        for (int ring = 0; ring < rings - 1; ring++) {
            for (int slice = 0; slice < slices; slice++) {
                int a = ring * pointsPerRing + slice;
                int b = a + 1;
                int c = a + pointsPerRing;
                int d = b + pointsPerRing;
                faceIndex = addQuad(faces, faceIndex, a, c, b);
                faceIndex = addQuad(faces, faceIndex, b, c, d);
            }
        }
        int ringStart = 0;
        for (int slice = 1; slice < slices; slice++) {
            faceIndex = addTriangle(faces, faceIndex, ringStart, ringStart + slice, ringStart + slice + 1);
        }
        int ringEnd = (rings - 1) * pointsPerRing;
        for (int slice = 1; slice < slices; slice++) {
            faceIndex = addTriangle(faces, faceIndex, ringEnd, ringEnd + slice + 1, ringEnd + slice);
        }
        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().setAll(points);
        mesh.getFaces().setAll(faces);
        mesh.getTexCoords().addAll(0f, 0f);
        return mesh;
    }

    private static int addTriangle(int[] faces, int index, int p0, int p1, int p2) {
        faces[index++] = p0;
        faces[index++] = 0;
        faces[index++] = p1;
        faces[index++] = 0;
        faces[index++] = p2;
        faces[index++] = 0;
        return index;
    }

    private static int addQuad(int[] faces, int index, int p0, int p1, int p2) {
        faces[index++] = p0;
        faces[index++] = 0;
        faces[index++] = p1;
        faces[index++] = 0;
        faces[index++] = p2;
        faces[index++] = 0;
        return index;
    }

    private static TriangleMesh buildFrustumMesh(
            double r0,
            double r1,
            double z0,
            double z1,
            int slices,
            boolean capBottom,
            boolean capTop
    ) {
        double zBottom = Math.min(z0, z1);
        double zTop = Math.max(z0, z1);
        double bottomR = z0 <= z1 ? r0 : r1;
        double topR = z0 <= z1 ? r1 : r0;
        int pointsPerRing = slices + 1;
        int ringVerts = pointsPerRing * 2;
        int vertexCount = ringVerts + 2;
        float[] points = new float[vertexCount * 3];
        int vertex = 0;
        for (int ring = 0; ring < 2; ring++) {
            double z = ring == 0 ? zBottom : zTop;
            double radius = ring == 0 ? bottomR : topR;
            for (int slice = 0; slice <= slices; slice++) {
                double angle = Math.PI * 2.0 * slice / slices;
                points[vertex * 3] = (float)(radius * Math.cos(angle));
                points[vertex * 3 + 1] = (float)z;
                points[vertex * 3 + 2] = (float)(radius * Math.sin(angle));
                vertex++;
            }
        }
        int bottomCenter = vertex;
        points[vertex * 3] = 0f;
        points[vertex * 3 + 1] = (float)zBottom;
        points[vertex * 3 + 2] = 0f;
        vertex++;
        int topCenter = vertex;
        points[vertex * 3] = 0f;
        points[vertex * 3 + 1] = (float)zTop;
        points[vertex * 3 + 2] = 0f;

        int sideFaces = slices * 2;
        int capFaces = 0;
        if (capBottom && bottomR > 0.05) {
            capFaces += slices;
        }
        if (capTop && topR > 0.05) {
            capFaces += slices;
        }
        int[] faces = new int[(sideFaces + capFaces) * 6];
        int faceIndex = 0;
        for (int slice = 0; slice < slices; slice++) {
            int b0 = slice;
            int b1 = slice + 1;
            int t0 = pointsPerRing + slice;
            int t1 = t0 + 1;
            faceIndex = addTriangle(faces, faceIndex, b0, t0, b1);
            faceIndex = addTriangle(faces, faceIndex, b1, t0, t1);
        }
        if (capBottom && bottomR > 0.05) {
            for (int slice = 0; slice < slices; slice++) {
                int a = slice;
                int b = slice + 1;
                faceIndex = addTriangle(faces, faceIndex, bottomCenter, b, a);
            }
        }
        if (capTop && topR > 0.05) {
            for (int slice = 0; slice < slices; slice++) {
                int c = pointsPerRing + slice;
                int d = c + 1;
                faceIndex = addTriangle(faces, faceIndex, topCenter, c, d);
            }
        }
        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().setAll(points);
        mesh.getFaces().setAll(faces);
        mesh.getTexCoords().addAll(0f, 0f);
        return mesh;
    }
}
