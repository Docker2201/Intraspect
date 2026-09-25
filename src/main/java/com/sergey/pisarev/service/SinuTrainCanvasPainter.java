package com.sergey.pisarev.service;

import com.sergey.pisarev.model.GCodeMoveData;
import com.sergey.pisarev.model.WorkpieceDefinition;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/** 2D боковой вид SinuTrain (Z горизонтально, Xø вертикально). */
public final class SinuTrainCanvasPainter {
    public static final double STATUS_BAR_HEIGHT = 72.0;
    public static final double TOP_MARGIN = 44.0;
    public static final double SIDE_MARGIN = 52.0;

    private SinuTrainCanvasPainter() {
    }

    public static void fillBackground(GraphicsContext gc, double width, double height, boolean darkTheme) {
        gc.clearRect(0, 0, width, height);
        if (darkTheme) {
            gc.setFill(new RadialGradient(
                    0.5, 0.48, 0.5, 0.5, 0.85, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#3a3a3a")),
                    new Stop(1, Color.web("#050505"))));
        } else {
            gc.setFill(new RadialGradient(
                    0.5, 0.45, 0.5, 0.5, 0.9, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#ffffff")),
                    new Stop(1, Color.web("#9aa8b5"))));
        }
        gc.fillRect(0, 0, width, height);
    }

    public static void drawPlotPanel(
            GraphicsContext gc,
            double originX,
            double originY,
            double minZ,
            double maxZ,
            double minRadius,
            double maxRadius,
            double scale,
            boolean darkTheme
    ) {
        double plotW = Math.max(1.0, (maxZ - minZ) * scale);
        double plotH = Math.max(1.0, (maxRadius - minRadius) * scale);
        gc.setFill(darkTheme ? Color.web("#1e293b", 0.35) : Color.web("#e8f2fa", 0.92));
        gc.fillRect(originX, originY, plotW, plotH);
        gc.setStroke(darkTheme ? Color.web("#334155") : Color.web("#9eb8d4"));
        gc.setLineWidth(1.0);
        gc.strokeRect(originX, originY, plotW, plotH);
    }

    public static void drawValueGrid(
            GraphicsContext gc,
            double originX,
            double originY,
            double minZ,
            double maxZ,
            double minRadius,
            double maxRadius,
            double scale,
            boolean darkTheme
    ) {
        double plotW = Math.max(1.0, (maxZ - minZ) * scale);
        double plotH = Math.max(1.0, (maxRadius - minRadius) * scale);
        double stepZ = chooseStep(maxZ - minZ);
        double stepR = chooseStep(maxRadius - minRadius);
        gc.setLineWidth(1.0);
        double zStart = Math.floor(minZ / stepZ) * stepZ;
        for (double z = zStart; z <= maxZ + 0.001; z += stepZ) {
            double x = originX + (z - minZ) * scale;
            boolean major = Math.abs(z % (stepZ * 5.0)) < 0.001 || stepZ >= 500.0;
            gc.setStroke(major
                    ? Color.web(darkTheme ? "#475569" : "#9eb8d4")
                    : Color.web(darkTheme ? "#1e293b" : "#d4e8f5"));
            gc.strokeLine(x, originY, x, originY + plotH);
            if (major) {
                gc.setFill(Color.web(darkTheme ? "#94a3b8" : "#4a6a8a"));
                gc.setFont(Font.font(11.0));
                gc.fillText(String.format(Locale.US, "%.0f", z), x + 2.0, originY + plotH + 14.0);
            }
        }
        double rStart = Math.floor(minRadius / stepR) * stepR;
        for (double r = rStart; r <= maxRadius + 0.001; r += stepR) {
            double y = originY + (maxRadius - r) * scale;
            boolean major = Math.abs(r % (stepR * 5.0)) < 0.001 || stepR >= 500.0;
            gc.setStroke(major
                    ? Color.web(darkTheme ? "#475569" : "#9eb8d4")
                    : Color.web(darkTheme ? "#1e293b" : "#d4e8f5"));
            gc.strokeLine(originX, y, originX + plotW, y);
            if (major) {
                gc.setFill(Color.web(darkTheme ? "#94a3b8" : "#4a6a8a"));
                gc.setFont(Font.font(11.0));
                gc.fillText(String.format(Locale.US, "%.0f", r), originX - 40.0, y + 4.0);
            }
        }
    }

    public static void drawReferenceAxes(
            GraphicsContext gc,
            double originX,
            double originY,
            double minZ,
            double maxZ,
            double minRadius,
            double maxRadius,
            double scale
    ) {
        double plotW = Math.max(1.0, (maxZ - minZ) * scale);
        double plotH = Math.max(1.0, (maxRadius - minRadius) * scale);
        double axisY = originY + (maxRadius - 0.0) * scale;
        if (minZ <= 0.0 && maxZ >= 0.0) {
            double zZero = originX + (0.0 - minZ) * scale;
            gc.setStroke(Color.web("#2563eb", 0.55));
            gc.setLineWidth(1.5);
            gc.strokeLine(zZero, originY, zZero, originY + plotH);
        }
        gc.setStroke(Color.web("#1e40af"));
        gc.setLineWidth(2.0);
        gc.strokeLine(originX, axisY, originX + plotW, axisY);
        gc.setFill(Color.web("#1e3a5f"));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12.0));
        gc.fillText("Z", originX + plotW - 18.0, axisY - 8.0);
        gc.fillText("X\u00D8", originX + 6.0, originY + 14.0);
    }

    public static void drawStock(
            GraphicsContext gc,
            WorkpieceDefinition workpiece,
            double originX,
            double originY,
            double minZ,
            double maxZ,
            double minRadius,
            double maxRadius,
            double scale
    ) {
        if (workpiece == null || !workpiece.isValid()) {
            return;
        }
        double sx0 = originX + (workpiece.getZMin() - minZ) * scale;
        double sx1 = originX + (workpiece.getZMax() - minZ) * scale;
        double stockRadius = workpiece.getStockRadiusMm();
        double syTop = originY + (maxRadius - stockRadius) * scale;
        double syBottom = originY + (maxRadius + stockRadius) * scale;
        double w = Math.max(1.0, sx1 - sx0);
        double h = Math.max(1.0, syBottom - syTop);
        LinearGradient gradient = new LinearGradient(
                0, syTop, 0, syBottom, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#d4e8f5")),
                new Stop(0.5, Color.web("#8eb8d4")),
                new Stop(1, Color.web("#6a9fc4")));
        gc.setFill(gradient);
        gc.fillRect(sx0, syTop, w, h);
        gc.setStroke(Color.web("#3d6f96"));
        gc.setLineWidth(1.2);
        gc.strokeRect(sx0, syTop, w, h);
    }

    public static void drawToolpath(
            GraphicsContext gc,
            List<GCodeMoveData> moves,
            double originX,
            double originY,
            double minZ,
            double maxZ,
            double minRadius,
            double maxRadius,
            double scale
    ) {
        for (GCodeMoveData move : moves) {
            double startR = LatheMeshBuilder.toRadius(move.startX());
            double endR = LatheMeshBuilder.toRadius(move.endX());
            double sx = originX + (move.startZ() - minZ) * scale;
            double sy = originY + (maxRadius - startR) * scale;
            double ex = originX + (move.endZ() - minZ) * scale;
            double ey = originY + (maxRadius - endR) * scale;
            if (move.rapid()) {
                gc.setStroke(Color.web("#0891b2", 0.55));
                gc.setLineWidth(1.0);
                gc.setLineDashes(6.0, 5.0);
            } else if (move.arcSegment()) {
                gc.setStroke(Color.web("#92400e", 0.75));
                gc.setLineWidth(1.2);
                gc.setLineDashes(null);
            } else {
                gc.setStroke(Color.web("#dc2626", 0.85));
                gc.setLineWidth(1.35);
                gc.setLineDashes(null);
            }
            gc.strokeLine(sx, sy, ex, ey);
            gc.setLineDashes(null);
        }
    }

    public static void drawStatusBar(
            GraphicsContext gc,
            double width,
            double height,
            double displayDiameter,
            double displayZ,
            int activeOffset,
            int feedOverride,
            int spindleOverride,
            Optional<WorkpieceDefinition> workpiece,
            String statusText,
            String currentBlock,
            boolean darkTheme
    ) {
        double y = height - STATUS_BAR_HEIGHT;
        gc.setFill(darkTheme ? Color.web("#111827", 0.94) : Color.web("#e8eef4", 0.96));
        gc.fillRect(0, y, width, STATUS_BAR_HEIGHT);
        gc.setStroke(darkTheme ? Color.web("#334155") : Color.web("#94a3b8"));
        gc.setLineWidth(1.0);
        gc.strokeLine(0, y, width, y);
        gc.setFill(darkTheme ? Color.web("#f8fafc") : Color.web("#0f172a"));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22.0));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText(String.format(Locale.US, "X\u00D8 %.3f", displayDiameter), 14.0, y + 26.0);
        gc.fillText(String.format(Locale.US, "Z %.3f", displayZ), 210.0, y + 26.0);
        gc.setFont(Font.font("Segoe UI", 13.0));
        gc.setFill(darkTheme ? Color.web("#94a3b8") : Color.web("#475569"));
        // Для прямоугольной заготовки Ø ничего не говорит — показываем габариты.
        String blank = workpiece.map(WorkpieceDefinition::describeSize).orElse("\u2014");
        gc.fillText(
                "F " + feedOverride + "%  S " + spindleOverride + "%  G" + (activeOffset >= 54 ? activeOffset : 500)
                        + "  " + blank + "  " + statusText,
                400.0,
                y + 28.0);
        if (currentBlock != null && !currentBlock.isBlank()) {
            gc.setFont(Font.font("Consolas", 14.0));
            gc.setFill(darkTheme ? Color.web("#cbd5e1") : Color.web("#1e3a5f"));
            gc.fillText(currentBlock.trim(), 14.0, y + 54.0);
        }
    }

    private static double chooseStep(double span) {
        if (span <= 120.0) {
            return 10.0;
        }
        if (span <= 400.0) {
            return 20.0;
        }
        if (span <= 1200.0) {
            return 100.0;
        }
        if (span <= 6000.0) {
            return 500.0;
        }
        return 1000.0;
    }
}
