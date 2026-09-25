package com.sergey.pisarev.controller;

import javafx.geometry.Point2D;
import javafx.geometry.Point3D;
import javafx.geometry.Bounds;
import javafx.scene.Camera;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SubScene;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;

/**
 * Орбита камеры вокруг pivot (как СКМ в Blender): деталь неподвижна, вращается только камера.
 */
final class LatheOrbitCamera {
    private static final double MIN_DISTANCE = 20.0;
    private static final double MAX_DISTANCE = 160000.0;
    private static final double ORBIT_DEG_PER_PX = 0.14 / 1.5;
    private static final double PAN_SCALE = 1.0 / 1.5;

    private final Group contentRoot = new Group();
    private final PerspectiveCamera camera;
    private double azimuthDeg;
    private double elevationDeg = 12.0;
    private double distance = 1200.0;
    private double panX;
    private double panY;
    private double pivotX;
    private double pivotY;
    private double pivotZ;
    private boolean orbitGestureActive;
    private boolean orbitLocked;

    LatheOrbitCamera() {
        this.camera = new PerspectiveCamera(true);
        this.camera.setFieldOfView(38.0);
        this.camera.setNearClip(0.5);
        this.camera.setFarClip(100000.0);
        this.apply();
    }

    Group getContentRoot() {
        return this.contentRoot;
    }

    /** @deprecated используйте {@link #getContentRoot()} */
    Group getRotateGroup() {
        return this.contentRoot;
    }

    Camera getCamera() {
        return this.camera;
    }

    double getDistance() {
        return this.distance;
    }

    void resetAngles() {
        this.applySideView();
        this.panX = 0.0;
        this.panY = 0.0;
        this.resetPivot();
        this.apply();
    }

    void resetPivot() {
        this.pivotX = 0.0;
        this.pivotY = 0.0;
        this.pivotZ = 0.0;
        this.apply();
    }

    void setTarget(double x, double y, double z) {
        this.setPivot(x, y, z, null);
    }

    void setPivot(double x, double y, double z) {
        this.setPivot(x, y, z, null);
    }

    void setPivot(double x, double y, double z, SubScene subScene) {
        this.setPivotInternal(x, y, z, subScene, null, Double.NaN, Double.NaN, false);
    }

    void setPivotQuiet(double x, double y, double z) {
        this.setPivotInternal(x, y, z, null, null, Double.NaN, Double.NaN, true);
    }

    void setPivotWithScreenAnchor(
            double x,
            double y,
            double z,
            SubScene subScene,
            Group pivotMarker,
            double anchorSceneX,
            double anchorSceneY
    ) {
        boolean wasGesture = this.orbitGestureActive;
        this.orbitGestureActive = false;
        this.setPivotInternal(x, y, z, subScene, pivotMarker, anchorSceneX, anchorSceneY, false);
        this.orbitGestureActive = wasGesture;
    }

    private void setPivotInternal(
            double x,
            double y,
            double z,
            SubScene subScene,
            Group pivotMarker,
            double anchorScreenX,
            double anchorScreenY,
            boolean quiet
    ) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            this.resetPivot();
            return;
        }
        double prevX = this.pivotX;
        double prevY = this.pivotY;
        double prevZ = this.pivotZ;
        this.pivotX = x;
        this.pivotY = y;
        this.pivotZ = z;
        this.apply();
        if (quiet
                || this.orbitGestureActive
                || subScene == null
                || !Double.isFinite(anchorScreenX)
                || !Double.isFinite(anchorScreenY)) {
            return;
        }
        if (Math.abs(prevX - x) > 0.05 || Math.abs(prevY - y) > 0.05 || Math.abs(prevZ - z) > 0.05) {
            compensatePanForPivotMove(subScene, pivotMarker, anchorScreenX, anchorScreenY);
        }
    }

    /** Pan в SubScene: pivotMarker — узел в точке вращения на детали. */
    private void compensatePanForPivotMove(
            SubScene subScene,
            Group pivotMarker,
            double anchorSceneX,
            double anchorSceneY
    ) {
        if (subScene == null || pivotMarker == null) {
            return;
        }
        Point2D pivotInSub = this.markerToSubScene(subScene, pivotMarker);
        Point2D anchorInSub = this.scenePointToSubScene(subScene, anchorSceneX, anchorSceneY);
        if (pivotInSub == null || anchorInSub == null) {
            return;
        }
        this.panX += anchorInSub.getX() - pivotInSub.getX();
        this.panY += anchorInSub.getY() - pivotInSub.getY();
        this.apply();
    }

    private Point2D markerToSubScene(SubScene subScene, Group marker) {
        Point3D scene = marker.localToScene(0.0, 0.0, 0.0, true);
        if (scene == null) {
            return null;
        }
        return this.scenePointToSubScene(subScene, scene.getX(), scene.getY());
    }

    private Point2D scenePointToSubScene(SubScene subScene, double sceneX, double sceneY) {
        Point3D local = subScene.sceneToLocal(new Point3D(sceneX, sceneY, 0.0));
        if (local == null || !Double.isFinite(local.getX())) {
            return null;
        }
        return new Point2D(local.getX(), local.getY());
    }

    void endOrbitGesture() {
        this.orbitGestureActive = false;
    }

    void applySideView() {
        this.azimuthDeg = 0.0;
        this.elevationDeg = 12.0;
        this.apply();
    }

    /** Фиксированный боковой вид профиля (без наклона). */
    void applyLockedSideView() {
        this.azimuthDeg = 0.0;
        this.elevationDeg = 0.0;
        this.panX = 0.0;
        this.panY = 0.0;
        this.apply();
    }

    void setOrbitLocked(boolean locked) {
        this.orbitLocked = locked;
    }

    boolean isOrbitLocked() {
        return this.orbitLocked;
    }

    /** Стартовый ракурс 3D (как в Chekator 3.06). */
    void applySimulationView() {
        this.applySimulationView(false);
    }

    void applySimulationView(boolean vertical) {
        this.azimuthDeg = 28.0;
        this.elevationDeg = vertical ? -30.0 : 16.0;
        this.apply();
    }

    void applyEndView() {
        this.applySimulationView();
    }

    void setDistance(double value) {
        this.distance = clampDistance(value);
        this.apply();
    }

    void zoomByWheel(double deltaY) {
        if (!Double.isFinite(deltaY) || Math.abs(deltaY) < 0.01) {
            return;
        }
        double factor = Math.pow(1.0018, -deltaY);
        this.setDistance(this.distance * factor);
    }

    void zoomByWheelAt(double deltaY, double anchorX, double anchorY, double viewWidth, double viewHeight) {
        if (!Double.isFinite(deltaY) || Math.abs(deltaY) < 0.01) {
            return;
        }
        double previousDistance = this.distance;
        double factor = Math.pow(1.0018, -deltaY);
        double nextDistance = clampDistance(previousDistance * factor);
        if (!Double.isFinite(previousDistance) || previousDistance <= 0.0) {
            this.setDistance(nextDistance);
            return;
        }
        double ratio = nextDistance / previousDistance;
        this.distance = nextDistance;
        if (Double.isFinite(anchorX)
                && Double.isFinite(anchorY)
                && Double.isFinite(viewWidth)
                && Double.isFinite(viewHeight)
                && viewWidth > 1.0
                && viewHeight > 1.0) {
            double panScale = PAN_SCALE;
            if (this.orbitLocked && viewHeight > 80.0) {
                double fovRad = Math.toRadians(this.camera.getFieldOfView());
                panScale = 2.0 * previousDistance * Math.tan(fovRad / 2.0) / viewHeight;
            }
            this.panX += (1.0 - ratio) * (anchorX - viewWidth * 0.5) * panScale;
            this.panY += (1.0 - ratio) * (anchorY - viewHeight * 0.5) * panScale;
        }
        this.apply();
    }

    void zoomIn() {
        this.setDistance(this.distance * 0.9);
    }

    void zoomOut() {
        this.setDistance(this.distance * 1.1);
    }

    void beginOrbitGesture(
            double pickRadius,
            double pickZ,
            double partCenterRadius,
            double partCenterZ,
            double halfSpanZ,
            double maxRadius
    ) {
        this.orbitGestureActive = true;
    }

    void dragOrbit(double deltaX, double deltaY) {
        if (this.orbitLocked) {
            return;
        }
        this.azimuthDeg -= deltaX * ORBIT_DEG_PER_PX;
        this.elevationDeg -= deltaY * ORBIT_DEG_PER_PX;
        this.apply();
    }

    Point3D pivotLocalPoint() {
        return new Point3D(this.pivotX, this.pivotY, this.pivotZ);
    }

    boolean hasPivot() {
        return Math.abs(this.pivotX) > 0.001
                || Math.abs(this.pivotY) > 0.001
                || Math.abs(this.pivotZ) > 0.001;
    }

    void dragPan(double deltaX, double deltaY) {
        this.dragPan(deltaX, deltaY, false, 0.0);
    }

    /** Панорама; при {@code allowWhenOrbitLocked} работает в «Вид сбоку». */
    void dragPan(double deltaX, double deltaY, boolean allowWhenOrbitLocked) {
        this.dragPan(deltaX, deltaY, allowWhenOrbitLocked, 0.0);
    }

    /**
     * Панорама с поправкой 1:1 на пиксель, если viewportHeight задан (для заблокированного вида сбоку).
     */
    void dragPan(double deltaX, double deltaY, boolean allowWhenOrbitLocked, double viewportHeightPx) {
        if (this.orbitLocked && !allowWhenOrbitLocked) {
            return;
        }
        double scale;
        if (this.orbitLocked && viewportHeightPx > 80.0) {
            double fovRad = Math.toRadians(this.camera.getFieldOfView());
            scale = 2.0 * this.distance * Math.tan(fovRad / 2.0) / viewportHeightPx;
        } else {
            scale = PAN_SCALE;
        }
        this.panX += deltaX * scale;
        this.panY += deltaY * scale;
        this.apply();
    }

    void resetPan() {
        this.panX = 0.0;
        this.panY = 0.0;
        this.apply();
    }

    void addPanScreenOffset(double deltaScreenX, double deltaScreenY) {
        if (!Double.isFinite(deltaScreenX) || !Double.isFinite(deltaScreenY)) {
            return;
        }
        this.panX += deltaScreenX * PAN_SCALE;
        this.panY += deltaScreenY * PAN_SCALE;
        this.apply();
    }

    void fitToBounds(double spanDiameter, double spanZ, double viewWidth, double viewHeight) {
        fitToBounds(spanDiameter, spanZ, viewWidth, viewHeight, 1.22);
    }

    void fitToBounds(double spanDiameter, double spanZ, double viewWidth, double viewHeight, double marginFactor) {
        double safeD = Math.max(10.0, spanDiameter);
        double safeZ = Math.max(10.0, spanZ);
        double aspect = Math.max(0.25, viewWidth / Math.max(1.0, viewHeight));
        double fovRad = Math.toRadians(this.camera.getFieldOfView());
        double halfFovTan = Math.tan(fovRad / 2.0);
        double distZ = (safeZ * 0.52) / (halfFovTan * aspect);
        double distD = (safeD * 0.52) / halfFovTan;
        this.distance = clampDistance(Math.max(distZ, distD) * marginFactor);
        this.apply();
    }

    /** Точный сдвиг pan, чтобы bbox детали оказался в центре SubScene. */
    void centerProjectedBounds(
            SubScene subScene,
            Group projectionRoot,
            double radiusMin,
            double zMin,
            double radiusMax,
            double zMax
    ) {
        if (subScene == null || projectionRoot == null) {
            return;
        }
        double targetX = Math.max(1.0, subScene.getWidth()) / 2.0;
        double targetY = Math.max(1.0, subScene.getHeight()) / 2.0;
        double[] center = computeProjectedCenter(subScene, projectionRoot, radiusMin, zMin, radiusMax, zMax);
        if (center == null) {
            return;
        }
        double dxScreen0 = targetX - center[0];
        double dyScreen0 = targetY - center[1];
        if (Math.abs(dxScreen0) < 0.5 && Math.abs(dyScreen0) < 0.5) {
            return;
        }
        // Калибровка «пиксели на единицу pan»: пробное смещение, измеряем эффект.
        double probe = Math.max(5.0, Math.min(200.0, this.distance * 0.05));
        double basePanX = this.panX;
        double basePanY = this.panY;
        this.panX = basePanX + probe;
        this.apply();
        double[] afterX = computeProjectedCenter(subScene, projectionRoot, radiusMin, zMin, radiusMax, zMax);
        this.panX = basePanX;
        this.panY = basePanY + probe;
        this.apply();
        double[] afterY = computeProjectedCenter(subScene, projectionRoot, radiusMin, zMin, radiusMax, zMax);
        this.panX = basePanX;
        this.panY = basePanY;
        this.apply();
        if (afterX == null || afterY == null) {
            return;
        }
        double pxPerUnitX = (afterX[0] - center[0]) / probe;
        double pxPerUnitY = (afterY[1] - center[1]) / probe;
        if (Math.abs(pxPerUnitX) < 1.0e-5 || Math.abs(pxPerUnitY) < 1.0e-5) {
            return;
        }
        double panX = basePanX + dxScreen0 / pxPerUnitX;
        double panY = basePanY + dyScreen0 / pxPerUnitY;
        if (!Double.isFinite(panX) || !Double.isFinite(panY)) {
            return;
        }
        this.panX = panX;
        this.panY = panY;
        this.apply();
    }

    void centerProjectedBounds(SubScene subScene, Group projectionRoot, Bounds bounds) {
        if (subScene == null || projectionRoot == null || bounds == null || bounds.isEmpty()) {
            return;
        }
        double targetX = Math.max(1.0, subScene.getWidth()) / 2.0;
        double targetY = Math.max(1.0, subScene.getHeight()) / 2.0;
        double[] center = computeProjectedCenter(subScene, projectionRoot, bounds);
        if (center == null) {
            return;
        }
        double dxScreen0 = targetX - center[0];
        double dyScreen0 = targetY - center[1];
        if (Math.abs(dxScreen0) < 0.5 && Math.abs(dyScreen0) < 0.5) {
            return;
        }
        double probe = Math.max(5.0, Math.min(200.0, this.distance * 0.05));
        double basePanX = this.panX;
        double basePanY = this.panY;
        this.panX = basePanX + probe;
        this.apply();
        double[] afterX = computeProjectedCenter(subScene, projectionRoot, bounds);
        this.panX = basePanX;
        this.panY = basePanY + probe;
        this.apply();
        double[] afterY = computeProjectedCenter(subScene, projectionRoot, bounds);
        this.panX = basePanX;
        this.panY = basePanY;
        this.apply();
        if (afterX == null || afterY == null) {
            return;
        }
        double pxPerUnitX = (afterX[0] - center[0]) / probe;
        double pxPerUnitY = (afterY[1] - center[1]) / probe;
        if (Math.abs(pxPerUnitX) < 1.0e-5 || Math.abs(pxPerUnitY) < 1.0e-5) {
            return;
        }
        double panX = basePanX + dxScreen0 / pxPerUnitX;
        double panY = basePanY + dyScreen0 / pxPerUnitY;
        if (!Double.isFinite(panX) || !Double.isFinite(panY)) {
            return;
        }
        this.panX = panX;
        this.panY = panY;
        this.apply();
    }

    private double[] computeProjectedCenter(
            SubScene subScene,
            Group projectionRoot,
            double radiusMin,
            double zMin,
            double radiusMax,
            double zMax
    ) {
        double screenMinX = Double.POSITIVE_INFINITY;
        double screenMaxX = Double.NEGATIVE_INFINITY;
        double screenMinY = Double.POSITIVE_INFINITY;
        double screenMaxY = Double.NEGATIVE_INFINITY;
        boolean any = false;
        double[][] samples = {
                {radiusMin, zMin},
                {radiusMax, zMin},
                {radiusMin, zMax},
                {radiusMax, zMax},
                {0.0, (zMin + zMax) / 2.0}
        };
        for (double[] sample : samples) {
            Point2D point = this.project(subScene, projectionRoot, sample[0], sample[1]);
            if (point == null) {
                continue;
            }
            any = true;
            screenMinX = Math.min(screenMinX, point.getX());
            screenMaxX = Math.max(screenMaxX, point.getX());
            screenMinY = Math.min(screenMinY, point.getY());
            screenMaxY = Math.max(screenMaxY, point.getY());
        }
        if (!any) {
            return null;
        }
        return new double[]{(screenMinX + screenMaxX) / 2.0, (screenMinY + screenMaxY) / 2.0};
    }

    private double[] computeProjectedCenter(SubScene subScene, Group projectionRoot, Bounds bounds) {
        double screenMinX = Double.POSITIVE_INFINITY;
        double screenMaxX = Double.NEGATIVE_INFINITY;
        double screenMinY = Double.POSITIVE_INFINITY;
        double screenMaxY = Double.NEGATIVE_INFINITY;
        boolean any = false;
        double minX = bounds.getMinX();
        double maxX = bounds.getMaxX();
        double minY = bounds.getMinY();
        double maxY = bounds.getMaxY();
        double minZ = bounds.getMinZ();
        double maxZ = bounds.getMaxZ();
        double centerX = (minX + maxX) * 0.5;
        double centerY = (minY + maxY) * 0.5;
        double centerZ = (minZ + maxZ) * 0.5;
        double[][] samples = {
                {minX, minY, minZ},
                {minX, minY, maxZ},
                {minX, maxY, minZ},
                {minX, maxY, maxZ},
                {maxX, minY, minZ},
                {maxX, minY, maxZ},
                {maxX, maxY, minZ},
                {maxX, maxY, maxZ},
                {centerX, centerY, centerZ}
        };
        for (double[] sample : samples) {
            Point2D point = this.projectToSubScene(subScene, projectionRoot, sample[0], sample[1], sample[2]);
            if (point == null) {
                continue;
            }
            any = true;
            screenMinX = Math.min(screenMinX, point.getX());
            screenMaxX = Math.max(screenMaxX, point.getX());
            screenMinY = Math.min(screenMinY, point.getY());
            screenMaxY = Math.max(screenMaxY, point.getY());
        }
        if (!any) {
            return null;
        }
        return new double[]{(screenMinX + screenMaxX) / 2.0, (screenMinY + screenMaxY) / 2.0};
    }

    Point2D project(SubScene subScene, Group projectionRoot, double radiusMm, double zProgram) {
        if (subScene == null || projectionRoot == null) {
            return null;
        }
        Point3D scene = projectionRoot.localToScene(new Point3D(radiusMm, zProgram, 0.0), true);
        if (!Double.isFinite(scene.getX()) || !Double.isFinite(scene.getY())) {
            return null;
        }
        Point3D local3 = subScene.sceneToLocal(scene);
        if (local3 == null || !Double.isFinite(local3.getX()) || !Double.isFinite(local3.getY())) {
            return null;
        }
        return new Point2D(local3.getX(), local3.getY());
    }

    /**
     * Полная проекция world-точки в SubScene-координаты (пиксели) с учётом камеры.
     * Ручная View-Projection матрица вместо Node.localToScreen(), ненадёжной для SubScene.
     *
     * @param subScene      SubScene (нужен для viewport size и FOV)
     * @param projectionRoot узел, в чьём локальном пространстве задана точка
     * @param worldX        радиус (мм)
     * @param worldY        Z программы (мм)
     * @param worldZ        0 (ось Y/Z 3D сцены)
     * @return Point2D в координатах SubScene (пиксели), или null если точка за камерой
     */
    Point2D projectToSubScene(
            SubScene subScene,
            Group projectionRoot,
            double worldX, double worldY, double worldZ
    ) {
        if (subScene == null || projectionRoot == null) return null;
        // Шаг 1: World → внутренние координаты SubScene (без флага rootScene:
        // с ним точка проецировалась бы через камеру во внешнюю сцену и глубина терялась).
        Point3D scenePt = projectionRoot.localToScene(new Point3D(worldX, worldY, worldZ));
        if (scenePt == null || !Double.isFinite(scenePt.getX())) return null;
        // Шаг 2: SubScene → Camera-local (обратные трансформы камеры, те же координаты SubScene)
        Point3D camLocal = this.camera.sceneToLocal(scenePt.getX(), scenePt.getY(), scenePt.getZ());
        if (camLocal == null || !Double.isFinite(camLocal.getX())) return null;
        // Шаг 3: Перспективная проекция вручную
        double viewW = Math.max(1.0, subScene.getWidth());
        double viewH = Math.max(1.0, subScene.getHeight());
        double aspect = viewW / viewH;
        double fovRad = Math.toRadians(this.camera.getFieldOfView());
        double near = this.camera.getNearClip();
        double z = camLocal.getZ();
        if (z <= near) return null;  // за ближней плоскостью отсечения
        double top = near * Math.tan(fovRad / 2.0);
        double right = top * aspect;
        double ndcX = (camLocal.getX() * near) / (z * right);
        double ndcY = (camLocal.getY() * near) / (z * top);
        // Шаг 4: NDC → пиксели SubScene. Ось Y камеры JavaFX направлена вниз,
        // как и пиксельная — без переворота.
        double screenX = (ndcX + 1.0) * 0.5 * viewW;
        double screenY = (ndcY + 1.0) * 0.5 * viewH;
        if (!Double.isFinite(screenX) || !Double.isFinite(screenY)) return null;
        return new Point2D(screenX, screenY);
    }

    Point3D unprojectSubSceneToLocalPlane(
            SubScene subScene,
            Group projectionRoot,
            double screenX,
            double screenY,
            double planeZ
    ) {
        if (subScene == null
                || projectionRoot == null
                || !Double.isFinite(screenX)
                || !Double.isFinite(screenY)
                || !Double.isFinite(planeZ)) {
            return null;
        }
        double viewW = Math.max(1.0, subScene.getWidth());
        double viewH = Math.max(1.0, subScene.getHeight());
        double near = Math.max(0.001, this.camera.getNearClip());
        double far = Math.max(near + 1000.0, Math.min(this.camera.getFarClip() * 0.85, MAX_DISTANCE));
        Point3D nearPoint = this.cameraLocalPointForSubScenePixel(screenX, screenY, near, viewW, viewH);
        Point3D farPoint = this.cameraLocalPointForSubScenePixel(screenX, screenY, far, viewW, viewH);
        if (!isFinitePoint(nearPoint) || !isFinitePoint(farPoint)) {
            return null;
        }
        // Без флага rootScene: луч остаётся во внутренних координатах SubScene,
        // согласованных с projectionRoot.sceneToLocal ниже.
        Point3D nearScene = this.camera.localToScene(nearPoint);
        Point3D farScene = this.camera.localToScene(farPoint);
        if (!isFinitePoint(nearScene) || !isFinitePoint(farScene)) {
            return null;
        }
        Point3D p0 = projectionRoot.sceneToLocal(nearScene);
        Point3D p1 = projectionRoot.sceneToLocal(farScene);
        if (!isFinitePoint(p0) || !isFinitePoint(p1)) {
            return null;
        }
        double dz = p1.getZ() - p0.getZ();
        if (Math.abs(dz) < 1.0e-9) {
            return null;
        }
        double t = (planeZ - p0.getZ()) / dz;
        if (!Double.isFinite(t)) {
            return null;
        }
        Point3D hit = p0.add(p1.subtract(p0).multiply(t));
        return isFinitePoint(hit) ? hit : null;
    }

    private Point3D cameraLocalPointForSubScenePixel(
            double screenX,
            double screenY,
            double depth,
            double viewW,
            double viewH
    ) {
        double ndcX = screenX / viewW * 2.0 - 1.0;
        // Ось Y камеры JavaFX направлена вниз, как и пиксельная — без переворота.
        double ndcY = screenY / viewH * 2.0 - 1.0;
        double near = Math.max(0.001, this.camera.getNearClip());
        double aspect = viewW / viewH;
        double top = near * Math.tan(Math.toRadians(this.camera.getFieldOfView()) / 2.0);
        double right = top * aspect;
        return new Point3D(
                ndcX * depth * right / near,
                ndcY * depth * top / near,
                depth);
    }

    private void apply() {
        this.contentRoot.getTransforms().clear();
        this.camera.getTransforms().clear();
        this.camera.getTransforms().addAll(
                new Translate(this.panX, this.panY, 0),
                new Translate(this.pivotX, this.pivotY, this.pivotZ),
                new Rotate(this.azimuthDeg, Rotate.Y_AXIS),
                new Rotate(this.elevationDeg, Rotate.X_AXIS),
                new Translate(0, 0, -this.distance));
        this.camera.setRotationAxis(Rotate.X_AXIS);
        this.camera.setRotate(0.0);
    }

    private static double clampDistance(double value) {
        return Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, value));
    }

    private static boolean isFinitePoint(Point3D point) {
        return point != null
                && Double.isFinite(point.getX())
                && Double.isFinite(point.getY())
                && Double.isFinite(point.getZ());
    }
}
