package com.sergey.pisarev.service;

import com.sergey.pisarev.model.GCodeMoveData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javafx.scene.shape.TriangleMesh;

/**
 * Построение 3D-тела вращения по профилю X/Z (токарная обработка).
 */
public final class LatheMeshBuilder {
    private static final int SLICES = 48;

    private LatheMeshBuilder() {
    }

    public static TriangleMesh buildStockMesh(List<GCodeMoveData> moves, double blankDiameterMm, boolean useBlankDiameter) {
        List<double[]> profile = buildProfile(moves, blankDiameterMm, useBlankDiameter);
        if (profile.size() < 2) {
            return emptyMesh();
        }
        return revolveProfile(profile, SLICES);
    }

    public static List<double[]> buildProfile(List<GCodeMoveData> moves, double blankDiameterMm, boolean useBlankDiameter) {
        ArrayList<double[]> points = new ArrayList<>();
        if (moves == null || moves.isEmpty()) {
            return points;
        }
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (GCodeMoveData move : moves) {
            addProfilePoint(points, move.startX(), move.startZ());
            addProfilePoint(points, move.endX(), move.endZ());
            minZ = Math.min(minZ, Math.min(move.startZ(), move.endZ()));
            maxZ = Math.max(maxZ, Math.max(move.startZ(), move.endZ()));
        }
        if (useBlankDiameter && blankDiameterMm > 0.0) {
            double radius = blankDiameterMm / 2.0;
            points.add(new double[]{minZ, radius});
            points.add(new double[]{maxZ, radius});
        }
        points.sort(Comparator.comparingDouble(a -> a[0]));
        ArrayList<double[]> merged = new ArrayList<>();
        for (double[] point : points) {
            if (merged.isEmpty()) {
                merged.add(point);
                continue;
            }
            double[] last = merged.get(merged.size() - 1);
            if (Math.abs(last[0] - point[0]) < 0.001) {
                last[1] = Math.max(last[1], point[1]);
            } else {
                merged.add(point);
            }
        }
        return merged;
    }

    private static void addProfilePoint(List<double[]> points, double x, double z) {
        double radius = toRadius(x);
        if (!Double.isFinite(radius) || !Double.isFinite(z)) {
            return;
        }
        points.add(new double[]{z, Math.max(0.0, radius)});
    }

    /** X в программе — диаметр (стандарт токарного G-code). */
    public static double toRadius(double x) {
        return Math.abs(x) / 2.0;
    }

    private static TriangleMesh revolveProfile(List<double[]> profile, int slices) {
        int rings = profile.size();
        int pointsPerRing = slices + 1;
        int vertexCount = rings * pointsPerRing;
        float[] points = new float[vertexCount * 3];
        int faceCount = (rings - 1) * slices * 2;
        int[] faces = new int[faceCount * 6];
        int vertex = 0;
        for (int ring = 0; ring < rings; ring++) {
            double z = profile.get(ring)[0];
            double radius = profile.get(ring)[1];
            for (int slice = 0; slice <= slices; slice++) {
                double angle = Math.PI * 2.0 * slice / slices;
                float px = (float)z;
                float py = (float)(radius * Math.cos(angle));
                float pz = (float)(radius * Math.sin(angle));
                points[vertex * 3] = px;
                points[vertex * 3 + 1] = py;
                points[vertex * 3 + 2] = pz;
                vertex++;
            }
        }
        int faceIndex = 0;
        for (int ring = 0; ring < rings - 1; ring++) {
            for (int slice = 0; slice < slices; slice++) {
                int a = ring * pointsPerRing + slice;
                int b = a + 1;
                int c = a + pointsPerRing;
                int d = c + 1;
                faceIndex = addQuad(faces, faceIndex, a, c, b);
                faceIndex = addQuad(faces, faceIndex, b, c, d);
            }
        }
        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().setAll(points);
        mesh.getFaces().setAll(faces);
        mesh.getTexCoords().addAll(0f, 0f);
        return mesh;
    }

    private static int addQuad(int[] faces, int index, int p0, int p1, int p2) {
        faces[index++] = p0;
        faces[index++] = 0;
        faces[index++] = p1;
        faces[index++] = 0;
        faces[index++] = p2;
        faces[index++] = 0;
        return index;
    }

    private static TriangleMesh emptyMesh() {
        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().setAll(0f, 0f, 0f);
        mesh.getTexCoords().addAll(0f, 0f);
        mesh.getFaces().setAll(0, 0, 0, 0);
        return mesh;
    }

    public static String formatCoordLine(double x, double z) {
        return String.format(Locale.US, "X %.3f  Z %.3f", x, z);
    }

    /** Точка траектории в 3D: ось детали вдоль X сцены (Z программы), радиус по Y. */
    public static double sceneX(double zProgram) {
        return zProgram;
    }

    public static double sceneX(double zProgram, double zCenter) {
        return zProgram - zCenter;
    }

    public static double sceneY(double xDiameter) {
        return toRadius(xDiameter);
    }

    public static double sceneZ() {
        return 0.0;
    }
}
