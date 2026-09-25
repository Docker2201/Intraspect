package com.sergey.pisarev.controller;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Rotate;

/** Вращение детали вокруг pivot (ЛКМ по кольцу gizmo). */
final class PivotGizmoRotator {
    private final Group pivotRotateGroup = new Group();
    private final Affine partRotation = new Affine();

    PivotGizmoRotator() {
        this.pivotRotateGroup.getTransforms().add(this.partRotation);
    }

    Group getPivotRotateGroup() {
        return this.pivotRotateGroup;
    }

    void reset() {
        this.partRotation.setToIdentity();
    }

    void rotateAroundSceneAxis(Point3D axisScene, double deltaDeg) {
        if (axisScene == null || !Double.isFinite(deltaDeg) || Math.abs(deltaDeg) < 1.0e-6) {
            return;
        }
        Point3D axis = axisScene.normalize();
        Rotate delta = new Rotate(deltaDeg, axis);
        Affine step = new Affine();
        step.append(delta);
        this.partRotation.append(step);
    }
}
