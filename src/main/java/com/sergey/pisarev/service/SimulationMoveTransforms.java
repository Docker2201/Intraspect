package com.sergey.pisarev.service;

import com.sergey.pisarev.model.GCodeMoveData;
import com.sergey.pisarev.model.SimulationContext;
import com.sergey.pisarev.model.WorkOffsetValues;
import java.util.ArrayList;
import java.util.List;

/** Применение смещений нулевой точки к траектории симуляции. */
public final class SimulationMoveTransforms {
    private SimulationMoveTransforms() {
    }

    public static List<GCodeMoveData> applyActiveWorkOffset(SimulationContext context, List<GCodeMoveData> moves) {
        if (moves == null || moves.isEmpty()) {
            return List.of();
        }
        WorkOffsetValues offset = GraphWorkOffsetTransform.resolveActiveOffset(
                context.getActiveWorkOffsetCode(),
                context.getWorkOffsets());
        if (offset == null || offset.equals(WorkOffsetValues.ZERO)) {
            return List.copyOf(moves);
        }
        double dx = offset.xMm();
        double dz = offset.zMm();
        if (Math.abs(dx) < 1.0e-6 && Math.abs(dz) < 1.0e-6) {
            return List.copyOf(moves);
        }
        ArrayList<GCodeMoveData> shifted = new ArrayList<>(moves.size());
        for (GCodeMoveData move : moves) {
            shifted.add(new GCodeMoveData(
                    move.startX() + dx,
                    move.startZ() + dz,
                    move.endX() + dx,
                    move.endZ() + dz,
                    move.rapid(),
                    move.sourceLine(),
                    move.arcSegment(),
                    move.toolNumber(),
                    move.edgeNumber(),
                    move.arcEnd()));
        }
        return shifted;
    }
}
