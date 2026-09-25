package com.sergey.pisarev.controller;

import com.sergey.pisarev.model.*;
import com.sergey.pisarev.util.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;

public final class ToolRadiusExpressionRegressionTest {
    public static void main(String[] args)throws Exception {
        var folder=Files.createTempDirectory(Path.of(args[0]),"tool-radius-");
        System.setProperty("chekator.data.dir",folder.toString());
        var tool=new CncToolDefinition(18,"round","turning",18,1,0,0,16,550,"Button",3);
        tool.setModelId("round_cranked");ToolLibraryStore.save(List.of(tool));
        check(ToolLibraryStore.load().get(0).getModelId().equals("round_cranked"),"model persists in machine library");
        Platform.startup(()->{});var done=new CompletableFuture<Void>();
        Platform.runLater(()->{try {verify();done.complete(null);}catch(Throwable e){done.completeExceptionally(e);}});
        try {done.get(30,TimeUnit.SECONDS);}finally{Platform.exit();}
        System.out.println("Tool radius expressions and machine re-entry passed.");
    }
    static void verify()throws Exception {
        String nc="DIAMOF\nT18\nL6\nT99 ; preselection must not supply radius\nG0 X200 Z100\nG1 X180 Z80\n"
                +"G0 X=IC(-$P_TOOLR) Z=IC(5)\nG153 W=N_GANTRYPOS_W\nM30";
        var c=new MainController();var applied=MainController.class.getDeclaredField("appliedProgramText");applied.setAccessible(true);
        var sync=MainController.class.getDeclaredMethod("syncAxisModeForProgram",String.class);sync.setAccessible(true);
        var build=MainController.class.getDeclaredMethod("buildSimulationContext",List.class);build.setAccessible(true);
        applied.set(c,nc);sync.invoke(c,nc);
        var context=(SimulationContext)build.invoke(c,List.of());
        var retract=context.getContourMoves().stream().filter(m->m.sourceLine()==7).findFirst().orElseThrow();
        check(retract.endX()==328 && retract.endZ()==85,"active R16 produces radial retract in DIAMOF");
        nc="DIAMOF\nT18\nG0 X200 Z100\nG1 X180 Z80\nG0 G153 Z=N_GANTRYPOS_Z\nX=225-45-$P_TOOLR\nZ130\nG1 Z120\nM30";
        applied.set(c,nc);sync.invoke(c,nc);context=(SimulationContext)build.invoke(c,List.of());
        check(context.getContourMoves().stream().noneMatch(m->m.sourceLine()>=5&&m.sourceLine()<=7),"no invented connector after unknown machine retract");
        var cut=context.getContourMoves().stream().filter(m->m.sourceLine()==8).findFirst().orElseThrow();
        check(cut.startX()==328&&cut.startZ()==130&&cut.endZ()==120,"re-entry waits for both restored axes");
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
