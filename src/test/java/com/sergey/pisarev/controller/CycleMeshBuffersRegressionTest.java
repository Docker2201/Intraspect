package com.sergey.pisarev.controller;

import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;

public final class CycleMeshBuffersRegressionTest {
    public static void main(String[] args) {
        var source=triangle();var display=new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
        display=CycleMeshBuffers.update(display,source);
        int[] changes=new int[2];
        display.getFaces().addListener((a,size,from,to)->changes[0]++);
        display.getPoints().addListener((a,size,from,to)->changes[1]++);
        CycleMeshBuffers.update(display,source);
        check(changes[0]==0 && changes[1]==0,"identical snapshot invalidated GPU data");
        var moved=triangle();moved.getPoints().set(3,2f);moved.getNormals().set(1,.1f);
        CycleMeshBuffers.update(display,moved);
        check(changes[0]==0 && changes[1]==1,"vertex motion rebuilt topology");
        check(display.getPoints().get(3)==2f && display.getNormals().get(1)==.1f,"position/normal update lost");
        check(source.getPoints().get(3)==1f,"display modified the immutable worker snapshot");
        var changed=triangle();changed.getFaces().setAll(0,0,0,2,0,0,1,0,0);
        var oldDisplay=display;display=CycleMeshBuffers.update(display,changed);
        check(display!=oldDisplay && display.getFaces().get(3)==2,"changed winding retained old indices");
        check(display.getPoints().get(3)==1f,"seeking backwards retained deformed vertices");
        System.out.println("PASS changed vertex/normal ranges reuse topology; immutable snapshots and new topology retained");
    }
    private static TriangleMesh triangle() {
        var mesh=new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
        mesh.getPoints().setAll(0,0,0,1,0,0,0,1,0);mesh.getNormals().setAll(0,0,1);
        mesh.getTexCoords().setAll(0,0);mesh.getFaces().setAll(0,0,0,1,0,0,2,0,0);
        return mesh;
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
