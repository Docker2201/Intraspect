package com.sergey.pisarev.service;

import com.sergey.pisarev.model.*;
import com.sergey.pisarev.occt.*;
import java.util.*;

public final class AxialSectionRegressionTest {
    public static void main(String[] args) {
        var tool=new CncToolDefinition(1,"test","turning",1,1,0,0,0,510,"Finish",3);
        var pocket=context("T1 D1\nG41",List.of(new GCodeMoveData(80,5,20,5,false,2,false,1)),tool);
        var section=LatheStockRemovalSimulator.buildRevolveProfile(pocket);
        check("hub survives face pocket",AxialStockSection.contains(section,15,5));
        check("rim survives face pocket at same Z",AxialStockSection.contains(section,15,45));
        check("pocket is empty",!AxialStockSection.contains(section,15,25));
        check("disc under pocket survives",AxialStockSection.contains(section,4,25));
        check("display keeps ordered section",StockRemovalMeshBuilder.prepareDisplayProfile(pocket,section,50)==section);
        verifyNative(section,"pocket",new double[][]{{15,5,1},{15,45,1},{15,25,0},{4,25,1}});

        var second = context("T1 D1\nG41", List.of(new GCodeMoveData(70,28,30,28,false,2,false,1)),tool)
                .afterTurnover(pocket,30);
        var flipped = AxialStockSection.initialSection(second);
        check("turnover changes datum without changing stock thickness",second.getWorkpiece().getZMin()==10
                && second.getWorkpiece().getZMax()==30 && second.getWorkpiece().getLengthMm()==20);
        check("first-side pocket survives turnover",!AxialStockSection.contains(flipped,15,25)
                && AxialStockSection.contains(flipped,27,25));
        check("turnover keeps hub and rim",AxialStockSection.contains(flipped,15,5)&&AxialStockSection.contains(flipped,15,45));
        var finished = LatheStockRemovalSimulator.buildRevolveProfile(second);
        check("second setup cuts opposite face",!AxialStockSection.contains(finished,29,25)
                && AxialStockSection.contains(finished,27,25)&&!AxialStockSection.contains(finished,15,25));
        verifyNative(finished,"two setups",new double[][]{{29,25,0},{27,25,1},{15,25,0},{15,5,1},{15,45,1}});
        check("second setup initial mesh includes first-side pocket",StockRemovalMeshBuilder.buildStockRevolvedTriangleMesh(second,false)!=null
                && !inside(StockRemovalMeshBuilder.buildStockRevolvedTriangleMesh(second,false),15,25));
        var shifted = context("",List.of(),tool).afterTurnover(pocket,130);
        check("turnover datum is arbitrary, not a wheel constant",!AxialStockSection.contains(AxialStockSection.initialSection(shifted),115,25)
                && AxialStockSection.contains(AxialStockSection.initialSection(shifted),127,25));

        var separated=context("T1 D1\nG41",List.of(new GCodeMoveData(80,-1,20,-1,false,2,false,1)),tool);
        var two=LatheStockRemovalSimulator.buildRevolveProfile(separated);
        check("disconnected hub and rim both retained",AxialStockSection.contains(two,10,5)
                && AxialStockSection.contains(two,10,45) && !AxialStockSection.contains(two,10,25));
        verifyNative(two,"two solids",new double[][]{{10,5,1},{10,45,1},{10,25,0}});

        var concave=context("T1 D1\nG0 X76 Z25\nG41\nG1",List.of(
                new GCodeMoveData(76,25,80,15,false,3,false,1),
                new GCodeMoveData(80,15,70,10,false,4,false,1),
                new GCodeMoveData(70,10,60,5,false,4,false,1),
                new GCodeMoveData(60,5,40,5,false,4,false,1),
                new GCodeMoveData(40,5,30,10,false,4,false,1)),tool);
        var curved=LatheStockRemovalSimulator.buildRevolveProfile(concave);
        check("compensation entry does not cut a ring through the stock",AxialStockSection.contains(curved,2,39));
        check("concave pass keeps both walls",AxialStockSection.contains(curved,10,10)&&AxialStockSection.contains(curved,10,45));
        check("concave pass removes pocket",!AxialStockSection.contains(curved,10,25));
        verifyNative(curved,"concave pocket",new double[][]{{2,39,1},{10,10,1},{10,45,1},{10,25,0}});

        var partial=context("T1 D1\nG41",List.of(
                new GCodeMoveData(60,25,70,25,false,2,false,1),
                new GCodeMoveData(70,25,70,10,false,2,false,1),
                new GCodeMoveData(70,10,68,8,false,2,false,1),
                new GCodeMoveData(68,8,60,5,false,2,false,1)),tool);
        var partialSection=LatheStockRemovalSimulator.buildRevolveProfile(partial);
        check("partial inner wall does not clear the central disc",AxialStockSection.contains(partialSection,12,20)
                && !AxialStockSection.contains(partialSection,12,33)&&AxialStockSection.contains(partialSection,12,45));
        var outside=context("T1 D1\nG42",List.of(
                new GCodeMoveData(40,24,40,18,false,2,false,1),
                new GCodeMoveData(40,18,70,18,false,2,false,1),
                new GCodeMoveData(70,18,80,8,false,2,false,1),
                new GCodeMoveData(80,8,82,2,false,2,false,1)),tool);
        var outsideSection=LatheStockRemovalSimulator.buildRevolveProfile(outside);
        check("outward taper clears to outside of blank",!AxialStockSection.contains(outsideSection,10,45)
                && AxialStockSection.contains(outsideSection,10,20));
        verifyNumericContact();

        String resolved=LatheCompensationProcessor.resolveOffnExpressions("T1 D1\nOFFN=ALLOWANCE + 0.5 F1\nG41",Map.of("ALLOWANCE",1.5));
        check("named OFFN expression is resolved",LatheCompensationProcessor.scanOffnByLine(resolved).get(2)==2);
        var allowance=context(resolved,List.of(new GCodeMoveData(90,25,80,5,false,3,false,1),
                new GCodeMoveData(80,5,20,5,false,3,false,1)),tool);
        var allowanceSection=LatheStockRemovalSimulator.buildRevolveProfile(allowance);
        check("OFFN leaves material on the free side of the nominal contour",AxialStockSection.contains(allowanceSection,6,25)
                && !AxialStockSection.contains(allowanceSection,8,25));

        String toolsProgram="T11\nL6\nT21\nG1 X20\nD0\nL6\nT11\nMSG(\"PART-A T99 D99\")\nG1 X30\nD2\nG1 X40";
        var activeTools=GCodeProgramParser.parseToolNumberByLine(toolsProgram);
        var activeEdges=LatheCompensationProcessor.parseDNumberByLine(toolsProgram);
        check("T preselection does not replace active tool",activeTools.get(4)==11);
        check("L6 activates preselected tool",activeTools.get(9)==21);
        check("tool change reactivates default edge after D0",activeEdges.get(9)==1);
        check("explicit D2 is retained",activeEdges.get(11)==2);
        check("message text does not select a tool",activeTools.get(8)==21 && activeEdges.get(8)==1);
        var immediate=GCodeProgramParser.parseToolNumberByLine("T1\nG1 X20\nT2\nG1 X30");
        check("T-only programs still change tools immediately",immediate.get(2)==1&&immediate.get(4)==2);

        tool.setRadius(2); tool.setToolPosition(8);
        var bore=context("T1 D1\nG40",List.of(new GCodeMoveData(40,12,42,12,false,2,false,1)),tool);
        var hole=LatheStockRemovalSimulator.buildRevolveProfile(bore);
        check("G40 finite nose does not erase distant rim",AxialStockSection.contains(hole,10,45));
        check("G40 internal sweep leaves a hole",!AxialStockSection.contains(hole,10,20.5));
        verifyNative(hole,"internal loop",new double[][]{{10,20.5,0},{10,45,1}});
    }

    private static SimulationContext context(String program,List<GCodeMoveData> moves,CncToolDefinition tool) {
        var machine=new MachineConfiguration(MachineConfiguration.ControlSystem.SIEMENS_TURNING,
                MachineConfiguration.MachineType.VERTICAL_LATHE,Set.of('X','Z'),100,true,0,false);
        return new SimulationContext(moves,machine,
                new WorkpieceDefinition(WorkpieceDefinition.Shape.CYLINDER,100,20,0,20,1),
                List.of(tool),100,100,program,true);
    }

    @SuppressWarnings("unchecked")
    private static void verifyNumericContact() {
        try {
            var area=new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Double(0,0,50,10));
            area.add(new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Double(0,10+1e-12,50,10)));
            var method=AxialStockSection.class.getDeclaredMethod("toSection",java.awt.geom.Area.class);
            method.setAccessible(true);
            var section=(List<double[]>)method.invoke(null,area);
            check("floating-point contact does not produce internal caps",section.stream().map(p->p[2]).distinct().count()==1);
            var mesh=OcctLatheMesher.buildRevolvedSolid(section);
            check("OCCT contact has no interior horizontal face",!hasHorizontalFace(mesh,10));
            var adjacent=List.of(new double[]{0,10,0},new double[]{0,50,0},new double[]{10,50,0},
                    new double[]{10,10,0},new double[]{0,10,0},new double[]{10,10,1},
                    new double[]{10,50,1},new double[]{20,50,1},new double[]{20,10,1},new double[]{10,10,1});
            var united=OcctLatheMesher.buildRevolvedSolid(adjacent);
            check("OCCT unites adjacent loops before revolving",united!=null&&!hasHorizontalFace(united,10)
                    &&inside(united,10,25)&&!inside(united,10,5));
            var gap=new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Double(0,0,50,10));
            gap.add(new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Double(0,10.005,50,10)));
            var separated=(List<double[]>)method.invoke(null,gap);
            check("real five-micron gap is retained",!AxialStockSection.contains(separated,10.002,25));
            check("OCCT also preserves the real gap",!inside(OcctLatheMesher.buildRevolvedSolid(separated),10.002,25));
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static boolean hasHorizontalFace(javafx.scene.shape.TriangleMesh mesh,double z) {
        var p=mesh.getPoints();var f=mesh.getFaces();int stride=mesh.getVertexFormat().getVertexIndexSize();
        for(int i=0;i<f.size();i+=3*stride) {
            if(Math.abs(p.get(f.get(i)*3+1)-z)<1e-5&&Math.abs(p.get(f.get(i+stride)*3+1)-z)<1e-5
                    &&Math.abs(p.get(f.get(i+2*stride)*3+1)-z)<1e-5)return true;
        }
        return false;
    }

    private static void verifyNative(List<double[]> section,String name,double[][] samples) {
        var mesh=OcctLatheMesher.buildRevolvedSolid(section);
        check("OCCT builds " + name,mesh!=null);
        // Ray along local +X; compare the actual triangulated solid to its section.
        for(var sample:samples) check("native material " + name+" Z="+sample[0]+" R="+sample[1],
                inside(mesh,sample[0],sample[1])==(sample[2]==1));
        var preview=StockRemovalMeshBuilder.buildCyclePreviewTriangleMesh(null,section,0);
        for(var sample:samples) check("live preview material " + name+" Z="+sample[0]+" R="+sample[1],
                inside(preview,sample[0],sample[1])==(sample[2]==1));
    }

    private static boolean inside(javafx.scene.shape.TriangleMesh mesh,double z,double r) {
        var p=mesh.getPoints(); var f=mesh.getFaces(); int stride=mesh.getVertexFormat().getVertexIndexSize();
        double depth=.12347; double x=Math.sqrt(r*r-depth*depth); var hits=new TreeSet<Double>();
        for(int i=0;i<f.size();i+=3*stride) {
            int a=f.get(i)*3,b=f.get(i+stride)*3,c=f.get(i+2*stride)*3;
            double ay=p.get(a+1),az=p.get(a+2),by=p.get(b+1),bz=p.get(b+2),cy=p.get(c+1),cz=p.get(c+2);
            double det=(by-ay)*(cz-az)-(cy-ay)*(bz-az);
            if(Math.abs(det)<1e-9) continue;
            double u=((z-ay)*(cz-az)-(cy-ay)*(depth-az))/det;
            double v=((by-ay)*(depth-az)-(z-ay)*(bz-az))/det;
            if(u<0||v<0||u+v>1) continue;
            double hit=p.get(a)+u*(p.get(b)-p.get(a))+v*(p.get(c)-p.get(a));
            if(hit>x) hits.add(Math.rint(hit*1e6)/1e6);
        }
        return hits.size()%2==1;
    }

    private static void check(String name,boolean ok) {
        if(!ok) throw new AssertionError(name+": "+OcctNative.meshErrorMessage());
        System.out.println("PASS "+name);
    }
}
