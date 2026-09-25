package com.sergey.pisarev.model;

import com.sergey.pisarev.util.I18n;
import java.util.Locale;

/**
 * Cutting direction for a turning tool in the X/Z plane.
 */
public enum ToolCutDirection {
    BOTH("BOTH", I18n.text("cutdir.001")),
    Z_PLUS("Z_PLUS", I18n.text("cutdir.002")),
    Z_MINUS("Z_MINUS", I18n.text("cutdir.003")),
    X_MINUS("X_MINUS", I18n.text("cutdir.004")),
    X_PLUS("X_PLUS", I18n.text("cutdir.005"));

    private static final double EPS = 1.0E-6;

    private final String key;
    private final String label;

    ToolCutDirection(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String getKey() {
        return this.key;
    }

    public String getLabel() {
        return this.label;
    }

    public boolean allowsMove(double startX, double startZ, double endX, double endZ) {
        double dz = endZ - startZ;
        double dx = endX - startX;
        return switch (this) {
            case Z_PLUS -> dz >= -EPS;
            case Z_MINUS -> dz <= EPS;
            case X_MINUS -> dx <= EPS;
            case X_PLUS -> dx >= -EPS;
            case BOTH -> true;
        };
    }

    public static ToolCutDirection fromKey(String value) {
        if (value == null || value.isBlank()) {
            return BOTH;
        }
        String normalized = value.trim().toUpperCase(Locale.US);
        for (ToolCutDirection direction : values()) {
            if (direction.key.equals(normalized) || direction.name().equals(normalized)) {
                return direction;
            }
        }
        return switch (normalized) {
            case "RIGHT", "R", "+Z" -> Z_PLUS;
            case "LEFT", "L", "-Z" -> Z_MINUS;
            case "IN", "CENTER", "-X" -> X_MINUS;
            case "OUT", "+X" -> X_PLUS;
            default -> BOTH;
        };
    }

    @Override
    public String toString() {
        return this.label;
    }
}
