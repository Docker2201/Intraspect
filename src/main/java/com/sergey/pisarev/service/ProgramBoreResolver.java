package com.sergey.pisarev.service;

import com.sergey.pisarev.model.SimulationContext;
import java.util.List;

/** Reconstructs the nominal starting bore for a program-defined through-boring operation. */
public final class ProgramBoreResolver {
    private ProgramBoreResolver() { }

    public static SimulationContext resolve(SimulationContext complete) {
        if (!AxialStockSection.enabled(complete)) return complete;
        // Never replace a measured raw bore. Automatic values are transient:
        // a parameter edit must recompute them from freshly parsed NC moves.
        var raw = clearAutomaticBore(complete);
        if (raw.getWorkpiece().getInitialBoreDiameterMm()>0) return raw;
        List<double[]> outside = null;
        double diameter=Double.POSITIVE_INFINITY, sign=1, offset=0;
        for (var setup=raw; setup!=null; setup=setup.getPrecedingSetup()) {
            var analysis=new BoringPassAnalysis(setup);
            if (analysis.hasPasses() && outside==null)
                outside=AxialStockSection.build(raw,raw.getContourMoves());
            for (var pass:analysis.getPasses()) {
                if (pass.diameter()>=raw.getWorkpiece().getDiameterMm()) continue;
                // Check the actual hub wall after facing both sides. Testing
                // the original blank's Z bounds would keep a false central cap
                // when that blank includes facing allowance outside the hub.
                var bounds=axialBounds(outside,pass.diameter()*.5+.0001);
                double a=sign*pass.startZ()+offset,b=sign*pass.endZ()+offset;
                if (bounds!=null && Math.min(a,b)<=bounds[0]+1e-6 && Math.max(a,b)>=bounds[1]-1e-6)
                    diameter=Math.min(diameter,pass.diameter());
            }
            offset+=sign*setup.getTurnoverZSum(); sign=-sign;
        }
        // Only a confirmed through pass establishes a through preform. A blind
        // pocket or a rapid move must not open the opposite face of the stock.
        return Double.isFinite(diameter) ? applyBore(raw,diameter) : raw;
    }

    private static SimulationContext clearAutomaticBore(SimulationContext context) {
        var first=context.getPrecedingSetup();
        var stock=context.getWorkpiece();
        var result=stock.isBoreFromProgram() ? context.withWorkpiece(stock.withInitialBoreDiameter(0)) : context;
        return first==null ? result : result.afterTurnover(clearAutomaticBore(first),context.getTurnoverZSum());
    }

    private static SimulationContext applyBore(SimulationContext context,double diameter) {
        var stock=context.getWorkpiece();
        var result=context.withWorkpiece(stock.withProgramBoreDiameter(diameter));
        var first=context.getPrecedingSetup();
        return first==null ? result : result.afterTurnover(applyBore(first,diameter),context.getTurnoverZSum());
    }

    private static double[] axialBounds(List<double[]> section,double radius) {
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for (int i=1;i<section.size();i++) {
            var a=section.get(i-1); var b=section.get(i);
            if (a[2]!=b[2] || (a[1]>radius)==(b[1]>radius)) continue;
            double z=a[0]+(radius-a[1])/(b[1]-a[1])*(b[0]-a[0]);
            min=Math.min(min,z); max=Math.max(max,z);
        }
        return max>min ? new double[]{min,max} : null;
    }
}
