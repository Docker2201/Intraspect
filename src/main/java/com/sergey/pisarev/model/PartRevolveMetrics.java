package com.sergey.pisarev.model;

import com.sergey.pisarev.util.I18n;

/** Метрики детали после снятия материала (для 3D / статуса). */
public record PartRevolveMetrics(
        double stockDiameterMm,
        double minDiameterMm,
        double maxDiameterMm,
        double zSpanMm,
        int profileVertices,
        int cuttingMoves,
        double removedVolumeEstimateMm3
) {
    public String formatStatus() {
        return String.format(
                I18n.text("metrics.001"),
                stockDiameterMm,
                minDiameterMm,
                maxDiameterMm,
                zSpanMm,
                profileVertices,
                cuttingMoves,
                removedVolumeEstimateMm3 / 1000.0);
    }
}
