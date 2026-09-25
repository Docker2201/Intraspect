package com.sergey.pisarev.service;

import java.util.TreeMap;

/** Lower boundary of a circular nose swept along a straight centre path. */
final class LatheNoseSweep {
    private static final double CURVE_ERROR_MM = 0.00025;

    private LatheNoseSweep() { }

    static void cut(TreeMap<Double, Double> stock, double z0, double r0,
                    double z1, double r1, double radius) {
        if (z1 < z0) {
            double t = z0; z0 = z1; z1 = t;
            t = r0; r0 = r1; r1 = t;
        }
        if (radius <= 1e-9) {
            LatheRadiusEnvelope.cut(stock, z0, r0, z1, r1);
            return;
        }
        if (Math.abs(z1-z0) < 1e-9) {
            circle(stock, z0, Math.min(r0,r1), radius);
            return;
        }
        circle(stock, z0, r0, radius);
        circle(stock, z1, r1, radius);
        double dz = z1-z0, dr = r1-r0, length = Math.hypot(dz, dr);
        double tangentZ = radius * dr / length;
        double tangentR = -radius * dz / length;
        LatheRadiusEnvelope.cut(stock, z0+tangentZ, r0+tangentR, z1+tangentZ, r1+tangentR);
    }

    private static void circle(TreeMap<Double, Double> stock, double cz, double cr, double radius) {
        // The equator is an actual feature; all other samples are adaptive.
        arc(stock, cz, cr, radius, -radius, 0, 0, -radius, 0);
        arc(stock, cz, cr, radius, 0, -radius, radius, 0, 0);
    }

    private static void arc(TreeMap<Double, Double> stock, double cz, double cr, double radius,
                            double z0, double r0, double z1, double r1, int depth) {
        double z = (z0+z1)*0.5;
        double r = -Math.sqrt(Math.max(0, radius*radius-z*z));
        if (depth < 24 && (r0+r1)*0.5-r > CURVE_ERROR_MM) {
            arc(stock, cz, cr, radius, z0, r0, z, r, depth+1);
            arc(stock, cz, cr, radius, z, r, z1, r1, depth+1);
        } else {
            LatheRadiusEnvelope.cut(stock, cz+z0, cr+r0, cz+z1, cr+r1);
        }
    }
}
