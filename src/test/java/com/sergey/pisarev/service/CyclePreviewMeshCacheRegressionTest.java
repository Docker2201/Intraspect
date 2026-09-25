package com.sergey.pisarev.service;

import com.sergey.pisarev.model.WorkpieceDefinition;
import java.util.*;
import javafx.scene.shape.TriangleMesh;

/** Rendering batches must retain every triangle/normal, including the bore and
 * separate loops. A local cut must not upload the whole wheel again. */
public final class CyclePreviewMeshCacheRegressionTest {
    public static void main(String[] args) {
        var stock=new WorkpieceDefinition(WorkpieceDefinition.Shape.CYLINDER,200,80,0,80,0);
        var cache=new CyclePreviewMeshCache(stock);
        var raw=section();var first=cache.build(raw);
        equivalent(raw,first);
        var repeat=cache.build(raw);
        for(var key:first.keySet())check(first.get(key)==repeat.get(key),"unchanged batch rebuilt");
        var cut=new ArrayList<>(raw);
        var point=cut.get(18);cut.set(18,new double[]{point[0]-.05,point[1],point[2]});
        var next=cache.build(cut);equivalent(cut,next);
        long unchanged=next.entrySet().stream().filter(e->first.get(e.getKey())==e.getValue()).count();
        check(unchanged>first.size()/2,"local cut rebuilt most of the wheel");
        equivalent(raw,first); // Later builds cannot mutate a mesh already on screen.
        equivalent(raw,cache.build(raw)); // Seek backwards.
        var oneLoop=raw.stream().filter(p->p[2]==0).toList();
        equivalent(oneLoop,cache.build(oneLoop)); // A removed loop must disappear.
        check(cache.build(List.of()).isEmpty(),"empty material retained old geometry");
        equivalent(raw,cache.build(raw));
        // Число срезов задаётся снаружи (уровень качества): при любом значении
        // пакеты должны совпадать с цельной сборкой на том же числе срезов.
        simplification(raw);
        var coarse=new CyclePreviewMeshCache(stock,48);
        equivalent(raw,coarse.build(raw),48);
        check(new CyclePreviewMeshCache(stock,4).build(raw).size()>0,"minimum slice count rejected");
        System.out.println("PASS batch triangles and normals match the full mesh exactly; bore/loops/seek preserved");
        System.out.println("PASS local cut reuses "+unchanged+" of "+first.size()+" batches; old snapshots immutable");
    }
    private static List<double[]> section() {
        var p=new ArrayList<double[]>();
        p.add(new double[]{0,15,0});p.add(new double[]{0,100,0});p.add(new double[]{80,100,0});
        for(int i=0;i<=60;i++) {
            double r=100-i*85.0/60;
            p.add(new double[]{50+20*Math.cos((r-15)/85*Math.PI),r,0});
        }
        p.add(p.get(0).clone());
        // Separate annular component, with its own inner wall.
        p.addAll(List.of(new double[]{74,30,1},new double[]{74,40,1},new double[]{78,40,1},
                new double[]{78,30,1},new double[]{74,30,1}));
        return p;
    }
    private static void equivalent(List<double[]> section,Map<Integer,TriangleMesh> batches) {
        equivalent(section,batches,CyclePreviewMeshCache.DEFAULT_SLICES);
    }

    /**
     * Разбивка на пакеты не должна менять геометрию против цельной сборки.
     *
     * <p>Сравнение идёт с упрощённым контуром: упрощение — отдельный шаг
     * предпросмотра, и проверяется оно ниже своими проверками.
     */
    private static void equivalent(List<double[]> section,Map<Integer,TriangleMesh> batches,int slices) {
        check(batches.size()<=144,"cache grows with playback history");
        var expected=triangles(List.of(CycleSectionMesh.build(
                CyclePreviewMeshCache.simplify(section),slices)));
        check(expected.equals(triangles(batches.values())),"batching changed faces, normals or winding");
    }

    /** Упрощение контура: точки только из исходных, в допуске, петли целы. */
    private static void simplification(List<double[]> section) {
        var simplified=CyclePreviewMeshCache.simplify(section);
        check(simplified.size()<=section.size(),"simplification added points");
        var loops=new TreeSet<Integer>();
        for(var point:section)loops.add((int)point[2]);
        var keptLoops=new TreeSet<Integer>();
        for(var point:simplified)keptLoops.add((int)point[2]);
        check(loops.equals(keptLoops),"simplification lost a boundary loop");
        for(var point:simplified) {
            boolean found=false;
            for(var original:section)
                if(original[0]==point[0]&&original[1]==point[1]&&original[2]==point[2]){found=true;break;}
            check(found,"simplification invented a point");
        }
        // Каждая выброшенная точка должна лежать у оставшегося контура в допуске.
        double worst=0;
        for(var point:section) {
            double best=Double.MAX_VALUE;
            for(int i=0;i+1<simplified.size();i++) {
                var a=simplified.get(i);var b=simplified.get(i+1);
                if(a[2]!=point[2]||b[2]!=point[2])continue;
                best=Math.min(best,distance(point,a,b));
            }
            if(best<Double.MAX_VALUE)worst=Math.max(worst,best);
        }
        check(worst<=0.05,"simplification moved the contour by "+worst+" mm");
        System.out.println("PASS simplification keeps the contour within "
                +Math.round(worst*1000)/1000.0+" mm; points "+section.size()+" -> "+simplified.size());
    }

    private static double distance(double[] p,double[] a,double[] b) {
        double dz=b[0]-a[0],dr=b[1]-a[1];
        double length=Math.hypot(dz,dr);
        if(length<1e-9)return Math.hypot(p[0]-a[0],p[1]-a[1]);
        double t=Math.max(0,Math.min(1,((p[0]-a[0])*dz+(p[1]-a[1])*dr)/(length*length)));
        return Math.hypot(p[0]-(a[0]+t*dz),p[1]-(a[1]+t*dr));
    }
    private static Map<List<Integer>,Integer> triangles(Collection<TriangleMesh> meshes) {
        var result=new HashMap<List<Integer>,Integer>();
        for(var mesh:meshes) {
            int stride=mesh.getFaceElementSize();
            for(int i=0;i<mesh.getFaces().size();i+=stride) {
                var signature=new ArrayList<Integer>(18);
                for(int j=0;j<3;j++) {
                    int point=mesh.getFaces().get(i+j*3)*3,normal=mesh.getFaces().get(i+j*3+1)*3;
                    for(int k=0;k<3;k++)signature.add(Float.floatToIntBits(mesh.getPoints().get(point+k)));
                    for(int k=0;k<3;k++)signature.add(Float.floatToIntBits(mesh.getNormals().get(normal+k)));
                }
                result.merge(signature,1,Integer::sum);
            }
        }
        return result;
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
