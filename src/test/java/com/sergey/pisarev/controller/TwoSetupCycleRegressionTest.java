package com.sergey.pisarev.controller;

import com.sergey.pisarev.model.*;
import com.sergey.pisarev.service.*;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Exercises the real FXML controller, native meshes and animation across both setups. */
public final class TwoSetupCycleRegressionTest {
    private static Simulation3dController controller;
    private static SimulationContext complete;
    public static void main(String[] args) throws Exception {
        Path folder=Files.createTempDirectory(Path.of(args[0]),"two-setup-");
        System.setProperty("user.home",folder.resolve("home").toString());
        System.setProperty("chekator.data.dir",folder.resolve("data").toString());
        Platform.startup(() -> {});
        int code=0;
        try { run(); } catch(Throwable error) {error.printStackTrace();code=1;}
        finally { fx(() -> {if(controller!=null)controller.stopPlayback();return null;});Platform.exit(); }
        System.exit(code);
    }

    private static void run() throws Exception {
        var tool=new CncToolDefinition(1,"test","turning",1,1,0,0,2,510,"Finish",3);
        var first=context(new GCodeMoveData(80,5,20,5,false,2,false,1),tool);
        complete=context(new GCodeMoveData(70,28,30,28,false,2,false,1),tool).afterTurnover(first,30);
        var ready=new CompletableFuture<Void>();
        fx(() -> {
            var loader=new javafx.fxml.FXMLLoader(Simulation3dController.class.getResource("/simulation3d.fxml"),
                    com.sergey.pisarev.util.I18n.bundle());
            javafx.scene.Parent root=loader.load();controller=loader.getController();
            var scene=new javafx.scene.Scene(root,1320,820);
            scene.getStylesheets().add(Simulation3dController.class.getResource("/app.css").toExternalForm());
            root.applyCss();root.layout();
            controller.setData(complete,true,line->{},()->ready.complete(null));return null;
        });
        ready.get(30,TimeUnit.SECONDS);
        check("static result includes both actual pockets",!inside(15,25)&&!inside(29,25)&&inside(27,25));
        fx(() -> {call("onViewCycle");return null;});
        waitFor(() -> !bool("cycleResetLoadingPending")&&!bool("cycleMeshBuildRunning"));
        check("cycle controls remain inside the window",fx(()->{
            var controls=(javafx.scene.layout.HBox)get("cycleControls");
            var root=controls.getScene().getRoot();root.applyCss();root.requestLayout();root.layout();
            return controls.localToScene(controls.getBoundsInLocal()).getMaxX()<=controls.getScene().getWidth();
        }));
        check("cycle starts at first setup on raw stock",fx(()->controller.displayedSetupSide()==1)&&inside(15,25));
        check("restart retains the vertical path cache and support model",fx(()->{
            var tools=(Group)get("cycleTool3d");
            return get("verticalCyclePath")!=null && tools.getChildren().size()==1
                    &&Boolean.TRUE.equals(tools.getProperties().get("vertical-supports"));
        }));
        check("tool cannot outrun the displayed material snapshot",fx(()->{
            var tools=(Group)get("cycleTool3d");var support=tools.getChildren().get(0);
            double x=support.getTranslateX(),z=support.getTranslateY();
            double requested=(double)get("cycleDistanceProgress");
            set("cycleDistanceProgress",requested+38);
            var moves=(java.util.List<?>)call("cycleMoves");
            call("updateVerticalCycleTools",new Class[]{java.util.List.class,int.class},moves,0);
            set("cycleDistanceProgress",requested);
            double shown=(int)get("renderedCycleMeshFrame")*.25/38;
            return support.getTranslateX()==x && support.getTranslateY()==z
                    &&Math.abs((double)support.getProperties().get("presented-seconds")-shown)<1e-10;
        }));
        check("visual tool uses the solver's T/D snapshot, not disk settings",fx(()->{
            var selected=(CncToolDefinition)call("findToolDefinition",new Class[]{int.class,int.class},1,1);
            var missing=call("findToolDefinition",new Class[]{int.class,int.class},1,2);
            return selected.getRadius()==2 && missing==null;
        }));
        fx(() -> {
            double total=(double)get("cycleTotalDistance");
            set("cycleDistanceProgress",total-.001);
            set("cycleProgress",call("cycleProgressFromDistance",new Class[]{double.class},total-.001));
            call("toggleCyclePlayback");return null;
        });
        waitFor(() -> (double)get("turnoverProgress")>.15);
        fx(() -> {call("toggleCyclePlayback");return null;});
        double paused=fx(()->(double)get("turnoverProgress"));
        fx(() -> {call("advanceTurnover",new Class[]{double.class},1.0);return null;});
        check("pause freezes the physical turnover",fx(()->!bool("cyclePlaying")&&(double)get("turnoverProgress")==paused));
        check("wheel visibly rotates about a transverse axis",fx(()->{
            var rotation=(javafx.scene.transform.Rotate)get("turnoverRotation");
            return rotation.getAngle()>0&&rotation.getAngle()<180&&rotation.getAxis().equals(javafx.scene.transform.Rotate.X_AXIS);
        }));
        fx(()->{call("toggleCyclePlayback");return null;});
        waitFor(()->controller.displayedSetupSide()==2);
        fx(()->{call("toggleCyclePlayback");return null;});
        check("second setup retains the finite nose cut after turning",!inside(23,25)&&inside(15,25)&&inside(27,25));
        fx(()->{call("seekCycleSeconds",new Class[]{double.class},1000.0);return null;});
        waitFor(()->!bool("cycleMeshBuildRunning")&&(int)get("renderedCycleMeshFrame")==
                (int)call("cycleMeshFrameForProgress"));
        check("cycle end keeps the first-side cut",!inside(23,25));
        check("cycle end cuts the second side",!inside(29,25));
        check("cycle end preserves material outside the swept nose",inside(15,25));
        fx(()->{call("toggleCyclePlayback");call("toggleCyclePlayback");return null;});
        waitFor(()->!bool("cycleResetLoadingPending")&&!bool("cycleMeshBuildRunning"));
        check("replay starts the complete sequence on side one",fx(()->controller.displayedSetupSide()==1));
        fx(()->{call("restartCycleFromToolbar");return null;});
        waitFor(()->!bool("cycleResetLoadingPending")&&!bool("cycleMeshBuildRunning"));
        check("restart returns to raw material",inside(15,25));
        fx(()->{call("onView3d");return null;});
        waitFor(()->controller.displayedSetupSide()==2&&get("runningMeshTask")!=null
                &&!((javafx.concurrent.Task<?>)get("runningMeshTask")).isRunning()
                && ((java.util.List<?>)get("cachedFinishedProfile")).size()>2);
        check("3D restores the complete result from a paused first-side cycle",!inside(15,25)&&!inside(29,25)&&inside(27,25));
        fx(()->{call("onViewCycle");call("seekCycleSeconds",new Class[]{double.class},1000.0);call("onView3d");return null;});
        waitFor(()->controller.displayedSetupSide()==2&&!(boolean)get("turnoverPreparing")
                &&get("runningMeshTask")!=null&&!((javafx.concurrent.Task<?>)get("runningMeshTask")).isRunning());
        check("leaving during turnover preparation cancels the old sequence",fx(()->(double)get("turnoverProgress")<0&&!bool("cyclePlaying")));
        fx(()->{controller.syncContext(first,true);return null;});
        check("single-setup progress updates the context used by rebuild",fx(()->get("completeSimulationContext")==first));
        System.out.println("Two-setup cycle regressions passed.");
    }

    private static SimulationContext context(GCodeMoveData move,CncToolDefinition tool) {
        var machine=new MachineConfiguration(MachineConfiguration.ControlSystem.SIEMENS_TURNING,
                MachineConfiguration.MachineType.VERTICAL_LATHE,Set.of('X','Z'),0,false);
        return new SimulationContext(List.of(move),machine,new WorkpieceDefinition(WorkpieceDefinition.Shape.CYLINDER,
                100,20,0,20,0),List.of(tool),100,100,"T1 D1\nG41",true);
    }
    private static boolean inside(double z,double r) throws Exception {
        return fx(()->{
            var model=(Group)get("modelRoot");
            var mesh=new TriangleMesh();mesh.getTexCoords().setAll(0,0);
            for(var node:model.getChildren())if("finished-part".equals(node.getUserData()))appendGeometry(node,mesh);
            var method=AxialSectionRegressionTest.class.getDeclaredMethod("inside",TriangleMesh.class,double.class,double.class);
            method.setAccessible(true);return (boolean)method.invoke(null,mesh,z,r);
        });
    }
    private static void appendGeometry(javafx.scene.Node node,TriangleMesh target) {
        if(node instanceof Group group)for(var child:group.getChildren())appendGeometry(child,target);
        else if(node instanceof MeshView view && view.getMesh() instanceof TriangleMesh mesh) {
            int base=target.getPoints().size()/3,vertexStride=mesh.getFaceElementSize()/3;
            target.getPoints().addAll(mesh.getPoints());
            for(int i=0;i<mesh.getFaces().size();i+=vertexStride)
                target.getFaces().addAll(base+mesh.getFaces().get(i),0);
        }
    }
    private static Object get(String name)throws Exception {var f=Simulation3dController.class.getDeclaredField(name);f.setAccessible(true);return f.get(controller);}
    private static void set(String name,Object value)throws Exception {var f=Simulation3dController.class.getDeclaredField(name);f.setAccessible(true);f.set(controller,value);}
    private static boolean bool(String name)throws Exception{return (boolean)get(name);}
    private static Object call(String name)throws Exception{return call(name,new Class[0]);}
    private static Object call(String name,Class<?>[] types,Object... args)throws Exception {var m=Simulation3dController.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(controller,args);}
    private static <T>T fx(Callable<T> action)throws Exception {var future=new CompletableFuture<T>();Platform.runLater(()->{try{future.complete(action.call());}catch(Throwable t){future.completeExceptionally(t);}});return future.get(30,TimeUnit.SECONDS);}
    private static void waitFor(Callable<Boolean> condition)throws Exception {long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while(!fx(condition)){if(System.nanoTime()>deadline)throw new AssertionError("Timed out awaiting cycle state");Thread.sleep(30);}}
    private static void check(String text,boolean ok){if(!ok)throw new AssertionError(text);System.out.println("PASS "+text);}
}
