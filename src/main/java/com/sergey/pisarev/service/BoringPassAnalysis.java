package com.sergey.pisarev.service;

import com.sergey.pisarev.model.*;
import java.util.*;
import java.util.regex.Pattern;

/** Recognizes straight G40 boring passes, independently of tool numbers and part dimensions. */
public final class BoringPassAnalysis {
    private static final double EPS = 1e-6;
    private static final Pattern BORING_HEADING = Pattern.compile(
            "(?iu)^(?:rastochka|расточка|boring|ausdrehen|innendrehen)\\b.*");
    private final Set<Integer> internalLines = new HashSet<>();
    private final Set<Integer> nominalLines = new HashSet<>();
    public record Pass(double diameter, double startZ, double endZ) { }
    private final List<Pass> passes = new ArrayList<>();

    public BoringPassAnalysis(SimulationContext context) {
        if (!AxialStockSection.enabled(context)) return;
        var tools = new LatheCuttingEnvelope(context);
        var hints = boringHeadings(context.getProgramText());
        int boundary = context.getProgramText().indexOf("\n" + MultiChannelSimulation.CHANNEL_BOUNDARY + "\n");
        int offset = boundary < 0 ? Integer.MAX_VALUE : MultiChannelSimulation.secondChannelLineOffset(
                context.getProgramText().substring(0, boundary));
        for (int channel=0; channel<(boundary<0 ? 1 : 2); channel++) {
            var moves = new ArrayList<GCodeMoveData>();
            for (var move : context.getContourMoves())
                if ((move.sourceLine()>offset ? 1 : 0)==channel) moves.add(move);
            for (int i=0; i<moves.size();) {
                var first=moves.get(i);
                if (!axial(first, tools)) { i++; continue; }
                int end=i+1;
                while (end<moves.size() && axial(moves.get(end),tools)
                        && sameTool(first,moves.get(end)) && connected(moves.get(end-1),moves.get(end))
                        && (first.endZ()-first.startZ())*(moves.get(end).endZ()-moves.get(end).startZ())>0) end++;
                var tool=tools.activeToolAt(first.sourceLine());
                // For +X turning, positions 1/2/5 put the nose centre on the
                // inner side of the programmed wall. Other orientations retain
                // their finite swept nose; do not reinterpret outside turning.
                boolean configured=tool!=null && Set.of(1,2,5).contains(tool.getToolPosition());
                // Missing tool data is NOT permission to cut arbitrary stock.
                // A named boring operation AND a radial inward rapid followed
                // by an axial return establish an ideal straight inner contour.
                boolean nominal=tool==null && first.toolNumber()>0 && hints.contains(first.sourceLine())
                        && returnsInside(moves,first,end);
                if (configured || nominal) {
                    passes.add(new Pass(first.startX(),first.startZ(),moves.get(end-1).endZ()));
                    for (int j=i;j<end;j++) {
                        internalLines.add(moves.get(j).sourceLine());
                        if (nominal) nominalLines.add(moves.get(j).sourceLine());
                    }
                }
                i=end;
            }
        }
    }

    public boolean isInternal(GCodeMoveData move) {
        return !move.rapid() && !move.arcSegment() && internalLines.contains(move.sourceLine())
                && Math.abs(move.startX()-move.endX())<EPS && Math.abs(move.endZ()-move.startZ())>EPS;
    }

    public boolean isNominal(GCodeMoveData move) { return isInternal(move) && nominalLines.contains(move.sourceLine()); }
    public boolean hasPasses() { return !internalLines.isEmpty(); }
    public List<Pass> getPasses() { return List.copyOf(passes); }

    private static boolean axial(GCodeMoveData m, LatheCuttingEnvelope tools) {
        return !m.rapid() && !m.arcSegment() && m.startX()>EPS
                && Math.abs(m.endX()-m.startX())<EPS && Math.abs(m.endZ()-m.startZ())>EPS
                && !tools.isCompensated(m.sourceLine());
    }

    private static boolean sameTool(GCodeMoveData a,GCodeMoveData b) {
        return a.toolNumber()==b.toolNumber() && a.edgeNumber()==b.edgeNumber();
    }
    private static boolean connected(GCodeMoveData a,GCodeMoveData b) {
        return Math.hypot(a.endX()-b.startX(),a.endZ()-b.startZ())<EPS;
    }
    private static boolean returnsInside(List<GCodeMoveData> moves,GCodeMoveData first,int end) {
        if (end+1>=moves.size()) return false;
        var last=moves.get(end-1); var retract=moves.get(end); var back=moves.get(end+1);
        return retract.rapid() && back.rapid() && sameTool(first,retract) && sameTool(first,back)
                && connected(last,retract) && connected(retract,back)
                && retract.endX()>0 && retract.endX()<retract.startX()-EPS
                && Math.abs(retract.endZ()-retract.startZ())<EPS
                && Math.abs(back.endX()-back.startX())<EPS
                && (back.endZ()-back.startZ())*(last.endZ()-first.startZ())<0
                && (back.endZ()-first.startZ())*Math.signum(last.endZ()-first.startZ())<=EPS;
    }

    private static Set<Integer> boringHeadings(String program) {
        var result=new HashSet<Integer>();
        var tools=new TreeMap<>(GCodeProgramParser.parseToolNumberByLine(program));
        var edges=new TreeMap<>(LatheCompensationProcessor.parseDNumberByLine(program));
        String[] lines=program.split("\\R",-1);
        boolean boring=false; int previousTool=0,previousEdge=1;
        for (int i=0;i<lines.length;i++) {
            int line=i+1;
            var t=tools.floorEntry(line); var d=edges.floorEntry(line);
            int tool=t==null ? 0 : t.getValue(), edge=d==null ? 1 : d.getValue();
            if (tool!=previousTool || edge!=previousEdge || lines[i].equals(MultiChannelSimulation.CHANNEL_BOUNDARY)) boring=false;
            String text=lines[i].stripLeading();
            if (text.startsWith(";") && BORING_HEADING.matcher(text.substring(1).strip()).matches()) boring=true;
            if (boring) result.add(line);
            previousTool=tool; previousEdge=edge;
        }
        return result;
    }
}
