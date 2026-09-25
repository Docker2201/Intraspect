package com.sergey.pisarev.service;

import java.util.ArrayList;
import java.util.TreeMap;

/** Piecewise linear min(stock, swept tool), with intersections retained exactly. */
final class LatheRadiusEnvelope {
    // TreeMap stores one radius per Z. Represent a discontinuous shoulder over
    // 0.1 micrometre, rather than a stock-grid-sized chamfer (formerly up to 2 mm).
    private static final double SHOULDER_EPS_MM = 0.0001;

    private LatheRadiusEnvelope() { }

    static void cut(TreeMap<Double, Double> stock, double z0, double r0, double z1, double r1) {
        if (stock.size() < 2 || !Double.isFinite(z0) || !Double.isFinite(z1)
                || !Double.isFinite(r0) || !Double.isFinite(r1) || z1 < z0) return;
        double lo = Math.max(stock.firstKey(), z0);
        double hi = Math.min(stock.lastKey(), z1);
        if (hi < lo) return;
        double oldLo = radiusAt(stock, lo), oldHi = radiusAt(stock, hi);
        // Keep material outside this move. Sample BOTH guards before modifying
        // the map, so insertion order cannot change a neighbouring taper.
        double guardLo = Math.max(stock.firstKey(), lo - SHOULDER_EPS_MM);
        double guardHi = Math.min(stock.lastKey(), hi + SHOULDER_EPS_MM);
        double oldGuardLo = radiusAt(stock, guardLo), oldGuardHi = radiusAt(stock, guardHi);
        stock.put(guardLo, oldGuardLo);
        stock.put(guardHi, oldGuardHi);
        stock.put(lo, oldLo);
        stock.put(hi, oldHi);
        var points = new ArrayList<>(stock.subMap(lo, true, hi, true).entrySet());
        double prevZ = Double.NaN, prevOld = 0, prevCut = 0;
        for (var point : points) {
            double z = point.getKey();
            double old = point.getValue();
            double cut = z1 > z0 ? r0 + (r1 - r0) * (z - z0) / (z1 - z0) : Math.min(r0, r1);
            double difference = old - cut;
            double prevDifference = prevOld - prevCut;
            if (Double.isFinite(prevZ) && prevDifference * difference < 0) {
                double t = prevDifference / (prevDifference - difference);
                stock.put(prevZ + (z - prevZ) * t, prevOld + (old - prevOld) * t);
            }
            stock.put(z, Math.max(0, Math.min(old, cut)));
            prevZ = z; prevOld = old; prevCut = cut;
        }
    }

    private static double radiusAt(TreeMap<Double, Double> stock, double z) {
        var a = stock.floorEntry(z);
        var b = stock.ceilingEntry(z);
        if (a == null) return b.getValue();
        if (b == null || a.getKey().equals(b.getKey())) return a.getValue();
        return a.getValue() + (b.getValue() - a.getValue()) * (z - a.getKey()) / (b.getKey() - a.getKey());
    }
}
