package com.sergey.pisarev.service;

import com.sergey.pisarev.model.*;
import java.awt.geom.*;
import java.util.*;

/** Full meridional stock section for face turning: hub and rim may coexist at one Z. */
public final class AxialStockSection {
    private AxialStockSection() { }

    public static boolean enabled(SimulationContext context) {
        return context != null && context.getMachineConfiguration() != null
                && context.getMachineConfiguration().isVerticalLathe()
                && context.getWorkpiece() != null && context.getWorkpiece().isValid();
    }

    /** Third coordinate is a boundary-loop id, not a spatial coordinate. */
    public static boolean isSection(List<double[]> profile) {
        return profile != null && !profile.isEmpty() && profile.get(0).length == 3;
    }

    /**
     * Одно снятие материала: либо проход с коррекцией целиком, либо один ход без неё.
     *
     * <p>Проход с коррекцией ограничивает <em>одну</em> снятую область, и замыкать
     * её надо по всему контуру сразу: спроецировать каждый отрезок дуги отдельно —
     * значит прорезать противоположную стенку там, где касательная поворачивает.
     */
    public sealed interface Removal permits PassRemoval, MoveRemoval { }

    /** Проход по контуру с G41/G42: снимается всё между контуром и краем заготовки. */
    public record PassRemoval(List<GCodeMoveData> moves,
            LatheCompensationProcessor.CompensationMode mode, boolean entry) implements Removal { }

    /** Ход без коррекции: снимает только вершина, и расточка — по пройденному участку. */
    public record MoveRemoval(GCodeMoveData move, boolean internal, boolean nominal) implements Removal { }

    public static List<double[]> build(SimulationContext context, List<GCodeMoveData> moves) {
        Area material = initialMaterial(context);
        var tools = new LatheCuttingEnvelope(context);
        for (var removal : removals(context, moves)) apply(material, removal, context, tools);
        return toSection(material);
    }

    /**
     * Порядок снятий материала для программы.
     *
     * <p>Раньше этот разбор жил внутри {@link #build}, и цикл резания снимал
     * материал по-своему — только объём под пластиной. Поэтому в конце цикла под
     * диском и ободом оставалось тело заготовки, которого у готовой детали нет.
     * Теперь список один, и цикл применяет его по времени.
     *
     * <p>Снятия можно применять в любом порядке: вычитание областей переставимо.
     */
    public static List<Removal> removals(SimulationContext context, List<GCodeMoveData> moves) {
        WorkpieceDefinition wp = context.getWorkpiece();
        var tools = new LatheCuttingEnvelope(context);
        var boring = new BoringPassAnalysis(context);
        var modes = new TreeMap<>(LatheCompensationProcessor.scanCompensationByLine(context.getProgramText()));
        int boundary = context.getProgramText().indexOf("\n" + MultiChannelSimulation.CHANNEL_BOUNDARY + "\n");
        int offset = boundary < 0 ? Integer.MAX_VALUE : MultiChannelSimulation.secondChannelLineOffset(
                context.getProgramText().substring(0, boundary));
        var result = new ArrayList<Removal>();
        for (int channel = 0; channel < (boundary < 0 ? 1 : 2); channel++) {
            var pass = new ArrayList<GCodeMoveData>();
            var passMode = LatheCompensationProcessor.CompensationMode.NONE;
            boolean afterRapid = false, entryMove = false;
            GCodeMoveData previous = null;
            for (var move : moves) {
                if ((move.sourceLine() > offset ? 1 : 0) != channel) continue;
                var entry = modes.floorEntry(move.sourceLine());
                var mode = entry == null ? LatheCompensationProcessor.CompensationMode.NONE : entry.getValue();
                boolean internal = wp.getInitialBoreDiameterMm()>0 && boring.isInternal(move);
                boolean cutting = !move.rapid() && (internal || tools.canCutMove(move.sourceLine(), move.startX(),
                        move.startZ(), move.endX(), move.endZ()));
                boolean continuous = previous != null && Math.hypot((previous.endX()-move.startX())*.5,
                        previous.endZ()-move.startZ()) < 1e-6;
                if (!cutting || mode != passMode || !continuous
                        || (previous != null && (previous.toolNumber()!=move.toolNumber()
                        || previous.edgeNumber()!=move.edgeNumber()))) {
                    if (!pass.isEmpty()) result.add(new PassRemoval(List.copyOf(pass), passMode, entryMove));
                    pass.clear();
                }
                if (cutting && mode == LatheCompensationProcessor.CompensationMode.NONE) {
                    result.add(new MoveRemoval(move, internal, boring.isNominal(move)));
                } else if (cutting && Math.hypot((move.endX()-move.startX())*.5,move.endZ()-move.startZ()) > 1e-9) {
                    if (pass.isEmpty()) {
                        passMode = mode;
                        var beforeMode = modes.lowerEntry(move.sourceLine());
                        entryMove = afterRapid || (beforeMode != null && beforeMode.getValue() != mode);
                    }
                    pass.add(move);
                }
                afterRapid = move.rapid();
                previous = move;
            }
            if (!pass.isEmpty()) result.add(new PassRemoval(List.copyOf(pass), passMode, entryMove));
        }
        return List.copyOf(result);
    }

    /** Применяет одно снятие к материалу. */
    public static void apply(Area material, Removal removal, SimulationContext context, LatheCuttingEnvelope tools) {
        if (removal instanceof MoveRemoval one) {
            if (one.internal()) subtractBoringSweep(material, one.move(), tools, one.nominal());
            subtractNoseSweep(material, one.move(), tools);
        } else if (removal instanceof PassRemoval pass && !pass.moves().isEmpty()) {
            subtractPass(material, pass.moves(), pass.mode(), pass.entry(),
                    tools, context.getWorkpiece(), context.getProgramText());
        }
    }

    /** Rotation by 180 degrees about a transverse axis, in the new setup's coordinates. */
    private static Area initialMaterial(SimulationContext context) {
        var first = context.getPrecedingSetup();
        if (first == null) {
            if (!context.getInitialStockSection().isEmpty()) return AxialCycleStock.area(context.getInitialStockSection());
            var wp = context.getWorkpiece();
            double bore = wp.getInitialBoreDiameterMm() * .5;
            return new Area(new Rectangle2D.Double(bore, wp.getZMin(), wp.getStockRadiusMm()-bore, wp.getLengthMm()));
        }
        var profile = build(first, first.getContourMoves());
        var path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        double loop = Double.NaN;
        for (var p : profile) {
            if (p[2] != loop) {
                if (!Double.isNaN(loop)) path.closePath();
                path.moveTo(p[1], p[0]); loop = p[2];
            } else path.lineTo(p[1], p[0]);
        }
        if (!Double.isNaN(loop)) path.closePath();
        var result = new Area(path);
        result.transform(new AffineTransform(1, 0, 0, -1, 0, context.getTurnoverZSum()));
        return result;
    }

    public static List<double[]> initialSection(SimulationContext context) {
        return toSection(initialMaterial(context));
    }

    static List<double[]> toSection(Area material) {
        return toSection(material, true);
    }

    private static List<double[]> toSection(Area material, boolean snapContacts) {
        var result = new ArrayList<double[]>();
        double[] coords = new double[6]; double[] start = null; int loop = -1;
        for (var it = material.getPathIterator(null, .002); !it.isDone(); it.next()) {
            int type = it.currentSegment(coords);
            if (type == PathIterator.SEG_MOVETO) {
                start = new double[]{coords[1],coords[0],++loop}; result.add(start);
            } else if (type == PathIterator.SEG_LINETO) {
                var previous = result.get(result.size()-1);
                if (Math.hypot(coords[1]-previous[0], coords[0]-previous[1]) > 1e-8)
                    result.add(new double[]{coords[1],coords[0],loop});
            } else if (type == PathIterator.SEG_CLOSE && start != null) {
                var previous = result.get(result.size()-1);
                if (Math.hypot(start[0]-previous[0],start[1]-previous[1]) > 1e-8) result.add(start.clone());
            }
        }
        // Area enumerates outer boundaries clockwise; our revolver expects CCW.
        for (int from=0; from<result.size();) {
            int to=from+1;
            while (to<result.size() && result.get(to)[2]==result.get(from)[2]) to++;
            double area=0;
            for(int i=from;i+1<to;i++) {
                var a=result.get(i); var b=result.get(i+1); area+=a[1]*b[0]-b[1]*a[0];
            }
            // Boolean operations can leave zero-area roundoff loops at tangent contacts.
            if(to-from<4 || Math.abs(area)<1e-8) {result.subList(from,to).clear();continue;}
            Collections.reverse(result.subList(from,to));
            var cleaned = simplifyLoop(new ArrayList<>(result.subList(from,to)));
            result.subList(from,to).clear(); result.addAll(from,cleaned);
            from += cleaned.size();
        }
        if (snapContacts && !result.isEmpty()) {
            // Repeated Boolean intersections may split touching pieces by a
            // few floating-point ULPs. Snap at one nanometre and re-union before
            // revolving; otherwise each piece contributes a spurious full cap.
            var path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
            double id = Double.NaN;
            for (var p : result) {
                double r = Math.rint(p[1]*1e6)/1e6, z = Math.rint(p[0]*1e6)/1e6;
                if (p[2] != id) {
                    if (!Double.isNaN(id)) path.closePath();
                    path.moveTo(r,z); id=p[2];
                } else path.lineTo(r,z);
            }
            path.closePath();
            return mergeSeams(toSection(new Area(path), false));
        }
        return result;
    }

    /**
     * Сшивает петли по общим рёбрам: одно тело — одна внешняя петля.
     *
     * <p>После многих вычитаний Area отдаёт материал горизонтальными полосами:
     * швы делят одно тело на куски, а дно канавки внутри полосы выходит петлёй-
     * дыркой, лежащей прямо на шве. Такая дырка неоднозначна для «внутри/снаружи»,
     * и в 3D она становилась лишним кольцом, а швы — внутренними дисками.
     *
     * <p>Точки уже сведены к сетке 1 нм. Встречные рёбра одного шва взаимно
     * гасятся, оставшиеся собираются в петли обходом «материал слева».
     */
    static List<double[]> mergeSeams(List<double[]> section) {
        if (section.isEmpty()) return section;
        record Edge(long ar, long az, long br, long bz) { }
        var raw = new ArrayList<Edge>();
        var vertices = new HashMap<Long, TreeSet<Long>>(); // z -> r всех вершин на этой высоте
        for (int from = 0; from < section.size();) {
            int to = from + 1;
            while (to < section.size() && section.get(to)[2] == section.get(from)[2]) to++;
            for (int i = from; i + 1 < to; i++) {
                long ar = nano(section.get(i)[1]), az = nano(section.get(i)[0]);
                long br = nano(section.get(i+1)[1]), bz = nano(section.get(i+1)[0]);
                vertices.computeIfAbsent(az, k -> new TreeSet<>()).add(ar);
                if (ar != br || az != bz) raw.add(new Edge(ar, az, br, bz));
            }
            from = to;
        }
        // Горизонтальные рёбра: покрытие каждой высоты со знаком направления.
        var horizontal = new HashMap<Long, TreeMap<Long, Integer>>();
        var sloped = new HashMap<Edge, Integer>();
        for (var e : raw) {
            if (e.az() == e.bz()) {
                var line = horizontal.computeIfAbsent(e.az(), k -> new TreeMap<>());
                int sign = e.br() > e.ar() ? 1 : -1;
                line.merge(Math.min(e.ar(), e.br()), sign, Integer::sum);
                line.merge(Math.max(e.ar(), e.br()), -sign, Integer::sum);
            } else {
                var back = new Edge(e.br(), e.bz(), e.ar(), e.az());
                Integer pending = sloped.get(back);
                if (pending == null) sloped.merge(e, 1, Integer::sum);
                else if (pending == 1) sloped.remove(back);
                else sloped.put(back, pending - 1);
            }
        }
        var edges = new ArrayList<Edge>();
        sloped.forEach((e, n) -> { for (int i = 0; i < n; i++) edges.add(e); });
        for (var entry : horizontal.entrySet()) {
            long z = entry.getKey();
            var breaks = new TreeSet<>(entry.getValue().keySet());
            breaks.addAll(vertices.getOrDefault(z, new TreeSet<>()).subSet(breaks.first(), true, breaks.last(), true));
            int net = 0; Long previous = null;
            for (long r : breaks) {
                if (previous != null && net != 0) {
                    for (int i = 0; i < Math.abs(net); i++)
                        edges.add(net > 0 ? new Edge(previous, z, r, z) : new Edge(r, z, previous, z));
                }
                net += entry.getValue().getOrDefault(r, 0);
                previous = r;
            }
        }
        // Обход: из вершины берём ребро, ближайшее по часовой к обратному входящему,
        // тогда тела, касающиеся в точке, остаются разными петлями.
        var outgoing = new HashMap<List<Long>, List<Integer>>();
        for (int i = 0; i < edges.size(); i++)
            outgoing.computeIfAbsent(List.of(edges.get(i).ar(), edges.get(i).az()), k -> new ArrayList<>()).add(i);
        boolean[] used = new boolean[edges.size()];
        var result = new ArrayList<double[]>();
        int loop = 0;
        for (int first = 0; first < edges.size(); first++) {
            if (used[first]) continue;
            var points = new ArrayList<double[]>();
            int current = first; used[first] = true;
            var start = edges.get(first);
            points.add(new double[]{start.az() * 1e-6, start.ar() * 1e-6, loop});
            while (true) {
                var e = edges.get(current);
                points.add(new double[]{e.bz() * 1e-6, e.br() * 1e-6, loop});
                if (e.br() == start.ar() && e.bz() == start.az()) break;
                double back = Math.atan2(e.az() - e.bz(), e.ar() - e.br());
                int next = -1; double best = Double.POSITIVE_INFINITY;
                for (int candidate : outgoing.getOrDefault(List.of(e.br(), e.bz()), List.of())) {
                    if (used[candidate]) continue;
                    var c = edges.get(candidate);
                    double turn = back - Math.atan2(c.bz() - c.az(), c.br() - c.ar());
                    while (turn <= 1e-12) turn += 2 * Math.PI;
                    if (turn < best) { best = turn; next = candidate; }
                }
                // Незамкнутая цепочка — рёбра не сошлись; сечение остаётся как было.
                if (next < 0) return section;
                used[next] = true; current = next;
            }
            if (points.size() < 4) continue;
            var cleaned = simplifyLoop(points);
            double area = 0;
            for (int i = 0; i + 1 < cleaned.size(); i++)
                area += cleaned.get(i)[1]*cleaned.get(i+1)[0] - cleaned.get(i+1)[1]*cleaned.get(i)[0];
            if (cleaned.size() < 4 || Math.abs(area) < 1e-8) continue;
            result.addAll(cleaned);
            loop++;
        }
        return result.isEmpty() ? section : result;
    }

    private static long nano(double mm) {
        return Math.round(mm * 1e6);
    }

    /**
     * Только тело, стоящее на планшайбе, со своими внутренними полостями.
     *
     * <p>Кусок, который резец отделил от детали целиком (кольцо между проходами,
     * срезанная полоска), на станке падает, а не висит в воздухе. Держится то, что
     * касается нижнего торца — там кулачки и стол. Если отделённых кусков нет,
     * сечение возвращается без изменений.
     */
    public static List<double[]> clampedBody(List<double[]> section) {
        record Loop(int from, int to, double area, double zMin) { }
        var loops = new ArrayList<Loop>();
        double base = Double.POSITIVE_INFINITY;
        for (int from = 0; from < section.size();) {
            int to = from + 1;
            while (to < section.size() && section.get(to)[2] == section.get(from)[2]) to++;
            double area = 0, zMin = Double.POSITIVE_INFINITY;
            for (int i = from; i < to; i++) {
                zMin = Math.min(zMin, section.get(i)[0]);
                if (i + 1 < to) area += section.get(i)[1]*section.get(i+1)[0] - section.get(i+1)[1]*section.get(i)[0];
            }
            loops.add(new Loop(from, to, area, zMin));
            if (area > 0) base = Math.min(base, zMin);
            from = to;
        }
        if (loops.size() < 2 || !Double.isFinite(base)) return section;
        var kept = new ArrayList<Loop>();
        var dropped = new ArrayList<Loop>();
        for (var loop : loops) if (loop.area() > 0) (loop.zMin() <= base + 1e-3 ? kept : dropped).add(loop);
        if (dropped.isEmpty()) return section;
        var result = new ArrayList<double[]>();
        int id = 0;
        for (var loop : loops) {
            boolean keep = loop.area() > 0 ? kept.contains(loop)
                    : kept.stream().anyMatch(outer -> encloses(section, outer.from(), outer.to(), section.get(loop.from())))
                      && dropped.stream().noneMatch(outer -> encloses(section, outer.from(), outer.to(), section.get(loop.from())));
            if (!keep) continue;
            for (int i = loop.from(); i < loop.to(); i++) {
                var p = section.get(i);
                result.add(new double[]{p[0], p[1], id});
            }
            id++;
        }
        return result;
    }

    private static boolean encloses(List<double[]> section, int from, int to, double[] point) {
        boolean inside = false;
        for (int i = from, j = to - 2; i < to - 1; j = i++) {
            var a = section.get(i); var b = section.get(j);
            if ((a[0] > point[0]) != (b[0] > point[0])
                    && point[1] < (b[1]-a[1])*(point[0]-a[0])/(b[0]-a[0])+a[1]) inside = !inside;
        }
        return inside;
    }

    // Boolean intersections can introduce micrometre-long edges. Revolving such
    // an edge makes a near-zero-width annular face that the float mesh cannot
    // represent reliably. Bounded chord simplification removes only roundoff,
    // not holes or detached pieces: maximum meridian deviation is 0.001 mm.
    private static List<double[]> simplifyLoop(List<double[]> points) {
        int last=points.size()-1, pivot=1;
        for(int i=2;i<last;i++) if(distance(points.get(0),points.get(i))>distance(points.get(0),points.get(pivot))) pivot=i;
        boolean[] keep=new boolean[points.size()];keep[0]=keep[pivot]=keep[last]=true;
        simplifyRange(points,0,pivot,keep); simplifyRange(points,pivot,last,keep);
        var result=new ArrayList<double[]>();
        for(int i=0;i<points.size();i++)if(keep[i])result.add(points.get(i));
        return result.size()>=4?result:points;
    }
    private static double distance(double[] a,double[] b){return Math.hypot(a[0]-b[0],a[1]-b[1]);}
    private static void simplifyRange(List<double[]> p,int a,int b,boolean[] keep) {
        if(b<=a+1)return;
        double worst=.001;int index=-1;
        for(int i=a+1;i<b;i++) {
            double d=Line2D.ptSegDist(p.get(a)[0],p.get(a)[1],p.get(b)[0],p.get(b)[1],p.get(i)[0],p.get(i)[1]);
            if(d>worst){worst=d;index=i;}
        }
        if(index>=0){keep[index]=true;simplifyRange(p,a,index,keep);simplifyRange(p,index,b,keep);}
    }

    private static void subtractNoseSweep(Area material, GCodeMoveData move, LatheCuttingEnvelope tools) {
        double nose = tools.noseRadiusMm(move.sourceLine());
        if (nose <= 0) return;
        double rr = tools.noseCenterRadialOffsetMm(move.sourceLine());
        double zz = tools.noseCenterZOffsetMm(move.sourceLine());
        subtractCapsule(material, Math.abs(move.startX())*.5+rr,move.startZ()+zz,
                Math.abs(move.endX())*.5+rr,move.endZ()+zz,nose);
    }

    private static void subtractBoringSweep(Area material, GCodeMoveData move, LatheCuttingEnvelope tools, boolean nominal) {
        double nose=nominal ? 0 : tools.noseRadiusMm(move.sourceLine());
        double rr=nominal ? 0 : tools.noseCenterRadialOffsetMm(move.sourceLine());
        double zz=nominal ? 0 : tools.noseCenterZOffsetMm(move.sourceLine());
        double radius=Math.max(0,move.startX()*.5+rr+nose);
        double z0=move.startZ()+zz,z1=move.endZ()+zz;
        // Connect the swept inner wall to the existing bore, only across the
        // travelled axial interval. The finite nose supplies the rounded end;
        // a blind pass must never be extended through the back of the blank.
        material.subtract(new Area(new Rectangle2D.Double(0,Math.min(z0,z1),radius,Math.abs(z1-z0))));
    }

    private static void subtractCapsule(Area material, double r0, double z0, double r1, double z1, double nose) {
        var capsule = capsule(r0,z0,r1,z1,nose);
        if (capsule != null) material.subtract(capsule);
    }

    /** Объём под вершиной на ходе, или {@code null}. */
    private static Area capsule(double r0, double z0, double r1, double z1, double nose) {
        if (nose <= 0) return null;
        var path = new Path2D.Double(); path.moveTo(r0,z0); path.lineTo(r1,z1);
        return new Area(new java.awt.BasicStroke((float)(2*nose),
                java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND).createStrokedShape(path));
    }

    private static void subtractPass(Area material, List<GCodeMoveData> incoming,
            LatheCompensationProcessor.CompensationMode mode, boolean hasEntry,
            LatheCuttingEnvelope tools, WorkpieceDefinition wp, String program) {
        var area = passArea(incoming, mode, hasEntry, tools, wp, program);
        if (area != null) material.subtract(area);
    }

    /**
     * Область, которую снимает проход с коррекцией, или {@code null}.
     *
     * <p>Отдельно от вычитания: цикл резания снимает эту же область постепенно,
     * обрезая её по пройденному пути, иначе инструмент едет внутри нетронутого
     * металла до самого конца прохода.
     */
    static Area passArea(List<GCodeMoveData> incoming,
            LatheCompensationProcessor.CompensationMode mode, boolean hasEntry,
            LatheCuttingEnvelope tools, WorkpieceDefinition wp, String program) {
        if (incoming.isEmpty()) return null;
        // G41/G42 follow the programmed surface plus OFFN, not the tool centre.
        var pass = LatheEquidistantPath.applyContourAllowance(program, incoming, hasEntry);
        double sign = mode == LatheCompensationProcessor.CompensationMode.G41 ? 1 : -1;
        // The activation move approaches the contour. Its free side must not be
        // extended through the entire wheel. Retain only its finite nose sweep.
        int from = hasEntry && pass.size()>1 ? 1 : 0;
        // Кадр включения G41/G42 бывает и подводом, и настоящим резом вдоль
        // контура — например, кадр с G42, который сразу торцует ступицу. Если
        // считать такой кадр подводом, от прохода остаётся только след вершины,
        // и за торцом повисает кольцо стали.
        // Отличаем по направлению — подвод входит поперёк контура, рез его продолжает.
        if (from == 1 && alongContour(pass.get(0), pass.get(1))) from = 0;
        Area entryCapsule = null;
        if (from == 1) {
            var entry=pass.get(0); var next=pass.get(1);
            double dr=(next.endX()-next.startX())*.5, dz=next.endZ()-next.startZ();
            double length=Math.hypot(dr,dz), nose=tools.noseRadiusMm(entry.sourceLine());
            entryCapsule = capsule(Math.abs(entry.startX())*.5+tools.noseCenterRadialOffsetMm(entry.sourceLine()),
                    entry.startZ()+tools.noseCenterZOffsetMm(entry.sourceLine()),
                    Math.abs(entry.endX())*.5+sign*dz/length*nose,entry.endZ()-sign*dr/length*nose,nose);
        }
        var points=new ArrayList<double[]>();
        var first=pass.get(from);
        points.add(new double[]{Math.abs(first.startX())*.5,first.startZ()});
        for(int i=from;i<pass.size();i++) {
            var m=pass.get(i); points.add(new double[]{Math.abs(m.endX())*.5,m.endZ()});
        }
        // Extend only the two ends of the entire contour to free space. The
        // perimeter closes on the compensated side, including a face-to-OD turn.
        double xmin=-1, xmax=wp.getStockRadiusMm()+1, ymin=wp.getZMin()-1, ymax=wp.getZMax()+1;
        for(var p:points) {xmin=Math.min(xmin,p[0]-1);xmax=Math.max(xmax,p[0]+1);
            ymin=Math.min(ymin,p[1]-1);ymax=Math.max(ymax,p[1]+1);}
        double[] start=points.get(0), second=points.get(1), end=points.get(points.size()-1), before=points.get(points.size()-2);
        double sr=sign*(second[1]-start[1]), sz=-sign*(second[0]-start[0]);
        double er=sign*(end[1]-before[1]), ez=-sign*(end[0]-before[0]);
        // A face-to-OD pass must reach the radial stock boundary, even if the
        // last taper has a small axial normal component. Use the dominant face.
        double[] startExit=exit(start,Math.abs(sr)>Math.abs(sz)?sr:0,Math.abs(sz)>=Math.abs(sr)?sz:0,xmin,xmax,ymin,ymax);
        double[] endExit=exit(end,Math.abs(er)>Math.abs(ez)?er:0,Math.abs(ez)>=Math.abs(er)?ez:0,xmin,xmax,ymin,ymax);
        double width=xmax-xmin,height=ymax-ymin,perimeter=2*(width+height);
        double begin=perimeterPosition(endExit,xmin,xmax,ymin,ymax), finish=perimeterPosition(startExit,xmin,xmax,ymin,ymax);
        double distance=mod(sign*(finish-begin),perimeter);
        // At a partial concave pocket, independently projected normals can wrap
        // around the back of the whole blank. Close on their shared free face
        // instead. A zero component is tangent to that face, not opposed to it.
        if(distance>perimeter*.5) {
            if(sz*ez>=-1e-12 && Math.abs(sz+ez)>1e-9) {
                startExit=exit(start,0,sz+ez,xmin,xmax,ymin,ymax);
                endExit=exit(end,0,sz+ez,xmin,xmax,ymin,ymax);
            } else if(sr*er>=-1e-12 && Math.abs(sr+er)>1e-9) {
                startExit=exit(start,sr+er,0,xmin,xmax,ymin,ymax);
                endExit=exit(end,sr+er,0,xmin,xmax,ymin,ymax);
            }
            begin=perimeterPosition(endExit,xmin,xmax,ymin,ymax);
            finish=perimeterPosition(startExit,xmin,xmax,ymin,ymax);
            distance=mod(sign*(finish-begin),perimeter);
        }
        var cut=new Path2D.Double(); cut.moveTo(start[0],start[1]);
        for(int i=1;i<points.size();i++) cut.lineTo(points.get(i)[0],points.get(i)[1]);
        cut.lineTo(endExit[0],endExit[1]);
        double[][] corners={{xmin,ymin,0},{xmin,ymax,height},{xmax,ymax,height+width},{xmax,ymin,2*height+width}};
        var ordered=new ArrayList<double[]>();
        for(var c:corners) {double d=mod(sign*(c[2]-begin),perimeter);
            if(d>1e-8 && d<distance-1e-8) ordered.add(new double[]{c[0],c[1],d});}
        ordered.sort(Comparator.comparingDouble(p->p[2]));
        for(var c:ordered) cut.lineTo(c[0],c[1]);
        cut.lineTo(startExit[0],startExit[1]);cut.closePath();
        var area = new Area(cut);
        if (entryCapsule != null) area.add(entryCapsule);
        return area;
    }

    /** Идёт ли кадр включения вдоль следующего за ним, а не поперёк. */
    private static boolean alongContour(GCodeMoveData entry,GCodeMoveData next) {
        double ar=(entry.endX()-entry.startX())*.5, az=entry.endZ()-entry.startZ();
        double br=(next.endX()-next.startX())*.5, bz=next.endZ()-next.startZ();
        double la=Math.hypot(ar,az), lb=Math.hypot(br,bz);
        if(la<1e-9 || lb<1e-9)return false;
        return (ar*br+az*bz)/(la*lb) > Math.cos(Math.toRadians(30));
    }

    private static double mod(double x,double period) {return (x%period+period)%period;}
    private static double[] exit(double[] p,double dx,double dy,double xmin,double xmax,double ymin,double ymax) {
        double t=Double.POSITIVE_INFINITY;
        if(dx>1e-12)t=Math.min(t,(xmax-p[0])/dx);else if(dx< -1e-12)t=Math.min(t,(xmin-p[0])/dx);
        if(dy>1e-12)t=Math.min(t,(ymax-p[1])/dy);else if(dy< -1e-12)t=Math.min(t,(ymin-p[1])/dy);
        return new double[]{p[0]+t*dx,p[1]+t*dy};
    }
    private static double perimeterPosition(double[] p,double xmin,double xmax,double ymin,double ymax) {
        double h=ymax-ymin,w=xmax-xmin;
        if(Math.abs(p[0]-xmin)<1e-7)return p[1]-ymin;
        if(Math.abs(p[1]-ymax)<1e-7)return h+p[0]-xmin;
        if(Math.abs(p[0]-xmax)<1e-7)return h+w+ymax-p[1];
        return 2*h+w+xmax-p[0];
    }

    public static boolean contains(List<double[]> section, double z, double radius) {
        var path = new Path2D.Double(Path2D.WIND_EVEN_ODD); double loop = -1;
        for (var p : section) {
            if (p[2] != loop) { path.moveTo(p[1],p[0]); loop=p[2]; }
            else path.lineTo(p[1],p[0]);
        }
        return path.contains(radius,z);
    }
}
