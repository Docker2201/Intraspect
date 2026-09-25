package com.sergey.pisarev.occt;

import java.util.List;
import javafx.scene.shape.TriangleMesh;

/**
 * Построение тела вращения через Open CASCADE (BRep + триангуляция).
 */
public final class OcctLatheMesher {
    private OcctLatheMesher() {
    }

    public static TriangleMesh buildRevolvedSolid(List<double[]> profileZRadius) {
        if (!OcctNative.isAvailable() || profileZRadius == null || profileZRadius.size() < 2) {
            return null;
        }
        int n = profileZRadius.size();
        boolean section = com.sergey.pisarev.service.AxialStockSection.isSection(profileZRadius);
        int[] loops = new int[n];
        double[] z = new double[n];
        double[] r = new double[n];
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        double maxR = 0.0;
        for (int i = 0; i < n; i++) {
            z[i] = profileZRadius.get(i)[0];
            r[i] = Math.max(section ? 0 : 0.05, profileZRadius.get(i)[1]);
            if (section) loops[i] = (int)profileZRadius.get(i)[2];
        }
        for (int i = 0; i < n; i++) {
            minZ = Math.min(minZ, z[i]);
            maxZ = Math.max(maxZ, z[i]);
            maxR = Math.max(maxR, r[i]);
        }
        double span = Math.max(1.0, maxZ - minZ);
        double deflection = Math.max(0.06, Math.min(0.4, Math.min(span, maxR * 2.0) / 800.0));

        long handle = 0L;
        try {
            OcctNative.recordMeshError("");
            handle = section ? OcctNative.buildRevolvedSection(z,r,loops,deflection)
                    : OcctNative.buildRevolvedSolid(z, r, deflection);
            if (handle <= 0L) {
                return null;
            }
            int vertexCount = OcctNative.meshVertexCount(handle);
            int triangleCount = OcctNative.meshTriangleCount(handle);
            if (vertexCount < 3 || triangleCount < 1) {
                return null;
            }
            float[] positions = OcctNative.meshPositions(handle);
            int[] indices = OcctNative.meshIndices(handle);
            if (positions == null || indices == null) {
                return null;
            }
            TriangleMesh mesh = toJavaFxMesh(positions, indices, vertexCount);
            if (!isUsableMesh(mesh, maxR, span)) {
                return null;
            }
            return mesh;
        } catch (RuntimeException | LinkageError nativeFailure) {
            // Нативный код теперь сообщает причину исключением. Запоминаем её и отдаём null:
            // вызывающая сторона спокойно перейдёт на построение средствами JavaFX,
            // но причина отката больше не теряется.
            OcctNative.recordMeshError(nativeFailure.getMessage() != null
                    ? nativeFailure.getMessage()
                    : nativeFailure.getClass().getSimpleName());
            return null;
        } finally {
            if (handle > 0L) {
                OcctNative.releaseMesh(handle);
            }
        }
    }

    /** OCCT: X,Z — радиальная плоскость, Y — ось Z программы (как revolveSolidLathe). */
    private static TriangleMesh toJavaFxMesh(float[] occtXyz, int[] triangles, int vertexCount) {
        int maxIndex = vertexCount - 1;
        int[] faces = new int[triangles.length * 2];
        int face = 0;
        for (int i = 0; i < triangles.length; i += 3) {
            int i0 = triangles[i];
            int i1 = triangles[i + 1];
            int i2 = triangles[i + 2];
            if (i0 < 0 || i1 < 0 || i2 < 0 || i0 > maxIndex || i1 > maxIndex || i2 > maxIndex) {
                continue;
            }
            faces[face++] = i0;
            faces[face++] = 0;
            faces[face++] = i1;
            faces[face++] = 0;
            faces[face++] = i2;
            faces[face++] = 0;
        }
        if (face < 6) {
            return null;
        }
        if (face < faces.length) {
            int[] trimmed = new int[face];
            System.arraycopy(faces, 0, trimmed, 0, face);
            faces = trimmed;
        }
        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().setAll(occtXyz);
        mesh.getTexCoords().addAll(0f, 0f);
        mesh.getFaces().setAll(faces);
        return mesh;
    }

    private static boolean isUsableMesh(TriangleMesh mesh, double expectedMaxR, double expectedSpan) {
        if (mesh == null || mesh.getPoints().size() < 9 || mesh.getFaces().size() < 6) {
            return false;
        }
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        var points = mesh.getPoints();
        for (int i = 0; i < points.size(); i += 3) {
            float x = points.get(i);
            float y = points.get(i + 1);
            float z = points.get(i + 2);
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                return false;
            }
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        double radial = Math.max(maxX - minX, maxZ - minZ);
        double axial = maxY - minY;
        if (radial < expectedMaxR * 0.15 || axial < expectedSpan * 0.15) {
            return false;
        }
        return radial >= 0.5 && axial >= 0.5;
    }
}
