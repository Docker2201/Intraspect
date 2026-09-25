package com.sergey.pisarev.service;

import com.sergey.pisarev.util.I18n;
import com.sergey.pisarev.model.SimulationContext;
import java.awt.BasicStroke;
import java.awt.geom.*;
import java.util.*;

/** Explicitly approximate forging: complete NC contour plus a user-set normal
 * allowance, within the shared stock envelope. No wheel dimensions are presets.
 */
public final class ApproximatePreform {
    private ApproximatePreform() { }

    public static double suggestedAllowance(SimulationContext context) {
        double allowance=0;
        for(var setup=context;setup!=null;setup=setup.getPrecedingSetup())
            for(double value:LatheCompensationProcessor.scanOffnByLine(setup.getProgramText()).values())
                if(Double.isFinite(value))allowance=Math.max(allowance,value);
        return allowance;
    }

    public static SimulationContext apply(SimulationContext complete,double allowance) {
        if(!AxialStockSection.enabled(complete))return complete;
        if(!Double.isFinite(allowance)||allowance<=0)throw new IllegalArgumentException(I18n.text("preform.error.allowance"));
        // Always regenerate from the current NC/parameters, never expand the
        // previous preform again when a setting is edited.
        var base=clear(complete);
        var target=AxialStockSection.build(base,base.getContourMoves());
        if(target.isEmpty())throw new IllegalArgumentException(I18n.text("preform.error.contour"));
        var forging=expand(target,allowance,base);
        var first=base.getPrecedingSetup();
        if(first==null)return base.withInitialStockSection(forging,allowance);
        var initial=AxialCycleStock.turnOver(forging,base.getTurnoverZSum());
        return base.afterTurnover(first.withInitialStockSection(initial,allowance),base.getTurnoverZSum());
    }

    private static SimulationContext clear(SimulationContext context) {
        var bare=context.withInitialStockSection(List.of(),0);
        return context.getPrecedingSetup()==null?bare:
                bare.afterTurnover(clear(context.getPrecedingSetup()),context.getTurnoverZSum());
    }

    private static List<double[]> expand(List<double[]> target,double allowance,SimulationContext context) {
        var material=AxialCycleStock.area(target);
        material.add(new Area(new BasicStroke((float)(2*allowance),BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND).createStrokedShape(material)));
        var wp=context.getWorkpiece(); double bore=wp.getInitialBoreDiameterMm()*.5;
        // A separately specified/NC-resolved initial bore overrides the uniform
        // allowance on the straight inner wall. Facing allowance stays bounded.
        double inner=target.stream().mapToDouble(p->p[1]).min().orElse(0);
        if(bore>0 && inner>bore)for(int i=1;i<target.size();i++) {
            var a=target.get(i-1);var b=target.get(i);
            if(a[2]==b[2] && Math.abs(a[1]-inner)<1e-6 && Math.abs(b[1]-inner)<1e-6)
                material.add(new Area(new Rectangle2D.Double(bore,Math.min(a[0],b[0])-allowance,
                        inner-bore,Math.abs(a[0]-b[0])+2*allowance)));
        }
        material.intersect(new Area(new Rectangle2D.Double(bore,wp.getZMin(),wp.getStockRadiusMm()-bore,wp.getLengthMm())));
        return AxialStockSection.toSection(material);
    }
}
