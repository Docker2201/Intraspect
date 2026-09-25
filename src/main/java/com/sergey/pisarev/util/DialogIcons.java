package com.sergey.pisarev.util;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Иконки для диалогов: жёлтый и красный треугольники с восклицательным знаком и красный крест.
 *
 * <p>Нарисованы фигурами, а не картинками, поэтому остаются чёткими при любом масштабе
 * Windows — стандартная иконка JavaFX в тёмной теме выглядела как крестик на белом квадрате.
 */
public final class DialogIcons {
    private static final Color WARNING_COLOR = Color.web("#F5B916");
    private static final Color ERROR_COLOR = Color.web("#E02020");

    private DialogIcons() {
    }

    /** Жёлтый треугольник — некритичное предупреждение. */
    public static Node warning(double size) {
        return triangle(size, WARNING_COLOR);
    }

    /** Красный треугольник — критичная ошибка. */
    public static Node error(double size) {
        return triangle(size, ERROR_COLOR);
    }

    /** Красный крест — операция не выполнена. */
    public static Node cross(double size) {
        double inset = size * 0.24;
        Line first = new Line(inset, inset, size - inset, size - inset);
        Line second = new Line(size - inset, inset, inset, size - inset);
        for (Line line : new Line[]{first, second}) {
            line.setStroke(ERROR_COLOR);
            line.setStrokeWidth(size * 0.15);
            line.setStrokeLineCap(StrokeLineCap.ROUND);
        }
        return frame(size, new Group(first, second));
    }

    private static Node triangle(double size, Color color) {
        // Скругление углов делаем толстой обводкой того же цвета со скруглённым стыком.
        double corner = size * 0.11;
        Polygon body = new Polygon(
                size * 0.5, size * 0.10,
                size * 0.94, size * 0.86,
                size * 0.06, size * 0.86);
        body.setFill(color);
        body.setStroke(color);
        body.setStrokeWidth(corner);
        body.setStrokeLineJoin(StrokeLineJoin.ROUND);

        Rectangle bar = new Rectangle(size * 0.445, size * 0.33, size * 0.11, size * 0.28);
        bar.setArcWidth(size * 0.11);
        bar.setArcHeight(size * 0.11);
        bar.setFill(Color.WHITE);

        Circle dot = new Circle(size * 0.5, size * 0.71, size * 0.065, Color.WHITE);
        return frame(size, new Group(body, bar, dot));
    }

    /** Фиксирует размер в раскладке: у Group свои границы, диалог иначе резервирует лишнее. */
    private static Node frame(double size, Group content) {
        StackPane holder = new StackPane(content);
        holder.setMinSize(size, size);
        holder.setPrefSize(size, size);
        holder.setMaxSize(size, size);
        return holder;
    }
}
