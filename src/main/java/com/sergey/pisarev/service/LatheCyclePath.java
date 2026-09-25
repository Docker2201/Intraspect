package com.sergey.pisarev.service;

import com.sergey.pisarev.model.*;
import java.util.*;

/** Small, time-ordered channel moves. Each support retains its own T/D and position.
 * The stock and the tools consume the same prefix (no interpolated connecting cuts).
 * This clock covers NC motion; it does not emulate machine-specific PLC synchronization.
 */
public final class LatheCyclePath {
    public record Stroke(GCodeMoveData contour, GCodeMoveData centre, double startSeconds, double endSeconds, boolean cuttingEnabled) { }
    public record Pose(GCodeMoveData contour, GCodeMoveData centre, double fraction, boolean cuttingEnabled) { }
    public record Step(GCodeMoveData contour, GCodeMoveData centre, int channel,
                       double endSeconds, double rpm, boolean spindleCommanded, boolean approximate) { }
    private final List<Step> steps;
    private final int[][] indices;
    private final int boundary;
    private final int firstSupport;
    private final List<List<Stroke>> strokes = new ArrayList<>();

    public LatheCyclePath(SimulationContext context, List<GCodeMoveData> base, double segmentMm) {
        boundary = MultiChannelSimulation.channelBoundaryLine(context.getProgramText());
        firstSupport = MultiChannelSimulation.supportNumber(context.getProgramText(), 0);
        var result = new ArrayList<Step>();
        for (int channel = 0; channel < (boundary == Integer.MAX_VALUE ? 1 : 2); channel++) {
            var channelStrokes = new ArrayList<Stroke>();
            int c = channel;
            var moves = base.stream().filter(m -> (m.sourceLine() > boundary ? 1 : 0) == c).toList();
            var centres = LatheEquidistantPath.apply(context.getProgramText(), moves, context.getTools(), 0, true);
            var envelope = new LatheCuttingEnvelope(context);
            var clock = SinumerikCycleTimeEstimator.motionClock(context, moves);
            double time = 0;
            for (int i = 0; i < moves.size(); i++) {
                var move = moves.get(i); var centre = centres.get(i); var timing = clock.get(i);
                if (!envelope.isCompensated(move.sourceLine())) {
                    double dr = envelope.noseCenterRadialOffsetMm(move.sourceLine()) * 2;
                    double dz = envelope.noseCenterZOffsetMm(move.sourceLine());
                    centre = new GCodeMoveData(centre.startX()+dr,centre.startZ()+dz,
                            centre.endX()+dr,centre.endZ()+dz,centre.rapid(),centre.sourceLine(),
                            centre.arcSegment(),centre.toolNumber(),centre.edgeNumber(),centre.arcEnd());
                }
                double length = Math.hypot((move.endX()-move.startX())*.5, move.endZ()-move.startZ());
                // Compensation activation/cancellation must not teleport the nose.
                if (!channelStrokes.isEmpty()) {
                    var previous = channelStrokes.get(channelStrokes.size()-1);
                    if (previous.contour().toolNumber()==move.toolNumber()
                            && previous.contour().edgeNumber()==move.edgeNumber()
                            && Math.hypot((previous.contour().endX()-move.startX())*.5,
                                    previous.contour().endZ()-move.startZ())<1e-6)
                        centre = new GCodeMoveData(previous.centre().endX(),previous.centre().endZ(),
                                centre.endX(),centre.endZ(),centre.rapid(),centre.sourceLine(),centre.arcSegment(),
                                centre.toolNumber(),centre.edgeNumber(),centre.arcEnd());
                } else if (envelope.isCompensated(move.sourceLine())) {
                    // The parser may omit the initial G0 because no preceding
                    // coordinate exists. The entry still starts at the G40
                    // imaginary tip plus the active edge's nose-centre offset.
                    centre = new GCodeMoveData(move.startX()+2*envelope.noseCenterRadialOffsetMm(move.sourceLine()),
                            move.startZ()+envelope.noseCenterZOffsetMm(move.sourceLine()),
                            centre.endX(),centre.endZ(),centre.rapid(),centre.sourceLine(),centre.arcSegment(),
                            centre.toolNumber(),centre.edgeNumber(),centre.arcEnd());
                }
                channelStrokes.add(new Stroke(move, centre, time, time + timing.seconds(),
                        !timing.spindleCommanded() || Math.abs(timing.signedRpm())>1e-9));
                int parts = Math.max(1, (int)Math.ceil(length / segmentMm));
                for (int p = 0; p < parts; p++) {
                    double start = (double)p/parts, end = (double)(p+1)/parts;
                    result.add(new Step(part(move,start,end),part(centre,start,end),channel,
                            time + timing.seconds()*end,timing.signedRpm(),timing.spindleCommanded(),timing.approximate()));
                }
                time += timing.seconds();
            }
            strokes.add(List.copyOf(channelStrokes));
        }
        result.sort(Comparator.comparingDouble(Step::endSeconds));
        steps = List.copyOf(result);
        indices = new int[boundary == Integer.MAX_VALUE ? 1 : 2][];
        for (int c=0;c<indices.length;c++) {
            int channel=c;
            indices[c]=java.util.stream.IntStream.range(0,steps.size()).filter(i->steps.get(i).channel()==channel).toArray();
        }
    }
    public List<Step> steps() { return steps; }
    public List<List<Stroke>> strokes() { return List.copyOf(strokes); }
    public double durationSeconds() { return steps.isEmpty() ? 0 : steps.get(steps.size()-1).endSeconds(); }
    public int channelCount() { return indices.length; }
    public boolean dual() { return boundary != Integer.MAX_VALUE; }
    public int supportNumber(int channel) { return dual() ? (channel == 0 ? 2 : 1) : firstSupport; }

    public Pose poseAt(int channel, double seconds) {
        var moves = strokes.get(channel); if (moves.isEmpty()) return null;
        int lo=0,hi=moves.size()-1;
        while(lo<hi) {int mid=(lo+hi)>>>1; if(moves.get(mid).endSeconds()<=seconds)lo=mid+1;else hi=mid;}
        var m=moves.get(lo);
        return new Pose(m.contour(),m.centre(),Math.max(0,Math.min(1,
                (seconds-m.startSeconds())/(m.endSeconds()-m.startSeconds()))),m.cuttingEnabled());
    }

    /** Preserve original NC blocks: splitting an activation block must not create extra cutting passes. */
    public List<GCodeMoveData> prefixAt(double seconds, boolean centres) {
        var result = new ArrayList<GCodeMoveData>();
        for (var channel : strokes) for (var m : channel) {
            if (seconds <= m.startSeconds()) break;
            var move = centres ? m.centre() : m.contour();
            if (seconds >= m.endSeconds()) result.add(move);
            else { result.add(part(move,0,(seconds-m.startSeconds())/(m.endSeconds()-m.startSeconds()))); break; }
        }
        return List.copyOf(result);
    }
    /** A support obeys NC only between its first and last stroke. Outside that span
     * the carousel has it parked: poseAt clamps onto the contour, which is a position
     * the machine never holds and which can sit inside uncut metal. */
    public boolean activeAt(int channel, double seconds) {
        var moves = strokes.get(channel);
        return !moves.isEmpty()
                && seconds >= moves.get(0).startSeconds()
                && seconds <= moves.get(moves.size()-1).endSeconds();
    }

    /** The highest Z this support reaches in its own NC — its programmed retract. */
    public double retractZ(int channel) {
        double z = Double.NEGATIVE_INFINITY;
        for (var s : strokes.get(channel))
            z = Math.max(z, Math.max(s.centre().startZ(), s.centre().endZ()));
        return z;
    }

    /** Index of the current/last move of one support, even when the other support is active. */
    public int indexAt(int channel, int active) {
        int[] ids=indices[channel]; if(ids.length==0)return -1;
        int i=Arrays.binarySearch(ids,active);
        return ids[i>=0?i:Math.max(0,-i-2)];
    }
    public static GCodeMoveData part(GCodeMoveData m,double a,double b) {
        return new GCodeMoveData(m.startX()+(m.endX()-m.startX())*a,m.startZ()+(m.endZ()-m.startZ())*a,
                m.startX()+(m.endX()-m.startX())*b,m.startZ()+(m.endZ()-m.startZ())*b,m.rapid(),
                m.sourceLine(),m.arcSegment(),m.toolNumber(),m.edgeNumber(),m.arcEnd()&&b==1);
    }
}
