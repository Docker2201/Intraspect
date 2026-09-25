package com.sergey.pisarev.controller;

import com.sergey.pisarev.model.*;
import com.sergey.pisarev.service.*;
import javafx.application.Platform;
import javafx.geometry.Point3D;
import javafx.scene.*;
import javafx.scene.shape.*;
import java.awt.geom.Line2D;
import java.util.*;

/** Tests actual exported vertices against independent stock sections, rather
 * than just checking the reference point used to position the whole model. */
public final class MeasuredToolContactRegressionTest {
    public static void main(String[] args) {
        Platform.startup(()->{});
        try { for(double radius:new double[]{6,10,16,20})check(radius); }
        finally { Platform.exit(); }
        System.out.println("Measured tool contact regressions passed.");
    }

    private static void check(double radius) {
        var tool=new CncToolDefinition(1,"round","turning",1,1,0,0,radius,550,"Button",3);
        var model=VerticalToolModel3dFactory.create(tool,true);
        var assembly=(Group)model.getChildren().get(0);
        var insert=(Group)assembly.getChildren().get(0);
        var vertices=new ArrayList<Point3D>();collect(insert,model,vertices);
        int edgePoints=0;
        for(var p:vertices)if(Math.abs(p.getZ())<1e-6) {
            double distance=Math.hypot(p.getX(),p.getY());
            if(Math.abs(distance-radius)>.0001)throw new AssertionError("Visible rim differs from library R: "+distance+" vs "+radius);
            edgePoints++;
        }
        if(edgePoints<256)throw new AssertionError("Missing calibrated cutting rim");
        var holder=(Group)assembly.getChildren().get(1);
        double length=holder.getBoundsInParent().getHeight();
        if(Math.abs(length-80)>1e-4)throw new AssertionError("Changing insert R stretched the measured holder");

        var moves=List.of(new GCodeMoveData(100,80,80,80,true,2,false,1),
                new GCodeMoveData(80,80,80,0,false,3,false,1));
        var machine=new MachineConfiguration(MachineConfiguration.ControlSystem.SIEMENS_TURNING,
                MachineConfiguration.MachineType.VERTICAL_LATHE,Set.of('X','Z'),0,false);
        var context=new SimulationContext(moves,machine,new WorkpieceDefinition(WorkpieceDefinition.Shape.CYLINDER,
                100,40,0,40,0),List.of(tool),100,100,"T1 D1 G94 F60 G97 S120 M3\nG0\nG1",true);
        var path=new LatheCyclePath(context,moves,.25);var stock=new AxialCycleStock(context,path);
        vertices.clear();collect(model,model,vertices);
        double worst=0;
        for(double time:new double[]{45,60,77}) {
            var pose=path.poseAt(0,time);var c=pose.centre();double t=pose.fraction();
            double r=(c.startX()*(1-t)+c.endX()*t)*.5,z=c.startZ()*(1-t)+c.endZ()*t;
            var section=stock.sectionAt(time);
            for(var p:vertices) {
                double rr=Math.hypot(r+p.getX(),p.getZ()),zz=z+p.getY();
                if(!AxialStockSection.contains(section,zz,rr))continue;
                double depth=Double.POSITIVE_INFINITY;
                for(int i=1;i<section.size();i++) {
                    var a=section.get(i-1);var b=section.get(i);
                    if(a[2]==b[2])depth=Math.min(depth,Line2D.ptSegDist(a[1],a[0],b[1],b[0],rr,zz));
                }
                worst=Math.max(worst,depth);
                if(depth>.01)throw new AssertionError("Visible tool penetrates stock by "+depth+" mm at R="+radius+", t="+time);
            }
        }
        System.out.println("PASS measured rim R="+radius+", whole-tool penetration <= "+worst+" mm");
    }

    private static void collect(Node node,Node root,List<Point3D> out) {
        if(node instanceof MeshView view && view.getMesh() instanceof TriangleMesh mesh) {
            var p=mesh.getPoints();
            for(int i=0;i<p.size();i+=3) {
                var v=new Point3D(p.get(i),p.get(i+1),p.get(i+2));
                for(Node n=node;n!=root;n=n.getParent())v=n.localToParent(v);
                out.add(v);
            }
        }
        if(node instanceof Parent parent)for(var child:parent.getChildrenUnmodifiable())collect(child,root,out);
    }
}
