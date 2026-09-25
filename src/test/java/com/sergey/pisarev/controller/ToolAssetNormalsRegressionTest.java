package com.sergey.pisarev.controller;

import java.io.*;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.shape.*;

/** OBJ corner normals must survive import, including legacy faces without vn. */
public final class ToolAssetNormalsRegressionTest {
    public static void main(String[] args)throws Exception {
        Platform.startup(()->{});
        try {
            var reader=ToolAssetModelLoader.class.getDeclaredMethod("readObj",BufferedReader.class,String.class,int.class);
            reader.setAccessible(true);
            String obj="v 0 0 0\nv 1 0 0\nv 0 1 0\nvt .25 .75\nvn 0 1 1\n"
                    +"f 1/1/1 2/1/1 3/1/1\nf -3//-1 -2//-1 -1//-1\nf 1 2 3\n";
            var group=(Group)reader.invoke(null,new BufferedReader(new StringReader(obj)),"test.obj",550);
            var mesh=(TriangleMesh)((MeshView)group.getChildren().get(0)).getMesh();
            check(mesh.getVertexFormat()==VertexFormat.POINT_NORMAL_TEXCOORD,"explicit normals");
            for(int face=0;face<3;face++)for(int corner=0;corner<3;corner++) {
                int n=mesh.getFaces().get(face*9+corner*3+1)*3;
                double expectedY=face==2?0:Math.sqrt(.5),expectedZ=face==2?1:Math.sqrt(.5);
                check(Math.abs(mesh.getNormals().get(n+1)-expectedY)<1e-6
                        && Math.abs(mesh.getNormals().get(n+2)-expectedZ)<1e-6,"corner normal or legacy fallback");
            }
            var first=ToolAssetModelLoader.load("wheel_cranked_32.obj",550);
            var second=ToolAssetModelLoader.load("wheel_cranked_32.obj",550);
            check(first!=null&&first.getChildren().size()==second.getChildren().size(),"library loaded");
            int triangles=0;
            for(int i=0;i<first.getChildren().size();i++) {
                var a=(MeshView)first.getChildren().get(i);var b=(MeshView)second.getChildren().get(i);
                check(a!=b&&a.getMesh()==b.getMesh(),"cached geometry reused across supports");
                var m=(TriangleMesh)a.getMesh();triangles+=m.getFaces().size()/m.getFaceElementSize();
                check(m.getNormals().size()>0,"Blender normals retained");
            }
            check(triangles<5000,"bounded tool geometry cost");
            System.out.println("Tool OBJ normals passed; holder triangles="+triangles);
        } finally {Platform.exit();}
    }
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
}
