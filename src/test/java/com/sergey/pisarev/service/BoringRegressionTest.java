package com.sergey.pisarev.service;

import com.sergey.pisarev.model.*;
import com.sergey.pisarev.occt.OcctLatheMesher;
import java.util.*;

public final class BoringRegressionTest {
    public static void main(String[] args) throws Exception {
        var blank=new WorkpieceDefinition(WorkpieceDefinition.Shape.CYLINDER,100,60,0,60,1)
                .withInitialBoreDiameter(20);
        check("bore survives stock positioning",blank.withoutZAnchor().withZRange(20,80,60).getInitialBoreDiameterMm()==20);
        for (double invalid : new double[]{-1,100,Double.NaN,Double.POSITIVE_INFINITY}) {
            boolean rejected=false;
            try {blank.withInitialBoreDiameter(invalid);} catch(IllegalArgumentException e) {rejected=true;}
            check("invalid blank bore rejected "+invalid,rejected);
        }
        var tool=new CncToolDefinition(9,"boring","turning",9,1,0,0,2,500,"Rough",2);
        var configured=context("T9 D1\nG40\nG1 Z-5",List.of(new GCodeMoveData(40,65,40,-5,false,3,false,9)),List.of(tool),blank);
        var initial=AxialStockSection.initialSection(configured);
        check("raw blank already has its specified through bore",!AxialStockSection.contains(initial,30,5)
                &&AxialStockSection.contains(initial,30,11));
        var finished=AxialStockSection.build(configured,configured.getContourMoves());
        check("G40 internal contact leaves programmed diameter",!AxialStockSection.contains(finished,30,19.99)
                &&AxialStockSection.contains(finished,30,20.01));
        check("boring keeps the rim",AxialStockSection.contains(finished,30,45));
        verifyNative(finished,30,19,false);verifyNative(finished,30,21,true);
        var blindMove=new GCodeMoveData(40,65,40,30,false,3,false,9);
        var blind=AxialStockSection.build(configured,List.of(blindMove));
        check("partial boring never cuts ahead of the tool",AxialStockSection.contains(blind,25,15)
                &&!AxialStockSection.contains(blind,40,15));
        check("blind end retains the rounded nose",AxialStockSection.contains(blind,30.5,19.9)
                &&!AxialStockSection.contains(blind,31.5,19));
        var flipped=context("",List.of(),List.of(),blank).afterTurnover(configured,80);
        check("initial bore and enlarged bore survive turnover",flipped.getWorkpiece().getInitialBoreDiameterMm()==20
                &&!AxialStockSection.contains(AxialStockSection.initialSection(flipped),50,19));

        String code="T73\nL6\nT88\nDIAMOF\n;Rastochka stupicy\nG0 G40 X20 Z65\nG1 Z0\nZ-5\nG0 X17\nZ70";
        var moves=GCodeProgramParser.enrichMovesWithTools(code,List.of(
                new GCodeMoveData(60,70,40,65,true,6,false),new GCodeMoveData(40,65,40,0,false,7,false),
                new GCodeMoveData(40,0,40,-5,false,8,false),new GCodeMoveData(40,-5,34,-5,true,9,false),
                new GCodeMoveData(34,-5,34,70,true,10,false)));
        var nominal=context(code,moves,List.of(),blank);
        var analysis=new BoringPassAnalysis(nominal);
        check("unknown tool uses explicit boring heading and inward return",analysis.isNominal(moves.get(1)));
        check("preselection does not replace boring tool",moves.get(1).toolNumber()==73);
        var ideal=AxialStockSection.build(nominal,moves);
        check("missing nose can show a nominal NC bore",!AxialStockSection.contains(ideal,30,19.99)
                &&AxialStockSection.contains(ideal,30,20.01));
        var prefix=AxialStockSection.build(nominal,List.of(moves.get(0),new GCodeMoveData(40,65,40,40,false,7,false,73)));
        check("recognition uses full program but removal only uses cycle prefix",!AxialStockSection.contains(prefix,50,19)
                &&AxialStockSection.contains(prefix,30,19));
        var noHint=context(code.replace("Rastochka stupicy","Outside turning"),moves,List.of(),blank);
        check("unknown external tool is never assumed to bore",!new BoringPassAnalysis(noHint).hasPasses()
                &&AxialStockSection.contains(AxialStockSection.build(noHint,moves),30,19));
        var noReturn=context(code,moves.subList(0,3),List.of(),blank);
        check("heading alone cannot classify an unknown tool",!new BoringPassAnalysis(noReturn).hasPasses());
        var solid=nominal.withWorkpiece(blank.withInitialBoreDiameter(0));
        check("boring never invents a pre-drilled hole in solid stock",AxialStockSection.contains(AxialStockSection.build(solid,moves),30,5));
        var auto=ProgramBoreResolver.resolve(solid);
        check("automatic mode reconstructs a through bore from NC",auto.getWorkpiece().isBoreFromProgram()
                &&auto.getWorkpiece().getInitialBoreDiameterMm()==40
                &&!AxialStockSection.contains(AxialStockSection.build(auto,moves),30,5));
        check("measured raw bore takes priority over the automatic preform",ProgramBoreResolver.resolve(nominal)
                .getWorkpiece().getInitialBoreDiameterMm()==20);
        check("automatic bore remains identified after repositioning",auto.getWorkpiece().withZRange(20,80,60).isBoreFromProgram());
        var shortPass=List.of(moves.get(0),new GCodeMoveData(40,65,40,20,false,7,false,73),
                new GCodeMoveData(40,20,40,15,false,8,false,73),new GCodeMoveData(40,15,34,15,true,9,false,73),
                new GCodeMoveData(34,15,34,70,true,10,false,73));
        var blindAuto=ProgramBoreResolver.resolve(context(code,shortPass,List.of(),blank.withInitialBoreDiameter(0)));
        check("automatic mode never opens a blind pass through the rear face",blindAuto.getWorkpiece().getInitialBoreDiameterMm()==0);
        check("removing boring moves also removes the automatic hole",ProgramBoreResolver.resolve(
                context("",List.of(),List.of(),auto.getWorkpiece())).getWorkpiece().getInitialBoreDiameterMm()==0);
        var flippedAuto=ProgramBoreResolver.resolve(solid.afterTurnover(context("",List.of(),List.of(),blank.withInitialBoreDiameter(0)),60));
        check("bore detected in setup two is available in the initial stock of setup one",flippedAuto.getPrecedingSetup()
                .getWorkpiece().getInitialBoreDiameterMm()==40
                &&!AxialStockSection.contains(AxialStockSection.initialSection(flippedAuto.getPrecedingSetup()),30,5));
        var firstBore=ProgramBoreResolver.resolve(context("",List.of(),List.of(),blank.withInitialBoreDiameter(0))
                .afterTurnover(solid,160));
        check("first-side boring is checked in the final setup coordinate frame",firstBore.getWorkpiece().getInitialBoreDiameterMm()==40
                &&!AxialStockSection.contains(AxialStockSection.initialSection(firstBore),130,5));
        var oversize=new WorkpieceDefinition(WorkpieceDefinition.Shape.CYLINDER,100,100,-20,80,1);
        var facing=new CncToolDefinition(1,"facing","turning",1,1,0,0,0,510,"Finish",3);
        var firstFace=context("T1 D1\nG41",List.of(new GCodeMoveData(90,10,20,10,false,2,false,1)),List.of(facing),oversize);
        var faceAndBore=context("T1 D1\nG41\nG1\nT73\n;Rastochka\nG40\nG0\nG1\nG0\nG0",List.of(
                new GCodeMoveData(90,70,20,70,false,3,false,1),new GCodeMoveData(20,70,40,75,true,7,false,73),
                new GCodeMoveData(40,75,40,45,false,8,false,73),new GCodeMoveData(40,45,34,45,true,9,false,73),
                new GCodeMoveData(34,45,34,75,true,10,false,73)),List.of(facing),oversize).afterTurnover(firstFace,60);
        var faced=ProgramBoreResolver.resolve(faceAndBore);
        var finishedHub=AxialStockSection.build(faced,faced.getContourMoves());
        check("facing allowance outside both hub ends does not leave a false bore cap",faced.getWorkpiece().getInitialBoreDiameterMm()==40
                &&!AxialStockSection.contains(finishedHub,-10,5)&&!AxialStockSection.contains(finishedHub,60,5)
                &&AxialStockSection.contains(finishedHub,60,21));
        tool.setToolPosition(3);
        var external=AxialStockSection.build(configured,configured.getContourMoves());
        check("external tool keeps finite sweep instead of inner removal",AxialStockSection.contains(external,30,15));

        var other=context("T73 D1\nG40\nG1",List.of(new GCodeMoveData(70,65,70,-5,false,3,false,73)),List.of(),blank);
        var merged=MultiChannelSimulation.merge(nominal,other);
        var combined=AxialStockSection.build(merged,merged.getContourMoves());
        check("boring classification cannot leak into another channel",!AxialStockSection.contains(combined,30,19)
                &&AxialStockSection.contains(combined,30,30));
        verifyNative(combined,30,19,false);verifyNative(combined,30,30,true);
        System.out.println("BORING_REGRESSIONS_PASS");
    }
    private static SimulationContext context(String code,List<GCodeMoveData> moves,List<CncToolDefinition> tools,WorkpieceDefinition wp) {
        return new SimulationContext(moves,new MachineConfiguration(MachineConfiguration.ControlSystem.SIEMENS_TURNING,
                MachineConfiguration.MachineType.VERTICAL_LATHE,Set.of('X','Z'),100,true,0,false),wp,tools,100,100,code,true);
    }
    private static void verifyNative(List<double[]> section,double z,double r,boolean expected) throws Exception {
        var mesh=OcctLatheMesher.buildRevolvedSolid(section);
        check("OCCT builds bored solid",mesh!=null);
        var inside=AxialSectionRegressionTest.class.getDeclaredMethod("inside",javafx.scene.shape.TriangleMesh.class,double.class,double.class);
        inside.setAccessible(true);
        check("native bore Z="+z+" R="+r,(boolean)inside.invoke(null,mesh,z,r)==expected);
    }
    private static void check(String name,boolean ok) {
        if(!ok)throw new AssertionError(name);
        System.out.println("PASS "+name);
    }
}
