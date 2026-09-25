package com.sergey.pisarev.service;

import java.util.*;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;

/** Direct tessellation of an already subtracted meridional section for live playback.
 * No Boolean solid rebuild or nearest-face search per frame. Closed loops include
 * bore walls and shoulders; the final result still uses the OCCT solid mesher.
 */
final class CycleSectionMesh {
    private CycleSectionMesh() { }
    static TriangleMesh build(List<double[]> section, int slices) {
        return buildEdges(edges(section), slices);
    }

    // Normals are computed across the entire section, before dividing it into
    // render batches. Batch boundaries must never create a cap or a shading seam.
    record Edge(double az, double ar, double bz, double br,
                double anr, double anz, double bnr, double bnz) { }

    static List<Edge> edges(List<double[]> section) {
        var result = new ArrayList<Edge>();
        for(int first=0;first<section.size();) {
            int end=first+1;while(end<section.size()&&section.get(end)[2]==section.get(first)[2])end++;
            int edges=end-first-1;
            double[][] n=new double[Math.max(0,edges)][2];
            for(int e=0;e<edges;e++) {
                var a=section.get(first+e);var b=section.get(first+e+1);
                double dz=b[0]-a[0],dr=b[1]-a[1],len=Math.hypot(dz,dr);
                if(len>1e-9){n[e][0]=dz/len;n[e][1]=-dr/len;}
            }
            for(int e=0;e<edges;e++) {
                var a=section.get(first+e);var b=section.get(first+e+1);
                if((a[1]<1e-9&&b[1]<1e-9)||Math.hypot(a[0]-b[0],a[1]-b[1])<1e-9)continue;
                double[] na=blend(n[e],n[(e+edges-1)%edges]), nb=blend(n[e],n[(e+1)%edges]);
                result.add(new Edge(a[0],a[1],b[0],b[1],na[0],na[1],nb[0],nb[1]));
            }
            first=end;
        }
        return result;
    }

    static TriangleMesh buildEdges(List<Edge> edges, int slices) {
        int capacity = edges.size() * 2 * (slices+1);
        float[] points=new float[capacity*3], normals=new float[capacity*3];
        int[] faces=new int[edges.size()*slices*18];
        int pointCount=0, faceCount=0;
        double[] cos=new double[slices+1], sin=new double[slices+1];
        for(int j=0;j<=slices;j++){double angle=2*Math.PI*(j%slices)/slices;cos[j]=Math.cos(angle);sin[j]=Math.sin(angle);}
        int rings=slices+1;
        for(var edge:edges) {
                int base=pointCount/3;
                for(int ring=0;ring<2;ring++) {
                    double z=ring==0?edge.az:edge.bz, r=ring==0?edge.ar:edge.br;
                    double nr=ring==0?edge.anr:edge.bnr, nz=ring==0?edge.anz:edge.bnz;
                    for(int j=0;j<=slices;j++) {
                        double c=cos[j],s=sin[j];
                        points[pointCount]=(float)(r*c);normals[pointCount++]=(float)(nr*c);
                        points[pointCount]=(float)z;normals[pointCount++]=(float)nz;
                        points[pointCount]=(float)(r*s);normals[pointCount++]=(float)(nr*s);
                    }
                }
                for(int j=0;j<slices;j++) {
                    if(edge.ar>1e-9)faceCount=triangle(faces,faceCount,base+j,base+rings+j,base+j+1);
                    if(edge.br>1e-9)faceCount=triangle(faces,faceCount,base+rings+j,base+rings+j+1,base+j+1);
                }
        }
        var mesh=new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
        mesh.getPoints().setAll(points,0,pointCount);mesh.getNormals().setAll(normals,0,pointCount);
        mesh.getTexCoords().setAll(0,0);mesh.getFaces().setAll(faces,0,faceCount);
        return mesh;
    }
    private static double[] blend(double[] a,double[] b) {
        if(a[0]*b[0]+a[1]*b[1]<.8660254)return a;
        double x=a[0]+b[0],y=a[1]+b[1],length=Math.hypot(x,y);
        return length>1e-9?new double[]{x/length,y/length}:a;
    }
    private static int triangle(int[] faces,int offset,int a,int b,int c){
        faces[offset++]=a;faces[offset++]=a;faces[offset++]=0;
        faces[offset++]=b;faces[offset++]=b;faces[offset++]=0;
        faces[offset++]=c;faces[offset++]=c;faces[offset++]=0;
        return offset;
    }
}
