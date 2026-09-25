package com.sergey.pisarev.service;

import com.sergey.pisarev.model.*;
import java.util.*;

public final class ApproximatePreformRegressionTest {
    public static void main(String[] args) {
        var machine=new MachineConfiguration(MachineConfiguration.ControlSystem.SIEMENS_TURNING,
                MachineConfiguration.MachineType.VERTICAL_LATHE,Set.of('X','Z'),0,false);
        var tool=new CncToolDefinition(1,"test","turning",1,1,0,0,2,550,"round",3);
        for(double face:new double[]{6,10}) {
            var context=new SimulationContext(List.of(new GCodeMoveData(80,face,20,face,false,2,false,1)),
                    machine,new WorkpieceDefinition(WorkpieceDefinition.Shape.CYLINDER,100,20,0,20,0),
                    List.of(tool),100,100,"T1 D1\nG41",true);
            var preform=ApproximatePreform.apply(context,2);
            var initial=AxialCycleStock.create(preform).sectionAt(0);
            check("normal allowance follows the current NC face "+face,AxialStockSection.contains(initial,face+1.9,25)
                    && !AxialStockSection.contains(initial,face+2.1,25));
            check("preform retains the untouched core",AxialStockSection.contains(initial,face-1,25));
            check("preform is explicitly labelled approximate",preform.getPreformAllowanceMm()==2);
            var again=ApproximatePreform.apply(preform,2).getInitialStockSection();
            check("rebuilding does not grow a second allowance",!AxialStockSection.contains(again,face+2.1,25));
            for(var p:initial)if(p[1]<0||p[1]>50.0001||p[0]<0||p[0]>20.0001)throw new AssertionError("Outside shared envelope");
            var second=new SimulationContext(List.of(),machine,context.getWorkpiece(),List.of(tool),100,100,"",true)
                    .afterTurnover(context,30);
            var pair=ApproximatePreform.apply(second,2);
            check("two-side preform is stored in first-setup coordinates",
                    AxialStockSection.contains(pair.getPrecedingSetup().getInitialStockSection(),face+1.9,25)
                    &&!AxialStockSection.contains(pair.getPrecedingSetup().getInitialStockSection(),face+2.1,25));
        }
        var material=new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Double(0,0,100,100));
        var finish=new CncToolDefinition(1,"finish","turning",1,1,0,0,.2,510,"finish",3,
                ToolCutDirection.Z_MINUS,93,55,11,0);
        LatheInsertSweep.subtract(material,finish,40.2,20,40.2,40);
        check("finite insert removes allowance behind the nose",!material.contains(44,30));
        check("finite insert does not cut through the finished wall",material.contains(39.9,30));
        check("holder is not an infinite cutting half-plane",material.contains(80,30));
        System.out.println("Approximate preform regressions passed.");
    }
    private static void check(String label,boolean ok){if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);}
}
