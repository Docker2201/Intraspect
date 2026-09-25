package com.sergey.pisarev.service;

import com.sergey.pisarev.occt.*;
import java.util.*;

/** Run with the rebuilt OCCT DLL, not a JavaFX fallback. */
public final class LatheNativeRegressionTest {
    public static void main(String[] args) {
        if (!OcctNative.isAvailable()) throw new AssertionError(OcctNative.loadErrorMessage());
        var profile = new ArrayList<double[]>(List.of(p(-2000,50),p(-100,50),p(-100,40),
                p(-99.8,40),p(-99.8,50),p(0,50)));
        for (int direction = 0; direction < 2; direction++) {
            var mesh = OcctLatheMesher.buildRevolvedSolid(profile);
            if (mesh == null) throw new AssertionError("OCCT failed: " + OcctNative.meshErrorMessage());
            boolean outer = false, inner = false;
            for (int i = 0; i < mesh.getPoints().size(); i += 3) {
                double z = mesh.getPoints().get(i+1);
                double radius = Math.hypot(mesh.getPoints().get(i),mesh.getPoints().get(i+2));
                if (Math.abs(z + 100) < 1e-6) {
                    outer |= Math.abs(radius - 50) < 1e-4;
                    inner |= Math.abs(radius - 40) < 1e-4;
                }
            }
            if (!outer || !inner) throw new AssertionError("OCCT lost/moved shoulder, direction=" + direction);
            System.out.println("PASS native shoulder keeps both radii at exact Z; direction=" + direction);
            Collections.reverse(profile);
        }
    }
    private static double[] p(double z, double r) { return new double[]{z,r}; }
}
