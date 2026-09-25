package com.sergey.pisarev.controller;

import com.sergey.pisarev.model.SimulationContext;
import com.sergey.pisarev.model.WorkpieceDefinition;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.SubScene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Сетка и подписи осей Z / Xø поверх 3D в режиме «Вид сбоку» (как на 2D-графике).
 */
final class SimulationSideViewLabelsOverlay {
    /** Немного крупнее исходных 11px (без «перебора»). */
    private static final double LABEL_SCALE = 1.32;
    private static final double FONT_GRID = 11.0 * LABEL_SCALE;
    private static final double FONT_AXIS = 12.0 * LABEL_SCALE;
    private static final double FONT_ZERO = 10.0 * LABEL_SCALE;
    private static final double MIN_GRID_SPACING_PX = 5.0;
    private static final double MAX_LABEL_SPACING_PX = 58.0;
    private static final double MIN_Z_LABEL_SPACING_PX = 44.0;
    private static final double MIN_R_LABEL_SPACING_PX = 30.0;
    private static final double MIN_GRID_STEP = 0.001;
    private static final int MAX_GRID_LINES_PER_AXIS = 260;

    private final Canvas canvas;

    private static final class VisibleWindow {
        final double minZ;
        final double maxZ;
        final double minR;
        final double maxR;

        VisibleWindow(double minZ, double maxZ, double minR, double maxR) {
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.minR = minR;
            this.maxR = maxR;
        }
    }

    SimulationSideViewLabelsOverlay() {
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
            LatheOrbitCamera camera,
            SubScene subScene,
            Group projectionRoot,
            SimulationContext context,
            List<double[]> profile,
            boolean darkTheme
    ) {
        double w = Math.max(1.0, this.canvas.getWidth());
        double h = Math.max(1.0, this.canvas.getHeight());
        GraphicsContext gc = this.canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);
        if (subScene == null || projectionRoot == null || camera == null) {
            return;
        }
        double[] bounds = bounds(profile);
        double refZ = clamp(bounds[0], bounds[1], 0.0);
        VisibleWindow visible = visibleWindow(camera, subScene, projectionRoot, bounds, refZ, w, h);
        double stepZ = chooseStepForDivisions(visible.maxZ - visible.minZ, 22.0);
        double stepR = Math.max(MIN_GRID_STEP, chooseStepForDivisions((visible.maxR - visible.minR) * 2.0, 22.0) * 0.5);
        stepZ = adaptStepForScreen(camera, subScene, projectionRoot, visible.minZ, visible.maxZ, stepZ, true);
        stepR = adaptStepForScreen(camera, subScene, projectionRoot, visible.minR, visible.maxR, stepR, false);
        double zMin = Math.floor(visible.minZ / stepZ) * stepZ;
        double zMax = Math.ceil(visible.maxZ / stepZ) * stepZ;
        double minR = Math.max(0.0, Math.floor(visible.minR / stepR) * stepR);
        double maxR = Math.ceil(visible.maxR / stepR) * stepR;
        stepZ = limitGridDensity(stepZ, zMin, zMax);
        stepR = limitGridDensity(stepR, minR, maxR);
        zMin = Math.floor(visible.minZ / stepZ) * stepZ;
        zMax = Math.ceil(visible.maxZ / stepZ) * stepZ;
        minR = Math.max(0.0, Math.floor(visible.minR / stepR) * stepR);
        maxR = Math.ceil(visible.maxR / stepR) * stepR;
        Color minorGrid = darkTheme ? Color.web("#64748b", 0.52) : Color.web("#cbd5e1", 0.9);
        Color majorGrid = darkTheme ? Color.web("#cbd5e1", 0.9) : Color.web("#64748b");
        Color labelColor = darkTheme ? Color.web("#f8fafc") : Color.web("#1e293b");
        Color axisZ = darkTheme ? Color.web("#60a5fa", 0.75) : Color.web("#2563eb", 0.65);
        Color axisX = darkTheme ? Color.web("#f87171", 0.85) : Color.web("#dc2626", 0.75);

        Point2D axisLeft = camera.project(subScene, projectionRoot, 0.0, refZ);
        Point2D zProbe = camera.project(subScene, projectionRoot, 0.0, refZ + 1.0);
        Point2D rProbe = camera.project(subScene, projectionRoot, 1.0, refZ);
        if (!finite(axisLeft) || !finite(zProbe) || !finite(rProbe)) {
            return;
        }
        double pxPerZ = zProbe.getX() - axisLeft.getX();
        double pxPerR = rProbe.getY() - axisLeft.getY();
        if (Math.abs(pxPerZ) < 1.0e-6 || Math.abs(pxPerR) < 1.0e-6) {
            return;
        }
        gc.setLineWidth(1.0);
        double lastZLabelX = Double.NaN;
        double zMargin = Math.max(80.0, w * 0.12);
        for (double z = zMin; z <= zMax + stepZ * 0.5; z += stepZ) {
            double x = axisLeft.getX() + (z - refZ) * pxPerZ;
            if (x < -zMargin || x > w + zMargin) {
                continue;
            }
            boolean major = isMajorTick(z, stepZ);
            gc.setStroke(major ? majorGrid : minorGrid);
            gc.strokeLine(x, 0.0, x, h);
            if (shouldDrawLabel(x, lastZLabelX, MIN_Z_LABEL_SPACING_PX)) {
                lastZLabelX = x;
                gc.setFill(labelColor);
                gc.setFont(Font.font("Segoe UI", FONT_GRID));
                gc.fillText(formatTick(z, stepZ), x + 3.0, h - 18.0);
            }
        }

        double lastRLabelY = Double.NaN;
        // Вертикальная шкала Xø должна покрывать всю высоту вьюпорта (как ось Z —
        // всю ширину), а не обрываться по габаритам детали. Берём диапазон R прямо
        // по верхней (y=0) и нижней (y=h) кромкам экрана.
        double rEdgeTop = (0.0 - axisLeft.getY()) / pxPerR;
        double rEdgeBottom = (h - axisLeft.getY()) / pxPerR;
        double signedMinR = Math.min(rEdgeTop, rEdgeBottom);
        double signedMaxR = Math.max(rEdgeTop, rEdgeBottom);
        double rStart = Math.floor(signedMinR / stepR) * stepR;
        double rEnd = Math.ceil(signedMaxR / stepR) * stepR;
        double rMargin = Math.max(80.0, h * 0.12);
        for (double signedR = rStart; signedR <= rEnd + stepR * 0.5; signedR += stepR) {
            double y = axisLeft.getY() + signedR * pxPerR;
            if (y < -rMargin || y > h + rMargin) {
                continue;
            }
            double r = Math.abs(signedR);
            boolean major = isMajorTick(r, stepR);
            gc.setStroke(major ? majorGrid : minorGrid);
            gc.strokeLine(0.0, y, w, y);
            if (shouldDrawLabel(y, lastRLabelY, MIN_R_LABEL_SPACING_PX)) {
                lastRLabelY = y;
                gc.setFill(labelColor);
                gc.setFont(Font.font("Segoe UI", FONT_GRID));
                gc.fillText(formatTick(r * 2.0, stepR * 2.0), 12.0, y + 4.0);
            }
        }

        gc.setStroke(axisX);
        gc.setLineWidth(1.6);
        gc.strokeLine(0.0, axisLeft.getY(), w, axisLeft.getY());
        double arrowX = w - 6.0;
        double arrowY = axisLeft.getY();
        gc.strokeLine(arrowX, arrowY, arrowX - 10.0, arrowY - 5.0);
        gc.strokeLine(arrowX, arrowY, arrowX - 10.0, arrowY + 5.0);

        gc.setFill(axisZ);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, FONT_AXIS));
        gc.fillText("Z", w - 28.0, 22.0);
        gc.setFill(axisX);
        gc.fillText("X\u00D8", 10.0, 22.0);
        if (axisLeft != null) {
            gc.setFill(darkTheme ? Color.web("#94a3b8") : Color.web("#64748b"));
            gc.setFont(Font.font("Segoe UI", FONT_ZERO));
            gc.fillText("0", axisLeft.getX() - 14.0, axisLeft.getY() + 4.0);
        }
    }

    void clear() {
        double w = Math.max(1.0, this.canvas.getWidth());
        double h = Math.max(1.0, this.canvas.getHeight());
        this.canvas.getGraphicsContext2D().clearRect(0, 0, w, h);
    }

    private static boolean shouldDrawLabel(double coordinate, double lastCoordinate, double minSpacingPx) {
        if (!Double.isFinite(lastCoordinate)) {
            return true;
        }
        return Math.abs(coordinate - lastCoordinate) >= minSpacingPx;
    }

    private static boolean isMajorTick(double value, double step) {
        double majorStep = Math.max(MIN_GRID_STEP, step * 5.0);
        double ratio = value / majorStep;
        return Math.abs(ratio - Math.rint(ratio)) < 0.001 || step >= 500.0;
    }

    private static double limitGridDensity(double step, double min, double max) {
        double candidate = Math.max(0.001, step);
        double span = Math.max(0.0, max - min);
        while (span / candidate > MAX_GRID_LINES_PER_AXIS) {
            candidate = nextLargerStep(candidate);
        }
        return candidate;
    }

    private static double adaptStepForScreen(
            LatheOrbitCamera camera,
            SubScene subScene,
            Group projectionRoot,
            double min,
            double max,
            double step,
            boolean zAxis
    ) {
        double candidate = step;
        for (int attempt = 0; attempt < 14; attempt++) {
            double spacing = projectedSpacing(camera, subScene, projectionRoot, min, max, candidate, zAxis);
            if (!Double.isFinite(spacing) || spacing <= MAX_LABEL_SPACING_PX || candidate <= MIN_GRID_STEP) {
                break;
            }
            candidate = Math.max(MIN_GRID_STEP, nextSmallerStep(candidate));
        }
        for (int attempt = 0; attempt < 7; attempt++) {
            double spacing = projectedSpacing(camera, subScene, projectionRoot, min, max, candidate, zAxis);
            if (!Double.isFinite(spacing) || spacing >= MIN_GRID_SPACING_PX) {
                return candidate;
            }
            candidate = nextLargerStep(candidate);
        }
        return candidate;
    }

    private static double projectedSpacing(
            LatheOrbitCamera camera,
            SubScene subScene,
            Group projectionRoot,
            double min,
            double max,
            double step,
            boolean zAxis
    ) {
        double anchor = clamp(min, max, 0.0);
        double next = Math.min(max, anchor + step);
        if (Math.abs(next - anchor) < step * 0.25) {
            next = Math.max(min, anchor - step);
        }
        if (Math.abs(next - anchor) < 1.0e-9) {
            return Double.NaN;
        }
        Point2D first = zAxis
                ? camera.project(subScene, projectionRoot, 0.0, anchor)
                : camera.project(subScene, projectionRoot, anchor, 0.0);
        Point2D second = zAxis
                ? camera.project(subScene, projectionRoot, 0.0, next)
                : camera.project(subScene, projectionRoot, next, 0.0);
        if (first == null || second == null) {
            return Double.NaN;
        }
        return zAxis
                ? Math.abs(second.getX() - first.getX())
                : Math.abs(second.getY() - first.getY());
    }

    private static double clamp(double min, double max, double value) {
        return Math.max(min, Math.min(max, value));
    }

    private static double[] bounds(List<double[]> profile) {
        double zMin = Double.POSITIVE_INFINITY;
        double zMax = Double.NEGATIVE_INFINITY;
        double maxR = 20.0;
        if (profile != null) {
            for (double[] point : profile) {
                zMin = Math.min(zMin, point[0]);
                zMax = Math.max(zMax, point[0]);
                maxR = Math.max(maxR, point[1]);
            }
        }
        if (!Double.isFinite(zMin)) {
            zMin = -100.0;
            zMax = 100.0;
        }
        double padZ = Math.max(8.0, (zMax - zMin) * 0.03);
        maxR = Math.max(maxR * 1.08, 20.0);
        return new double[]{zMin - padZ, zMax + padZ, maxR};
    }

    private static VisibleWindow visibleWindow(
            LatheOrbitCamera camera,
            SubScene subScene,
            Group projectionRoot,
            double[] bounds,
            double refZ,
            double width,
            double height
    ) {
        Point2D origin = camera.project(subScene, projectionRoot, 0.0, refZ);
        Point2D zProbe = camera.project(subScene, projectionRoot, 0.0, refZ + 1.0);
        Point2D rProbe = camera.project(subScene, projectionRoot, 1.0, refZ);
        if (!finite(origin) || !finite(zProbe) || !finite(rProbe)) {
            return new VisibleWindow(bounds[0], bounds[1], 0.0, bounds[2]);
        }
        double pxPerZ = zProbe.getX() - origin.getX();
        double pxPerR = rProbe.getY() - origin.getY();
        if (Math.abs(pxPerZ) < 1.0e-6 || Math.abs(pxPerR) < 1.0e-6) {
            return new VisibleWindow(bounds[0], bounds[1], 0.0, bounds[2]);
        }

        double z0 = refZ + (0.0 - origin.getX()) / pxPerZ;
        double z1 = refZ + (width - origin.getX()) / pxPerZ;
        double minZ = Math.min(z0, z1);
        double maxZ = Math.max(z0, z1);
        double padZ = Math.max(MIN_GRID_STEP * 4.0, (maxZ - minZ) * 0.04);
        minZ -= padZ;
        maxZ += padZ;

        double r0 = (0.0 - origin.getY()) / pxPerR;
        double r1 = (height - origin.getY()) / pxPerR;
        double minR = Math.max(0.0, Math.min(r0, r1));
        double maxR = Math.max(r0, r1);
        if (!Double.isFinite(maxR) || maxR <= 0.0) {
            minR = 0.0;
            maxR = bounds[2];
        } else {
            double padR = Math.max(MIN_GRID_STEP * 4.0, Math.max(MIN_GRID_STEP, maxR - minR) * 0.05);
            minR = Math.max(0.0, minR - padR);
            maxR += padR;
        }
        return new VisibleWindow(minZ, maxZ, minR, Math.max(maxR, MIN_GRID_STEP));
    }

    private static boolean finite(Point2D point) {
        return point != null && Double.isFinite(point.getX()) && Double.isFinite(point.getY());
    }

    private static double chooseStep(double span) {
        if (!Double.isFinite(span) || span <= 0.0) {
            return 1.0;
        }
        return niceStep(span / 10.0);
    }

    private static double chooseStepForDivisions(double span, double divisions) {
        if (!Double.isFinite(span) || span <= 0.0 || !Double.isFinite(divisions) || divisions <= 0.0) {
            return MIN_GRID_STEP;
        }
        return niceStep(span / divisions);
    }

    private static double niceStep(double rawStep) {
        double raw = Math.max(MIN_GRID_STEP, Math.abs(rawStep));
        double exponent = Math.floor(Math.log10(raw));
        double magnitude = Math.pow(10.0, exponent);
        double fraction = raw / magnitude;
        double niceFraction;
        if (fraction <= 1.0) {
            niceFraction = 1.0;
        } else if (fraction <= 2.0) {
            niceFraction = 2.0;
        } else if (fraction <= 5.0) {
            niceFraction = 5.0;
        } else {
            niceFraction = 10.0;
        }
        return Math.max(MIN_GRID_STEP, niceFraction * magnitude);
    }

    private static double nextSmallerStep(double step) {
        return niceStep(step * 0.49);
    }

    private static double nextLargerStep(double step) {
        return niceStep(step * 2.01);
    }

    private static String formatTick(double value, double step) {
        double absStep = Math.abs(step);
        if (absStep < 0.01) {
            return String.format(Locale.US, "%.3f", value);
        }
        if (absStep < 0.1) {
            return String.format(Locale.US, "%.2f", value);
        }
        if (absStep < 1.0) {
            return String.format(Locale.US, "%.1f", value);
        }
        return String.format(Locale.US, "%.0f", value);
    }
}
