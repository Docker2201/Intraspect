package com.sergey.pisarev.service;

import com.sergey.pisarev.model.CncToolDefinition;
import java.awt.geom.*;
import java.util.*;

/** Nominal finite rhombic insert behind a round nose. The holder is not a cutter.
 * Uses the library's edge length, included angle, entering angle and tip position.
 */
final class LatheInsertSweep {
    private LatheInsertSweep() { }
    static void subtract(Area material,CncToolDefinition tool,double r0,double z0,double r1,double z1) {
        var swept=sweep(tool,r0,z0,r1,z1);
        if(swept!=null)material.subtract(swept);
    }

    /**
     * Область, заметённая пластиной на ходе, или {@code null}.
     *
     * <p>Отдельно от вычитания, чтобы вызывающий мог сложить заметания всех ходов
     * кадра и вычесть их из материала одним действием: каждое вычитание из сложной
     * области стоит куда дороже объединения нескольких простых контуров.
     */
    static Area sweep(CncToolDefinition tool,double r0,double z0,double r1,double z1) {
        if(tool==null || (tool.getTypeCode()!=500 && tool.getTypeCode()!=510))return null;
        double radius=tool.getRadius(),length=tool.getPlateLength(),angle=tool.getInsertAngleDeg();
        if(length<=0 || radius>=length*.5 || angle<=0 || angle>=180 || tool.getHolderAngleDeg()<=0)return null;
        double half=Math.toRadians(angle*.5),enter=Math.toRadians(tool.getHolderAngleDeg());
        double sr=Set.of(1,2,5).contains(tool.getToolPosition())?-1:1;
        double sz=Set.of(1,4,8).contains(tool.getToolPosition())?-1:1;
        double[] a={sr*Math.sin(enter),sz*Math.cos(enter)};
        double[] b={sr*Math.sin(enter-2*half),sz*Math.cos(enter-2*half)};
        double bx=a[0]+b[0],by=a[1]+b[1],norm=Math.hypot(bx,by);
        double setback=radius/Math.sin(half),tangent=radius/Math.tan(half);
        if(tangent>=length)return null;
        double pr=-bx/norm*setback,pz=-by/norm*setback;
        double[][] core={{pr+a[0]*tangent,pz+a[1]*tangent},
                {pr+a[0]*length,pz+a[1]*length},
                {pr+(a[0]+b[0])*length,pz+(a[1]+b[1])*length},
                {pr+b[0]*length,pz+b[1]*length},
                {pr+b[0]*tangent,pz+b[1]*tangent}};
        var points=new ArrayList<double[]>();
        for(var p:core){points.add(new double[]{r0+p[0],z0+p[1]});points.add(new double[]{r1+p[0],z1+p[1]});}
        points.sort(Comparator.<double[]>comparingDouble(p->p[0]).thenComparingDouble(p->p[1]));
        var hull=new ArrayList<double[]>();
        for(var p:points){while(hull.size()>1&&cross(hull.get(hull.size()-2),hull.get(hull.size()-1),p)<=0)hull.remove(hull.size()-1);hull.add(p);}
        int lower=hull.size();
        for(int i=points.size()-2;i>=0;i--){var p=points.get(i);while(hull.size()>lower&&cross(hull.get(hull.size()-2),hull.get(hull.size()-1),p)<=0)hull.remove(hull.size()-1);hull.add(p);}
        if(hull.size()<4)return null;
        var swept=new Path2D.Double();swept.moveTo(hull.get(0)[0],hull.get(0)[1]);
        for(int i=1;i<hull.size();i++)swept.lineTo(hull.get(i)[0],hull.get(i)[1]);
        swept.closePath();return new Area(swept);
    }
    private static double cross(double[] a,double[] b,double[] c){return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);}
}
