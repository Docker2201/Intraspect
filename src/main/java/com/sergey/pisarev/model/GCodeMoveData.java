package com.sergey.pisarev.model;

/**
 * Сегмент траектории G-code для 2D/3D отображения.
 */
public record GCodeMoveData(
        double startX,
        double startZ,
        double endX,
        double endZ,
        boolean rapid,
        int sourceLine,
        boolean arcSegment,
        int toolNumber,
        int edgeNumber,
        boolean arcEnd
) {
    public GCodeMoveData(
            double startX,
            double startZ,
            double endX,
            double endZ,
            boolean rapid,
            int sourceLine,
            boolean arcSegment,
            int toolNumber,
            int edgeNumber
    ) {
        this(startX, startZ, endX, endZ, rapid, sourceLine, arcSegment, toolNumber, edgeNumber, !arcSegment);
    }

    public GCodeMoveData(
            double startX,
            double startZ,
            double endX,
            double endZ,
            boolean rapid,
            int sourceLine,
            boolean arcSegment,
            int toolNumber
    ) {
        this(startX, startZ, endX, endZ, rapid, sourceLine, arcSegment, toolNumber, 1, !arcSegment);
    }

    public GCodeMoveData(
            double startX,
            double startZ,
            double endX,
            double endZ,
            boolean rapid,
            int sourceLine,
            boolean arcSegment
    ) {
        this(startX, startZ, endX, endZ, rapid, sourceLine, arcSegment, 0, 1, !arcSegment);
    }
}
