package com.sergey.pisarev.service;

import com.sergey.pisarev.model.SimulationContext;
import com.sergey.pisarev.model.WorkpieceDefinition;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.transform.Rotate;

/**
 * SP1 (главный шпиндель), оси X/Z, SP3 (приводной инструмент) — SinuTrain/DMG токарная схема.
 * Координаты modelRoot: X = радиус, Y = Z программы, Z = 0.
 */
public final class LatheMachineVisual3d {
    private static final String TAG = "lathe-machine-visual";

    private final Group root = new Group();

    private LatheMachineVisual3d() {
    }

    public static Group build(SimulationContext context) {
        LatheMachineVisual3d visual = new LatheMachineVisual3d();
        visual.buildInternal(context);
        return visual.root;
    }

    private void buildInternal(SimulationContext context) {
        double zMin = -80.0;
        double zMax = 80.0;
        double maxR = 60.0;
        WorkpieceDefinition workpiece = context != null ? context.getWorkpiece() : null;
        if (workpiece != null && workpiece.isValid()) {
            zMin = workpiece.getZMin();
            zMax = workpiece.getZMax();
            maxR = workpiece.getStockRadiusMm();
        }
        double zMid = (zMin + zMax) / 2.0;
        double zSpan = Math.max(20.0, zMax - zMin);

        Cylinder zAxis = new Cylinder(1.2, zSpan);
        zAxis.setMaterial(axisMaterial(Color.web("#38bdf8")));
        zAxis.setTranslateY(zMid);

        Cylinder xAxis = new Cylinder(1.2, maxR * 1.2);
        xAxis.setMaterial(axisMaterial(Color.web("#f87171")));
        xAxis.setTranslateY(zMid);
        xAxis.getTransforms().add(new Rotate(90, Rotate.Z_AXIS));

        Cylinder sp1 = new Cylinder(Math.max(8.0, maxR * 0.14), Math.max(12.0, zSpan * 0.07));
        sp1.setMaterial(axisMaterial(Color.web("#94a3b8")));
        sp1.setTranslateY(zMin - zSpan * 0.035);

        Cylinder sp3 = new Cylinder(Math.max(4.0, maxR * 0.05), Math.max(20.0, maxR * 0.4));
        sp3.setMaterial(axisMaterial(Color.web("#a78bfa")));
        sp3.setTranslateX(maxR * 0.82);
        sp3.setTranslateY(zMax - zSpan * 0.1);
        sp3.getTransforms().add(new Rotate(90, Rotate.Z_AXIS));

        for (javafx.scene.Node node : new javafx.scene.Node[]{zAxis, xAxis, sp1, sp3}) {
            node.setUserData(TAG);
        }
        this.root.getChildren().addAll(zAxis, xAxis, sp1, sp3);
        this.root.setUserData(TAG);
    }

    private static PhongMaterial axisMaterial(Color diffuse) {
        PhongMaterial material = new PhongMaterial(diffuse);
        material.setSpecularColor(Color.WHITE);
        material.setSpecularPower(12.0);
        return material;
    }

    public static boolean isMachineNode(javafx.scene.Node node) {
        for (javafx.scene.Node current = node; current != null; current = current.getParent()) {
            if (TAG.equals(current.getUserData())) {
                return true;
            }
        }
        return false;
    }
}
