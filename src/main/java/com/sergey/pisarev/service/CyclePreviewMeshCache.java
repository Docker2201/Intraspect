package com.sergey.pisarev.service;

import com.sergey.pisarev.model.WorkpieceDefinition;
import java.util.*;
import javafx.scene.shape.TriangleMesh;

/** Reuses unchanged GPU meshes during cutting. Spatial buckets only group
 * existing meridian edges: no resampling, clipping, extra faces or tolerance
 * changes. The cache holds one current snapshot per bucket, not a frame history.
 */
public final class CyclePreviewMeshCache {
    private static final int RADIAL_BUCKETS = 24, AXIAL_BUCKETS = 6;
    /**
     * Срезов по кругу в предпросмотре.
     *
     * <p>96 — столько же, сколько у токарного предпросмотра; у готовой детали их 192.
     * На крупной детали сечение даёт около 1300 рёбер, каждое крутится в два кольца, поэтому
     * число срезов прямо задаёт размер модели: при 144 в сцене выходило 430 тысяч
     * вершин, и видеокарта получала их заново каждый кадр. Отклонение хорды при 96 срезах
     * на Ø800 — около 0.2 мм, на экране это меньше точки.
     */
    public static final int DEFAULT_SLICES = 96;
    /** Допуск упрощения контура предпросмотра, мм. */
    private static final double SIMPLIFY_TOLERANCE = 0.02;
    private final int slices;
    private final double radius, zMin, length;
    private Map<Integer,List<CycleSectionMesh.Edge>> previous = Map.of();
    private Map<Integer,TriangleMesh> meshes = Map.of();

    public CyclePreviewMeshCache(WorkpieceDefinition stock) {
        this(stock,DEFAULT_SLICES);
    }

    public CyclePreviewMeshCache(WorkpieceDefinition stock,int slices) {
        radius = Math.max(1e-6,stock.getStockRadiusMm());
        zMin = stock.getZMin(); length = Math.max(1e-6,stock.getLengthMm());
        this.slices = Math.max(16,slices);
    }

    public synchronized Map<Integer,TriangleMesh> build(List<double[]> section) {
        var buckets = new TreeMap<Integer,List<CycleSectionMesh.Edge>>();
        for(var edge:CycleSectionMesh.edges(simplify(section))) {
            int r = bucket((edge.ar()+edge.br())*.5 / radius,RADIAL_BUCKETS);
            int z = bucket(((edge.az()+edge.bz())*.5-zMin) / length,AXIAL_BUCKETS);
            buckets.computeIfAbsent(z*RADIAL_BUCKETS+r,k->new ArrayList<>()).add(edge);
        }
        // Пакет — это ведро целиком, а не дюжина рёбер из него. Резать ведро на
        // куски по номеру нельзя: одно добавленное ребро сдвигает все последующие
        // куски, и они перестраиваются, хотя геометрия у них та же. На колесе так
        // уезжало 55 пакетов из 156 на кадр вместо нескольких.
        var next = new LinkedHashMap<Integer,TriangleMesh>();
        var descriptors=new LinkedHashMap<Integer,List<CycleSectionMesh.Edge>>();
        for(var entry:buckets.entrySet()) {
            var edges=List.copyOf(entry.getValue());
            descriptors.put(entry.getKey(),edges);
            next.put(entry.getKey(),edges.equals(previous.get(entry.getKey()))
                    ? meshes.get(entry.getKey()) : CycleSectionMesh.buildEdges(edges,slices));
        }
        previous=descriptors;
        meshes=Collections.unmodifiableMap(next);
        return meshes;
    }

    /**
     * Убирает точки, которые контур не меняют.
     *
     * <p>Сечение приходит из области материала, и после каждого реза в нём остаются
     * точки тесселяции: на колесе их около 1350, хотя профиль складывается из прямых
     * участков и больших радиусов. Каждая точка — это ещё два кольца вершин в модели,
     * поэтому лишние точки стоят дороже всего.
     *
     * <p>Допуск 0.02 мм: вдесятеро мельче допусков чертежа и незаметно на экране.
     * Готовая деталь строится по несокращённому сечению, здесь только предпросмотр.
     */
    static List<double[]> simplify(List<double[]> section) {
        if(section.size()<3)return section;
        var result=new ArrayList<double[]>(section.size());
        int from=0;
        while(from<section.size()) {
            int end=from+1;
            while(end<section.size() && section.get(end)[2]==section.get(from)[2])end++;
            simplifyLoop(section,from,end,result);
            from=end;
        }
        return result;
    }

    /**
     * Дуглас—Пейкер по одной петле: точка остаётся, если без неё контур уходит
     * дальше допуска от целой хорды, а не от соседней тройки.
     *
     * <p>Проверять соседние тройки нельзя: на плавном радиусе каждая тройка почти
     * прямая, точки уходят одна за другой, и ошибка складывается. На дуге из
     * 60 точек контур так сплющивало на 49 мм — большие радиусы чертежа
     * (R1500, R59) выпрямлялись бы на глазах.
     */
    private static void simplifyLoop(List<double[]> section,int from,int end,List<double[]> result) {
        int count=end-from;
        if(count<4) {
            for(int i=from;i<end;i++)result.add(section.get(i));
            return;
        }
        var keep=new boolean[count];
        keep[0]=keep[count-1]=true;
        var spans=new ArrayDeque<int[]>();
        spans.push(new int[]{0,count-1});
        while(!spans.isEmpty()) {
            var span=spans.pop();
            int lo=span[0],hi=span[1],worst=-1;
            double best=SIMPLIFY_TOLERANCE;
            for(int i=lo+1;i<hi;i++) {
                double away=distance(section.get(from+i),section.get(from+lo),section.get(from+hi));
                if(away>best) { best=away; worst=i; }
            }
            if(worst<0)continue;
            keep[worst]=true;
            spans.push(new int[]{lo,worst});
            spans.push(new int[]{worst,hi});
        }
        for(int i=0;i<count;i++)if(keep[i])result.add(section.get(from+i));
    }

    /** Насколько точка отходит от хорды между краями. */
    private static double distance(double[] p,double[] a,double[] b) {
        double dz=b[0]-a[0],dr=b[1]-a[1];
        double length=Math.hypot(dz,dr);
        if(length<1e-9)return Math.hypot(p[0]-a[0],p[1]-a[1]);
        double t=Math.max(0,Math.min(1,((p[0]-a[0])*dz+(p[1]-a[1])*dr)/(length*length)));
        return Math.hypot(p[0]-(a[0]+t*dz),p[1]-(a[1]+t*dr));
    }

    private static int bucket(double fraction,int count) {
        return Math.max(0,Math.min(count-1,(int)Math.floor(fraction*count)));
    }
}
