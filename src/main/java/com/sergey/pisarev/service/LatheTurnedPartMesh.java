package com.sergey.pisarev.service;

import java.util.List;
import javafx.scene.shape.TriangleMesh;

/**
 * Сплошное тело вращения по профилю [Z, R]: боковая поверхность + торцевые крышки (не «оболочка»).
 */
public final class LatheTurnedPartMesh {
    private static final double MIN_RADIUS_MM = 0.5;

    private LatheTurnedPartMesh() {
    }

    public static TriangleMesh build(List<double[]> profileZRadius, int slices) {
        if (profileZRadius == null || profileZRadius.size() < 2 || slices < 3) {
            return emptyMesh();
        }
        double zMin = profileZRadius.get(0)[0];
        double zMax = profileZRadius.get(profileZRadius.size() - 1)[0];
        double zCenter = (zMin + zMax) * 0.5;
        int rings = profileZRadius.size();
        int ringVerts = slices + 1;
        int sideVerts = rings * ringVerts;
        int totalVerts = sideVerts + 2;
        float[] points = new float[totalVerts * 3];
        int sideFaces = (rings - 1) * slices * 2;
        int capFaces = slices * 2;
        int[] faces = new int[(sideFaces + capFaces) * 6];

        int vertex = 0;
        for (int ring = 0; ring < rings; ring++) {
            double zLocal = profileZRadius.get(ring)[0] - zCenter;
            double radius = Math.max(MIN_RADIUS_MM, profileZRadius.get(ring)[1]);
            for (int slice = 0; slice <= slices; slice++) {
                double angle = Math.PI * 2.0 * slice / slices;
                points[vertex * 3] = (float) zLocal;
                points[vertex * 3 + 1] = (float) (radius * Math.cos(angle));
                points[vertex * 3 + 2] = (float) (radius * Math.sin(angle));
                vertex++;
            }
        }
        int leftCapCenter = sideVerts;
        int rightCapCenter = sideVerts + 1;
        float zLeft = (float) (profileZRadius.get(0)[0] - zCenter);
        float zRight = (float) (profileZRadius.get(rings - 1)[0] - zCenter);
        points[leftCapCenter * 3] = zLeft;
        points[leftCapCenter * 3 + 1] = 0f;
        points[leftCapCenter * 3 + 2] = 0f;
        points[rightCapCenter * 3] = zRight;
        points[rightCapCenter * 3 + 1] = 0f;
        points[rightCapCenter * 3 + 2] = 0f;

        int faceIndex = 0;
        for (int ring = 0; ring < rings - 1; ring++) {
            for (int slice = 0; slice < slices; slice++) {
                int a = ring * ringVerts + slice;
                int b = a + 1;
                int c = a + ringVerts;
                int d = c + 1;
                faceIndex = addTriangle(faces, faceIndex, a, c, b);
                faceIndex = addTriangle(faces, faceIndex, b, c, d);
            }
        }
        for (int slice = 0; slice < slices; slice++) {
            int a = slice;
            int b = slice + 1;
            faceIndex = addTriangle(faces, faceIndex, leftCapCenter, a, b);
        }
        int lastRingStart = (rings - 1) * ringVerts;
        for (int slice = 0; slice < slices; slice++) {
            int a = lastRingStart + slice;
            int b = lastRingStart + slice + 1;
            faceIndex = addTriangle(faces, faceIndex, rightCapCenter, b, a);
        }

        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().setAll(points);
        mesh.getFaces().setAll(faces);
        mesh.getTexCoords().addAll(0f, 0f);
        return mesh;
    }

    public static int countVertices(TriangleMesh mesh) {
        if (mesh == null || mesh.getPoints().size() < 9) {
            return 0;
        }
        return mesh.getPoints().size() / 3;
    }

    private static int addTriangle(int[] faces, int index, int p0, int p1, int p2) {
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
}
