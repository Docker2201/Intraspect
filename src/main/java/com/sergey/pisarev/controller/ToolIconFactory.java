package com.sergey.pisarev.controller;

import com.sergey.pisarev.model.CncToolDefinition;
import com.sergey.pisarev.model.ToolTypeCatalog;
import com.sergey.pisarev.model.ToolTypeCategory;
import com.sergey.pisarev.model.ToolTypeEntry;
import com.sergey.pisarev.util.I18n;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

final class ToolIconFactory {
    private static final Color INSERT = Color.web("#ffe74d");
    private static final Color INSERT_STROKE = Color.web("#101827");
    private static final Color HOLDER = Color.web("#cbd5e1");
    private static final Color HOLDER_STROKE = Color.web("#334155");
    private static final Color ACTIVE = Color.web("#1d4ed8");
    private static final Color EDGE = Color.web("#111827");
    private static final Color RED = Color.web("#ef4444");
    // Быстрые подсказки: показ почти мгновенный (120 мс гасит мигание при быстром
    // проходе мышью по ряду инструментов), скрытие сразу — старая подсказка не
    // накладывается на новую.
    private static final Duration TOOLTIP_SHOW_DELAY = Duration.millis(120.0);
    private static final Duration TOOLTIP_HIDE_DELAY = Duration.ZERO;
    private static final Duration TOOLTIP_SHOW_DURATION = Duration.seconds(30.0);
    private static final Map<String, Image> ICON_CACHE = new HashMap<>();

    private ToolIconFactory() {
    }

    static Node smallIcon(ToolTypeEntry entry, int position) {
        return icon(entry, position, 34.0);
    }

    static Node largeIcon(ToolTypeEntry entry, int position) {
        return icon(entry, position, 118.0);
    }

    static Node smallIcon(CncToolDefinition tool) {
        return icon(entryFor(tool), tool != null ? tool.getToolPosition() : 1, 34.0);
    }

    static Node icon(CncToolDefinition tool, int position, double size) {
        return icon(entryFor(tool), position, size);
    }

    static Tooltip tooltip(ToolTypeEntry entry, int position) {
        String title = entry == null
                ? I18n.text("app.083")
                : String.format(Locale.US, "%d - %s", entry.typeCode(), ToolTypeCatalog.russianName(entry));
        Tooltip tooltip = new Tooltip(title + "\n" + positionLabel(position));
        tooltip.getStyleClass().add("tool-icon-tooltip");
        tooltip.setStyle("-fx-font-size: 16px;");
        tooltip.setGraphic(icon(entry, position, 72.0));
        configureTooltipDelay(tooltip);
        return tooltip;
    }

    static Tooltip tooltip(CncToolDefinition tool) {
        ToolTypeEntry entry = entryFor(tool);
        String title = tool != null && tool.getName() != null && !tool.getName().isBlank()
                ? tool.getName()
                : ToolTypeCatalog.russianName(entry);
        Tooltip tooltip = new Tooltip(title + "\n" + positionLabel(tool != null ? tool.getToolPosition() : 1));
        tooltip.getStyleClass().add("tool-icon-tooltip");
        tooltip.setStyle("-fx-font-size: 16px;");
        tooltip.setGraphic(icon(tool, tool != null ? tool.getToolPosition() : 1, 72.0));
        configureTooltipDelay(tooltip);
        return tooltip;
    }

    static Node preview(ToolTypeEntry entry, int position) {
        VBox box = new VBox(8.0);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(8.0));
        box.getChildren().add(largeIcon(entry, position));
        Label label = new Label(entry == null ? "" : ToolTypeCatalog.russianName(entry));
        label.getStyleClass().add("muted-label");
        Label pos = new Label(positionLabel(position));
        pos.getStyleClass().add("muted-label");
        box.getChildren().addAll(label, pos);
        return box;
    }

    static Node positionGraphic(ToolTypeEntry entry, int position, double size) {
        HBox box = new HBox(6.0);
        box.getStyleClass().add("tool-position-graphic");
        box.setAlignment(Pos.CENTER);
        box.getChildren().add(icon(entry, position, size));
        Label label = new Label(String.valueOf(position));
        label.getStyleClass().add("tool-position-number");
        label.setFont(Font.font("Segoe UI", FontWeight.BOLD, Math.max(11.0, size * 0.28)));
        box.getChildren().add(label);
        return box;
    }

    static String positionLabel(int position) {
        if (position == 9) return I18n.text("tool.position.centre");
        return I18n.text("toolicon.001") + Math.max(1, position);
    }

    private static ToolTypeEntry entryFor(CncToolDefinition tool) {
        if (tool == null) {
            return ToolTypeCatalog.defaultTurning();
        }
        ToolTypeEntry entry = ToolTypeCatalog.findByCode(tool.getTypeCode());
        if (entry != null) {
            return entry;
        }
        ToolTypeCategory category = ToolTypeCategory.TURNING;
        for (ToolTypeCategory candidate : ToolTypeCategory.values()) {
            if (candidate.contains(tool.getTypeCode())) {
                category = candidate;
                break;
            }
        }
        String id = tool.getTypeIdentifier() != null && !tool.getTypeIdentifier().isBlank()
                ? tool.getTypeIdentifier()
                : tool.getName();
        return new ToolTypeEntry(tool.getTypeCode(), id, category, Math.max(4, ToolTypeCatalog.defaultTurning().positionVariants()));
    }

    private static Node icon(ToolTypeEntry entry, int position, double size) {
        ToolTypeEntry resolved = entry != null ? entry : ToolTypeCatalog.defaultTurning();
        int resolvedPosition = Math.max(1, position);
        Image positionIcon = positionIcon(resolved.typeCode(), resolvedPosition);
        if (positionIcon != null) {
            return imageIcon(positionIcon, size);
        }
        Canvas canvas = new Canvas(size, size);
        canvas.setMouseTransparent(true);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        draw(gc, resolved, resolvedPosition, size);
        return canvas;
    }

    private static Image positionIcon(int typeCode, int position) {
        if (position == 9) return null; // Draw the centre reference; no quadrant fallback icon.
        String key = String.format(Locale.US, "tool_%03d_p%d", typeCode, Math.max(1, position));
        Image image = loadIcon(key, String.format(Locale.US, "/tool-icons/tool_%03d_p%d.png", typeCode, Math.max(1, position)));
        if (image != null) {
            return image;
        }
        return loadIcon(String.format(Locale.US, "tool_%03d", typeCode),
                String.format(Locale.US, "/tool-icons/tool_%03d.png", typeCode));
    }

    private static Image loadIcon(String key, String resourcePath) {
        if (ICON_CACHE.containsKey(key)) {
            return ICON_CACHE.get(key);
        }
        Image image = loadIconResource(resourcePath);
        ICON_CACHE.put(key, image);
        return image;
    }

    private static Image loadIconResource(String resourcePath) {
        URL url = ToolIconFactory.class.getResource(resourcePath);
        if (url == null) {
            return null;
        }
        Image image = new Image(url.toExternalForm(), 256.0, 256.0, true, true, false);
        return image.isError() ? null : image;
    }

    private static void configureTooltipDelay(Tooltip tooltip) {
        fastTooltip(tooltip);
    }

    /** Общий быстрый профиль подсказок для всего приложения. */
    static Tooltip fastTooltip(Tooltip tooltip) {
        if (tooltip != null) {
            tooltip.setShowDelay(TOOLTIP_SHOW_DELAY);
            tooltip.setHideDelay(TOOLTIP_HIDE_DELAY);
            tooltip.setShowDuration(TOOLTIP_SHOW_DURATION);
        }
        return tooltip;
    }

    private static Node imageIcon(Image image, double size) {
        ImageView view = new ImageView(image);
        // Внутренний отступ: картинка не упирается в скруглённые углы квадрата
        // и не «вылезает» из него при крупном масштабе Windows.
        view.setFitWidth(size * 0.84);
        view.setFitHeight(size * 0.84);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setMouseTransparent(true);
        StackPane box = new StackPane(view);
        box.setAlignment(Pos.CENTER);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        double radius = Math.max(4.0, size * 0.12);
        box.setStyle(String.format(Locale.US,
                "-fx-background-color: rgba(248, 250, 252, 0.94);"
                        + "-fx-background-radius: %.1f;"
                        + "-fx-border-color: rgba(15, 23, 42, 0.14);"
                        + "-fx-border-radius: %.1f;",
                radius, radius));
        // Жёсткий клип по скруглённому квадрату — за пределы плитки ничего не рисуется.
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(size, size);
        clip.setArcWidth(radius * 2.0);
        clip.setArcHeight(radius * 2.0);
        box.setClip(clip);
        return box;
    }

    private static void draw(GraphicsContext gc, ToolTypeEntry entry, int position, double size) {
        gc.clearRect(0, 0, size, size);
        gc.setLineWidth(Math.max(1.0, size / 28.0));
        int code = entry.typeCode();
        if (entry.category() == ToolTypeCategory.MILLING) {
            drawMill(gc, size, code, position);
        } else if (entry.category() == ToolTypeCategory.DRILL || code == 560) {
            drawDrill(gc, size, code, position);
        } else if (entry.category() == ToolTypeCategory.SPECIAL) {
            drawSpecial(gc, size, code, position);
        } else if (code == 530) {
            drawCutoff(gc, size, position);
        } else if (code == 540) {
            drawThread(gc, size, position);
        } else if (code == 550) {
            drawButton(gc, size, position);
        } else if (code == 520) {
            drawGroove(gc, size, position);
        } else {
            drawTurning(gc, size, position);
        }
        drawPositionMark(gc, size, position);
    }

    private static void drawTurning(GraphicsContext gc, double s, int pos) {
        drawHolder(gc, s, pos);
        double[] p = insertPoint(s, pos);
        drawDiamond(gc, p[0], p[1], s * 0.14);
    }

    private static void drawGroove(GraphicsContext gc, double s, int pos) {
        drawHolder(gc, s, pos);
        double[] p = insertPoint(s, pos);
        gc.setFill(INSERT);
        gc.setStroke(INSERT_STROKE);
        gc.fillRoundRect(p[0] - s * 0.12, p[1] - s * 0.12, s * 0.24, s * 0.24, s * 0.04, s * 0.04);
        gc.strokeRoundRect(p[0] - s * 0.12, p[1] - s * 0.12, s * 0.24, s * 0.24, s * 0.04, s * 0.04);
        gc.setStroke(EDGE);
        gc.strokeLine(p[0], p[1] - s * 0.16, p[0], p[1] + s * 0.16);
    }

    private static void drawCutoff(GraphicsContext gc, double s, int pos) {
        drawHolder(gc, s, pos);
        double[] p = insertPoint(s, pos);
        gc.setFill(INSERT);
        gc.setStroke(INSERT_STROKE);
        gc.fillRect(p[0] - s * 0.08, p[1] - s * 0.22, s * 0.16, s * 0.44);
        gc.strokeRect(p[0] - s * 0.08, p[1] - s * 0.22, s * 0.16, s * 0.44);
        gc.setStroke(EDGE);
        gc.strokeLine(p[0] - s * 0.11, p[1] - s * 0.18, p[0] + s * 0.11, p[1] - s * 0.18);
    }

    private static void drawThread(GraphicsContext gc, double s, int pos) {
        drawHolder(gc, s, pos);
        double[] p = insertPoint(s, pos);
        gc.setFill(INSERT);
        gc.setStroke(INSERT_STROKE);
        double r = s * 0.18;
        gc.fillPolygon(new double[]{p[0], p[0] - r, p[0] + r}, new double[]{p[1] - r, p[1] + r, p[1] + r}, 3);
        gc.strokePolygon(new double[]{p[0], p[0] - r, p[0] + r}, new double[]{p[1] - r, p[1] + r, p[1] + r}, 3);
    }

    private static void drawButton(GraphicsContext gc, double s, int pos) {
        drawHolder(gc, s, pos);
        double[] p = insertPoint(s, pos);
        double r = s * 0.16;
        gc.setFill(INSERT);
        gc.setStroke(INSERT_STROKE);
        gc.fillOval(p[0] - r, p[1] - r, r * 2.0, r * 2.0);
        gc.strokeOval(p[0] - r, p[1] - r, r * 2.0, r * 2.0);
        gc.setFill(ACTIVE);
        gc.fillOval(p[0] - r * 0.35, p[1] - r * 0.35, r * 0.7, r * 0.7);
    }

    private static void drawDrill(GraphicsContext gc, double s, int code, int pos) {
        double y = s * 0.52;
        gc.setFill(HOLDER);
        gc.setStroke(HOLDER_STROKE);
        gc.fillRect(s * 0.18, y - s * 0.08, s * 0.44, s * 0.16);
        gc.strokeRect(s * 0.18, y - s * 0.08, s * 0.44, s * 0.16);
        gc.setFill(INSERT);
        gc.setStroke(INSERT_STROKE);
        gc.fillPolygon(new double[]{s * 0.62, s * 0.84, s * 0.62}, new double[]{y - s * 0.16, y, y + s * 0.16}, 3);
        gc.strokePolygon(new double[]{s * 0.62, s * 0.84, s * 0.62}, new double[]{y - s * 0.16, y, y + s * 0.16}, 3);
        if (code >= 240 && code <= 242) {
            gc.setStroke(EDGE);
            for (int i = 0; i < 3; i++) {
                double x = s * (0.28 + i * 0.11);
                gc.strokeLine(x, y - s * 0.12, x + s * 0.08, y + s * 0.12);
            }
        }
    }

    private static void drawMill(GraphicsContext gc, double s, int code, int pos) {
        double cx = s * 0.5;
        double cy = s * 0.5;
        double r = s * 0.24;
        gc.setFill(HOLDER);
        gc.setStroke(HOLDER_STROKE);
        gc.fillOval(cx - r, cy - r, r * 2.0, r * 2.0);
        gc.strokeOval(cx - r, cy - r, r * 2.0, r * 2.0);
        gc.setStroke(INSERT_STROKE);
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(i * 60.0);
            double x1 = cx + Math.cos(a) * r * 0.62;
            double y1 = cy + Math.sin(a) * r * 0.62;
            double x2 = cx + Math.cos(a) * r * 1.12;
            double y2 = cy + Math.sin(a) * r * 1.12;
            gc.strokeLine(x1, y1, x2, y2);
        }
        gc.setFill(INSERT);
        gc.fillOval(cx - r * 0.38, cy - r * 0.38, r * 0.76, r * 0.76);
        if (code == 110 || code == 111) {
            gc.setStroke(ACTIVE);
            gc.strokeOval(cx - r * 0.58, cy - r * 0.58, r * 1.16, r * 1.16);
        }
    }

    private static void drawSpecial(GraphicsContext gc, double s, int code, int pos) {
        double cx = s * 0.5;
        double cy = s * 0.5;
        gc.setStroke(HOLDER_STROKE);
        gc.setLineWidth(Math.max(1.2, s / 22.0));
        gc.strokeLine(cx, s * 0.18, cx, s * 0.82);
        gc.strokeLine(s * 0.18, cy, s * 0.82, cy);
        gc.setFill(INSERT);
        gc.setStroke(INSERT_STROKE);
        if (code == 730 || code == 732) {
            gc.fillRect(s * 0.26, s * 0.34, s * 0.48, s * 0.32);
            gc.strokeRect(s * 0.26, s * 0.34, s * 0.48, s * 0.32);
        } else {
            gc.fillOval(cx - s * 0.13, cy - s * 0.13, s * 0.26, s * 0.26);
            gc.strokeOval(cx - s * 0.13, cy - s * 0.13, s * 0.26, s * 0.26);
        }
    }

    private static void drawHolder(GraphicsContext gc, double s, int pos) {
        double[] p = insertPoint(s, pos);
        double[] edge = cuttingEdgeVector(pos);
        double width = s * 0.58;
        double x = clamp(p[0] - width * 0.5, s * 0.12, s * 0.30);
        double y;
        if (edge[1] > 0.05) {
            y = p[1] + s * 0.24;
        } else if (edge[1] < -0.05) {
            y = p[1] - s * 0.24;
        } else {
            y = s * 0.68;
        }
        y = clamp(y, s * 0.22, s * 0.78);
        gc.setFill(HOLDER);
        gc.setStroke(HOLDER_STROKE);
        gc.fillRoundRect(x, y - s * 0.08, width, s * 0.16, s * 0.04, s * 0.04);
        gc.strokeRoundRect(x, y - s * 0.08, width, s * 0.16, s * 0.04, s * 0.04);
        gc.setStroke(HOLDER_STROKE.deriveColor(0, 1, 0.85, 0.75));
        gc.strokeLine(x, y + s * 0.1, x + width, y + s * 0.1);
    }

    private static double[] insertPoint(double s, int pos) {
        double[] edge = cuttingEdgeVector(pos);
        return new double[]{
                s * (0.5 + edge[0] * 0.24),
                s * (0.5 - edge[1] * 0.24)
        };
    }

    /**
     * Siemens turning positions in the G18 machining plane: Z is the abscissa, X is the ordinate.
     * Positions 1-4 are quadrant positions; 5-8 lie on coordinate axes.
     */
    private static double[] cuttingEdgeVector(int pos) {
        return switch (Math.max(1, Math.min(9, pos))) {
            case 2 -> new double[]{-1.0, 1.0};
            case 3 -> new double[]{-1.0, -1.0};
            case 4 -> new double[]{1.0, -1.0};
            case 5 -> new double[]{0.0, 1.0};
            case 6 -> new double[]{0.0, -1.0};
            case 7 -> new double[]{-1.0, 0.0};
            case 8 -> new double[]{1.0, 0.0};
            case 9 -> new double[]{0.0, 0.0};
            default -> new double[]{1.0, 1.0};
        };
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void drawDiamond(GraphicsContext gc, double cx, double cy, double r) {
        gc.setFill(INSERT);
        gc.setStroke(INSERT_STROKE);
        gc.fillPolygon(
                new double[]{cx, cx + r, cx, cx - r},
                new double[]{cy - r, cy, cy + r, cy},
                4);
        gc.strokePolygon(
                new double[]{cx, cx + r, cx, cx - r},
                new double[]{cy - r, cy, cy + r, cy},
                4);
        gc.setFill(ACTIVE);
        gc.fillOval(cx - r * 0.22, cy - r * 0.22, r * 0.44, r * 0.44);
    }

    private static void drawPositionMark(GraphicsContext gc, double s, int pos) {
        double[] p = insertPoint(s, pos);
        double r = Math.max(2.0, s * 0.045);
        gc.setFill(RED);
        gc.setStroke(Color.WHITE);
        gc.fillOval(p[0] - r, p[1] - r, r * 2.0, r * 2.0);
        gc.strokeOval(p[0] - r, p[1] - r, r * 2.0, r * 2.0);
    }
}
