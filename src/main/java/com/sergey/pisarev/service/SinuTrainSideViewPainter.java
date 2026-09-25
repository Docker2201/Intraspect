package com.sergey.pisarev.service;

import com.sergey.pisarev.model.GCodeMoveData;
import com.sergey.pisarev.model.SimulationContext;
import com.sergey.pisarev.model.WorkpieceDefinition;
import java.util.List;
import java.util.Locale;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Отрисовка бокового вида SinuTrain: Z горизонтально, Xø по центру, заготовка, сетка, статус.
 */
public final class SinuTrainSideViewPainter {
    private static final double STATUS_HEIGHT = 72.0;
    private static final double MARGIN = 48.0;

    private SinuTrainSideViewPainter() {
    }

    public static Layout computeLayout(
            double width,
            double height,
            SimulationContext context,
            List<double[]> profile,
            List<GCodeMoveData> displayMoves,
            double panX,
            double panY,
            double zoom
    ) {
        double[] bounds = collectSceneBounds(context != null ? context.getWorkpiece() : null, profile, displayMoves);
        double minZ = bounds[0];
        double maxZ = bounds[1];
        double maxRadius = bounds[2];
        double padZ = Math.max(12.0, (maxZ - minZ) * 0.03);
        double symRadius = Math.max(maxRadius * 1.12, 20.0);
        minZ -= padZ;
        maxZ += padZ;
        double stepZ = chooseStep(maxZ - minZ);
        minZ = Math.floor(minZ / stepZ) * stepZ;
        maxZ = Math.ceil(maxZ / stepZ) * stepZ;
        double plotTop = MARGIN + panY;
        double plotH = Math.max(80.0, height - STATUS_HEIGHT - MARGIN * 2.0);
        double plotW = Math.max(80.0, width - MARGIN * 2.0);
        double baseScale = Math.min(
                plotW / Math.max(1.0, maxZ - minZ),
                plotH / Math.max(1.0, symRadius * 2.0));
        double scale = baseScale * zoom;
        double left = MARGIN + panX;
        double axisR = plotTop + plotH / 2.0;
        return new Layout(
                left,
                plotTop,
                plotW,
                plotH,
                -symRadius,
                symRadius,
                minZ,
                maxZ,
                scale,
                axisR,
                height - STATUS_HEIGHT,
                false);
    }

    /** Макет полусреза SinuTrain: ось Z внизу, радиус только вверх от оси. */
    public static Layout computeHalfSectionLayout(
            double width,
            double height,
            SimulationContext context,
            List<double[]> profile,
            List<GCodeMoveData> displayMoves,
            double panX,
            double panY,
            double zoom
    ) {
        double[] bounds = collectSceneBounds(context != null ? context.getWorkpiece() : null, profile, displayMoves);
        double minZ = bounds[0];
        double maxZ = bounds[1];
        double maxRadius = bounds[2];
        double padZ = Math.max(40.0, (maxZ - minZ) * 0.05);
        maxRadius = Math.max(maxRadius * 1.14, 20.0);
        minZ -= padZ;
        maxZ += padZ;
        double plotTop = MARGIN + panY;
        double plotH = Math.max(80.0, height - STATUS_HEIGHT - MARGIN * 2.0);
        double plotW = Math.max(80.0, width - MARGIN * 2.0);
        double baseScale = Math.min(
                plotW / Math.max(1.0, maxZ - minZ),
                plotH / Math.max(1.0, maxRadius * 1.08));
        double scale = baseScale * zoom;
        double left = MARGIN + panX;
        double axisR = plotTop + plotH - 8.0;
        return new Layout(
                left,
                plotTop,
                plotW,
                plotH,
                0.0,
                maxRadius,
                minZ,
                maxZ,
                scale,
                axisR,
                height - STATUS_HEIGHT,
                true);
    }

    public static void paint(
            GraphicsContext gc,
            double width,
            double height,
            Layout layout,
            SimulationContext context,
            List<double[]> profile,
            List<GCodeMoveData> displayMoves,
            GCodeMoveData hover,
            String profileError,
            boolean darkTheme
    ) {
        paint(gc, width, height, layout, context, profile, displayMoves, hover, profileError, darkTheme, true);
    }

    /**
     * @param halfSection true — продольный полусрез SinuTrain (материал над осью, снятие штриховкой).
     */
    public static void paint(
            GraphicsContext gc,
            double width,
            double height,
            Layout layout,
            SimulationContext context,
            List<double[]> profile,
            List<GCodeMoveData> displayMoves,
            GCodeMoveData hover,
            String profileError,
            boolean darkTheme,
            boolean halfSection
    ) {
        gc.clearRect(0, 0, width, height);
        gc.setFill(darkTheme ? Color.web("#0f172a") : Color.WHITE);
        gc.fillRect(0, 0, width, height);
        double stockRadius = resolveStockRadius(context, profile);
        if (AxialStockSection.isSection(profile)) {
            drawFullMeridian(gc, layout, profile, layout.halfSectionAxis, darkTheme);
        } else if (halfSection) {
            boolean hasCut = profile.size() >= 2
                    && StockRemovalMeshBuilder.profileHasMaterialRemoval(profile, stockRadius);
            if (layout.halfSectionAxis) {
                // Старый «полусрез» (ось внизу), оставлен для совместимости.
                if (hasCut) {
                    drawHalfSectionSimulation(gc, layout, profile, stockRadius, context.getWorkpiece());
                } else {
                    drawHalfSectionStock(gc, layout, context.getWorkpiece(), stockRadius);
                }
            } else {
                // Симметричный срез SinuTrain: ось в центре, материал зеркально.
                if (hasCut) {
                    drawSymmetricSectionSimulation(gc, layout, profile, context.getWorkpiece());
                } else {
                    drawSymmetricSectionStock(gc, layout, context.getWorkpiece(), stockRadius);
                }
            }
            if ((!hasCut || profile.size() < 2) && profileError != null) {
                gc.setFill(Color.web("#ef4444"));
                gc.setFont(Font.font(13.0));
                gc.fillText(profileError, 24, 48);
            }
        } else {
            drawStock(gc, layout, context.getWorkpiece());
            if (profile.size() >= 2) {
                drawFinishedPartSideView(gc, layout, profile);
            }
            if (profile.size() < 2 && profileError != null) {
                gc.setFill(Color.web("#ef4444"));
                gc.setFont(Font.font(13.0));
                gc.fillText(profileError, 24, 48);
            }
        }
        drawGrid(gc, layout, darkTheme, width, height);
        drawToolpathSinuTrain(gc, layout, context, displayMoves, darkTheme, width, height);
        drawStatusBar(gc, width, layout, context, hover, darkTheme);
    }

    private static void drawFullMeridian(GraphicsContext gc, Layout layout, List<double[]> section,
                                         boolean half, boolean dark) {
        gc.save();
        gc.setFillRule(javafx.scene.shape.FillRule.EVEN_ODD);
        gc.setFill(Color.web(dark ? "#74bad5" : "#66a8c3"));
        gc.setStroke(Color.web(dark ? "#c5f0ff" : "#234758"));
        gc.setLineWidth(1);
        gc.beginPath();
        for (int sign : half ? new int[]{1} : new int[]{1,-1}) {
            double loop=-1;
            for(var p:section) {
                double x=layout.toScreenZ(p[0]), y=layout.toScreenR(sign*p[1]);
                if(p[2]!=loop) { if(loop>=0) gc.closePath(); gc.moveTo(x,y); loop=p[2]; }
                else gc.lineTo(x,y);
            }
            gc.closePath();
        }
        gc.fill(); gc.stroke(); gc.restore();
    }

    private static double resolveStockRadius(SimulationContext context, List<double[]> profile) {
        return resolveStockRadius(context != null ? context.getWorkpiece() : null, profile);
    }

    private static double resolveStockRadius(WorkpieceDefinition workpiece, List<double[]> profile) {
        if (workpiece != null && workpiece.isValid()) {
            return workpiece.getStockRadiusMm();
        }
        double maxR = 10.0;
        for (double[] point : profile) {
            maxR = Math.max(maxR, point[1]);
        }
        return maxR;
    }

    private static final double GRID_LABEL_SCALE = 1.32;
    private static final double GRID_FONT = 11.0 * GRID_LABEL_SCALE;
    private static final double GRID_AXIS_FONT = 12.0 * GRID_LABEL_SCALE;
    private static final double GRID_ZERO_FONT = 10.0 * GRID_LABEL_SCALE;
    private static final double GRID_MIN_SPACING_PX = 5.0;
    private static final double GRID_MIN_LABEL_SPACING_PX = 42.0;
    private static final double GRID_MIN_STEP = 0.001;

    private static void drawGrid(GraphicsContext gc, Layout layout, boolean darkTheme, double width, double height) {
        double[] visibleZ = visibleZRange(layout, width);
        double[] visibleR = visibleRRange(layout);
        double stepZ = adaptCanvasGridStep(niceStep((visibleZ[1] - visibleZ[0]) / 22.0), layout.scale());
        double stepR = adaptCanvasGridStep(
                Math.max(GRID_MIN_STEP, niceStep((visibleR[1] - visibleR[0]) * 2.0 / 22.0) * 0.5),
                layout.scale());
        gc.setLineWidth(1.0);
        double zStart = Math.floor(visibleZ[0] / stepZ) * stepZ;
        double zEnd = Math.ceil(visibleZ[1] / stepZ) * stepZ;
        double lastZLabelX = Double.NaN;
        double plotTop = Math.max(0.0, Math.min(layout.top, layout.statusTop));
        double plotBottom = Math.min(height, Math.max(plotTop, layout.statusTop));
        for (double z = zStart; z <= zEnd + 0.001; z += stepZ) {
            double x = layout.toScreenZ(z);
            boolean major = isMajorGridLine(z, stepZ);
            gc.setStroke(darkTheme ? Color.web(major ? "#475569" : "#1e293b") : Color.web(major ? "#94a3b8" : "#e2e8f0"));
            gc.strokeLine(x, plotTop, x, plotBottom);
            if (shouldDrawCanvasLabel(x, lastZLabelX)) {
                lastZLabelX = x;
                gc.setFill(darkTheme ? Color.web("#f8fafc") : Color.web("#1e293b"));
                gc.setFont(Font.font("Segoe UI", GRID_FONT));
                gc.fillText(
                        formatGridValue(z, stepZ),
                        x + 4.0,
                        Math.min(plotBottom - 8.0, Math.max(18.0, plotBottom - GRID_FONT * 0.45)));
            }
        }
        double rStart = Math.max(layout.halfSectionAxis ? 0.0 : layout.minR, Math.floor(visibleR[0] / stepR) * stepR);
        double rEnd = Math.min(layout.maxR, Math.ceil(visibleR[1] / stepR) * stepR);
        double lastRLabelY = Double.NaN;
        for (double r = rStart; r <= rEnd + 0.001; r += stepR) {
            if (!layout.halfSectionAxis && r < layout.minR - 0.001) {
                continue;
            }
            double y = layout.toScreenR(r);
            if (y < -2.0 || y > plotBottom + 2.0) {
                continue;
            }
            boolean major = isMajorGridLine(r, stepR);
            gc.setStroke(darkTheme ? Color.web(major ? "#475569" : "#1e293b") : Color.web(major ? "#94a3b8" : "#e2e8f0"));
            gc.strokeLine(0.0, y, width, y);
            if (r >= 0.0 && shouldDrawCanvasLabel(y, lastRLabelY)) {
                lastRLabelY = y;
                gc.setFill(darkTheme ? Color.web("#f8fafc") : Color.web("#1e293b"));
                gc.setFont(Font.font("Segoe UI", GRID_FONT));
                gc.fillText(
                        formatGridValue(r * 2.0, stepR * 2.0),
                        12.0,
                        y + GRID_FONT * 0.12);
            }
        }
        double zZero = layout.toScreenZ(0.0);
        if (zZero >= layout.left && zZero <= layout.left + layout.width) {
            gc.setStroke(Color.web("#3b82f6", layout.halfSectionAxis ? 0.9 : 0.45));
            gc.setLineWidth(layout.halfSectionAxis ? 1.9 : 1.4);
            gc.strokeLine(zZero, layout.top, zZero, layout.top + layout.height);
            if (layout.halfSectionAxis) {
                gc.setFill(Color.web("#60a5fa"));
                gc.setFont(Font.font("Segoe UI", GRID_ZERO_FONT));
                gc.fillText("Z0", zZero + 6.0, layout.top + GRID_ZERO_FONT * 1.1);
            }
        }
        gc.setStroke(Color.web("#dc2626", layout.halfSectionAxis ? 0.95 : 0.5));
        gc.setLineWidth(layout.halfSectionAxis ? 2.0 : 2.0);
        gc.strokeLine(layout.left, layout.axisR, layout.left + layout.width, layout.axisR);
        gc.setFill(Color.web("#1e3a5f"));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, GRID_AXIS_FONT));
        gc.fillText("Z", layout.left + layout.width - 28.0, layout.axisR + (layout.halfSectionAxis ? 22.0 : -12.0));
        gc.fillText("X\u00D8", layout.left + 8.0, layout.halfSectionAxis ? layout.axisR - 20.0 : layout.top + 20.0);
        gc.setFont(Font.font("Segoe UI", GRID_ZERO_FONT));
        gc.fillText("0", layout.left - GRID_ZERO_FONT * 1.4, layout.axisR + (layout.halfSectionAxis ? -6.0 : 6.0));
    }

    private static boolean shouldDrawCanvasLabel(double coordinate, double lastCoordinate) {
        if (!Double.isFinite(lastCoordinate)) {
            return true;
        }
        return Math.abs(coordinate - lastCoordinate) >= GRID_MIN_LABEL_SPACING_PX;
    }

    private static double[] visibleZRange(Layout layout, double canvasWidth) {
        double z0 = screenToZ(layout, 0.0);
        double z1 = screenToZ(layout, Math.max(1.0, canvasWidth));
        double min = Math.min(z0, z1);
        double max = Math.max(z0, z1);
        double pad = Math.max(GRID_MIN_STEP * 4.0, (max - min) * 0.04);
        return new double[]{min - pad, max + pad};
    }

    private static double[] visibleRRange(Layout layout) {
        double rTop = screenToR(layout, 0.0);
        double rBottom = screenToR(layout, layout.statusTop);
        double min = Math.max(layout.halfSectionAxis ? 0.0 : layout.minR, Math.min(rTop, rBottom));
        double max = Math.min(layout.maxR, Math.max(rTop, rBottom));
        if (!Double.isFinite(min) || !Double.isFinite(max) || max <= min) {
            return new double[]{layout.halfSectionAxis ? 0.0 : layout.minR, layout.maxR};
        }
        double pad = Math.max(GRID_MIN_STEP * 4.0, (max - min) * 0.05);
        min = Math.max(layout.halfSectionAxis ? 0.0 : layout.minR, min - pad);
        max = Math.min(layout.maxR, max + pad);
        return new double[]{min, Math.max(min + GRID_MIN_STEP, max)};
    }

    private static double screenToZ(Layout layout, double screenX) {
        return layout.minZ + (screenX - layout.left) / Math.max(1.0e-9, layout.scale);
    }

    private static double screenToR(Layout layout, double screenY) {
        if (layout.halfSectionAxis) {
            return (layout.axisR - screenY) / Math.max(1.0e-9, layout.scale);
        }
        return layout.minR + (layout.axisR - screenY) / Math.max(1.0e-9, layout.scale);
    }

    private static double adaptCanvasGridStep(double step, double scale) {
        double candidate = Math.max(GRID_MIN_STEP, step);
        for (int attempt = 0; attempt < 16; attempt++) {
            if (Math.abs(candidate * scale) >= GRID_MIN_SPACING_PX) {
                return candidate;
            }
            candidate = niceStep(candidate * 2.01);
        }
        return candidate;
    }

    private static double niceStep(double rawStep) {
        double raw = Math.max(GRID_MIN_STEP, Math.abs(rawStep));
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
        return Math.max(GRID_MIN_STEP, niceFraction * magnitude);
    }

    private static double adaptCanvasStep(Layout layout, double step, boolean zAxis) {
        double candidate = step;
        for (int attempt = 0; attempt < 8; attempt++) {
            double prev = Double.NaN;
            boolean crowded = false;
            double min = zAxis ? layout.minZ : layout.minR;
            double max = zAxis ? layout.maxZ : layout.maxR;
            for (double value = min; value <= max + candidate * 0.5; value += candidate) {
                boolean major = isMajorGridLine(value, candidate);
                if (!major) {
                    continue;
                }
                double screen = zAxis ? layout.toScreenZ(value) : layout.toScreenR(value);
                if (Double.isFinite(prev) && Math.abs(screen - prev) < GRID_MIN_LABEL_SPACING_PX) {
                    crowded = true;
                    break;
                }
                prev = screen;
            }
            if (!crowded) {
                return candidate;
            }
            candidate *= 2.0;
        }
        return candidate;
    }

    private static boolean isMajorGridLine(double value, double step) {
        if (!Double.isFinite(value) || !Double.isFinite(step) || step <= 0.0) {
            return false;
        }
        double majorStep = step * 5.0;
        if (majorStep >= 500.0) {
            return true;
        }
        double normalized = value / majorStep;
        return Math.abs(normalized - Math.rint(normalized)) < 1.0e-6;
    }

    private static String formatGridValue(double value, double step) {
        double normalized = Math.abs(value) < Math.max(1.0e-9, step) * 0.25 ? 0.0 : value;
        if (step < 0.0099) {
            return String.format(Locale.US, "%.3f", normalized);
        }
        if (step < 0.099) {
            return String.format(Locale.US, "%.2f", normalized);
        }
        if (step < 0.999) {
            return String.format(Locale.US, "%.1f", normalized);
        }
        return String.format(Locale.US, "%.0f", normalized);
    }

    private static void drawStock(GraphicsContext gc, Layout layout, WorkpieceDefinition workpiece) {
        if (workpiece == null || !workpiece.isValid()) {
            return;
        }
        double sx0 = layout.toScreenZ(workpiece.getZMin());
        double sx1 = layout.toScreenZ(workpiece.getZMax());
        double syTop = layout.toScreenR(workpiece.getStockRadiusMm());
        double syBottom = layout.toScreenR(-workpiece.getStockRadiusMm());
        double w = Math.max(1.0, sx1 - sx0);
        double h = Math.max(1.0, syBottom - syTop);
        LinearGradient gradient = new LinearGradient(
                0, syTop, 0, syBottom, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#d4e8f5")),
                new Stop(0.5, Color.web("#8eb8d4")),
                new Stop(1, Color.web("#6a9fc4")));
        gc.setFill(gradient);
        gc.fillRect(sx0, syTop, w, h);
        gc.save();
        gc.beginPath();
        gc.rect(sx0, syTop, w, h);
        gc.clip();
        gc.setStroke(Color.web("#4a7aa8", 0.2));
        for (double d = -h; d < w + h; d += 10.0) {
            gc.strokeLine(sx0 + d, syBottom, sx0 + d + h, syTop);
        }
        gc.restore();
        gc.setStroke(Color.web("#3d6f96"));
        gc.setLineWidth(1.2);
        gc.strokeRect(sx0, syTop, w, h);
    }

    /** Полусрез SinuTrain: бирюзовая деталь с диагональной штриховкой, тонкий контур, без блестящих градиентов. */
    private static void drawHalfSectionSimulation(
            GraphicsContext gc,
            Layout layout,
            List<double[]> profile,
            double stockRadius,
            WorkpieceDefinition workpiece
    ) {
        double[] profileZ = profileZExtent(profile);
        double zStart = profileZ[0];
        double zEnd = profileZ[1];
        if (workpiece != null && workpiece.isValid()) {
            stockRadius = workpiece.getStockRadiusMm();
            zStart = workpiece.getZMin();
            zEnd = workpiece.getZMax();
        }
        double syAxis = layout.axisR;

        int n = profile.size();
        double[] partX = new double[n + 2];
        double[] partY = new double[n + 2];
        partX[0] = layout.toScreenZ(zStart);
        partY[0] = syAxis;
        for (int i = 0; i < n; i++) {
            partX[i + 1] = layout.toScreenZ(profile.get(i)[0]);
            partY[i + 1] = layout.toScreenR(Math.max(0.0, profile.get(i)[1]));
        }
        partX[n + 1] = layout.toScreenZ(zEnd);
        partY[n + 1] = syAxis;

        // 1. Сплошная заливка детали — цвет SinuTrain (приглушённый бирюзовый).
        gc.setFill(Color.web("#4fb5b3"));
        gc.fillPolygon(partX, partY, n + 2);

        // 2. Диагональная штриховка поверх (как у SinuTrain — материал).
        gc.save();
        gc.beginPath();
        gc.moveTo(partX[0], partY[0]);
        for (int i = 1; i < n + 2; i++) {
            gc.lineTo(partX[i], partY[i]);
        }
        gc.closePath();
        gc.clip();
        double minX = partX[0];
        double maxX = partX[n + 1];
        double minYClip = syAxis;
        for (int i = 1; i < n + 1; i++) {
            minYClip = Math.min(minYClip, partY[i]);
        }
        double hatchSpacing = 8.0;
        gc.setStroke(Color.web("#2d7a7a", 0.55));
        gc.setLineWidth(0.8);
        minX = Math.max(minX, -120.0);
        maxX = Math.min(maxX, layout.left + layout.width + 120.0);
        double diag = Math.min(4000.0, Math.max(0.0, (maxX - minX) + (syAxis - minYClip)));
        for (double d = -diag; d <= diag; d += hatchSpacing) {
            gc.strokeLine(minX + d, minYClip, minX + d + (syAxis - minYClip), syAxis);
        }
        gc.restore();

        // 3. Тонкий тёмно-зелёный контур (SinuTrain выделение детали).
        gc.setStroke(Color.web("#1f6f6e"));
        gc.setLineWidth(1.1);
        gc.strokePolyline(partX, partY, n + 2);
    }

    private static void drawHalfSectionStock(
            GraphicsContext gc,
            Layout layout,
            WorkpieceDefinition workpiece,
            double stockRadius
    ) {
        double sx0;
        double sx1;
        double syTop;
        if (workpiece != null && workpiece.isValid()) {
            sx0 = layout.toScreenZ(workpiece.getZMin());
            sx1 = layout.toScreenZ(workpiece.getZMax());
            syTop = layout.toScreenR(workpiece.getStockRadiusMm());
        } else {
            sx0 = layout.toScreenZ(layout.minZ);
            sx1 = layout.toScreenZ(layout.maxZ);
            syTop = layout.toScreenR(stockRadius);
        }
        double syAxis = layout.axisR;
        double w = Math.max(1.0, sx1 - sx0);
        double h = Math.max(1.0, syAxis - syTop);
        gc.setFill(Color.web("#4fb5b3"));
        gc.fillRect(sx0, syTop, w, h);
        gc.save();
        gc.beginPath();
        gc.rect(sx0, syTop, w, h);
        gc.clip();
        gc.setStroke(Color.web("#2d7a7a", 0.55));
        gc.setLineWidth(0.8);
        double diag = Math.min(4000.0, w + h);
        for (double d = -diag; d <= diag; d += 8.0) {
            gc.strokeLine(sx0 + d, syTop, sx0 + d + h, syAxis);
        }
        gc.restore();
        gc.setStroke(Color.web("#1f6f6e"));
        gc.setLineWidth(1.1);
        gc.strokeRect(sx0, syTop, w, h);
    }

    /** Симметричный продольный срез заготовки (как в SinuTrain до начала снятия). */
    private static void drawSymmetricSectionStock(
            GraphicsContext gc,
            Layout layout,
            WorkpieceDefinition workpiece,
            double stockRadius
    ) {
        double zMin;
        double zMax;
        double radius;
        if (workpiece != null && workpiece.isValid()) {
            zMin = workpiece.getZMin();
            zMax = workpiece.getZMax();
            radius = workpiece.getStockRadiusMm();
        } else {
            zMin = layout.minZ;
            zMax = layout.maxZ;
            radius = stockRadius;
        }
        double sx0 = layout.toScreenZ(zMin);
        double sx1 = layout.toScreenZ(zMax);
        double syTop = layout.toScreenR(radius);
        double syBottom = layout.toScreenR(-radius);
        double w = Math.max(1.0, sx1 - sx0);
        double h = Math.max(1.0, syBottom - syTop);
        gc.setFill(Color.web("#9ec4dc"));
        gc.fillRect(sx0, syTop, w, h);
        gc.save();
        gc.beginPath();
        gc.rect(sx0, syTop, w, h);
        gc.clip();
        gc.setStroke(Color.web("#4a7aa8", 0.32));
        gc.setLineWidth(0.9);
        double diag = w + h;
        for (double d = -diag; d <= diag; d += 8.0) {
            gc.strokeLine(sx0 + d, syBottom, sx0 + d + h, syTop);
        }
        gc.restore();
        gc.setStroke(Color.web("#0f766e", 0.75));
        gc.setLineWidth(1.1);
        gc.strokeRect(sx0, syTop, w, h);
    }

    /**
     * Симметричный продольный срез SinuTrain: ось Z в центре, материал зеркально сверху и снизу.
     * Полигон: top-left → верхний профиль L→R → top-right → bottom-right → нижний профиль R→L → bottom-left.
     */
    private static void drawSymmetricSectionSimulation(
            GraphicsContext gc,
            Layout layout,
            List<double[]> profile,
            WorkpieceDefinition workpiece
    ) {
        int n = profile.size();
        if (n < 2) {
            return;
        }
        int pointCount = 2 * n;
        double[] partX = new double[pointCount];
        double[] partY = new double[pointCount];
        int idx = 0;
        for (int i = 0; i < n; i++) {
            partX[idx] = layout.toScreenZ(profile.get(i)[0]);
            partY[idx++] = layout.toScreenR(Math.max(0.0, profile.get(i)[1]));
        }
        for (int i = n - 1; i >= 0; i--) {
            partX[idx] = layout.toScreenZ(profile.get(i)[0]);
            partY[idx++] = layout.toScreenR(-Math.max(0.0, profile.get(i)[1]));
        }

        // 1. Бирюзовая заливка тела детали.
        gc.setFill(Color.web("#4fb5b3"));
        gc.fillPolygon(partX, partY, pointCount);

        // 2. Диагональная штриховка поверх (SinuTrain — материал).
        gc.save();
        gc.beginPath();
        gc.moveTo(partX[0], partY[0]);
        for (int i = 1; i < pointCount; i++) {
            gc.lineTo(partX[i], partY[i]);
        }
        gc.closePath();
        gc.clip();
        double minX = partX[0];
        double maxX = partX[0];
        double minY = partY[0];
        double maxY = partY[0];
        for (int i = 1; i < pointCount; i++) {
            minX = Math.min(minX, partX[i]);
            maxX = Math.max(maxX, partX[i]);
            minY = Math.min(minY, partY[i]);
            maxY = Math.max(maxY, partY[i]);
        }
        gc.setStroke(Color.web("#2d7a7a", 0.55));
        gc.setLineWidth(0.8);
        double hatchSpacing = 8.0;
        double diag = (maxX - minX) + (maxY - minY);
        for (double d = -diag; d <= diag * 2.0; d += hatchSpacing) {
            // Линии под углом 45°: от (minX+d, minY) до (minX+d+(maxY-minY), maxY).
            gc.strokeLine(minX + d, minY, minX + d + (maxY - minY), maxY);
        }
        gc.restore();

        // 3. Тонкий тёмно-зелёный контур (верхняя и нижняя кромка).
        gc.setStroke(Color.web("#1f6f6e"));
        gc.setLineWidth(1.1);
        // Верхний контур.
        double[] topX = new double[n];
        double[] topY = new double[n];
        double[] botX = new double[n];
        double[] botY = new double[n];
        for (int i = 0; i < n; i++) {
            topX[i] = layout.toScreenZ(profile.get(i)[0]);
            topY[i] = layout.toScreenR(Math.max(0.0, profile.get(i)[1]));
            botX[i] = topX[i];
            botY[i] = layout.toScreenR(-Math.max(0.0, profile.get(i)[1]));
        }
        gc.strokePolyline(topX, topY, n);
        gc.strokePolyline(botX, botY, n);
    }

    /** Контур готовой детали на оси (Side view SinuTrain): тонкий профиль по Xø. */
    private static void drawFinishedPartSideView(GraphicsContext gc, Layout layout, List<double[]> profile) {
        int n = profile.size();
        if (n < 2) {
            return;
        }
        double[] upperX = new double[n];
        double[] upperY = new double[n];
        double[] lowerX = new double[n];
        double[] lowerY = new double[n];
        for (int i = 0; i < n; i++) {
            upperX[i] = layout.toScreenZ(profile.get(i)[0]);
            upperY[i] = layout.toScreenR(profile.get(i)[1]);
            lowerX[i] = upperX[i];
            lowerY[i] = layout.toScreenR(-profile.get(i)[1]);
        }
        gc.setStroke(Color.web("#0d9488", 0.9));
        gc.setLineWidth(2.2);
        gc.strokePolyline(upperX, upperY, n);
        gc.strokePolyline(lowerX, lowerY, n);
        gc.setFill(Color.web("#7ec8e3", 0.35));
        for (int i = 0; i < n - 1; i++) {
            double h = Math.max(1.0, Math.abs(upperY[i] - lowerY[i]));
            gc.fillRect(upperX[i], upperY[i], Math.max(1.0, upperX[i + 1] - upperX[i]), h);
        }
    }

    /** Траектория SinuTrain: G00 пунктир бирюзовый, G01 красный, G02/G03 коричневый. */
    private static void drawToolpathSinuTrain(
            GraphicsContext gc,
            Layout layout,
            SimulationContext context,
            List<GCodeMoveData> moves,
            boolean darkTheme,
            double canvasWidth,
            double canvasHeight
    ) {
        if (moves == null || moves.isEmpty()) {
            return;
        }
        for (GCodeMoveData move : moves) {
            double startR = toolPathRadius(move.startX());
            double endR = toolPathRadius(move.endX());
            if (startR < 0.02 && endR < 0.02) {
                continue;
            }
            double sx = layout.toScreenZ(move.startZ());
            double sy = layout.toScreenR(startR);
            double ex = layout.toScreenZ(move.endZ());
            double ey = layout.toScreenR(endR);
            double[] clipped = clipSegmentToLayout(sx, sy, ex, ey, layout, canvasWidth, canvasHeight);
            if (clipped == null) {
                continue;
            }
            applyToolpathBackingStyle(gc, move, darkTheme);
            gc.strokeLine(clipped[0], clipped[1], clipped[2], clipped[3]);
            applyToolpathStyle(gc, move);
            gc.strokeLine(clipped[0], clipped[1], clipped[2], clipped[3]);
        }
        gc.setLineDashes(null);
        for (GCodeMoveData move : moves) {
            if (move.arcSegment() && !move.arcEnd()) {
                continue;
            }
            double endR = toolPathRadius(move.endX());
            if (endR < 0.02) {
                continue;
            }
            double px = layout.toScreenZ(move.endZ());
            double py = layout.toScreenR(endR);
            if (px < -32.0 || px > canvasWidth + 32.0 || py < -32.0 || py > canvasHeight + 32.0) {
                continue;
            }
            drawToolpathNode(gc, px, py);
        }
    }

    private static double[] clipSegmentToLayout(
            double sx,
            double sy,
            double ex,
            double ey,
            Layout layout,
            double canvasWidth,
            double canvasHeight
    ) {
        if (!Double.isFinite(sx) || !Double.isFinite(sy) || !Double.isFinite(ex) || !Double.isFinite(ey)) {
            return null;
        }
        double margin = Math.max(320.0, Math.max(layout.width, layout.height) * 0.35);
        double left = -margin;
        double right = Math.max(canvasWidth, layout.left + layout.width) + margin;
        double top = -margin;
        double bottom = Math.min(Math.max(1.0, canvasHeight), layout.statusTop) + margin;
        double x0 = sx;
        double y0 = sy;
        double x1 = ex;
        double y1 = ey;
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

    private static void applyToolpathBackingStyle(GraphicsContext gc, GCodeMoveData move, boolean darkTheme) {
        gc.setStroke(darkTheme ? Color.web("#020617", 0.48) : Color.web("#ffffff", 0.58));
        gc.setLineWidth(move.rapid() ? 3.0 : 3.8);
        gc.setLineDashes(move.rapid() ? new double[]{8.0, 6.0} : null);
    }

    private static void applyToolpathStyle(GraphicsContext gc, GCodeMoveData move) {
        if (move.arcSegment()) {
            gc.setLineDashes(null);
            gc.setStroke(Color.web("#92400e", 0.98));
            gc.setLineWidth(2.4);
        } else if (move.rapid()) {
            gc.setLineDashes(8.0, 6.0);
            gc.setStroke(Color.web("#06b6d4", 0.96));
            gc.setLineWidth(1.5);
        } else {
            gc.setLineDashes(null);
            gc.setStroke(Color.web("#ef4444", 0.98));
            gc.setLineWidth(2.4);
        }
    }

    private static double toolPathRadius(double xDiameter) {
        return Math.max(0.0, LatheMeshBuilder.toRadius(xDiameter));
    }

    private static void drawToolpathNode(GraphicsContext gc, double x, double y) {
        double r = 2.2;
        gc.setLineDashes(null);
        gc.setFill(Color.web("#334155"));
        gc.fillOval(x - r, y - r, r * 2.0, r * 2.0);
    }

    private static double finishContourRadius(
            LatheCuttingEnvelope envelope,
            int sourceLine,
            double xDiameter,
            double stockRadiusMm
    ) {
        if (envelope != null && stockRadiusMm > 0.0) {
            return Math.max(0.0, envelope.removalRadiusAt(sourceLine, xDiameter, stockRadiusMm));
        }
        return Math.max(0.0, LatheMeshBuilder.toRadius(xDiameter));
    }

    private static void drawProfile(GraphicsContext gc, Layout layout, List<double[]> profile) {
        int n = profile.size();
        double[] upperX = new double[n + 2];
        double[] upperY = new double[n + 2];
        double[] lowerX = new double[n];
        double[] lowerY = new double[n];
        upperX[0] = layout.toScreenZ(profile.get(0)[0]);
        upperY[0] = layout.axisR;
        for (int i = 0; i < n; i++) {
            upperX[i + 1] = layout.toScreenZ(profile.get(i)[0]);
            upperY[i + 1] = layout.toScreenR(profile.get(i)[1]);
            lowerX[i] = upperX[i + 1];
            lowerY[i] = layout.toScreenR(-profile.get(i)[1]);
        }
        upperX[n + 1] = layout.toScreenZ(profile.get(n - 1)[0]);
        upperY[n + 1] = layout.axisR;
        gc.setFill(Color.web("#4a90b8", 0.5));
        gc.fillPolygon(upperX, upperY, n + 2);
        gc.setStroke(Color.web("#0f172a"));
        gc.setLineWidth(2.0);
        gc.strokePolyline(upperX, upperY, n + 2);
        gc.setLineWidth(1.3);
        gc.strokePolyline(lowerX, lowerY, n);
    }

    private static void drawToolpath(GraphicsContext gc, Layout layout, List<GCodeMoveData> moves) {
        double canvasWidth = Math.max(1.0, layout.left + layout.width + MARGIN);
        double canvasHeight = Math.max(1.0, layout.statusTop + STATUS_HEIGHT);
        drawToolpathSinuTrain(gc, layout, null, moves, false, canvasWidth, canvasHeight);
    }

    private static void drawStatusBar(
            GraphicsContext gc,
            double width,
            Layout layout,
            SimulationContext context,
            GCodeMoveData hover,
            boolean darkTheme
    ) {
        double y = layout.statusTop;
        gc.setFill(darkTheme ? Color.web("#111827", 0.92) : Color.web("#f1f5f9", 0.95));
        gc.fillRect(0, y, width, STATUS_HEIGHT);
        gc.setStroke(darkTheme ? Color.web("#334155") : Color.web("#cbd5e1"));
        gc.strokeLine(0, y, width, y);
        double x = 12.0;
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22.0));
        gc.setFill(darkTheme ? Color.web("#f8fafc") : Color.web("#0f172a"));
        double displayX = hover != null ? hover.endX() : 0.0;
        double displayZ = hover != null ? hover.endZ() : 0.0;
        gc.fillText(String.format(Locale.US, "X\u00D8 %.3f", displayX), x, y + 28.0);
        gc.fillText(String.format(Locale.US, "Z %.3f", displayZ), x + 200.0, y + 28.0);
        gc.setFont(Font.font("Segoe UI", 13.0));
        int offset = context.getActiveWorkOffsetCode();
        String offsetText = offset >= 54 ? "G" + offset : "G500";
        WorkpieceDefinition wp = context.getWorkpiece();
        String blank = wp != null && wp.isValid()
                ? String.format(Locale.US, "\u00D8%.0f", wp.getDiameterMm())
                : "\u2014";
        gc.setFill(darkTheme ? Color.web("#94a3b8") : Color.web("#475569"));
        gc.fillText(
                "F " + context.getFeedOverridePercent() + "%  S " + context.getSpindleOverridePercent()
                        + "%  " + offsetText + "  " + blank,
                x + 400.0,
                y + 28.0);
        if (hover != null) {
            gc.fillText("N" + hover.sourceLine(), x + 400.0, y + 48.0);
        }
    }

    /** minZ, maxZ, maxRadius — по контуру детали, не по всей заготовке. */
    private static double[] collectSceneBounds(
            WorkpieceDefinition workpiece,
            List<double[]> profile,
            List<GCodeMoveData> displayMoves
    ) {
        if (profile != null && profile.size() >= 2) {
            double minZ = Double.POSITIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            double maxRadius = 10.0;
            for (double[] point : profile) {
                minZ = Math.min(minZ, point[0]);
                maxZ = Math.max(maxZ, point[0]);
                maxRadius = Math.max(maxRadius, point[1]);
            }
            if (displayMoves != null) {
                for (GCodeMoveData move : displayMoves) {
                    maxRadius = Math.max(
                            maxRadius,
                            LatheMeshBuilder.toRadius(Math.max(move.startX(), move.endX())));
                }
            }
            return new double[]{minZ, maxZ, maxRadius};
        }
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        double maxRadius = 10.0;
        if (workpiece != null && workpiece.isValid()) {
            minZ = workpiece.getZMin();
            maxZ = workpiece.getZMax();
            maxRadius = workpiece.getStockRadiusMm();
        }
        if (displayMoves != null) {
            for (GCodeMoveData move : displayMoves) {
                maxRadius = Math.max(maxRadius, LatheMeshBuilder.toRadius(Math.max(move.startX(), move.endX())));
                minZ = Math.min(minZ, Math.min(move.startZ(), move.endZ()));
                maxZ = Math.max(maxZ, Math.max(move.startZ(), move.endZ()));
            }
        }
        if (!Double.isFinite(minZ)) {
            minZ = 0.0;
            maxZ = 100.0;
        }
        return new double[]{minZ, maxZ, maxRadius};
    }

    private static double[] profileZExtent(List<double[]> profile) {
        double zStart = profile.get(0)[0];
        double zEnd = profile.get(profile.size() - 1)[0];
        for (double[] point : profile) {
            zStart = Math.min(zStart, point[0]);
            zEnd = Math.max(zEnd, point[0]);
        }
        return new double[]{zStart, zEnd};
    }

    private static double chooseStep(double span) {
        span = Math.abs(span);
        if (!Double.isFinite(span) || span <= 0.0) {
            return 1.0;
        }
        if (span <= 0.08) {
            return 0.01;
        }
        if (span <= 0.25) {
            return 0.02;
        }
        if (span <= 0.6) {
            return 0.05;
        }
        if (span <= 1.2) {
            return 0.1;
        }
        if (span <= 2.5) {
            return 0.2;
        }
        if (span <= 6.0) {
            return 0.5;
        }
        if (span <= 12.0) {
            return 1.0;
        }
        if (span <= 30.0) {
            return 2.0;
        }
        if (span <= 70.0) {
            return 5.0;
        }
        if (span <= 160.0) {
            return 10.0;
        }
        if (span <= 400.0) {
            return 20.0;
        }
        if (span <= 800.0) {
            return 50.0;
        }
        if (span <= 1600.0) {
            return 100.0;
        }
        if (span <= 4000.0) {
            return 250.0;
        }
        if (span <= 8000.0) {
            return 500.0;
        }
        return 1000.0;
    }

    public record Layout(
            double left,
            double top,
            double width,
            double height,
            double minR,
            double maxR,
            double minZ,
            double maxZ,
            double scale,
            double axisR,
            double statusTop,
            boolean halfSectionAxis
    ) {
        public double toScreenZ(double z) {
            return this.left + (z - this.minZ) * this.scale;
        }

        public double toScreenR(double radius) {
            if (this.halfSectionAxis) {
                return this.axisR - radius * this.scale;
            }
            return this.axisR - (radius - this.minR) * this.scale;
        }
    }
}
