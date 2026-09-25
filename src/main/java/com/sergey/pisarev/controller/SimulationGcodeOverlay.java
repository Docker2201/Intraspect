package com.sergey.pisarev.controller;

import com.sergey.pisarev.model.GCodeMoveData;
import com.sergey.pisarev.model.SimulationContext;
import com.sergey.pisarev.service.LatheMeshBuilder;
import com.sergey.pisarev.util.I18n;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.SubScene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Траектория поверх 3D: G00 — красный пунктир, подача — зелёная, как на 2D-графике.
 * Хранит МИРОВЫЕ координаты (radius, z), проецирует в screen КАЖДЫЙ КАДР в paint().
 * Это решает дрифт при зуме/орбите — координаты не кэшируются в screen-space.
 */
final class SimulationGcodeOverlay {
    private static final double PICK_RADIUS_PX = 10.0;
    private static final double DOT_RADIUS_PX = 2.8;
    private static final double HOVER_RADIUS_PX = 4.8;
    private static final double SELECT_RADIUS_PX = 5.2;
    private static final double PROJECTION_HARD_LIMIT_PX = 50_000_000.0;

    private final Canvas canvas;
    /** World-координаты точек для кликов/хакра */
    private final List<WorldPoint> worldPoints = new ArrayList<>();
    /** World-координаты сегментов для линий */
    private final List<WorldSegment> worldSegments = new ArrayList<>();
    /** Кэш screen-точек, обновляется при каждом paint() — для pick/hover */
    private final List<ScreenPoint> screenPointCache = new ArrayList<>();
    private final Set<Integer> selectedIndices = new LinkedHashSet<>();
    private GCodeMoveData hoveredMove;
    private int hoveredIndex = -1;
    private Group projectionRoot;
    private SubScene subScene;
    private LatheOrbitCamera camera;
    private boolean drawTrajectory;
    private boolean darkTheme;
    private boolean flatProjection;
    private SideProjection currentSideProjection;

    SimulationGcodeOverlay() {
        this.canvas = new Canvas(800, 600);
        this.canvas.setMouseTransparent(true);
    }

    Canvas getCanvas() {
        return this.canvas;
    }

    void bindSize(SubScene subScene) {
        this.canvas.widthProperty().bind(subScene.widthProperty());
        this.canvas.heightProperty().bind(subScene.heightProperty());
    }

    void rebuild(
            List<GCodeMoveData> moves,
            boolean drawTrajectory,
            Group projectionRoot,
            SimulationContext context,
            SubScene subScene,
            LatheOrbitCamera camera,
            boolean darkTheme
    ) {
        this.worldPoints.clear();
        this.worldSegments.clear();
        this.screenPointCache.clear();
        this.selectedIndices.clear();
        this.hoveredMove = null;
        this.hoveredIndex = -1;
        if (moves == null || moves.isEmpty() || projectionRoot == null || subScene == null || camera == null) {
            this.clear();
            return;
        }
        this.projectionRoot = projectionRoot;
        this.subScene = subScene;
        this.camera = camera;
        this.drawTrajectory = drawTrajectory;
        this.darkTheme = darkTheme;

        int index = 0;
        for (GCodeMoveData move : moves) {
            double startR = toolPathRadius(move.startX());
            double endR = toolPathRadius(move.endX());
            double startZ = move.startZ();
            double endZ = move.endZ();

            // Сохраняем мировые координаты — проекция будет в paint()
            if (drawTrajectory) {
                this.worldSegments.add(new WorldSegment(startR, startZ, endR, endZ, segmentStyle(move)));
            }
            if (index == 0) {
                this.worldPoints.add(new WorldPoint(startR, startZ, move, true, move.rapid()));
            }
            if (!move.arcSegment() || move.arcEnd()) {
                this.worldPoints.add(new WorldPoint(endR, endZ, move, false, move.rapid()));
            }
            index++;
        }
        this.paint(darkTheme, -1, drawTrajectory);
    }

    /** Перерисовать overlay с текущими world-данными (вызывается при изменении камеры) */
    void refresh(boolean darkTheme, boolean drawTrajectory) {
        this.darkTheme = darkTheme;
        this.drawTrajectory = drawTrajectory;
        this.paint(darkTheme, this.hoveredIndex, drawTrajectory);
    }

    void setFlatProjection(boolean flatProjection) {
        this.flatProjection = flatProjection;
    }

    private static double toolPathRadius(double xDiameter) {
        return Math.max(0.0, LatheMeshBuilder.toRadius(xDiameter));
    }

    private static SegmentStyle segmentStyle(GCodeMoveData move) {
        if (move.arcSegment()) {
            return SegmentStyle.ARC;
        }
        if (move.rapid()) {
            return SegmentStyle.RAPID;
        }
        return SegmentStyle.FEED;
    }

    GCodeMoveData findPointAt(double mouseX, double mouseY) {
        GCodeMoveData best = null;
        double bestDist = PICK_RADIUS_PX;
        for (int i = 0; i < this.screenPointCache.size(); i++) {
            ScreenPoint point = this.screenPointCache.get(i);
            double dist = Math.hypot(mouseX - point.x, mouseY - point.y);
            if (dist <= bestDist) {
                bestDist = dist;
                best = point.move;
            }
        }
        return best;
    }

    void selectSingle(GCodeMoveData move, boolean darkTheme, boolean drawTrajectory) {
        this.selectedIndices.clear();
        if (move != null) {
            for (int i = 0; i < this.screenPointCache.size(); i++) {
                if (this.screenPointCache.get(i).move == move) {
                    this.selectedIndices.add(i);
                    break;
                }
            }
        }
        this.paint(darkTheme, -1, drawTrajectory);
    }

    GCodeMoveData pick(double mouseX, double mouseY, boolean extendSelection, boolean darkTheme, boolean drawTrajectory) {
        GCodeMoveData best = null;
        double bestDist = PICK_RADIUS_PX;
        int bestIndex = -1;
        for (int i = 0; i < this.screenPointCache.size(); i++) {
            ScreenPoint point = this.screenPointCache.get(i);
            double dist = Math.hypot(mouseX - point.x, mouseY - point.y);
            if (dist <= bestDist) {
                bestDist = dist;
                best = point.move;
                bestIndex = i;
            }
        }
        if (bestIndex >= 0) {
            if (this.selectedIndices.contains(bestIndex)) {
                this.selectedIndices.remove(bestIndex);
            } else {
                if (!extendSelection) {
                    this.selectedIndices.clear();
                }
                this.selectedIndices.add(bestIndex);
            }
        } else if (!extendSelection) {
            this.selectedIndices.clear();
        }
        this.hoveredIndex = bestIndex;
        this.hoveredMove = best;
        this.paint(darkTheme, bestIndex, drawTrajectory);
        return best;
    }

    void clearSelection(boolean darkTheme, boolean drawTrajectory) {
        this.selectedIndices.clear();
        this.paint(darkTheme, this.hoveredIndex, drawTrajectory);
    }

    List<GCodeMoveData> getSelectedMoves() {
        ArrayList<GCodeMoveData> selected = new ArrayList<>();
        for (int index : this.selectedIndices) {
            if (index >= 0 && index < this.screenPointCache.size()) {
                selected.add(this.screenPointCache.get(index).move);
            }
        }
        return selected;
    }

    void hover(double mouseX, double mouseY, boolean darkTheme, boolean drawTrajectory) {
        int index = -1;
        double bestDist = HOVER_RADIUS_PX;
        for (int i = 0; i < this.screenPointCache.size(); i++) {
            ScreenPoint point = this.screenPointCache.get(i);
            double dist = Math.hypot(mouseX - point.x, mouseY - point.y);
            if (dist <= bestDist) {
                bestDist = dist;
                index = i;
            }
        }
        if (index == this.hoveredIndex) {
            return;
        }
        this.hoveredIndex = index;
        this.hoveredMove = index >= 0 ? this.screenPointCache.get(index).move : null;
        this.paint(darkTheme, index, drawTrajectory);
    }

    GCodeMoveData getHoveredMove() {
        return this.hoveredMove;
    }

    void setHoveredMove(GCodeMoveData move) {
        this.hoveredMove = move;
        this.hoveredIndex = -1;
    }

    void clear() {
        this.screenPointCache.clear();
        this.selectedIndices.clear();
        this.hoveredMove = null;
        this.hoveredIndex = -1;
        double w = Math.max(1.0, this.canvas.getWidth());
        double h = Math.max(1.0, this.canvas.getHeight());
        this.canvas.getGraphicsContext2D().clearRect(0, 0, w, h);
    }

    /**
     * Проецирует 3D-точку из world в локальные координаты Canvas через localToScreen.
     * Вызывается на каждый кадр из paint() — нет кэша, нет дрифта.
     */
    private Point2D worldToCanvas(double radiusMm, double zProgram) {
        return this.worldToCanvas(radiusMm, zProgram, false);
    }

    private Point2D worldToCanvas(double radiusMm, double zProgram, boolean allowOffscreen) {
        if (this.projectionRoot == null || this.subScene == null || this.camera == null) {
            return null;
        }
        if (this.flatProjection && this.currentSideProjection != null) {
            Point2D projected = this.currentSideProjection.project(radiusMm, zProgram);
            return (allowOffscreen ? this.isFiniteProjection(projected) : this.isVisibleOrNearProjection(projected))
                    ? projected
                    : null;
        }
        if (this.flatProjection) {
            try {
                Point2D projected = this.camera.project(this.subScene, this.projectionRoot, radiusMm, zProgram);
                return (allowOffscreen ? this.isFiniteProjection(projected) : this.isVisibleOrNearProjection(projected))
                        ? projected
                        : null;
            } catch (Exception ignored) {
                return null;
            }
        }
        Point2D manualProjection = null;
        try {
            manualProjection = this.camera.projectToSubScene(
                    this.subScene,
                    this.projectionRoot,
                    radiusMm,
                    zProgram,
                    0.0);
        } catch (Exception ignored) {
            // Fallback below keeps the trajectory visible when the camera matrix is temporarily unavailable.
        }
        if (this.isFiniteProjection(manualProjection)) {
            return manualProjection;
        }

        Point2D localProjection = null;
        try {
            localProjection = this.camera.project(this.subScene, this.projectionRoot, radiusMm, zProgram);
        } catch (Exception ignored) {
            return null;
        }
        if (this.isVisibleOrNearProjection(localProjection)) {
            return localProjection;
        }
        return null;
    }

    private SideProjection computeSideProjection() {
        if (!this.flatProjection || this.camera == null || this.subScene == null || this.projectionRoot == null) {
            return null;
        }
        double refZ = this.sideProjectionReferenceZ();
        try {
            Point2D origin = this.camera.project(this.subScene, this.projectionRoot, 0.0, refZ);
            Point2D zProbe = this.camera.project(this.subScene, this.projectionRoot, 0.0, refZ + 1.0);
            Point2D rProbe = this.camera.project(this.subScene, this.projectionRoot, 1.0, refZ);
            if (!this.isFiniteProjection(origin)
                    || !this.isFiniteProjection(zProbe)
                    || !this.isFiniteProjection(rProbe)) {
                return null;
            }
            return new SideProjection(
                    origin.getX(),
                    origin.getY(),
                    zProbe.getX() - origin.getX(),
                    zProbe.getY() - origin.getY(),
                    rProbe.getX() - origin.getX(),
                    rProbe.getY() - origin.getY(),
                    refZ);
        } catch (Exception ignored) {
            return null;
        }
    }

    private double sideProjectionReferenceZ() {
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (WorldSegment segment : this.worldSegments) {
            minZ = Math.min(minZ, Math.min(segment.startZ, segment.endZ));
            maxZ = Math.max(maxZ, Math.max(segment.startZ, segment.endZ));
        }
        if (!Double.isFinite(minZ)) {
            for (WorldPoint point : this.worldPoints) {
                minZ = Math.min(minZ, point.zProgram);
                maxZ = Math.max(maxZ, point.zProgram);
            }
        }
        if (!Double.isFinite(minZ)) {
            return 0.0;
        }
        if (minZ <= 0.0 && maxZ >= 0.0) {
            return 0.0;
        }
        return (minZ + maxZ) * 0.5;
    }

    private boolean isFiniteProjection(Point2D projected) {
        if (projected == null
                || !Double.isFinite(projected.getX())
                || !Double.isFinite(projected.getY())) {
            return false;
        }
        return Math.abs(projected.getX()) <= PROJECTION_HARD_LIMIT_PX
                && Math.abs(projected.getY()) <= PROJECTION_HARD_LIMIT_PX;
    }

    private boolean isVisibleOrNearProjection(Point2D projected) {
        if (!this.isFiniteProjection(projected)) {
            return false;
        }
        double w = Math.max(1.0, this.canvas.getWidth());
        double h = Math.max(1.0, this.canvas.getHeight());
        double marginX = Math.max(240.0, w * 0.75);
        double marginY = Math.max(240.0, h * 0.75);
        return projected.getX() >= -marginX
                && projected.getX() <= w + marginX
                && projected.getY() >= -marginY
                && projected.getY() <= h + marginY;
    }

    private void paint(boolean darkTheme, int highlightIndex, boolean drawTrajectory) {
        double w = Math.max(1.0, this.canvas.getWidth());
        double h = Math.max(1.0, this.canvas.getHeight());
        GraphicsContext gc = this.canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);
        this.screenPointCache.clear();
        this.currentSideProjection = this.computeSideProjection();

        if (!drawTrajectory || this.worldSegments.isEmpty()) {
            this.currentSideProjection = null;
            return;
        }

        // Проецируем сегменты (линии) динамически — каждый кадр
        for (WorldSegment seg : this.worldSegments) {
            Point2D start = worldToCanvas(seg.startR, seg.startZ, true);
            Point2D end = worldToCanvas(seg.endR, seg.endZ, true);
            if (start == null || end == null) continue;
            double[] clipped = this.clipSegmentToViewport(start, end, w, h);
            if (clipped == null) {
                continue;
            }
            applySegmentBackingStyle(gc, seg.style, darkTheme);
            gc.strokeLine(clipped[0], clipped[1], clipped[2], clipped[3]);
            applySegmentStyle(gc, seg.style);
            gc.strokeLine(clipped[0], clipped[1], clipped[2], clipped[3]);
        }

        gc.setLineDashes(null);

        // Проецируем точки динамически + строим кэш для pick/hover
        for (int wi = 0; wi < this.worldPoints.size(); wi++) {
            WorldPoint wp = this.worldPoints.get(wi);
            Point2D pos = worldToCanvas(wp.radiusMm, wp.zProgram);
            if (pos == null || !Double.isFinite(pos.getX()) || !Double.isFinite(pos.getY())) {
                this.screenPointCache.add(new ScreenPoint(-9999, -9999, wp.move, wp.isStart, wp.rapid));
                continue;
            }
            double marginX = Math.max(80.0, w * 0.18);
            double marginY = Math.max(80.0, h * 0.18);
            double px = pos.getX();
            double py = pos.getY();
            this.screenPointCache.add(new ScreenPoint(px, py, wp.move, wp.isStart, wp.rapid));

            if (px >= -marginX && px <= w + marginX && py >= -marginY && py <= h + marginY) {
                boolean highlight = wi == highlightIndex;
                boolean selected = this.selectedIndices.contains(wi);
                double r = highlight ? HOVER_RADIUS_PX : (selected ? SELECT_RADIUS_PX : DOT_RADIUS_PX);
                gc.setFill(colorFor(wp, highlight, selected));
                gc.fillOval(px - r, py - r, r * 2.0, r * 2.0);
                if (selected) {
                    gc.setStroke(Color.web("#f97316"));
                    gc.setLineWidth(2.2);
                    gc.strokeOval(px - r, py - r, r * 2.0, r * 2.0);
                } else if (highlight) {
                    gc.setStroke(darkTheme ? Color.web("#f8fafc", 0.92) : Color.web("#ffffff", 0.95));
                    gc.setLineWidth(1.8);
                    gc.strokeOval(px - r, py - r, r * 2.0, r * 2.0);
                } else {
                    // Light centres remain visible on the dark viewport; the
                    // dark outline separates them from the bright metal too.
                    gc.setStroke(darkTheme ? Color.web("#0f172a") : Color.WHITE);
                    gc.setLineWidth(1.1);
                    gc.strokeOval(px - r, py - r, r * 2.0, r * 2.0);
                }
            }
        }
        this.currentSideProjection = null;
    }

    private static void applySegmentStyle(GraphicsContext gc, SegmentStyle style) {
        switch (style) {
            case RAPID -> {
                gc.setStroke(Color.web(MainController.SINUMERIK_RAPID_COLOR, 0.96));
                gc.setLineWidth(1.5);
                gc.setLineDashes(8.0, 6.0);
            }
            case ARC -> {
                gc.setStroke(Color.web(MainController.SINUMERIK_FEED_COLOR, 0.98));
                gc.setLineWidth(2.4);
                gc.setLineDashes(null);
            }
            case FEED -> {
                gc.setStroke(Color.web(MainController.SINUMERIK_FEED_COLOR, 0.98));
                gc.setLineWidth(2.4);
                gc.setLineDashes(null);
            }
            default -> {
                gc.setStroke(Color.web(MainController.SINUMERIK_FEED_COLOR, 0.98));
                gc.setLineWidth(2.4);
                gc.setLineDashes(null);
            }
        }
    }

    private static void applySegmentBackingStyle(GraphicsContext gc, SegmentStyle style, boolean darkTheme) {
        double opacity = darkTheme ? 0.58 : 0.72;
        gc.setStroke(darkTheme ? Color.web("#020617", opacity) : Color.web("#ffffff", opacity));
        gc.setLineWidth(style == SegmentStyle.RAPID ? 3.4 : 4.2);
        gc.setLineDashes(style == SegmentStyle.RAPID ? new double[]{8.0, 6.0} : null);
    }

    private double[] clipSegmentToViewport(Point2D start, Point2D end, double width, double height) {
        double marginX = Math.max(260.0, width * 0.40);
        double marginY = Math.max(260.0, height * 0.40);
        double left = -marginX;
        double right = width + marginX;
        double top = -marginY;
        double bottom = height + marginY;

        double x0 = start.getX();
        double y0 = start.getY();
        double x1 = end.getX();
        double y1 = end.getY();
        int out0 = outCode(x0, y0, left, right, top, bottom);
        int out1 = outCode(x1, y1, left, right, top, bottom);

        while (true) {
            if ((out0 | out1) == 0) {
                return new double[]{x0, y0, x1, y1};
            }
            if ((out0 & out1) != 0) {
                return null;
            }

            int out = out0 != 0 ? out0 : out1;
            double x;
            double y;
            if ((out & 8) != 0) {
                if (Math.abs(y1 - y0) < 1.0e-9) {
                    return null;
                }
                x = x0 + (x1 - x0) * (bottom - y0) / (y1 - y0);
                y = bottom;
            } else if ((out & 4) != 0) {
                if (Math.abs(y1 - y0) < 1.0e-9) {
                    return null;
                }
                x = x0 + (x1 - x0) * (top - y0) / (y1 - y0);
                y = top;
            } else if ((out & 2) != 0) {
                if (Math.abs(x1 - x0) < 1.0e-9) {
                    return null;
                }
                y = y0 + (y1 - y0) * (right - x0) / (x1 - x0);
                x = right;
            } else {
                if (Math.abs(x1 - x0) < 1.0e-9) {
                    return null;
                }
                y = y0 + (y1 - y0) * (left - x0) / (x1 - x0);
                x = left;
            }

            if (out == out0) {
                x0 = x;
                y0 = y;
                out0 = outCode(x0, y0, left, right, top, bottom);
            } else {
                x1 = x;
                y1 = y;
                out1 = outCode(x1, y1, left, right, top, bottom);
            }
        }
    }

    private static int outCode(double x, double y, double left, double right, double top, double bottom) {
        int code = 0;
        if (x < left) {
            code |= 1;
        } else if (x > right) {
            code |= 2;
        }
        if (y < top) {
            code |= 4;
        } else if (y > bottom) {
            code |= 8;
        }
        return code;
    }

    private Color colorFor(WorldPoint point, boolean highlight, boolean selected) {
        if (selected) {
            return Color.web("#f97316");
        }
        if (highlight) {
            return Color.web("#f97316", 0.95);
        }
        return pointColor(this.darkTheme, point.rapid);
    }

    static Color pointColor(boolean darkTheme, boolean rapid) {
        return Color.web(darkTheme ? (rapid ? "#fecaca" : "#f1f5f9")
                : (rapid ? "#7f1d1d" : "#334155"));
    }

    String formatHover(GCodeMoveData move) {
        if (move == null) {
            return I18n.text("sim.038");
        }
        return String.format(Locale.US, "X %.3f  Z %.3f  ·  N%d", move.endX(), move.endZ(), move.sourceLine());
    }

    String formatSelectionSummary(List<GCodeMoveData> selected, GCodeMoveData hover) {
        if (selected == null || selected.isEmpty()) {
            return formatHover(hover);
        }
        if (selected.size() == 1) {
            GCodeMoveData one = selected.get(0);
            return String.format(Locale.US, "X %.3f  Z %.3f  ·  N%d", one.endX(), one.endZ(), one.sourceLine());
        }
        return String.format(Locale.US, I18n.text("sim.050"), selected.size());
    }

    private enum SegmentStyle {
        RAPID,
        FEED,
        ARC
    }

    private record WorldPoint(double radiusMm, double zProgram, GCodeMoveData move, boolean isStart, boolean rapid) {
    }

    private record WorldSegment(double startR, double startZ, double endR, double endZ, SegmentStyle style) {
    }

    private record ScreenPoint(double x, double y, GCodeMoveData move, boolean start, boolean rapid) {
    }

    private record SideProjection(
            double originX,
            double originY,
            double zDx,
            double zDy,
            double rDx,
            double rDy,
            double refZ
    ) {
        Point2D project(double radiusMm, double zProgram) {
            double dz = zProgram - this.refZ;
            return new Point2D(
                    this.originX + dz * this.zDx + radiusMm * this.rDx,
                    this.originY + dz * this.zDy + radiusMm * this.rDy);
        }
    }
}
