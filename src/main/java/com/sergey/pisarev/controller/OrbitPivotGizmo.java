package com.sergey.pisarev.controller;

import javafx.scene.DepthTest;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;

/**
 * Видимый pivot орбиты (как в Blender): центр + три кольца.
 */
final class OrbitPivotGizmo {
    private static final int RING_SEGMENTS = 32;
    private static final double RING_RADIUS = 28.0;
    private static final double DOT_RADIUS = 1.8;

    private final Group root = new Group();

    OrbitPivotGizmo() {
        PhongMaterial coreMaterial = new PhongMaterial(Color.web("#ff9f43"));
        coreMaterial.setSpecularColor(Color.web("#ffd28a"));
        coreMaterial.setSpecularPower(24.0);
        Sphere core = new Sphere(6.5);
        core.setMaterial(coreMaterial);
        core.setCullFace(CullFace.NONE);
        core.setDepthTest(DepthTest.DISABLE);
        this.root.getChildren().add(core);

        this.root.getChildren().add(this.buildRing(0.0, Color.web("#fbbf24", 0.95)));
        Group ringY = this.buildRing(90.0, Color.web("#ffffff", 0.88));
        ringY.setRotate(90.0);
        ringY.setRotationAxis(Rotate.X_AXIS);
        this.root.getChildren().add(ringY);
        Group ringZ = this.buildRing(90.0, Color.web("#38bdf8", 0.85));
        ringZ.setRotate(90.0);
        ringZ.setRotationAxis(Rotate.Y_AXIS);
        this.root.getChildren().add(ringZ);
    }

    Group getRoot() {
        return this.root;
    }

    void setVisible(boolean visible) {
        this.root.setVisible(visible);
        this.root.setManaged(visible);
    }

    void setPosition(double x, double y, double z) {
        this.root.setTranslateX(x);
        this.root.setTranslateY(y);
        this.root.setTranslateZ(z);
    }

    void setScale(double scale) {
        this.root.setScaleX(scale);
        this.root.setScaleY(scale);
        this.root.setScaleZ(scale);
    }

    private Group buildRing(double tiltDeg, Color color) {
        Group ring = new Group();
        PhongMaterial material = new PhongMaterial(color);
        material.setSpecularColor(Color.WHITE);
        material.setSpecularPower(16.0);
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double angle = Math.PI * 2.0 * i / RING_SEGMENTS;
            Sphere dot = new Sphere(DOT_RADIUS);
            dot.setMaterial(material);
            dot.setDrawMode(DrawMode.FILL);
            dot.setDepthTest(DepthTest.DISABLE);
            dot.setTranslateX(RING_RADIUS * Math.cos(angle));
            dot.setTranslateY(RING_RADIUS * Math.sin(angle));
            ring.getChildren().add(dot);
        }
        if (Math.abs(tiltDeg) > 0.001) {
            ring.getTransforms().add(new Rotate(tiltDeg, Rotate.X_AXIS));
        }
        return ring;
    }
}
