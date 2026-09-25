package com.sergey.pisarev.service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;

/** Normals of the generating profile, independent of triangle size/diagonals.
 * Averaging tessellation faces made long cylinders look folded beside dense arcs. */
final class LatheMeshNormals {
    private LatheMeshNormals() { }

    static TriangleMesh apply(TriangleMesh source, List<double[]> profile) {
        if (source == null || profile.size() < 2) return source;
        float[] points = source.getPoints().toArray(null);
        int[] original = source.getFaces().toArray(null);
        int stride = source.getVertexFormat().getVertexIndexSize();
        int[] faces = new int[original.length / stride * 3];
        float[] normals = new float[faces.length];
        var indices = new HashMap<Long, Integer>();
        double[][] sectionNormals = new double[profile.size() - 1][2];
        for (int i = 0; i < sectionNormals.length; i++) {
            double dz = profile.get(i + 1)[0] - profile.get(i)[0];
            double dr = profile.get(i + 1)[1] - profile.get(i)[1];
            double length = Math.hypot(dz, dr);
            sectionNormals[i][0] = length > 0 ? dz / length : 1;
            sectionNormals[i][1] = length > 0 ? -dr / length : 0;
        }
        int faceOut = 0, normalCount = 0;
        for (int face = 0; face < original.length; face += stride * 3) {
            int a = original[face] * 3, b = original[face + stride] * 3, c = original[face + stride * 2] * 3;
            double ay = points[a + 1], by = points[b + 1], cy = points[c + 1];
            double ux = points[b] - points[a], uy = by - ay, uz = points[b + 2] - points[a + 2];
            double vx = points[c] - points[a], vy = cy - ay, vz = points[c + 2] - points[a + 2];
            double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz;
            double faceRadius = (Math.hypot(points[a],points[a+2])+Math.hypot(points[b],points[b+2])
                    + Math.hypot(points[c],points[c+2]))/3;
            int section = AxialStockSection.isSection(profile)
                    ? nearestSection(profile,(ay+by+cy)/3,faceRadius) : segmentAt(profile, (ay + by + cy) / 3);
            // Planar ends/shoulders and the longitudinal half-cut get separate
            // normals. They must never pull the cylindrical surface towards a cap.
            int flat = Math.max(ay, Math.max(by, cy)) - Math.min(ay, Math.min(by, cy)) < 1e-7
                    ? (ny >= 0 ? -1 : -2) : 0;
            if (flat == 0 && Math.abs(points[a]) < 1e-7 && Math.abs(points[b]) < 1e-7 && Math.abs(points[c]) < 1e-7)
                flat = nx >= 0 ? -3 : -4;
            for (int corner = 0; corner < 3; corner++) {
                int vertex = original[face + stride * corner];
                long key = ((long) (flat != 0 ? flat : section) << 32) | (vertex & 0xffffffffL);
                Integer normalIndex = indices.get(key);
                if (normalIndex == null) {
                    normalIndex = normalCount++;
                    indices.put(key, normalIndex);
                    int n = normalIndex * 3, p = vertex * 3;
                    if (flat != 0) {
                        normals[n + (flat >= -2 ? 1 : 0)] = (flat == -1 || flat == -3) ? 1 : -1;
                    } else {
                        double nr = sectionNormals[section][0], nz = sectionNormals[section][1];
                        double y = points[p + 1];
                        double eps = Math.max(1e-6, Math.ulp((float) y) * 2.0);
                        int neighbour = Math.abs(y - profile.get(section)[0]) <= eps ? section - 1
                                : (Math.abs(y - profile.get(section + 1)[0]) <= eps ? section + 1 : -1);
                        if (neighbour >= 0 && neighbour < sectionNormals.length) {
                            double[] other = sectionNormals[neighbour];
                            if (nr * other[0] + nz * other[1] > 0.8660254) { // blend smooth joins <30 degrees
                                nr += other[0]; nz += other[1];
                                double length = Math.hypot(nr, nz); nr /= length; nz /= length;
                            }
                        }
                        double radius = Math.hypot(points[p], points[p + 2]);
                        normals[n] = radius > 0 ? (float) (nr * points[p] / radius) : 0;
                        normals[n + 1] = (float) nz;
                        normals[n + 2] = radius > 0 ? (float) (nr * points[p + 2] / radius) : 0;
                    }
                }
                faces[faceOut++] = vertex;
                faces[faceOut++] = normalIndex;
                faces[faceOut++] = original[face + stride * corner + stride - 1];
            }
        }
        var result = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
        result.getPoints().setAll(points);
        result.getTexCoords().setAll(source.getTexCoords());
        result.getNormals().setAll(Arrays.copyOf(normals, normalCount * 3));
        result.getFaces().setAll(faces);
        return result;
    }

    private static int segmentAt(List<double[]> profile, double z) {
        int lo = 0, hi = profile.size() - 1;
        while (lo + 1 < hi) {
            int mid = (lo + hi) >>> 1;
            if (profile.get(mid)[0] <= z) lo = mid; else hi = mid;
        }
        return Math.min(lo, profile.size() - 2);
    }

    private static int nearestSection(List<double[]> profile, double z, double r) {
        double best=Double.POSITIVE_INFINITY; int found=0;
        for(int i=0;i+1<profile.size();i++) {
            var a=profile.get(i); var b=profile.get(i+1);
            if(a[2]!=b[2]) continue;
            double dz=b[0]-a[0], dr=b[1]-a[1], length=dz*dz+dr*dr;
            double t=length==0?0:Math.max(0,Math.min(1,((z-a[0])*dz+(r-a[1])*dr)/length));
            double dist=Math.hypot(z-a[0]-t*dz,r-a[1]-t*dr);
            if(dist<best) {best=dist;found=i;}
        }
        return found;
    }
}
