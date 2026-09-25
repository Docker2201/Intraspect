package com.sergey.pisarev.ai;

import com.sergey.pisarev.model.MeshBuildResult;
import com.sergey.pisarev.model.SimulationContext;
import com.sergey.pisarev.service.StockRemovalMeshBuilder;

import javafx.geometry.Bounds;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.ParallelCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;

/**
 * Снимок трёхмерной модели в PNG — то, что нейросеть «увидит» после своей правки.
 *
 * <p>Окно 3D для этого не нужно и открывать его не надо: меш строит тот же
 * {@link StockRemovalMeshBuilder}, что и в окне, а сцена собирается на время снимка.
 * Так нейросеть может посмотреть на результат, пока человек работает в других окнах.
 *
 * <p>Свет тот же, что в окне симуляции, иначе картинка врала бы про вид детали.
 * Камера параллельная: технический вид без перспективных искажений, и не нужно
 * подбирать расстояние — координаты сцены совпадают с точками картинки.
 */
public final class ModelSnapshot {
    private ModelSnapshot() {
    }

    /**
     * Строит модель и снимает картинку. Вызывать только в потоке JavaFX.
     *
     * @return PNG или пустой массив, если модель не построилась
     */
    public static AppAccess.Picture render(SimulationContext context, String programText,
                                           int width, int height, boolean darkTheme) {
        int w = Math.max(320, Math.min(2400, width));
        int h = Math.max(200, Math.min(1600, height));
        if (context == null) {
            return AppAccess.Picture.failed("нет данных для симуляции: программа не разобрана");
        }
        MeshBuildResult built = StockRemovalMeshBuilder.buildFinishedPart(context, programText);
        if (built == null) {
            return AppAccess.Picture.failed("построение модели не ответило");
        }
        if (!built.isSuccess() || built.getMesh() == null) {
            return AppAccess.Picture.failed(built.getMessage() == null
                    ? "модель не построилась" : built.getMessage());
        }
        MeshView part = StockRemovalMeshBuilder.createFinishedMeshView(
                built.getMesh(), darkTheme ? Color.web("#7ec8e3") : Color.web("#4f9fb5"));
        if (part == null) {
            return AppAccess.Picture.failed("меш детали пустой");
        }

        Group laid = new Group(part);
        boolean vertical = context.getMachineConfiguration() != null
                && context.getMachineConfiguration().isVerticalLathe();
        laid.getTransforms().add(com.sergey.pisarev.util.MachineOrientation.modelRotation(vertical));
        Group tilted = new Group(laid);
        tilted.getTransforms().add(new Rotate(vertical ? 24.0 : -14.0, Rotate.X_AXIS));

        // Вписываем деталь в кадр: сначала масштаб по габаритам, потом сдвиг в центр.
        Bounds raw = tilted.getBoundsInParent();
        if (raw.getWidth() <= 0.0 || raw.getHeight() <= 0.0) {
            return AppAccess.Picture.failed("у модели нулевые габариты");
        }
        double scale = Math.min(w * 0.92 / raw.getWidth(), h * 0.80 / raw.getHeight());
        Group scaled = new Group(tilted);
        scaled.getTransforms().add(new Scale(scale, scale, scale));
        Bounds fitted = scaled.getBoundsInParent();
        scaled.setTranslateX(w / 2.0 - (fitted.getMinX() + fitted.getWidth() / 2.0));
        scaled.setTranslateY(h / 2.0 - (fitted.getMinY() + fitted.getHeight() / 2.0));

        Group root = new Group(scaled);
        addLights(root, w, h);

        Scene scene = new Scene(root, w, h, true);
        scene.setFill(darkTheme ? Color.web("#0b1220") : Color.web("#f8fafc"));
        ParallelCamera camera = new ParallelCamera();
        // По умолчанию дальняя плоскость отсечения близко, и деталь пропадала бы целиком.
        camera.setNearClip(0.01);
        camera.setFarClip(100000.0);
        scene.setCamera(camera);

        WritableImage image = scene.snapshot(null);
        return AppAccess.Picture.of(PngWriter.encode(image));
    }

    /** Тот же свет, что в окне симуляции: рассеянный плюс основной и две подсветки краёв. */
    private static void addLights(Group root, double width, double height) {
        root.getChildren().add(new AmbientLight(Color.color(0.46, 0.47, 0.50)));
        root.getChildren().add(light(0.48, 0.49, 0.53, width / 2.0 + 280.0, height / 2.0 - 220.0, -320.0));
        root.getChildren().add(light(0.34, 0.35, 0.38, width / 2.0 - 820.0, height / 2.0 - 520.0, 120.0));
        root.getChildren().add(light(0.30, 0.31, 0.34, width / 2.0 + 760.0, height / 2.0 + 560.0, 160.0));
    }

    private static PointLight light(double red, double green, double blue, double x, double y, double z) {
        PointLight point = new PointLight(Color.color(red, green, blue));
        point.setTranslateX(x);
        point.setTranslateY(y);
        point.setTranslateZ(z);
        return point;
    }
}
