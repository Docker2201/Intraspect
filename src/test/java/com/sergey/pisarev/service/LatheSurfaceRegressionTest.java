package com.sergey.pisarev.service;

import com.sergey.pisarev.model.*;
import java.util.*;

/** Standalone numerical regressions; no user settings or native library required. */
public final class LatheSurfaceRegressionTest {
    private static int failures;

    public static void main(String[] args) {
        var flat = context(List.of(move(40, -93.37, 40, -12.13)), -110.23, 0.17);
        verifySurface("straight feed", flat, z -> 20, -92, -14, 1e-7);
        var taper = context(List.of(move(40, -93.37, 60, -12.13)), -110.23, 0.17);
        verifySurface("taper", taper, z -> 20 + 10 * (z + 93.37) / 81.24, -92, -14, 0.001);
        var crossing = context(List.of(move(40, -90, 60, -10), move(60, -90, 40, -10)), -110, 0);
        verifySurface("intersecting passes", crossing,
                z -> Math.min(20 + (z + 90) / 8, 30 - (z + 90) / 8), -89, -11, 0.001);
        var profile = LatheStockRemovalSimulator.buildRevolveProfile(flat);
        check("left stock end", Math.abs(profile.get(0)[0] + 110.23) < 1e-8);
        check("right stock end", Math.abs(profile.get(profile.size() - 1)[0] - 0.17) < 1e-8);
        check("uncut stock preserved", Math.abs(radius(profile, -105) - 50) < 1e-8);
        List<double[]> narrow = List.of(p(-2000, 50), p(-100, 50), p(-100, 40),
                p(-99.8, 40), p(-99.8, 50), p(0, 50));
        var shaped = StockRemovalMeshBuilder.buildSmoothMeshProfile(
                context(List.of(move(80, -100, 80, -99.8)), -2000, 0), narrow, 50);
        check("narrow groove depth", Math.abs(radius(shaped, -99.9) - 40) < 0.001);
        check("groove left shoulder stays put", Math.abs(radius(shaped, -100.1) - 50) < 0.001);
        check("groove right shoulder stays put", Math.abs(radius(shaped, -99.7) - 50) < 0.001);
        var manyMoves = new ArrayList<GCodeMoveData>();
        for (int i = 0; i < 100; i++) manyMoves.add(move(40, -90 + i * .7, 40, -90 + (i + 1) * .7));
        var segmented = context(manyMoves, -110, 0);
        verifySurface("segmented straight feed", segmented, z -> 20, -89, -21, 1e-7);
        var incremental = LatheStockRemovalSimulator.newStockEnvelope(segmented);
        var envelope = new LatheCuttingEnvelope(segmented);
        for (int i = 0; i < manyMoves.size(); i++)
            LatheStockRemovalSimulator.applyMovesToEnvelope(incremental, segmented, envelope, manyMoves, i, i + 1);
        var a = LatheStockRemovalSimulator.envelopeToProfile(segmented, incremental);
        var b = LatheStockRemovalSimulator.buildRevolveProfile(segmented);
        check("cycle equals finished stock", Math.abs(radius(a, -50.123) - radius(b, -50.123)) < 1e-8);
        var entry = context(List.of(move(120, -100, 80, 0)), -110, 0);
        verifySurface("entry intersects stock at Z-50", entry, z -> Math.min(50, 60 - .2 * (z + 100)), -99, -1, .001);
        var outside = context(List.of(move(40, -200, 40, -150), move(40, 1, 40, 50)), -110, 0);
        var untouched = LatheStockRemovalSimulator.buildRevolveProfile(outside);
        check("out-of-stock moves do not grow or cut blank", untouched.size() == 2
                && untouched.get(0)[0] == -110 && untouched.get(1)[0] == 0
                && untouched.get(0)[1] == 50 && untouched.get(1)[1] == 50);
        var arcMoves = new ArrayList<GCodeMoveData>();
        for (int i = 0; i < 180; i++) {
            double t0 = Math.PI * i / 360, t1 = Math.PI * (i + 1) / 360;
            arcMoves.add(new GCodeMoveData(2 * (20 + 10 * Math.cos(t0)), -60 + 10 * Math.sin(t0),
                    2 * (20 + 10 * Math.cos(t1)), -60 + 10 * Math.sin(t1), false, 1, true, 0, 1));
        }
        verifySurface("quarter-circle arc", context(arcMoves, -100, 0),
                z -> 20 + Math.sqrt(100 - (z + 60) * (z + 60)), -59.99, -50.1, .004);
        verifyNormals();
        System.out.println("FAILURES=" + failures);
        if (failures > 0) System.exit(1);
    }

    private static void verifyNormals() {
        var context = context(List.of(move(40, -90, 40, -10)), -100, 0);
        var mesh = StockRemovalMeshBuilder.buildCyclePreviewTriangleMesh(context, List.of(p(-100, 20), p(0, 20)), 50);
        check("explicit surface normals", mesh.getVertexFormat() == javafx.scene.shape.VertexFormat.POINT_NORMAL_TEXCOORD);
        boolean unit = true, radial = true, caps = false;
        for (int i = 0; i < mesh.getNormals().size(); i += 3) {
            double nx = mesh.getNormals().get(i), ny = mesh.getNormals().get(i+1), nz = mesh.getNormals().get(i+2);
            unit &= Math.abs(nx*nx+ny*ny+nz*nz-1) < 1e-6;
        }
        for (int i = 0; i < mesh.getFaces().size(); i += 9) {
            int a = mesh.getFaces().get(i)*3, b = mesh.getFaces().get(i+3)*3, c = mesh.getFaces().get(i+6)*3;
            boolean cap = mesh.getPoints().get(a+1) == mesh.getPoints().get(b+1)
                    && mesh.getPoints().get(b+1) == mesh.getPoints().get(c+1);
            for (int j = 0; j < 9; j += 3) {
                int n = mesh.getFaces().get(i+j+1)*3;
                if (cap) caps |= Math.abs(mesh.getNormals().get(n+1)) == 1;
                else radial &= Math.abs(mesh.getNormals().get(n+1)) < 1e-7;
            }
        }
        check("unit normals", unit);
        check("cylinder does not bend into end caps", radial && caps);
    }

    private static void verifySurface(String label, SimulationContext context,
                                      java.util.function.DoubleUnaryOperator expected,
                                      double start, double end, double tolerance) {
        var profile = LatheStockRemovalSimulator.buildRevolveProfile(context);
        var mesh = StockRemovalMeshBuilder.buildSmoothMeshProfile(context, profile, 50);
        double worst = 0;
        for (double z = start; z < end; z += .0137) {
            worst = Math.max(worst, Math.abs(radius(profile, z) - expected.applyAsDouble(z)));
            worst = Math.max(worst, Math.abs(radius(mesh, z) - expected.applyAsDouble(z)));
        }
        System.out.printf(Locale.US, "%s: max radius error %.9f mm; profile %d, mesh %d points%n",
                label, worst, profile.size(), mesh.size());
        check(label, worst <= tolerance);
    }

    public static double radius(List<double[]> profile, double z) {
        for (int i = 1; i < profile.size(); i++) {
            double[] a = profile.get(i - 1), b = profile.get(i);
            if (z < b[0]) return a[1] + (b[1] - a[1]) * (z - a[0]) / (b[0] - a[0]);
        }
        return profile.get(profile.size() - 1)[1];
    }
    public static SimulationContext context(List<GCodeMoveData> moves, double lo, double hi) {
        return new SimulationContext(moves, null,
                new WorkpieceDefinition(WorkpieceDefinition.Shape.CYLINDER, 100, hi-lo, lo, hi, 1),
                List.of(), 100, 100, "", true);
    }
    private static GCodeMoveData move(double x0, double z0, double x1, double z1) {
        return new GCodeMoveData(x0, z0, x1, z1, false, 1, false, 0, 1);
    }
    private static double[] p(double z, double r) { return new double[]{z,r}; }
    private static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) failures++;
    }
}
