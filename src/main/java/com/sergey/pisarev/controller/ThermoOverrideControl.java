package com.sergey.pisarev.controller;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Вертикальный переключатель override (SINUMERIK / SinuTrain).
 */
public final class ThermoOverrideControl extends VBox {
    private static final double SCALE = 0.85;

    private final int[] stops;
    private final IntegerProperty value = new SimpleIntegerProperty(100);
    private final StackPane card = new StackPane();
    private final Canvas canvas = new Canvas(s(140), s(300));
    private final Label valueLabel = new Label();
    private final Label titleLabel = new Label();

    private static final double TRACK_TOP = s(52.0);
    private static final double TRACK_HEIGHT = s(194.0);
    private static final double TRACK_CENTER_X = s(70.0);
    private static final double TRACK_WIDTH = s(22.0);

    public ThermoOverrideControl(String title, String tooltipText, int[] stops, int initialValue) {
        super(s(8.0));
        this.stops = stops;
        this.setAlignment(Pos.TOP_CENTER);
        this.setPadding(new Insets(0));
        this.getStyleClass().add("thermo-override-root");
        this.titleLabel.setText(title);
        this.titleLabel.getStyleClass().add("thermo-override-title");
        this.valueLabel.getStyleClass().add("thermo-override-value");
        this.card.getStyleClass().add("thermo-override-card");
        this.card.getChildren().add(this.canvas);
        Tooltip tooltip = ToolIconFactory.fastTooltip(new Tooltip(tooltipText));
        tooltip.getStyleClass().add("thermo-override-tooltip");
        Tooltip.install(this, tooltip);
        VBox readout = new VBox(s(4.0));
        readout.setAlignment(Pos.CENTER);
        Label current = new Label("CURRENT");
        current.getStyleClass().add("thermo-override-current-label");
        readout.getChildren().addAll(this.valueLabel, current);
        this.getChildren().addAll(this.titleLabel, this.card, readout);
        this.setValue(initialValue);
        this.value.addListener((observable, oldValue, newValue) -> this.redraw());
        this.canvas.setOnMouseClicked(event -> {
            int picked = this.pickStop(event.getY());
            if (picked >= 0) {
                this.setValue(picked);
            }
        });
        this.canvas.setOnMouseDragged(event -> {
            int picked = this.pickStop(event.getY());
            if (picked >= 0) {
                this.setValue(picked);
            }
            event.consume();
        });
        this.canvas.setOnScroll(event -> {
            int delta = event.getDeltaY() > 0 ? -1 : 1;
            this.setValue(this.neighborValue(this.getValue(), delta));
            event.consume();
        });
        this.redraw();
    }

    public IntegerProperty valueProperty() {
        return this.value;
    }

    public int getValue() {
        return this.value.get();
    }

    public void setValue(int percent) {
        this.value.set(this.snap(percent));
        this.valueLabel.setText(this.getValue() + "%");
        this.redraw();
    }

    private int neighborValue(int current, int direction) {
        int index = 0;
        for (int i = 0; i < this.stops.length; i++) {
            if (this.stops[i] == current) {
                index = i;
                break;
            }
        }
        index = Math.max(0, Math.min(this.stops.length - 1, index + direction));
        return this.stops[index];
    }

    private int snap(int percent) {
        int best = this.stops[0];
        int bestDiff = Integer.MAX_VALUE;
        for (int stop : this.stops) {
            int diff = Math.abs(stop - percent);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = stop;
            }
        }
        return best;
    }

    private int pickStop(double mouseY) {
        if (mouseY < TRACK_TOP - s(12) || mouseY > TRACK_TOP + TRACK_HEIGHT + s(12)) {
            return -1;
        }
        double t = 1.0 - (mouseY - TRACK_TOP) / TRACK_HEIGHT;
        int index = (int)Math.round(t * (this.stops.length - 1));
        index = Math.max(0, Math.min(this.stops.length - 1, index));
        return this.stops[index];
    }

    private int indexForValue(int current) {
        for (int i = 0; i < this.stops.length; i++) {
            if (this.stops[i] == current) {
                return i;
            }
        }
        return 0;
    }

    /** Максимум вверху, минимум внизу — как на панели станка. */
    private double knobTopForValue(int current) {
        double t = 1.0 - this.indexForValue(current) / (double)Math.max(1, this.stops.length - 1);
        return TRACK_TOP + t * TRACK_HEIGHT;
    }

    private double fillRatioForValue(int current) {
        int min = this.stops[0];
        int max = this.stops[this.stops.length - 1];
        return (current - min) / (double)Math.max(1, max - min);
    }

    private Theme themeFor(int current) {
        double ratio = this.fillRatioForValue(current);
        if (ratio < 0.45) {
            return new Theme("#00d9ff", Color.color(0.0, 0.85, 1.0, 0.58));
        }
        if (ratio < 0.75) {
            return new Theme("#38ff9c", Color.color(0.22, 1.0, 0.61, 0.58));
        }
        return new Theme("#ff3f4f", Color.color(1.0, 0.25, 0.31, 0.62));
    }

    /** Отрисовка «корабельного рычага»: металлическая панель, паз, светящаяся шкала, рукоятка. */
    private void redraw() {
        double w = this.canvas.getWidth();
        double h = this.canvas.getHeight();
        GraphicsContext gc = this.canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);
        int current = this.getValue();
        Theme theme = this.themeFor(current);
        Color accent = Color.web(theme.colorHex);
        double knobY = this.knobTopForValue(current);
        double fillRatio = 1.0 - (knobY - TRACK_TOP) / TRACK_HEIGHT;

        // Корпус: тёмный металл с вертикальным градиентом и светлой верхней кромкой.
        LinearGradient bg = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#2c313d")),
                new Stop(0.08, Color.web("#232834")),
                new Stop(1, Color.web("#12151d")));
        gc.setFill(bg);
        gc.fillRoundRect(0, 0, w, h, s(36), s(36));
        gc.setStroke(Color.color(1.0, 1.0, 1.0, 0.14));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(0.5, 0.5, w - 1, h - 1, s(36), s(36));
        gc.setStroke(Color.color(0.0, 0.0, 0.0, 0.55));
        gc.strokeRoundRect(1.5, 1.5, w - 3, h - 3, s(34), s(34));

        // Площадка рычага: «вдавленная» панель с боковыми направляющими.
        double bodyX = TRACK_CENTER_X - s(37);
        double bodyY = TRACK_TOP - s(30);
        double bodyW = s(74);
        double bodyH = TRACK_HEIGHT + s(44);
        LinearGradient plate = new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#0c0f15")),
                new Stop(0.5, Color.web("#161b25")),
                new Stop(1, Color.web("#0c0f15")));
        gc.setFill(plate);
        gc.fillRoundRect(bodyX, bodyY, bodyW, bodyH, s(28), s(28));
        gc.setStroke(Color.color(1.0, 1.0, 1.0, 0.07));
        gc.strokeRoundRect(bodyX + 0.5, bodyY + 0.5, bodyW - 1, bodyH - 1, s(28), s(28));

        // Паз (прорезь) рычага — глубокий и узкий.
        double slotW = s(14);
        double slotX = TRACK_CENTER_X - slotW / 2.0;
        double slotY = TRACK_TOP - s(8);
        double slotH = TRACK_HEIGHT + s(16);
        gc.setFill(Color.web("#05070b"));
        gc.fillRoundRect(slotX, slotY, slotW, slotH, s(10), s(10));
        gc.setStroke(Color.color(0.0, 0.0, 0.0, 0.8));
        gc.strokeRoundRect(slotX + 0.5, slotY + 0.5, slotW - 1, slotH - 1, s(10), s(10));
        gc.setStroke(Color.color(1.0, 1.0, 1.0, 0.06));
        gc.strokeLine(slotX + slotW + 1, slotY + s(4), slotX + slotW + 1, slotY + slotH - s(4));

        // Светящееся заполнение от низа до рукоятки (ореол + ядро).
        double fillH = TRACK_HEIGHT * Math.max(0.0, Math.min(1.0, fillRatio));
        if (fillH > 1.0) {
            double fillTop = TRACK_TOP + TRACK_HEIGHT - fillH;
            gc.setFill(Color.color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0.22));
            gc.fillRoundRect(slotX - s(4), fillTop - s(3), slotW + s(8), fillH + s(6), s(12), s(12));
            LinearGradient glow = new LinearGradient(0, fillTop, 0, fillTop + fillH, false, CycleMethod.NO_CYCLE,
                    new Stop(0, accent.brighter()),
                    new Stop(1, accent.darker()));
            gc.setFill(glow);
            gc.fillRoundRect(slotX + s(2), fillTop, slotW - s(4), fillH, s(8), s(8));
        }

        // Шкала: значения слева, насечки справа; активная ступень подсвечена.
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, s(8)));
        for (int i = 0; i < this.stops.length; i++) {
            double tMark = 1.0 - i / (double) Math.max(1, this.stops.length - 1);
            double markY = TRACK_TOP + tMark * TRACK_HEIGHT;
            boolean active = this.stops[i] == current;
            gc.setFill(active
                    ? accent.brighter()
                    : Color.color(0.78, 0.84, 0.93, 0.78));
            gc.fillText(Integer.toString(this.stops[i]), s(16), markY + s(3));
            gc.setFill(active ? accent : Color.color(1.0, 1.0, 1.0, 0.22));
            gc.fillRoundRect(s(104), markY - s(1), active ? s(14) : s(10), s(2), s(2), s(2));
        }

        // Тень под рукояткой.
        gc.setFill(Color.color(0.0, 0.0, 0.0, 0.45));
        gc.fillRoundRect(TRACK_CENTER_X - s(28), knobY - s(9) + s(3), s(56), s(20), s(10), s(10));

        // Рукоятка: металлический брусок с фасками, насечками и цветным индикатором.
        double handleW = s(56);
        double handleH = s(20);
        double handleX = TRACK_CENTER_X - handleW / 2.0;
        double handleY = knobY - handleH / 2.0;
        LinearGradient metal = new LinearGradient(0, handleY, 0, handleY + handleH, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#eef2f8")),
                new Stop(0.42, Color.web("#b9c3d2")),
                new Stop(0.58, Color.web("#8d99ab")),
                new Stop(1, Color.web("#566175")));
        gc.setFill(metal);
        gc.fillRoundRect(handleX, handleY, handleW, handleH, s(10), s(10));
        gc.setStroke(Color.color(0.0, 0.0, 0.0, 0.6));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(handleX + 0.5, handleY + 0.5, handleW - 1, handleH - 1, s(10), s(10));
        gc.setStroke(Color.color(1.0, 1.0, 1.0, 0.55));
        gc.strokeLine(handleX + s(6), handleY + 1.5, handleX + handleW - s(6), handleY + 1.5);
        // Насечки грипа.
        gc.setStroke(Color.color(0.0, 0.0, 0.0, 0.28));
        for (int g = 1; g <= 3; g++) {
            double gy = handleY + handleH * g / 4.0;
            gc.strokeLine(handleX + s(8), gy, handleX + handleW - s(8), gy);
        }
        // Цветной индикатор по центру рукоятки со свечением.
        gc.setFill(Color.color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0.35));
        gc.fillRoundRect(TRACK_CENTER_X - s(4.5), handleY - s(1.5), s(9), handleH + s(3), s(5), s(5));
        gc.setFill(accent);
        gc.fillRoundRect(TRACK_CENTER_X - s(2.5), handleY + s(1.5), s(5), handleH - s(3), s(3), s(3));

        this.valueLabel.setText(current + "%");
    }

    private static double s(double value) {
        return value * SCALE;
    }

    private record Theme(String colorHex, Color glow) {
    }
}
