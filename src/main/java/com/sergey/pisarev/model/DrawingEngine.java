/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javafx.application.Platform
 *  javafx.scene.canvas.Canvas
 *  javafx.scene.canvas.GraphicsContext
 *  javafx.scene.image.WritableImage
 *  javafx.scene.paint.Color
 *  javafx.scene.paint.Paint
 *  javafx.scene.text.Font
 *  javafx.scene.text.TextAlignment
 */
package com.sergey.pisarev.model;

import com.sergey.pisarev.model.DrawCommand;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

public class DrawingEngine {
    private static final Logger LOGGER = Logger.getLogger(DrawingEngine.class.getName());
    private final Canvas canvas;
    private final GraphicsContext gc;
    private final ConcurrentLinkedQueue<DrawCommand> drawQueue;
    private final AtomicBoolean isRendering = new AtomicBoolean(false);
    private static final int MAX_QUEUE_SIZE = 1000;
    private static final int BATCH_SIZE = 50;
    private static final long RENDER_DELAY_MS = 16L;
    private final Paint[] colorCache = new Paint[256];
    private final Font[] fontCache = new Font[16];

    public DrawingEngine(Canvas canvas) {
        this.canvas = canvas;
        this.gc = canvas.getGraphicsContext2D();
        this.drawQueue = new ConcurrentLinkedQueue();
        this.initializeCaches();
        this.setupOptimizations();
    }

    private void initializeCaches() {
        this.colorCache[0] = Color.BLACK;
        this.colorCache[1] = Color.WHITE;
        this.colorCache[2] = Color.RED;
        this.colorCache[3] = Color.GREEN;
        this.colorCache[4] = Color.BLUE;
        this.colorCache[5] = Color.YELLOW;
        this.colorCache[6] = Color.CYAN;
        this.colorCache[7] = Color.MAGENTA;
        this.colorCache[8] = Color.GRAY;
        this.colorCache[9] = Color.DARKGRAY;
        this.colorCache[10] = Color.LIGHTGRAY;
        this.fontCache[0] = Font.font((String)"Arial", (double)10.0);
        this.fontCache[1] = Font.font((String)"Arial", (double)12.0);
        this.fontCache[2] = Font.font((String)"Arial", (double)14.0);
        this.fontCache[3] = Font.font((String)"Arial", (double)16.0);
        this.fontCache[4] = Font.font((String)"Arial", (double)18.0);
        this.fontCache[5] = Font.font((String)"Arial", (double)20.0);
        this.fontCache[6] = Font.font((String)"Arial", (double)24.0);
        this.fontCache[7] = Font.font((String)"Arial", (double)28.0);
        this.fontCache[8] = Font.font((String)"Arial", (double)32.0);
        this.fontCache[9] = Font.font((String)"Arial", (double)36.0);
        this.fontCache[10] = Font.font((String)"Arial", (double)48.0);
    }

    private void setupOptimizations() {
        this.gc.setImageSmoothing(false);
        this.gc.setFill((Paint)Color.BLACK);
        this.gc.setStroke((Paint)Color.BLACK);
        this.gc.setLineWidth(1.0);
    }

    public void queueDraw(DrawCommand drawCommand) {
        if (this.drawQueue.size() >= 1000) {
            this.drawQueue.poll();
        }
        this.drawQueue.offer(drawCommand);
        if (!this.isRendering.get()) {
            this.scheduleRender();
        }
    }

    private void scheduleRender() {
        if (this.isRendering.compareAndSet(false, true)) {
            new Thread(() -> {
                try {
                    Thread.sleep(16L);
                    this.processDrawQueue();
                }
                catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
                finally {
                    this.isRendering.set(false);
                }
            }).start();
        }
    }

    private void processDrawQueue() {
        DrawCommand[] drawCommandArray = new DrawCommand[50];
        int n = 0;
        while (n < 50 && !this.drawQueue.isEmpty()) {
            DrawCommand drawCommand = this.drawQueue.poll();
            if (drawCommand == null) continue;
            drawCommandArray[n++] = drawCommand;
        }
        if (n > 0) {
            int n2 = n;
            Platform.runLater(() -> this.executeDrawBatch(drawCommandArray, n2));
        }
    }

    private void executeDrawBatch(DrawCommand[] drawCommandArray, int n) {
        for (int i = 0; i < n; ++i) {
            DrawCommand drawCommand = drawCommandArray[i];
            if (drawCommand == null) continue;
            this.executeCommand(drawCommand);
        }
    }

    private void executeCommand(DrawCommand drawCommand) {
        try {
            switch (drawCommand.getType()) {
                case LINE: {
                    this.drawLine(drawCommand);
                    break;
                }
                case RECTANGLE: {
                    this.drawRectangle(drawCommand);
                    break;
                }
                case CIRCLE: {
                    this.drawCircle(drawCommand);
                    break;
                }
                case TEXT: {
                    this.drawText(drawCommand);
                    break;
                }
                case POLYGON: {
                    this.drawPolygon(drawCommand);
                    break;
                }
                case CLEAR: {
                    this.clearCanvas();
                    break;
                }
                default: {
                    LOGGER.warning("Unknown draw command type: " + String.valueOf((Object)drawCommand.getType()));
                    break;
                }
            }
        }
        catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Error executing draw command", exception);
        }
    }

    private void drawLine(DrawCommand drawCommand) {
        this.gc.setStroke(this.getCachedColor(drawCommand.getColorIndex()));
        this.gc.setLineWidth(drawCommand.getLineWidth());
        this.gc.strokeLine(drawCommand.getX1(), drawCommand.getY1(), drawCommand.getX2(), drawCommand.getY2());
    }

    private void drawRectangle(DrawCommand drawCommand) {
        if (drawCommand.isFilled()) {
            this.gc.setFill(this.getCachedColor(drawCommand.getColorIndex()));
            this.gc.fillRect(drawCommand.getX1(), drawCommand.getY1(), drawCommand.getX2() - drawCommand.getX1(), drawCommand.getY2() - drawCommand.getY1());
        } else {
            this.gc.setStroke(this.getCachedColor(drawCommand.getColorIndex()));
            this.gc.setLineWidth(drawCommand.getLineWidth());
            this.gc.strokeRect(drawCommand.getX1(), drawCommand.getY1(), drawCommand.getX2() - drawCommand.getX1(), drawCommand.getY2() - drawCommand.getY1());
        }
    }

    private void drawCircle(DrawCommand drawCommand) {
        double d = drawCommand.getRadius();
        if (drawCommand.isFilled()) {
            this.gc.setFill(this.getCachedColor(drawCommand.getColorIndex()));
            this.gc.fillOval(drawCommand.getX1() - d, drawCommand.getY1() - d, 2.0 * d, 2.0 * d);
        } else {
            this.gc.setStroke(this.getCachedColor(drawCommand.getColorIndex()));
            this.gc.setLineWidth(drawCommand.getLineWidth());
            this.gc.strokeOval(drawCommand.getX1() - d, drawCommand.getY1() - d, 2.0 * d, 2.0 * d);
        }
    }

    private void drawText(DrawCommand drawCommand) {
        this.gc.setFill(this.getCachedColor(drawCommand.getColorIndex()));
        this.gc.setFont(this.getCachedFont(drawCommand.getFontIndex()));
        this.gc.setTextAlign(TextAlignment.LEFT);
        this.gc.fillText(drawCommand.getText(), drawCommand.getX1(), drawCommand.getY1());
    }

    private void drawPolygon(DrawCommand drawCommand) {
        double[] dArray = drawCommand.getXPoints();
        double[] dArray2 = drawCommand.getYPoints();
        if (drawCommand.isFilled()) {
            this.gc.setFill(this.getCachedColor(drawCommand.getColorIndex()));
            this.gc.fillPolygon(dArray, dArray2, dArray.length);
        } else {
            this.gc.setStroke(this.getCachedColor(drawCommand.getColorIndex()));
            this.gc.setLineWidth(drawCommand.getLineWidth());
            this.gc.strokePolygon(dArray, dArray2, dArray.length);
        }
    }

    private void clearCanvas() {
        this.gc.clearRect(0.0, 0.0, this.canvas.getWidth(), this.canvas.getHeight());
    }

    private Paint getCachedColor(int n) {
        if (n >= 0 && n < this.colorCache.length && this.colorCache[n] != null) {
            return this.colorCache[n];
        }
        return Color.BLACK;
    }

    private Font getCachedFont(int n) {
        if (n >= 0 && n < this.fontCache.length && this.fontCache[n] != null) {
            return this.fontCache[n];
        }
        return Font.font((String)"Arial", (double)12.0);
    }

    public void clearQueue() {
        this.drawQueue.clear();
    }

    public int getQueueSize() {
        return this.drawQueue.size();
    }

    public void forceRender() {
        while (!this.drawQueue.isEmpty()) {
            DrawCommand drawCommand = this.drawQueue.poll();
            if (drawCommand == null) continue;
            Platform.runLater(() -> this.executeCommand(drawCommand));
        }
    }

    public WritableImage createSnapshot() {
        return this.canvas.snapshot(null, null);
    }

    public void setCanvasSize(double d, double d2) {
        this.canvas.setWidth(d);
        this.canvas.setHeight(d2);
    }

    public double getCanvasWidth() {
        return this.canvas.getWidth();
    }

    public double getCanvasHeight() {
        return this.canvas.getHeight();
    }
}

