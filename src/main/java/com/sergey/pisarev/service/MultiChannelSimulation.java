package com.sergey.pisarev.service;

import com.sergey.pisarev.model.*;
import java.util.ArrayList;
import java.util.List;

/** Independent NC states, one stock. No artificial move joins the channels. */
public final class MultiChannelSimulation {
    public static final String CHANNEL_BOUNDARY = "G40 T0 D1 ; INTRASPECT_CHANNEL_1";
    private MultiChannelSimulation() { }

    /** First text is the LH support (channel 2), second text the RH support (channel 1). */
    public static int channelBoundaryLine(String program) {
        String marker = "\n" + CHANNEL_BOUNDARY + "\n";
        int index = program == null ? -1 : program.indexOf(marker);
        return index < 0 ? Integer.MAX_VALUE : secondChannelLineOffset(program.substring(0, index));
    }

    /** Machine program headers identify a lone LH channel too; no station is inferred from feed direction. */
    public static int supportNumber(String program, int channelIndex) {
        if (channelBoundaryLine(program) != Integer.MAX_VALUE) return channelIndex == 0 ? 2 : 1;
        var header = java.util.regex.Pattern.compile("(?im)^\\s*;\\s*Channel\\s*:\\s*([12])\\b")
                .matcher(program == null ? "" : program);
        return header.find() ? Integer.parseInt(header.group(1)) : 1;
    }

    public static int secondChannelLineOffset(String firstProgram) {
        return firstProgram.split("\\R", -1).length + 1;
    }

    public static SimulationContext merge(SimulationContext first, SimulationContext second) {
        int offset = secondChannelLineOffset(first.getProgramText());
        // Unique source lines for T/D/compensation lookup; reset between channels.
        String program = first.getProgramText() + "\n" + CHANNEL_BOUNDARY + "\n" + second.getProgramText();
        WorkpieceDefinition a = first.getWorkpiece(), b = second.getWorkpiece();
        WorkpieceDefinition stock;
        if (first.isWorkpieceFromProgram() && !second.isWorkpieceFromProgram()) stock = a;
        else if (second.isWorkpieceFromProgram() && !first.isWorkpieceFromProgram()) stock = b;
        else stock = union(a, b);
        return new SimulationContext(join(first.getMoves(), second.getMoves(), offset),
                join(first.getContourMoves(), second.getContourMoves(), offset),
                first.getMachineConfiguration(), stock, first.getTools(),
                first.getFeedOverridePercent(), first.getSpindleOverridePercent(), program,
                first.isWorkpieceFromProgram() || second.isWorkpieceFromProgram(),
                first.getActiveWorkOffsetCode(), first.getWorkOffsets());
    }

    private static WorkpieceDefinition union(WorkpieceDefinition a, WorkpieceDefinition b) {
        if (a == null || !a.isValid()) return b;
        if (b == null || !b.isValid()) return a;
        double min = Math.min(a.getZMin(), b.getZMin());
        double max = Math.max(a.getZMax(), b.getZMax());
        return new WorkpieceDefinition(a.getShape(), Math.max(a.getDiameterMm(), b.getDiameterMm()),
                max - min, min, max, a.getSourceLine(),
                Math.max(a.getWidthXmm(), b.getWidthXmm()), Math.max(a.getWidthYmm(), b.getWidthYmm()))
                .withInitialBoreDiameter(Math.min(a.getInitialBoreDiameterMm(), b.getInitialBoreDiameterMm()));
    }

    private static List<GCodeMoveData> join(List<GCodeMoveData> first, List<GCodeMoveData> second, int offset) {
        var moves = new ArrayList<GCodeMoveData>(first.size() + second.size());
        var shifted = new ArrayList<GCodeMoveData>(second.size());
        for (GCodeMoveData move : second) {
            shifted.add(new GCodeMoveData(move.startX(), move.startZ(), move.endX(), move.endZ(),
                    move.rapid(), move.sourceLine() + offset, move.arcSegment(),
                    move.toolNumber(), move.edgeNumber(), move.arcEnd()));
        }
        // The viewer advances by path distance, not PLC time. Interleave both
        // channels at equal normalized progress so both profiles develop together.
        // Each channel's NC order and independent segment endpoints are retained.
        double[] a=progress(first), b=progress(shifted);
        int i=0,j=0;
        while(i<first.size() || j<shifted.size()) {
            if(j==shifted.size() || (i<first.size() && a[i]<=b[j])) moves.add(first.get(i++));
            else moves.add(shifted.get(j++));
        }
        return moves;
    }

    private static double[] progress(List<GCodeMoveData> moves) {
        double[] ends=new double[moves.size()]; double total=0;
        for(int i=0;i<moves.size();i++) {
            var m=moves.get(i);
            total+=Math.max(.001,Math.hypot((m.endX()-m.startX())*.5,m.endZ()-m.startZ()));
            ends[i]=total;
        }
        if(total>0) for(int i=0;i<ends.length;i++) ends[i]/=total;
        return ends;
    }
}
