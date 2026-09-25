package com.sergey.pisarev.service;

import com.sergey.pisarev.model.SimulationContext;
import com.sergey.pisarev.model.WorkpieceDefinition;
import java.util.List;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;

/**
 * Сетка координат Z / R в плоскости профиля (X — радиус, Y — ось Z программы).
 */
public final class LatheSideViewGrid3d {
    private static final double LINE_RADIUS_MM = 0.35;

    private LatheSideViewGrid3d() {
    }

    public static Group build(SimulationContext context, List<double[]> profile, boolean darkTheme) {
        double[] bounds = bounds(context, profile);
        double zMin = bounds[0];
        double zMax = bounds[1];
        double maxR = bounds[2];
        double stepZ = chooseStep(zMax - zMin);
        double stepR = chooseStep(maxR * 2.0);
        zMin = Math.floor(zMin / stepZ) * stepZ;
        zMax = Math.ceil(zMax / stepZ) * stepZ;
        maxR = Math.ceil(maxR / stepR) * stepR;
        Color minor = darkTheme ? Color.web("#334155", 0.9) : Color.web("#e2e8f0");
        Color major = darkTheme ? Color.web("#64748b", 0.98) : Color.web("#94a3b8");
        Color axisZ = Color.web("#3b82f6", 0.55);
        Color axisR = Color.web("#dc2626", 0.75);
        Group group = new Group();
        for (double z = zMin; z <= zMax + 0.001; z += stepZ) {
            boolean majorLine = Math.abs(z % (stepZ * 5.0)) < 0.001 || stepZ >= 500.0;
            group.getChildren().add(lineSegment(
                    0.0,
                    z,
                    maxR * 1.06,
                    z,
                    majorLine ? major : minor));
        }
        for (double r = 0.0; r <= maxR + 0.001; r += stepR) {
            boolean majorLine = Math.abs(r % (stepR * 5.0)) < 0.001 || stepR >= 500.0;
            group.getChildren().add(lineSegment(
                    r,
                    zMin,
                    r,
                    zMax,
                    majorLine ? major : minor));
        }
        if (zMin <= 0.0 && zMax >= 0.0) {
            group.getChildren().add(lineSegment(0.0, 0.0, maxR * 1.06, 0.0, axisZ));
        }
        group.getChildren().add(lineSegment(0.0, zMin, 0.0, zMax, axisR));
        return group;
    }

    private static Cylinder lineSegment(double x0, double y0, double x1, double y1, Color color) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double length = Math.hypot(dx, dy);
        if (length < 0.05) {
            length = 0.05;
        }
        Cylinder cylinder = new Cylinder(LINE_RADIUS_MM, length);
        PhongMaterial material = new PhongMaterial(color);
        material.setSpecularColor(Color.TRANSPARENT);
        cylinder.setMaterial(material);
        cylinder.setTranslateX((x0 + x1) / 2.0);
        cylinder.setTranslateY((y0 + y1) / 2.0);
        double angleZ = Math.toDegrees(Math.atan2(dy, dx)) - 90.0;
        cylinder.getTransforms().add(new Rotate(angleZ, Rotate.Z_AXIS));
        return cylinder;
    }

    private static double[] bounds(SimulationContext context, List<double[]> profile) {
        if (profile != null && profile.size() >= 2) {
            double zMin = Double.POSITIVE_INFINITY;
            double zMax = Double.NEGATIVE_INFINITY;
            double maxR = 10.0;
            for (double[] point : profile) {
                zMin = Math.min(zMin, point[0]);
                zMax = Math.max(zMax, point[0]);
                maxR = Math.max(maxR, point[1]);
            }
            double padZ = Math.max(12.0, (zMax - zMin) * 0.04);
            maxR = Math.max(maxR * 1.12, 20.0);
            return new double[]{zMin - padZ, zMax + padZ, maxR};
        }
        WorkpieceDefinition workpiece = context != null ? context.getWorkpiece() : null;
        if (workpiece != null && workpiece.isValid()) {
            double padZ = Math.max(12.0, workpiece.getLengthMm() * 0.04);
            return new double[]{
                    workpiece.getZMin() - padZ,
                    workpiece.getZMax() + padZ,
                    workpiece.getStockRadiusMm() * 1.12};
        }
        return new double[]{-100.0, 100.0, 50.0};
    }

    private static double chooseStep(double span) {
        if (span <= 120.0) {
            return 10.0;
        }
        if (span <= 400.0) {
            return 25.0;
        }
        if (span <= 1200.0) {
            return 50.0;
        }
        if (span <= 4000.0) {
            return 100.0;
        }
        return 500.0;
    }
}
