package com.sergey.pisarev.model;

/**
 * Смещение нулевой точки (G54–G59): приращение X и Z к координатам программы.
 */
public record WorkOffsetValues(double xMm, double zMm) {
    public static final WorkOffsetValues ZERO = new WorkOffsetValues(0.0, 0.0);

    public boolean isZero() {
        return Math.abs(this.xMm) < 1.0E-6 && Math.abs(this.zMm) < 1.0E-6;
    }
}
