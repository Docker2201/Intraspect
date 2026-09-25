package com.sergey.pisarev.util;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Scale;

/**
 * Масштаб окна целиком, как в браузере.
 *
 * <p>На цеховых мониторах и на ноутбуке с масштабом Windows 150 % одно и то же
 * окно выходит либо мелким, либо не влезает. Размер шрифта редактора этого не
 * решает: кнопки, таблицы и график остаются прежними.
 *
 * <p>Содержимое окна лежит в {@link Group} внутри подложки: группа не растягивает
 * своих детей, поэтому окно раскладывается по своему размеру, который привязан к
 * размеру подложки, делённому на масштаб, а сам масштаб — обычное преобразование.
 * Так масштабируется всё сразу и без пересборки разметки.
 */
public final class UiScale {
    /** Шаги как в браузере: привычные числа, а не произвольное умножение. */
    private static final double[] STEPS = {.5, .67, .75, .8, .9, 1, 1.1, 1.25, 1.5, 1.75, 2, 2.5};
    private static final DoubleProperty FACTOR = new SimpleDoubleProperty(load());

    private UiScale() { }

    /** Подложка со масштабируемым содержимым: её и ставят корнем сцены. */
    public static Parent wrap(Parent content) {
        var group = new Group(content);
        var wrapper = new StackPane(group);
        StackPane.setAlignment(group, Pos.TOP_LEFT);
        wrapper.getStyleClass().add("ui-scale-root");
        var scale = new Scale();
        scale.xProperty().bind(FACTOR);
        scale.yProperty().bind(FACTOR);
        content.getTransforms().add(scale);
        if (content instanceof Region region) {
            // Размер содержимого — размер окна, делённый на масштаб: после
            // преобразования оно занимает окно ровно целиком, без полей и обрезки.
            region.prefWidthProperty().bind(wrapper.widthProperty().divide(FACTOR));
            region.prefHeightProperty().bind(wrapper.heightProperty().divide(FACTOR));
            region.minWidthProperty().bind(region.prefWidthProperty());
            region.minHeightProperty().bind(region.prefHeightProperty());
            region.maxWidthProperty().bind(region.prefWidthProperty());
            region.maxHeightProperty().bind(region.prefHeightProperty());
        }
        return wrapper;
    }

    public static DoubleProperty factorProperty() {
        return FACTOR;
    }

    public static double factor() {
        return FACTOR.get();
    }

    /** Следующий шаг вверх (+1) или вниз (-1). */
    public static void step(int direction) {
        double current = FACTOR.get();
        if (direction > 0) {
            for (double step : STEPS) if (step > current + 1e-6) { set(step); return; }
            set(STEPS[STEPS.length - 1]);
        } else {
            for (int i = STEPS.length - 1; i >= 0; i--) if (STEPS[i] < current - 1e-6) { set(STEPS[i]); return; }
            set(STEPS[0]);
        }
    }

    public static void reset() {
        set(1);
    }

    public static void set(double factor) {
        double clamped = Math.max(STEPS[0], Math.min(STEPS[STEPS.length - 1], factor));
        FACTOR.set(clamped);
        UserSettings.setUiScale(clamped);
    }

    /** Подпись для кнопки: «100 %». */
    public static String label() {
        return Math.round(FACTOR.get() * 100) + " %";
    }

    private static double load() {
        double saved = UserSettings.getUiScale();
        return saved > 0 ? Math.max(STEPS[0], Math.min(STEPS[STEPS.length - 1], saved)) : 1;
    }
}
