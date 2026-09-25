package com.sergey.pisarev.service;

import com.sergey.pisarev.model.*;
import java.awt.BasicStroke;
import java.awt.geom.*;
import java.util.*;

/** Time-resolved material swept by the finite cutting insert. A nominal contour
 * is not a licence to erase stock outside the cutter. All channels, seeks and
 * turnovers retain the same physical material, including any unmachined stock.
 */
public final class AxialCycleStock {
    private final SimulationContext context;
    private final LatheCyclePath path;
    private final LatheCuttingEnvelope tools;
    private final BoringPassAnalysis boring;
    private final List<LatheCyclePath.Stroke> ordered;
    private static final int UNION_BATCH=Math.max(1,Integer.getInteger("chekator.cycle.unionBatch",32));
    private static final int KEYFRAME_LIMIT=48, MIN_KEYFRAME_STRIDE=8;
    private record Snapshot(Area material,int strokes) { }
    private final NavigableMap<Double,Snapshot> keyframes=new TreeMap<>();
    private final int keyframeStride;
    private Area initial,completed;
    private int completedCount;

    public AxialCycleStock(SimulationContext context,LatheCyclePath path) {
        this(context,path,0);
    }
    AxialCycleStock(SimulationContext context,LatheCyclePath path,int keyframeStride) {
        this.context=context;this.path=path;
        tools=new LatheCuttingEnvelope(context);boring=new BoringPassAnalysis(context);
        ordered=path.strokes().stream().flatMap(List::stream)
                .sorted(Comparator.comparingDouble(LatheCyclePath.Stroke::endSeconds)).toList();
        this.keyframeStride=keyframeStride>0?keyframeStride:
                Math.max(MIN_KEYFRAME_STRIDE,ordered.size()/KEYFRAME_LIMIT);
    }
    public static AxialCycleStock create(SimulationContext context) {
        return new AxialCycleStock(context,new LatheCyclePath(context,context.getContourMoves(),.25));
    }
    public synchronized List<double[]> sectionAt(double seconds) {
        if(initial==null) {
            var preceding=context.getPrecedingSetup();
            if(preceding==null && !context.getInitialStockSection().isEmpty()) {
                initial=area(context.getInitialStockSection());
            } else if(preceding==null) {
                var wp=context.getWorkpiece(); double bore=wp.getInitialBoreDiameterMm()*.5;
                initial=new Area(new Rectangle2D.Double(bore,wp.getZMin(),
                        wp.getStockRadiusMm()-bore,wp.getLengthMm()));
            } else {
                initial=area(create(preceding).sectionAt(Double.POSITIVE_INFINITY));
                initial.transform(new AffineTransform(1,0,0,-1,0,context.getTurnoverZSum()));
            }
        }
        if(completed==null || (completedCount>0 && ordered.get(completedCount-1).endSeconds()>seconds)) {
            var from=keyframes.floorEntry(seconds);
            completed=new Area(from==null?initial:from.getValue().material());
            completedCount=from==null?0:from.getValue().strokes();
        }
        // Union short sweeps before subtracting from the complex stock. Completed
        // stroke checkpoints keep rewinding fast without caching every video frame.
        Area batch=null;int batched=0;
        while(completedCount<ordered.size() && ordered.get(completedCount).endSeconds()<=seconds) {
            var stroke=ordered.get(completedCount++);
            if(!stroke.cuttingEnabled())continue;
            var removal=removal(stroke.contour(),stroke.centre());
            if(removal==null)continue;
            if(batch==null)batch=removal;else batch.add(removal);
            if(++batched>=UNION_BATCH) {
                completed.subtract(batch);batch=null;batched=0;
                remember(stroke.endSeconds());
            }
        }
        if(batch!=null)completed.subtract(batch);
        remember(seconds);
        var material=new Area(completed);
        for(int channel=0;channel<path.channelCount();channel++) {
            var pose=path.poseAt(channel,seconds);
            if(pose!=null && pose.cuttingEnabled() && pose.fraction()>0 && pose.fraction()<1) {
                var removal=removal(LatheCyclePath.part(pose.contour(),0,pose.fraction()),
                        LatheCyclePath.part(pose.centre(),0,pose.fraction()));
                if(removal!=null)material.subtract(removal);
            }
        }
        // Stock cut free of the clamped body falls away; it must not hover.
        return AxialStockSection.clampedBody(AxialStockSection.toSection(material));
    }
    private void remember(double seconds) {
        if(!Double.isFinite(seconds) || completedCount>=ordered.size())return;
        var below=keyframes.floorEntry(seconds);var above=keyframes.ceilingEntry(seconds);
        if(below!=null && completedCount-below.getValue().strokes()<keyframeStride)return;
        if(above!=null && above.getValue().strokes()-completedCount<keyframeStride)return;
        keyframes.put(seconds,new Snapshot(new Area(completed),completedCount));
    }

    /** Что снимает один ход: область материала под пластиной и под вершиной, или {@code null}. */
    private Area removal(GCodeMoveData move,GCodeMoveData centre) {
        if(move.rapid())return null;
        boolean nominal=context.getWorkpiece().getInitialBoreDiameterMm()>0 && boring.isNominal(move);
        if(!nominal && !tools.canCutMove(move.sourceLine(),move.startX(),move.startZ(),move.endX(),move.endZ()))return null;
        // An explicitly identified nominal boring pass defines an inner wall,
        // but only along its travelled interval, never through the back face.
        if(nominal) {
            return new Area(new Rectangle2D.Double(0,Math.min(move.startZ(),move.endZ()),
                    Math.abs(move.startX())*.5,Math.abs(move.endZ()-move.startZ())));
        }
        Area removed=LatheInsertSweep.sweep(tools.activeToolAt(move.sourceLine()),
                Math.abs(centre.startX())*.5,centre.startZ(),Math.abs(centre.endX())*.5,centre.endZ());
        double radius=tools.noseRadiusMm(move.sourceLine());
        if(radius<=0)return removed; // No invented cutting radius for missing/zero-radius data.
        var sweep=new Path2D.Double();
        sweep.moveTo(Math.abs(centre.startX())*.5,centre.startZ());
        sweep.lineTo(Math.abs(centre.endX())*.5,centre.endZ());
        var nose=new Area(new BasicStroke((float)(2*radius),BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND).createStrokedShape(sweep));
        if(removed==null)return nose;
        removed.add(nose);
        return removed;
    }

    public static List<double[]> turnOver(List<double[]> section,double zSum) {
        var material=area(section);
        material.transform(new AffineTransform(1,0,0,-1,0,zSum));
        return AxialStockSection.toSection(material);
    }

    static Area area(List<double[]> section) {
        var shape=new Path2D.Double(Path2D.WIND_EVEN_ODD);double loop=Double.NaN;
        for(var point:section) {
            if(point[2]!=loop) {
                if(!Double.isNaN(loop))shape.closePath();
                shape.moveTo(point[1],point[0]);loop=point[2];
            } else shape.lineTo(point[1],point[0]);
        }
        if(!Double.isNaN(loop))shape.closePath();
        return new Area(shape);
    }
}
