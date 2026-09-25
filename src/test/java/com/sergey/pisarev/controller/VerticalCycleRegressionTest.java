package com.sergey.pisarev.controller;

import com.sergey.pisarev.model.*;
import com.sergey.pisarev.service.*;
import javafx.scene.shape.*;
import java.util.*;

public final class VerticalCycleRegressionTest {
    public static void main(String[] args) {
        javafx.application.Platform.startup(()->{});
        try { run(); } finally { javafx.application.Platform.exit(); }
    }
    private static void run() {
        var leftTool = tool(1,6,550,3); var rightTool=tool(2,.2,510,4);
        var left=context("T1 D1\nG94 F=60 S120 M3",List.of(move(100,0,80,0,2,1,false)),leftTool,rightTool);
        var right=context("T2 D1\nG94 F=120",List.of(move(100,0,60,0,2,2,false)),leftTool,rightTool);
        var merged=MultiChannelSimulation.merge(left,right);
        var path=new LatheCyclePath(merged,merged.getContourMoves(),.25);
        check("two physical supports",path.channelCount()==2);
        check("a selected LH channel keeps its station",MultiChannelSimulation.supportNumber(";Channel : 2 (left)\nG1",0)==2);
        near("parallel channels take max time, not sum",path.steps().get(path.steps().size()-1).endSeconds(),10);
        double[] lastZ={0,0},lastX={100,100}; int switches=0,last=-1;
        for(var step:path.steps()) {
            int c=step.channel(); var m=step.contour();
            near("channel retains its own path start",m.startX(),lastX[c]);
            near("channel retains its own axial start",m.startZ(),lastZ[c]);
            if(last>=0&&last!=c)switches++;last=c;lastX[c]=m.endX();lastZ[c]=m.endZ();
            near("tool centre keeps actual R in G40",step.centre().endX(),m.endX()+(c==0?12:.4));
            near("tool centre keeps edge orientation in G40",step.centre().endZ(),c==0?6:-.2);
        }
        check("supports interleave small moves instead of entire passes",switches>30);
        near("left finishes its own command",lastX[0],80);near("right finishes its own command",lastX[1],60);
        int mid=path.steps().size()/2;
        check("each support has a different current tool",path.steps().get(path.indexAt(0,mid)).contour().toolNumber()==1
                &&path.steps().get(path.indexAt(1,mid)).contour().toolNumber()==2);
        for(int c=0;c<2;c++) {
            var a=path.poseAt(c,.011);var b=path.poseAt(c,.021);
            check("both supports interpolate between render steps",b.fraction()>a.fraction());
            var prefix=path.prefixAt(.021,false);
            check("partial stock retains original NC blocks",prefix.size()==2);
            near("stock and tool use the same channel time",prefix.get(c).endX(),
                    b.contour().startX()+(b.contour().endX()-b.contour().startX())*b.fraction());
        }
        var rapid=context("G94 F60",List.of(move(100,0,80,0,1,1,true)),leftTool);
        near("rapid traverse is faster than feed",SinumerikCycleTimeEstimator.motionClock(rapid,rapid.getMoves()).get(0).seconds(),.05);
        var crossing=context("G94 F60",List.of(move(20,0,-20,0,1,1,false)),leftTool);
        near("crossing spindle axis retains signed X travel",SinumerikCycleTimeEstimator.motionClock(crossing,crossing.getMoves()).get(0).seconds(),20);
        var css=context("G96 F=0.5 S100 LIMS=200 M4\nG1\nM5\nG0",
                List.of(move(200,0,200,10,2,1,false),move(200,10,200,20,4,1,true)),leftTool);
        var clock=SinumerikCycleTimeEstimator.motionClock(css,css.getMoves());
        near("CSS calculates RPM from actual diameter",clock.get(0).signedRpm(),-100000/(Math.PI*200));
        near("G95 uses feed per revolution",clock.get(0).seconds(),10*60/(.5*100000/(Math.PI*200)));
        near("M5 stops the table",clock.get(1).signedRpm(),0);
        var dwell=context("G97 S140 M4 F.8\nG4 S1\nG1\nG4 F2\nG1",
                List.of(move(200,0,200,10,3,1,false),move(200,10,200,20,5,1,false)),leftTool);
        for(var timing:SinumerikCycleTimeEstimator.motionClock(dwell,dwell.getMoves())) {
            near("G4 S does not replace spindle speed",timing.signedRpm(),-140);
            near("G4 F does not replace modal feed",timing.seconds(),10*60/(.8*140));
        }
        String resolved=SinumerikCycleTimeEstimator.resolveMotionExpressions("DEF REAL V_CUT=100\nG96 F=FEED S=V_CUT LIMS=45",Map.of("FEED",.8));
        check("NC F/S parameters are evaluated",resolved.contains("F=0.8 S=100.0 LIMS=45.0"));
        var limit=context("G96 F1 S100 LIMS=45 M3",List.of(move(100,0,100,10,1,1,false)),leftTool);
        near("LIMS bounds RPM",SinumerikCycleTimeEstimator.motionClock(limit,limit.getMoves()).get(0).signedRpm(),45);
        var model=VerticalToolModel3dFactory.create(leftTool,true);
        check("cycle uses the calibrated Blender asset", "calibrated_button_insert.obj".equals(model.getProperties().get("tool-asset")));
        check("only the existing asset is mounted",model.getChildren().size()==1
                &&model.getChildren().get(0) instanceof javafx.scene.Group);
        var asset=(javafx.scene.Group)model.getChildren().get(0);
        var insert=(javafx.scene.Group)asset.getChildren().get(0);
        for(var component:asset.getChildren())for(var child:((javafx.scene.Group)component).getChildren())
            check("only exported Blender mesh components",child instanceof MeshView);
        var mesh=(TriangleMesh)((MeshView)insert.getChildren().get(0)).getMesh();
        check("measured round insert loaded",Boolean.TRUE.equals(insert.getProperties().get("cutting-insert")));
        near("nose radius remains library data",(double)model.getProperties().get("nose-radius-mm"),6);
        var front=(javafx.geometry.Point3D)asset.getProperties().get("front-tip");
        var placed=asset.localToParent(front);
        near("nose is centred in the radial plane",placed.getX(),0);
        near("nose is seated on the physical cutting circle",placed.getY(),-6);
        near("nose stays on the section plane",placed.getZ(),0);
        near("nothing hangs below the cutting nose",model.getBoundsInLocal().getMinY(),-6);
        for(double[] offset:new double[][]{{6,0},{0,6},{3,4},{-5,2}}) {
            var contour=move(100,40,100,40,1,1,false);
            var centre=move(100+2*offset[0],40+offset[1],100+2*offset[0],40+offset[1],1,1,false);
            VerticalToolModel3dFactory.alignContact(model,new LatheCyclePath.Pose(contour,centre,.5,true));
            var point=asset.localToParent(front);
            near("visible working point is on the swept cutting circle",Math.hypot(point.getX(),point.getY()),6);
            near("contact keeps its radial cutting plane",point.getZ(),0);
            near("holder cannot slide around the cutting circle",point.getX(),0);
            near("insert remains rigidly mounted",point.getY(),-6);
        }
        var another=ToolAssetModelLoader.load("calibrated_button_insert.obj",550);
        check("tool changes reuse immutable mesh data",mesh==((MeshView)another.getChildren().get(0)).getMesh());
        for(int code:new int[]{500,510,520,530,540,550}) {
            var mounted=VerticalToolModel3dFactory.create(tool(3,.2,code,3),true);
            check("existing model retained for type "+code,mounted.getChildren().size()==1);
            var part=(javafx.scene.Group)mounted.getChildren().get(0);
            var anchor=part.localToParent((javafx.geometry.Point3D)part.getProperties().get("front-tip"));
            near("all working tips use the cutting plane",anchor.getX(),0);
            near("all working tips use the library radius",anchor.getY(),-.2);
            near("all working tips are centred in depth",anchor.getZ(),0);
        }
        try {
            var main=new MainController();var field=MainController.class.getDeclaredField("toolsObservableList");
            field.setAccessible(true);field.set(main,javafx.collections.FXCollections.observableArrayList(rightTool));
            var machineField=MainController.class.getDeclaredField("toolsMachine");machineField.setAccessible(true);
            machineField.set(main,com.sergey.pisarev.util.ToolLibraryStore.savedMachineType());
            var method=MainController.class.getDeclaredMethod("simulationToolLibrary",String.class);method.setAccessible(true);
            var actual=(java.util.List<?>)method.invoke(main,";T2 - chernovoj D=200 mm");
            check("NC comments cannot change selected library tool",actual.get(0)==rightTool && rightTool.getRadius()==.2);
        } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
        var entryTool=tool(1,2,550,3);
        var entry=context("T1 D1\nG0\nG41\nG1",List.of(move(120,25,110,25,2,1,true),
                move(110,25,80,15,3,1,false),move(80,15,30,15,4,1,false)),entryTool);
        var ep=new LatheCyclePath(entry,entry.getMoves(),.25);
        // Halfway into activation: material far below this nose must still exist.
        double enterTime=SinumerikCycleTimeEstimator.motionClock(entry,entry.getMoves()).get(0).seconds()
                +SinumerikCycleTimeEstimator.motionClock(entry,entry.getMoves()).get(1).seconds()*.6;
        var partial=new AxialCycleStock(entry,ep).sectionAt(enterTime);
        check("compensation entry cannot erase a distant axial wall",AxialStockSection.contains(partial,5,49));
        var noInitialRapid=context("T1 D1 G94 F60\nG41",List.of(move(110,25,80,15,2,1,false)),entryTool);
        var firstPose=new LatheCyclePath(noInitialRapid,noInitialRapid.getMoves(),.25).poseAt(0,0);
        near("omitted initial rapid still locates the nose radially",firstPose.centre().startX(),114);
        near("omitted initial rapid still locates the nose axially",firstPose.centre().startZ(),27);
        var missing=VerticalToolModel3dFactory.create(null,true);
        check("unknown tool is not replaced by invented geometry",missing.getChildren().isEmpty() && Boolean.TRUE.equals(missing.getProperties().get("missing-tool-model")));
        System.out.println("Vertical cycle regressions passed.");
    }
    static CncToolDefinition tool(int t,double r,int code,int p){return new CncToolDefinition(t,"test","turning",t,1,0,0,r,code,"test",p);}
    static GCodeMoveData move(double x,double z,double x2,double z2,int line,int t,boolean rapid){return new GCodeMoveData(x,z,x2,z2,rapid,line,false,t);}
    static SimulationContext context(String program,List<GCodeMoveData> moves,CncToolDefinition... tools){
        var machine=new MachineConfiguration(MachineConfiguration.ControlSystem.SIEMENS_TURNING,
                MachineConfiguration.MachineType.VERTICAL_LATHE,Set.of('X','Z'),0,false);
        return new SimulationContext(moves,machine,new WorkpieceDefinition(WorkpieceDefinition.Shape.CYLINDER,120,20,0,20,0),
                List.of(tools),100,100,program,true);
    }
    static void near(String label,double actual,double expected){if(Math.abs(actual-expected)>1e-7)throw new AssertionError(label+": "+actual+" != "+expected);}
    static void check(String label,boolean ok){if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);}
}
