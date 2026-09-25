/*
 * Decompiled with CFR 0.152.
 */
package com.sergey.pisarev.model;

public final class DrawCommand {
    private final CommandType type;
    private final double x1;
    private final double y1;
    private final double x2;
    private final double y2;
    private final double radius;
    private final String text;
    private final int colorIndex;
    private final int fontIndex;
    private final double lineWidth;
    private final boolean filled;
    private final double[] xPoints;
    private final double[] yPoints;

    private DrawCommand(Builder builder) {
        this.type = builder.type;
        this.x1 = builder.x1;
        this.y1 = builder.y1;
        this.x2 = builder.x2;
        this.y2 = builder.y2;
        this.radius = builder.radius;
        this.text = builder.text;
        this.colorIndex = builder.colorIndex;
        this.fontIndex = builder.fontIndex;
        this.lineWidth = builder.lineWidth;
        this.filled = builder.filled;
        this.xPoints = builder.xPoints != null ? (double[])builder.xPoints.clone() : null;
        this.yPoints = builder.yPoints != null ? (double[])builder.yPoints.clone() : null;
    }

    public CommandType getType() {
        return this.type;
    }

    public double getX1() {
        return this.x1;
    }

    public double getY1() {
        return this.y1;
    }

    public double getX2() {
        return this.x2;
    }

    public double getY2() {
        return this.y2;
    }

    public double getRadius() {
        return this.radius;
    }

    public String getText() {
        return this.text;
    }

    public int getColorIndex() {
        return this.colorIndex;
    }

    public int getFontIndex() {
        return this.fontIndex;
    }

    public double getLineWidth() {
        return this.lineWidth;
    }

    public boolean isFilled() {
        return this.filled;
    }

    public double[] getXPoints() {
        return this.xPoints != null ? (double[])this.xPoints.clone() : null;
    }

    public double[] getYPoints() {
        return this.yPoints != null ? (double[])this.yPoints.clone() : null;
    }

    public static DrawCommand line(double d, double d2, double d3, double d4) {
        return new Builder(CommandType.LINE).line(d, d2, d3, d4).build();
    }

    public static DrawCommand line(double d, double d2, double d3, double d4, int n, double d5) {
        return new Builder(CommandType.LINE).line(d, d2, d3, d4).color(n).lineWidth(d5).build();
    }

    public static DrawCommand rectangle(double d, double d2, double d3, double d4, boolean bl) {
        return new Builder(CommandType.RECTANGLE).rectangle(d, d2, d3, d4).filled(bl).build();
    }

    public static DrawCommand rectangle(double d, double d2, double d3, double d4, int n, boolean bl) {
        return new Builder(CommandType.RECTANGLE).rectangle(d, d2, d3, d4).color(n).filled(bl).build();
    }

    public static DrawCommand circle(double d, double d2, double d3, boolean bl) {
        return new Builder(CommandType.CIRCLE).circle(d, d2, d3).filled(bl).build();
    }

    public static DrawCommand circle(double d, double d2, double d3, int n, boolean bl) {
        return new Builder(CommandType.CIRCLE).circle(d, d2, d3).color(n).filled(bl).build();
    }

    public static DrawCommand text(double d, double d2, String string) {
        return new Builder(CommandType.TEXT).text(d, d2, string).build();
    }

    public static DrawCommand text(double d, double d2, String string, int n, int n2) {
        return new Builder(CommandType.TEXT).text(d, d2, string).color(n).font(n2).build();
    }

    public static DrawCommand polygon(double[] dArray, double[] dArray2, boolean bl) {
        return new Builder(CommandType.POLYGON).polygon(dArray, dArray2).filled(bl).build();
    }

    public static DrawCommand polygon(double[] dArray, double[] dArray2, int n, boolean bl) {
        return new Builder(CommandType.POLYGON).polygon(dArray, dArray2).color(n).filled(bl).build();
    }

    public static DrawCommand clear() {
        return new Builder(CommandType.CLEAR).build();
    }

    public String toString() {
        return "DrawCommand{type=" + String.valueOf((Object)this.type) + ", x1=" + this.x1 + ", y1=" + this.y1 + ", x2=" + this.x2 + ", y2=" + this.y2 + ", radius=" + this.radius + ", text='" + this.text + "', colorIndex=" + this.colorIndex + ", fontIndex=" + this.fontIndex + ", lineWidth=" + this.lineWidth + ", filled=" + this.filled + "}";
    }

    public static class Builder {
        private CommandType type;
        private double x1;
        private double y1;
        private double x2;
        private double y2;
        private double radius;
        private String text;
        private int colorIndex = 0;
        private int fontIndex = 1;
        private double lineWidth = 1.0;
        private boolean filled = false;
        private double[] xPoints;
        private double[] yPoints;

        public Builder(CommandType commandType) {
            this.type = commandType;
        }

        public Builder line(double d, double d2, double d3, double d4) {
            this.x1 = d;
            this.y1 = d2;
            this.x2 = d3;
            this.y2 = d4;
            return this;
        }

        public Builder rectangle(double d, double d2, double d3, double d4) {
            this.x1 = d;
            this.y1 = d2;
            this.x2 = d3;
            this.y2 = d4;
            return this;
        }

        public Builder circle(double d, double d2, double d3) {
            this.x1 = d;
            this.y1 = d2;
            this.radius = d3;
            return this;
        }

        public Builder text(double d, double d2, String string) {
            this.x1 = d;
            this.y1 = d2;
            this.text = string;
            return this;
        }

        public Builder polygon(double[] dArray, double[] dArray2) {
            this.xPoints = dArray;
            this.yPoints = dArray2;
            return this;
        }

        public Builder color(int n) {
            this.colorIndex = n;
            return this;
        }

        public Builder font(int n) {
            this.fontIndex = n;
            return this;
        }

        public Builder lineWidth(double d) {
            this.lineWidth = d;
            return this;
        }

        public Builder filled(boolean bl) {
            this.filled = bl;
            return this;
        }

        public DrawCommand build() {
            return new DrawCommand(this);
        }
    }

    public static enum CommandType {
        LINE,
        RECTANGLE,
        CIRCLE,
        TEXT,
        POLYGON,
        CLEAR;

    }
}

