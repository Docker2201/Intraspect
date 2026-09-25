package com.sergey.pisarev.service;

import com.sergey.pisarev.model.*;
import java.util.*;

/** Physical bounds and temporal invariants, independent of the ideal final contour. */
public final class AxialCycleStockRegressionTest {
    public static void main(String[] args) {
        var tool=new CncToolDefinition(1,"test","turning",1,1,0,0,2,550,"Round",3);
        var moves=List.of(move(80,25,80,5,2,false),move(80,5,80,0,3,true),move(80,0,80,-5,5,false));
        var context=context("T1 D1 G94 F60 M3 S120\nG1\nG0\nM5\nG1",moves,tool);
        var path=new LatheCyclePath(context,moves,.25);var stock=new AxialCycleStock(context,path);
        var partial=stock.sectionAt(12);
        check("finite nose cuts where it has travelled",!AxialStockSection.contains(partial,14,42));
        check("nose does not cut ahead of its current tip",AxialStockSection.contains(partial,12.9,42));
        check("nose does not erase remote radial material",AxialStockSection.contains(partial,14,50));
        var afterFeed=stock.sectionAt(20);
        var afterStop=stock.sectionAt(Double.POSITIVE_INFINITY);
        for(double z=.31;z<19.8;z+=.73)for(double r=1.27;r<59.5;r+=.79)
            if(AxialStockSection.contains(afterFeed,z,r)!=AxialStockSection.contains(afterStop,z,r))
                throw new AssertionError("Rapid traverse or stopped spindle removed stock");
        check("rapid and M5 feed preserve the material",true);
        check("seeking backwards reconstructs the uncut blank",AxialStockSection.contains(stock.sectionAt(0),14,42));
        boolean[][] wasCut=new boolean[39][119];
        for(double time=0;time<=20;time+=.2) {
            var section=stock.sectionAt(time);
            for(int z=0;z<39;z++)for(int r=0;r<119;r++) {
                boolean cut=!AxialStockSection.contains(section,.271+z*.5,.173+r*.5);
                if(wasCut[z][r]&&!cut)throw new AssertionError("Cut material reappeared at "+time);
                wasCut[z][r]|=cut;
            }
        }
        check("material never regrows while playing forward",true);
        var turn=AxialCycleStock.turnOver(afterFeed,30);
        check("turnover retains the finite cut",!AxialStockSection.contains(turn,16,42));
        check("turnover retains untouched core",AxialStockSection.contains(turn,16,50));
        var compensated=context("T1 D1 G94 F60\nG0\nG41\nG1\nG40 G0",List.of(
                move(100,25,90,25,2,true),move(90,25,80,18,3,false),
                move(80,18,40,18,4,false),move(40,18,40,30,5,true)),tool);
        var cp=new LatheCyclePath(compensated,compensated.getMoves(),.25);
        var strokes=cp.strokes().get(0);
        for(int i=1;i<strokes.size();i++) {
            var before=strokes.get(i-1).centre();var next=strokes.get(i).centre();
            if(Math.hypot(before.endX()-next.startX(),before.endZ()-next.startZ())>1e-8)
                throw new AssertionError("Nose teleports on G41/G40 transition");
        }
        check("G41 activation and G40 cancellation retain a continuous tool centre",true);
        seeking(context,moves);
        finiteCompensatedCut(tool);
        centreReference();
        sectionLoops();
        cutFreeRing(tool);
        System.out.println("Axial cycle stock regressions passed.");
    }

    /** Полосы Area со швами и дыркой на шве сшиваются в одну петлю тела. */
    private static void sectionLoops() {
        var bands=new ArrayList<double[]>();
        loop(bands,0,new double[][]{{0,0},{50,0},{50,10},{0,10}});
        loop(bands,1,new double[][]{{20,6},{20,10},{30,10},{30,6}}); // дно кармана, лежит на шве
        loop(bands,2,new double[][]{{0,10},{20,10},{20,20},{0,20}});
        loop(bands,3,new double[][]{{30,10},{50,10},{50,20},{30,20}});
        var merged=AxialStockSection.mergeSeams(bands);
        check("seam bands become one loop",merged.stream().map(p->p[2]).distinct().count()==1);
        check("pocket open at the top stays empty",!AxialStockSection.contains(merged,8,25)
                && !AxialStockSection.contains(merged,15,25));
        check("walls and floor of the pocket stay solid",AxialStockSection.contains(merged,15,10)
                && AxialStockSection.contains(merged,15,40) && AxialStockSection.contains(merged,3,25));

        var loose=new ArrayList<double[]>();
        loop(loose,0,new double[][]{{0,0},{50,0},{50,20},{0,20}});
        loop(loose,1,new double[][]{{10,5},{10,10},{15,10},{15,5}}); // полость внутри тела
        loop(loose,2,new double[][]{{60,5},{70,5},{70,15},{60,15}}); // кольцо в воздухе
        var body=AxialStockSection.clampedBody(loose);
        check("stock cut free of the clamped body falls away",!AxialStockSection.contains(body,10,65));
        check("clamped body and its cavity are retained",AxialStockSection.contains(body,2,5)
                && !AxialStockSection.contains(body,7,12));
        var standing=new ArrayList<double[]>();
        loop(standing,0,new double[][]{{0,0},{20,0},{20,20},{0,20}});
        loop(standing,1,new double[][]{{30,0},{50,0},{50,20},{30,20}});
        check("separate pieces standing on the table are both retained",
                AxialStockSection.clampedBody(standing).size()==standing.size());
    }

    /** Паз и подрезка отделяют верхнее кольцо: на станке оно падает, в сцене не висит. */
    private static void cutFreeRing(CncToolDefinition tool) {
        var moves=List.of(move(80,25,80,10,2,false),move(80,10,140,10,3,false));
        var context=context("T1 D1 G94 F60 M3 S120\nG1\nG1",moves,tool);
        var path=new LatheCyclePath(context,moves,.25);
        var stock=new AxialCycleStock(context,path);
        double end=path.durationSeconds();
        check("ring is still held while the undercut runs",AxialStockSection.contains(stock.sectionAt(end*.6),17,55));
        var done=stock.sectionAt(Double.POSITIVE_INFINITY);
        check("ring cut free does not hover over the part",!AxialStockSection.contains(done,17,52));
        check("clamped core keeps its full height",AxialStockSection.contains(done,17,20)
                && AxialStockSection.contains(done,5,50));
    }

    /** Петля (r,z) против часовой — тело, по часовой — полость; в формате сечения (z,r,id). */
    private static void loop(List<double[]> section,int id,double[][] rz) {
        for(var p:rz)section.add(new double[]{p[1],p[0],id});
        section.add(new double[]{rz[0][1],rz[0][0],id});
    }

    private static void centreReference() {
        var tool=new CncToolDefinition(1,"centre datum","turning",1,1,0,0,10,550,"Round",9);
        var moves=List.of(move(100,30,100,0,2,false));
        var context=context("T1 D1 G40 G94 F60 M3 S120\nG1",moves,tool);
        var envelope=new LatheCuttingEnvelope(context);
        check("position 9 has no radial or axial tip offset",envelope.noseCenterZOffsetMm(2)==0
                && envelope.noseCenterRadialOffsetMm(2)==0);
        check("centre-programmed G40 leaves diameter minus 2R",envelope.removalRadiusAt(2,100,60)==40);
        check("centre-referenced rapid overlay has no extra R",envelope.toolPathRadiusAt(2,100)==50);
        var path=new LatheCyclePath(context,moves,.25);
        var pose=path.poseAt(0,15);
        check("centre follows the programmed coordinates",pose.centre().startX()==100
                && pose.centre().startZ()==30 && pose.centre().endZ()==0);
        var section=new AxialCycleStock(context,path).sectionAt(30);
        check("centre-referenced insert removes its finite swept area",!AxialStockSection.contains(section,10,45)
                && AxialStockSection.contains(section,10,35));
        check("centre reference can be selected in the tool library",ToolTypeCatalog.findByCode(550).positionVariants()==9);
    }

    /** A compensated contour must never become an infinite cutting blade. */
    private static void finiteCompensatedCut(CncToolDefinition tool) {
        var moves=List.of(move(120,1,80,1,2,true),move(80,1,80,19,3,false),
                move(80,19,120,19,4,true));
        var context=context("T1 D1 G94 F60 M3 S120\nG0\nG41 G1\nG40 G0",moves,tool);
        var path=new LatheCyclePath(context,moves,.25);var stock=new AxialCycleStock(context,path);
        var stroke=path.strokes().get(0).get(1);
        double end=stroke.endSeconds();
        for(double t:new double[]{end*.5,end-1e-5,end,end+1}) {
            var section=stock.sectionAt(t);
            check("remote radial stock survives compensated pass at "+t,AxialStockSection.contains(section,10,55));
            check("uncut core is retained",AxialStockSection.contains(section,10,35));
        }
        var section=stock.sectionAt(end);
        var centre=stroke.centre();
        double r=(Math.abs(centre.startX())+Math.abs(centre.endX()))*.25;
        double z=(centre.startZ()+centre.endZ())*.5;
        check("the cutter actually clears its own travelled centre",!AxialStockSection.contains(section,z,r));
        // Reaching the end of G41 cannot erase the rest of the outside annulus.
        var before=AxialCycleStock.area(new AxialCycleStock(context,path).sectionAt(end-1e-5));
        before.subtract(AxialCycleStock.area(section));
        check("pass completion cannot delete a distant wall",before.getBounds2D().getWidth()<5);
    }

    private static void seeking(SimulationContext context,List<GCodeMoveData> moves) {
        var path=new LatheCyclePath(context,moves,.25);
        var seeking=new AxialCycleStock(context,path,8);
        seeking.sectionAt(Double.POSITIVE_INFINITY); // Проигрываем до конца: снимки набраны.
        for(double time:new double[]{18,4,11,.5,20,7}) {
            var straight=new AxialCycleStock(context,path,8).sectionAt(time);
            var rewound=seeking.sectionAt(time);
            if(rewound.size()!=straight.size())
                throw new AssertionError("Rewind to "+time+" changed the section: "
                        +rewound.size()+" points against "+straight.size());
            for(int i=0;i<straight.size();i++)
                for(int k=0;k<3;k++)if(Math.abs(rewound.get(i)[k]-straight.get(i)[k])>1e-9)
                    throw new AssertionError("Rewind to "+time+" moved the contour");
        }
        check("rewinding from a keyframe reproduces the section exactly",true);
    }
    private static GCodeMoveData move(double x,double z,double endX,double endZ,int line,boolean rapid) {
        return new GCodeMoveData(x,z,endX,endZ,rapid,line,false,1);
    }
    private static SimulationContext context(String nc,List<GCodeMoveData> moves,CncToolDefinition tool) {
        var machine=new MachineConfiguration(MachineConfiguration.ControlSystem.SIEMENS_TURNING,
                MachineConfiguration.MachineType.VERTICAL_LATHE,Set.of('X','Z'),0,false);
        return new SimulationContext(moves,machine,new WorkpieceDefinition(WorkpieceDefinition.Shape.CYLINDER,
                120,20,0,20,0),List.of(tool),100,100,nc,true);
    }
    private static void check(String label,boolean ok) {if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);}
}
