package com.sergey.pisarev.controller;

import com.sergey.pisarev.model.CncToolDefinition;
import javafx.application.Platform;
import javafx.geometry.Point3D;
import javafx.scene.*;
import javafx.scene.shape.*;
import java.util.*;

/** A straight shank's corners can all miss an overhang while its face cuts
 * through it. Test triangle interiors against an independent flange section. */
public final class CrankedToolRegressionTest {
    public static void main(String[] args) {
        Platform.startup(()->{});
        try {
            for (double r:new double[]{10,16}) {
                var tool=new CncToolDefinition(17,"arbitrary T","turning",901,2,0,0,r,550,"Button",3);
                tool.setModelId("round_straight");
                var straight=VerticalToolModel3dFactory.create(tool,true);
                tool.setModelId("round_cranked");
                var cranked=VerticalToolModel3dFactory.create(tool,true);
                check("straight holder intersects overhanging flange",hits(straight)>100);
                check("cranked holder clears same flange",hits(cranked)==0);
                check("cutting centre fixed",Point3D.ZERO.equals(cranked.getProperties().get("cutting-centre")));
                check("library model chosen independently of T",cranked.getProperties().get("tool-asset").toString().contains("cranked"));
                var assembly=(Group)cranked.getChildren().get(0);
                var insert=(Group)assembly.getChildren().get(0);
                var points=new ArrayList<Point3D>();sample(insert,cranked,points,2);
                check("rim reaches specified R",points.stream().anyMatch(p->Math.abs(p.getZ())<1e-5 && Math.abs(p.getX()-r)<1e-4));
                for(var p:points) if(Math.abs(p.getZ())<1e-5)
                    check("front face stays inside cutting circle",Math.hypot(p.getX(),p.getY())<=r+.0001);
                System.out.println("PASS face-interior clearance and exact cutting radius R="+r);
            }
        } finally {Platform.exit();}
    }
    private static int hits(Group model) {
        var points=new ArrayList<Point3D>();sample(model,model,points,2);
        int hits=0;
        for(var p:points) {
            double r=Math.hypot(110+p.getX(),p.getZ());
            if(r>80 && r<120 && p.getY()>30 && p.getY()<55)hits++;
        }
        return hits;
    }
    static void sample(Node node,Node root,List<Point3D> out,double spacing) {
        if(node instanceof MeshView view && view.getMesh() instanceof TriangleMesh mesh) {
            var points=mesh.getPoints();var faces=mesh.getFaces();int stride=mesh.getFaceElementSize()/3;
            for(int i=0;i<faces.size();i+=stride*3) {
                Point3D[] p=new Point3D[3];
                for(int k=0;k<3;k++) {
                    int index=faces.get(i+k*stride)*3;p[k]=new Point3D(points.get(index),points.get(index+1),points.get(index+2));
                    for(Node n=node;n!=root;n=n.getParent())p[k]=n.localToParent(p[k]);
                }
                int count=Math.max(1,(int)Math.ceil(Math.max(p[0].distance(p[1]),Math.max(p[1].distance(p[2]),p[2].distance(p[0])))/spacing));
                for(int a=0;a<=count;a++)for(int b=0;b<=count-a;b++)
                    out.add(p[0].multiply(1-(a+b)/(double)count).add(p[1].multiply(a/(double)count)).add(p[2].multiply(b/(double)count)));
            }
        }
        if(node instanceof Parent parent)for(var child:parent.getChildrenUnmodifiable())sample(child,root,out,spacing);
    }
    private static void check(String message,boolean condition){if(!condition)throw new AssertionError(message);}
}
