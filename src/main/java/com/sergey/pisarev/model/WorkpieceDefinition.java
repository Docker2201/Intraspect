package com.sergey.pisarev.model;

/**
 * Заготовка из команды WORKPIECE (SINUMERIK / SinuTrain).
 */
public final class WorkpieceDefinition {
    public enum Shape {
        CYLINDER,
        BOX,
        UNKNOWN
    }

    private final Shape shape;
    private final double diameterMm;
    private final double lengthMm;
    private final double zMin;
    private final double zMax;
    private final int sourceLine;
    /** Габарит прямоугольной заготовки по X; 0 — размер не задан (цилиндр). */
    private final double widthXmm;
    /** Габарит прямоугольной заготовки по Y; 0 — размер не задан (цилиндр). */
    private final double widthYmm;
    /**
     * Привязана ли заготовка по Z самой программой.
     *
     * <p>Запись {@code WORKPIECE(,,,"CYLINDER",D,L)} говорит только диаметр и длину:
     * где этот пруток стоит вдоль Z, она не сообщает. Такую заготовку двигать можно
     * и нужно — иначе она встаёт наугад. Формы с ZA/ZI/ZB привязаны, их положение
     * трогать нельзя: это металл, а не рамка.
     */
    private final boolean zAnchored;
    /** Initial through bore of a cylindrical blank; zero means solid stock. */
    private final double initialBoreDiameterMm;
    private final boolean boreFromProgram;

    public WorkpieceDefinition(
            Shape shape,
            double diameterMm,
            double lengthMm,
            double zMin,
            double zMax,
            int sourceLine
    ) {
        this(shape, diameterMm, lengthMm, zMin, zMax, sourceLine, 0.0, 0.0);
    }

    public WorkpieceDefinition(
            Shape shape,
            double diameterMm,
            double lengthMm,
            double zMin,
            double zMax,
            int sourceLine,
            double widthXmm,
            double widthYmm
    ) {
        this.shape = shape;
        this.diameterMm = diameterMm;
        this.lengthMm = lengthMm;
        this.zMin = Math.min(zMin, zMax);
        this.zMax = Math.max(zMin, zMax);
        this.sourceLine = sourceLine;
        this.widthXmm = Math.max(0.0, widthXmm);
        this.widthYmm = Math.max(0.0, widthYmm);
        this.zAnchored = true;
        this.initialBoreDiameterMm = 0;
        this.boreFromProgram = false;
    }

    private WorkpieceDefinition(WorkpieceDefinition source, boolean zAnchored) {
        this(source, zAnchored, source.initialBoreDiameterMm, source.boreFromProgram);
    }

    private WorkpieceDefinition(WorkpieceDefinition source, boolean zAnchored, double initialBoreDiameterMm, boolean boreFromProgram) {
        this.shape = source.shape;
        this.diameterMm = source.diameterMm;
        this.lengthMm = source.lengthMm;
        this.zMin = source.zMin;
        this.zMax = source.zMax;
        this.sourceLine = source.sourceLine;
        this.widthXmm = source.widthXmm;
        this.widthYmm = source.widthYmm;
        this.zAnchored = zAnchored;
        this.initialBoreDiameterMm = initialBoreDiameterMm;
        this.boreFromProgram = boreFromProgram;
    }

    public WorkpieceDefinition withInitialBoreDiameter(double diameter) {
        if (!Double.isFinite(diameter) || diameter < 0
                || (diameter > 0 && (diameter >= this.diameterMm || this.shape != Shape.CYLINDER)))
            throw new IllegalArgumentException("Initial bore must be smaller than the cylindrical blank");
        return new WorkpieceDefinition(this, this.zAnchored, diameter, false);
    }

    /** Nominal preform reconstructed from a through-boring operation, not a measured raw blank. */
    public WorkpieceDefinition withProgramBoreDiameter(double diameter) {
        var checked = withInitialBoreDiameter(diameter);
        return new WorkpieceDefinition(checked, checked.zAnchored, diameter, true);
    }

    public boolean isBoreFromProgram() { return this.boreFromProgram; }

    public double getInitialBoreDiameterMm() { return this.initialBoreDiameterMm; }

    /** Та же заготовка, но её положение вдоль Z программа не задавала. */
    public WorkpieceDefinition withoutZAnchor() {
        return new WorkpieceDefinition(this, false);
    }

    /** Задала ли программа положение заготовки вдоль Z. */
    public boolean isZAnchored() {
        return this.zAnchored;
    }

    /** Та же заготовка с другими границами по Z: габариты X/Y при этом не теряются. */
    public WorkpieceDefinition withZRange(double newZMin, double newZMax, double newLengthMm) {
        WorkpieceDefinition moved = new WorkpieceDefinition(
                this.shape, this.diameterMm, newLengthMm, newZMin, newZMax, this.sourceLine,
                this.widthXmm, this.widthYmm);
        return new WorkpieceDefinition(moved, this.zAnchored, this.initialBoreDiameterMm, this.boreFromProgram);
    }

    public Shape getShape() {
        return this.shape;
    }

    public double getDiameterMm() {
        return this.diameterMm;
    }

    public double getLengthMm() {
        return this.lengthMm;
    }

    public double getZMin() {
        return this.zMin;
    }

    public double getZMax() {
        return this.zMax;
    }

    public double getWidthXmm() {
        return this.widthXmm;
    }

    public double getWidthYmm() {
        return this.widthYmm;
    }

    /**
     * Радиус заготовки. Для прямоугольной — радиус описанного цилиндра: токарная модель
     * снятия материала работает с телом вращения, и описанный цилиндр её не занижает.
     */
    public double getStockRadiusMm() {
        return Math.max(0.0, this.diameterMm / 2.0);
    }

    /** Короткая подпись габаритов: «Ø100» для цилиндра, «100×80» для прямоугольной. */
    public String describeSize() {
        if (this.shape == Shape.BOX) {
            return String.format(java.util.Locale.US, "%.0f\u00D7%.0f", this.widthXmm, this.widthYmm);
        }
        return String.format(java.util.Locale.US, "\u00D8%.0f", this.diameterMm);
    }

    public int getSourceLine() {
        return this.sourceLine;
    }

    public boolean isValid() {
        if (this.zMax <= this.zMin) {
            return false;
        }
        if (this.shape == Shape.BOX) {
            return this.widthXmm > 0.0 && this.widthYmm > 0.0 && this.diameterMm > 0.0;
        }
        return this.shape == Shape.CYLINDER && this.diameterMm > 0.0;
    }
}
