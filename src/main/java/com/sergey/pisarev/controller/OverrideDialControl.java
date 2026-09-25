package com.sergey.pisarev.controller;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Круговой переключатель override (подача / шпиндель) в стиле панели SINUMERIK.
 */
public final class OverrideDialControl extends VBox {
    private static final int[] FEED_STOPS = {0, 2, 4, 6, 10, 30, 50, 70, 80, 90, 100, 110, 120};
    private static final int[] SPINDLE_STOPS = {50, 60, 70, 80, 90, 100, 110, 120};

    private final boolean feedDial;
    private final Canvas canvas = new Canvas(96, 96);
    private final Label valueLabel = new Label("100 %");
    private final IntegerProperty value = new SimpleIntegerProperty(100);
    private double dragStartAngle;

    public OverrideDialControl(String title, String tooltipText, boolean feedDial, int initialValue) {
        super(6.0);
        this.feedDial = feedDial;
        this.setPadding(new Insets(4, 0, 4, 0));
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("override-dial-title");
        this.valueLabel.getStyleClass().add("override-dial-value");
        this.canvas.getStyleClass().add("override-dial-canvas");
        Tooltip.install(this, ToolIconFactory.fastTooltip(new Tooltip(tooltipText)));
        this.getChildren().addAll(titleLabel, this.canvas, this.valueLabel);
        this.setValue(initialValue);
        this.value.addListener((observable, oldValue, newValue) -> this.redraw());
        this.canvas.widthProperty().addListener(obs -> this.redraw());
        this.canvas.heightProperty().addListener(obs -> this.redraw());
        this.canvas.setOnMousePressed(this::handlePress);
        this.canvas.setOnMouseDragged(this::handleDrag);
        this.canvas.setOnScroll(event -> {
            int delta = event.getDeltaY() > 0 ? 1 : -1;
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
        this.valueLabel.setText(this.value.get() + " %");
        this.redraw();
    }

    private void handlePress(MouseEvent event) {
        this.dragStartAngle = this.angleAt(event.getX(), event.getY());
        event.consume();
    }

    private void handleDrag(MouseEvent event) {
        double angle = this.angleAt(event.getX(), event.getY());
        double delta = angle - this.dragStartAngle;
        if (delta > 180.0) {
            delta -= 360.0;
        } else if (delta < -180.0) {
            delta += 360.0;
        }
        int steps = (int)Math.round(delta / 18.0);
        if (steps != 0) {
            this.setValue(this.neighborValue(this.getValue(), steps));
            this.dragStartAngle = angle;
        }
        event.consume();
    }

    private double angleAt(double x, double y) {
        double cx = this.canvas.getWidth() / 2.0;
        double cy = this.canvas.getHeight() / 2.0;
        return Math.toDegrees(Math.atan2(y - cy, x - cx));
    }

    private int neighborValue(int current, int direction) {
        int[] stops = this.feedDial ? FEED_STOPS : SPINDLE_STOPS;
        int index = 0;
        for (int i = 0; i < stops.length; i++) {
            if (stops[i] == current) {
                index = i;
                break;
            }
        }
        index = Math.max(0, Math.min(stops.length - 1, index + direction));
        return stops[index];
    }

    private int snap(int percent) {
        int[] stops = this.feedDial ? FEED_STOPS : SPINDLE_STOPS;
        int best = stops[0];
        int bestDiff = Integer.MAX_VALUE;
        for (int stop : stops) {
            int diff = Math.abs(stop - percent);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = stop;
            }
        }
        return best;
    }

    private void redraw() {
        double size = Math.min(this.canvas.getWidth(), this.canvas.getHeight());
        if (size < 40.0) {
            size = 96.0;
            this.canvas.setWidth(size);
            this.canvas.setHeight(size);
        }
        GraphicsContext gc = this.canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, size, size);
        double cx = size / 2.0;
        double cy = size / 2.0;
        double radius = size * 0.42;
        gc.setFill(Color.web("#1e293b"));
        gc.fillOval(cx - radius, cy - radius, radius * 2.0, radius * 2.0);
        gc.setStroke(Color.web("#64748b"));
        gc.setLineWidth(1.2);
        gc.strokeOval(cx - radius, cy - radius, radius * 2.0, radius * 2.0);
        int[] stops = this.feedDial ? FEED_STOPS : SPINDLE_STOPS;
        int min = stops[0];
        int max = stops[stops.length - 1];
        int current = this.getValue();
        for (int stop : stops) {
            double angle = this.valueToAngle(stop, min, max);
            double tx = cx + Math.cos(angle) * radius * 0.82;
            double ty = cy + Math.sin(angle) * radius * 0.82;
            gc.setFill(Color.web("#94a3b8"));
            gc.setFont(Font.font("Segoe UI", 8));
            gc.fillText(Integer.toString(stop), tx - 6, ty + 3);
        }
        double pointerAngle = this.valueToAngle(current, min, max);
        gc.setFill(Color.web("#2dd4bf"));
        double px = cx + Math.cos(pointerAngle) * radius * 0.55;
        double py = cy + Math.sin(pointerAngle) * radius * 0.55;
        gc.fillOval(px - 5, py - 5, 10, 10);
        gc.setStroke(Color.web("#0f766e"));
        gc.setLineWidth(2.5);
        gc.strokeLine(cx, cy, px, py);
        gc.setFill(Color.web("#334155"));
        gc.fillOval(cx - 10, cy - 10, 20, 20);
        gc.setFill(Color.web("#e2e8f0"));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
        gc.fillText("%", cx - 4, cy + 3);
        this.valueLabel.setText(current + " %");
    }

    private double valueToAngle(int value, int min, int max) {
        double t = (value - min) / (double)Math.max(1, max - min);
        return Math.toRadians(135.0 - t * 270.0);
    }
}
