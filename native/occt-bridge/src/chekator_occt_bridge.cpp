#include <jni.h>
#include <vector>
#include <string>
#include <exception>
#include <stdexcept>
#include <array>
#include <cmath>
#include <algorithm>
#include <unordered_map>
#include <Standard.hxx>
#include <Standard_Failure.hxx>
#include <gp_Pnt.hxx>
#include <gp_Ax1.hxx>
#include <gp_Dir.hxx>
#include <gp_Pln.hxx>
#include <BRepBuilderAPI_MakePolygon.hxx>
#include <BRepBuilderAPI_MakeFace.hxx>
#include <BRepCheck_Analyzer.hxx>
#include <BRepAlgoAPI_Fuse.hxx>
#include <TopTools_ListOfShape.hxx>
#include <BRepPrimAPI_MakeRevol.hxx>
#include <BRepMesh_IncrementalMesh.hxx>
#include <TopExp_Explorer.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Face.hxx>
#include <TopoDS_Wire.hxx>
#include <TopoDS_Shape.hxx>
#include <TopoDS_Compound.hxx>
#include <BRep_Builder.hxx>
#include <TopLoc_Location.hxx>
#include <BRep_Tool.hxx>
#include <Poly_Triangulation.hxx>
#include <Poly_MeshPurpose.hxx>

namespace {

struct MeshData {
    std::vector<float> positions;
    std::vector<int> indices;
};

MeshData* castHandle(jlong handle) {
    return reinterpret_cast<MeshData*>(handle);
}

struct WeldKey {
    int64_t x;
    int64_t y;
    int64_t z;

    bool operator==(const WeldKey& other) const {
        return x == other.x && y == other.y && z == other.z;
    }
};

struct WeldKeyHash {
    std::size_t operator()(const WeldKey& key) const {
        const std::size_t hx = static_cast<std::size_t>(key.x);
        const std::size_t hy = static_cast<std::size_t>(key.y);
        const std::size_t hz = static_cast<std::size_t>(key.z);
        return hx ^ (hy << 16) ^ (hz << 32);
    }
};

WeldKey weldKey(const gp_Pnt& point) {
    constexpr double scale = 10000.0; // 0.1 µm grid
    return WeldKey{
        static_cast<int64_t>(std::llround(point.X() * scale)),
        static_cast<int64_t>(std::llround(point.Y() * scale)),
        static_cast<int64_t>(std::llround(point.Z() * scale))};
}

std::vector<std::pair<double, double>> normalizeProfile(const double* zMm, const double* rMm, jsize count) {
    std::vector<std::pair<double, double>> points;
    points.reserve(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        points.emplace_back(zMm[i], std::max(0.05, rMm[i]));
    }
    if (points.size() > 1 && points.front().first > points.back().first) {
        std::reverse(points.begin(), points.end());
    }
    std::stable_sort(points.begin(), points.end(), [](const auto& a, const auto& b) {
        return a.first < b.first;
    });
    std::vector<std::pair<double, double>> merged;
    merged.reserve(points.size());
    for (const auto& point : points) {
        // Equal Z with different radii is a real shoulder. Preserve its order
        // and both ends; only an identical point may be discarded here.
        if (!merged.empty()
            && std::abs(merged.back().first - point.first) < 1e-9
            && std::abs(merged.back().second - point.second) < 1e-9) {
            continue;
        }
        merged.push_back(point);
    }
    return merged;
}

void tessellateShape(const TopoDS_Shape& shape, double deflection, MeshData& out) {
    BRepMesh_IncrementalMesh mesher(shape, deflection, Standard_False, 0.5, Standard_True);
    mesher.Perform();

    std::vector<gp_Pnt> nodes;
    std::vector<std::array<int, 3>> triangles;
    std::unordered_map<WeldKey, int, WeldKeyHash> weld;

    auto vertexIndex = [&](const gp_Pnt& point) -> int {
        const WeldKey key = weldKey(point);
        const auto found = weld.find(key);
        if (found != weld.end()) {
            return found->second;
        }
        const int index = static_cast<int>(nodes.size());
        nodes.push_back(point);
        weld.emplace(key, index);
        return index;
    };

    for (TopExp_Explorer explorer(shape, TopAbs_FACE); explorer.More(); explorer.Next()) {
        const TopoDS_Face face = TopoDS::Face(explorer.Current());
        TopLoc_Location location;
        Handle(Poly_Triangulation) triangulation = BRep_Tool::Triangulation(
            face, location, static_cast<Poly_MeshPurpose>(Poly_MeshPurpose_NONE | Poly_MeshPurpose_AnyFallback));
        if (triangulation.IsNull()) {
            triangulation = BRep_Tool::Triangulation(face, location, Poly_MeshPurpose_Presentation);
        }
        if (triangulation.IsNull() || !triangulation->HasGeometry()) {
            continue;
        }
        const gp_Trsf& transform = location.Transformation();
        const bool reversed = (face.Orientation() == TopAbs_REVERSED);
        for (int i = 1; i <= triangulation->NbTriangles(); ++i) {
            int n1 = 0;
            int n2 = 0;
            int n3 = 0;
            triangulation->Triangle(i).Get(n1, n2, n3);
            if (n1 < 1 || n2 < 1 || n3 < 1 || n1 > triangulation->NbNodes() || n2 > triangulation->NbNodes()
                || n3 > triangulation->NbNodes()) {
                continue;
            }
            gp_Pnt p1 = triangulation->Node(n1);
            gp_Pnt p2 = triangulation->Node(n2);
            gp_Pnt p3 = triangulation->Node(n3);
            if (!location.IsIdentity()) {
                p1.Transform(transform);
                p2.Transform(transform);
                p3.Transform(transform);
            }
            if (reversed) {
                std::swap(p1, p2);
            }
            triangles.push_back({
                vertexIndex(p1),
                vertexIndex(p2),
                vertexIndex(p3)});
        }
    }

    out.positions.reserve(nodes.size() * 3);
    for (const gp_Pnt& point : nodes) {
        out.positions.push_back(static_cast<float>(point.X()));
        out.positions.push_back(static_cast<float>(point.Y()));
        out.positions.push_back(static_cast<float>(point.Z()));
    }
    out.indices.reserve(triangles.size() * 3);
    for (const auto& triangle : triangles) {
        out.indices.push_back(triangle[0]);
        out.indices.push_back(triangle[1]);
        out.indices.push_back(triangle[2]);
    }
}

TopoDS_Shape buildRevolvedProfile(const double* zMm, const double* rMm, jsize count) {
    const std::vector<std::pair<double, double>> profile = normalizeProfile(zMm, rMm, count);
    if (profile.size() < 2) {
        return TopoDS_Shape();
    }
    BRepBuilderAPI_MakePolygon polygon;
    polygon.Add(gp_Pnt(0.0, profile.front().first, 0.0));
    for (const auto& point : profile) {
        polygon.Add(gp_Pnt(point.second, point.first, 0.0));
    }
    polygon.Add(gp_Pnt(0.0, profile.back().first, 0.0));
    if (!polygon.IsDone()) {
        return TopoDS_Shape();
    }
    const TopoDS_Wire wire = polygon.Wire();
    if (wire.IsNull()) {
        return TopoDS_Shape();
    }
    BRepBuilderAPI_MakeFace face(wire, Standard_True);
    if (!face.IsDone()) {
        return TopoDS_Shape();
    }
    gp_Ax1 axis(gp_Pnt(0.0, 0.0, 0.0), gp_Dir(0.0, 1.0, 0.0));
    BRepPrimAPI_MakeRevol revol(face.Face(), axis, 2.0 * M_PI);
    revol.Build();
    if (!revol.IsDone()) {
        return TopoDS_Shape();
    }
    return revol.Shape();
}

// Ordered section loops preserve face pockets, bores and detached stock pieces.
TopoDS_Shape buildSection(const double* z, const double* r, const int* ids, jsize count) {
    struct Loop { int from, to; double area; TopoDS_Wire wire; };
    std::vector<Loop> loops;
    for (int from=0; from<count;) {
        int to=from+1;
        while (to<count && ids[to]==ids[from]) ++to;
        if (to-from<4) throw std::runtime_error("Section loop has fewer than three edges");
        BRepBuilderAPI_MakePolygon polygon;
        double area=0;
        for(int i=from;i<to-1;++i) {
            polygon.Add(gp_Pnt(r[i],z[i],0));
            area += r[i]*z[i+1]-r[i+1]*z[i];
        }
        polygon.Close();
        if(!polygon.IsDone()) throw std::runtime_error("Section wire failed");
        loops.push_back({from,to,area,polygon.Wire()}); from=to;
    }
    auto contains = [&](const Loop& loop, double x, double y) {
        bool inside=false;
        for(int i=loop.from,j=loop.to-2;i<loop.to-1;j=i++)
            if((z[i]>y)!=(z[j]>y) && x<(r[j]-r[i])*(y-z[i])/(z[j]-z[i])+r[i]) inside=!inside;
        return inside;
    };
    TopTools_ListOfShape sections;
    for(const auto& outer:loops) {
        if(outer.area<=0) continue;
        BRepBuilderAPI_MakeFace face(gp_Pln(gp_Pnt(0,0,0),gp_Dir(0,0,1)),outer.wire,Standard_True);
        for(const auto& hole:loops)
            if(hole.area<0 && contains(outer,r[hole.from],z[hole.from])) face.Add(hole.wire);
        if(!face.IsDone()) throw std::runtime_error("Section face failed");
        if(!BRepCheck_Analyzer(face.Face()).IsValid()) throw std::runtime_error("Section face is not valid");
        sections.Append(face.Face());
    }
    if(sections.IsEmpty()) return TopoDS_Shape();
    TopoDS_Shape section=sections.First(); sections.RemoveFirst();
    if(!sections.IsEmpty()) {
        // Unite touching meridian faces BEFORE revolving. Leaving them in a
        // compound creates duplicate internal caps at numeric split lines.
        TopTools_ListOfShape arguments; arguments.Append(section);
        BRepAlgoAPI_Fuse fuse;
        fuse.SetArguments(arguments); fuse.SetTools(sections);
        fuse.SetFuzzyValue(1e-6); // mm: numerical contact only, not a machining gap
        fuse.Build();
        if(!fuse.IsDone()) throw std::runtime_error("Section union failed");
        fuse.SimplifyResult(); section=fuse.Shape();
    }
    if(!BRepCheck_Analyzer(section).IsValid()) throw std::runtime_error("Unified section is not valid");
    BRepPrimAPI_MakeRevol revol(section,gp_Ax1(gp_Pnt(0,0,0),gp_Dir(0,1,0)),2*M_PI);
    revol.Build();
    if(!revol.IsDone()) throw std::runtime_error("Section revolution failed");
    return revol.Shape();
}

// An OCCT failure must reach Java as a normal exception carrying its message.
// BRep construction used to sit outside the try block, so the exception escaped
// across the JNI boundary - undefined behaviour, up to a silent JVM crash.
void throwJavaFailure(JNIEnv* env, const std::string& message) {
    if (env == nullptr || env->ExceptionCheck()) {
        return;
    }
    jclass runtimeException = env->FindClass("java/lang/RuntimeException");
    if (runtimeException != nullptr) {
        env->ThrowNew(runtimeException, message.c_str());
        env->DeleteLocalRef(runtimeException);
    }
}

double adaptiveDeflection(const double* zMm, const double* rMm, jsize count, double requested) {
    double zMin = zMm[0];
    double zMax = zMm[0];
    double maxR = rMm[0];
    for (jsize i = 1; i < count; ++i) {
        zMin = std::min(zMin, zMm[i]);
        zMax = std::max(zMax, zMm[i]);
        maxR = std::max(maxR, rMm[i]);
    }
    const double span = std::max(1.0, zMax - zMin);
    const double size = std::max(maxR * 2.0, span);
    const double autoDeflection = std::max(0.06, std::min(0.45, size / 900.0));
    return std::max(0.05, std::min(requested, autoDeflection));
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_sergey_pisarev_occt_OcctNative_buildRevolvedSolid(
        JNIEnv* env,
        jclass,
        jdoubleArray zArray,
        jdoubleArray rArray,
        jdouble deflectionMm) {
    if (zArray == nullptr || rArray == nullptr) {
        return 0L;
    }
    const jsize count = env->GetArrayLength(zArray);
    if (count < 2 || env->GetArrayLength(rArray) != count) {
        return 0L;
    }
    std::vector<double> z(count);
    std::vector<double> r(count);
    env->GetDoubleArrayRegion(zArray, 0, count, z.data());
    env->GetDoubleArrayRegion(rArray, 0, count, r.data());

    MeshData* mesh = nullptr;
    try {
        // Both solid construction and tessellation are guarded: both call into OCCT.
        const TopoDS_Shape shape = buildRevolvedProfile(z.data(), r.data(), count);
        if (shape.IsNull()) {
            return 0L;
        }
        mesh = new MeshData();
        const double deflection = adaptiveDeflection(z.data(), r.data(), count, deflectionMm);
        tessellateShape(shape, deflection, *mesh);
        if (mesh->positions.size() < 9 || mesh->indices.size() < 3) {
            delete mesh;
            return 0L;
        }
        return reinterpret_cast<jlong>(mesh);
    } catch (const Standard_Failure& failure) {
        delete mesh;
        const char* text = failure.GetMessageString();
        std::string message = "OCCT: ";
        message += (text != nullptr && *text != '\0') ? text : "failure without message";
        throwJavaFailure(env, message);
        return 0L;
    } catch (const std::exception& error) {
        delete mesh;
        throwJavaFailure(env, std::string("OCCT bridge: ") + error.what());
        return 0L;
    } catch (...) {
        delete mesh;
        throwJavaFailure(env, "OCCT bridge: unknown native failure");
        return 0L;
    }
}

JNIEXPORT jlong JNICALL
Java_com_sergey_pisarev_occt_OcctNative_buildRevolvedSection(
        JNIEnv* env,
        jclass,
        jdoubleArray zArray,
        jdoubleArray rArray,
        jintArray loopArray,
        jdouble deflectionMm) {
    if (zArray == nullptr || rArray == nullptr) {
        return 0L;
    }
    const jsize count = env->GetArrayLength(zArray);
    if (count < 2 || env->GetArrayLength(rArray) != count) {
        return 0L;
    }
    if (loopArray == nullptr || env->GetArrayLength(loopArray) != count) return 0L;
    std::vector<int> loops(count);
    env->GetIntArrayRegion(loopArray,0,count,loops.data());
    std::vector<double> z(count);
    std::vector<double> r(count);
    env->GetDoubleArrayRegion(zArray, 0, count, z.data());
    env->GetDoubleArrayRegion(rArray, 0, count, r.data());

    MeshData* mesh = nullptr;
    try {
        // Both solid construction and tessellation are guarded: both call into OCCT.
        const TopoDS_Shape shape = buildSection(z.data(), r.data(), loops.data(), count);
        if (shape.IsNull()) {
            return 0L;
        }
        mesh = new MeshData();
        const double deflection = adaptiveDeflection(z.data(), r.data(), count, deflectionMm);
        tessellateShape(shape, deflection, *mesh);
        if (mesh->positions.size() < 9 || mesh->indices.size() < 3) {
            delete mesh;
            return 0L;
        }
        return reinterpret_cast<jlong>(mesh);
    } catch (const Standard_Failure& failure) {
        delete mesh;
        const char* text = failure.GetMessageString();
        std::string message = "OCCT: ";
        message += (text != nullptr && *text != '\0') ? text : "failure without message";
        throwJavaFailure(env, message);
        return 0L;
    } catch (const std::exception& error) {
        delete mesh;
        throwJavaFailure(env, std::string("OCCT bridge: ") + error.what());
        return 0L;
    } catch (...) {
        delete mesh;
        throwJavaFailure(env, "OCCT bridge: unknown native failure");
        return 0L;
    }
}

JNIEXPORT jint JNICALL
Java_com_sergey_pisarev_occt_OcctNative_meshVertexCount(JNIEnv*, jclass, jlong handle) {
    MeshData* mesh = castHandle(handle);
    if (mesh == nullptr) {
        return 0;
    }
    return static_cast<jint>(mesh->positions.size() / 3);
}

JNIEXPORT jint JNICALL
Java_com_sergey_pisarev_occt_OcctNative_meshTriangleCount(JNIEnv*, jclass, jlong handle) {
    MeshData* mesh = castHandle(handle);
    if (mesh == nullptr) {
        return 0;
    }
    return static_cast<jint>(mesh->indices.size() / 3);
}

JNIEXPORT jfloatArray JNICALL
Java_com_sergey_pisarev_occt_OcctNative_meshPositions(JNIEnv* env, jclass, jlong handle) {
    MeshData* mesh = castHandle(handle);
    if (mesh == nullptr || mesh->positions.empty()) {
        return nullptr;
    }
    jfloatArray array = env->NewFloatArray(static_cast<jsize>(mesh->positions.size()));
    if (array == nullptr) {
        return nullptr;
    }
    env->SetFloatArrayRegion(array, 0, static_cast<jsize>(mesh->positions.size()), mesh->positions.data());
    return array;
}

JNIEXPORT jintArray JNICALL
Java_com_sergey_pisarev_occt_OcctNative_meshIndices(JNIEnv* env, jclass, jlong handle) {
    MeshData* mesh = castHandle(handle);
    if (mesh == nullptr || mesh->indices.empty()) {
        return nullptr;
    }
    jintArray array = env->NewIntArray(static_cast<jsize>(mesh->indices.size()));
    if (array == nullptr) {
        return nullptr;
    }
    env->SetIntArrayRegion(array, 0, static_cast<jsize>(mesh->indices.size()), mesh->indices.data());
    return array;
}

JNIEXPORT void JNICALL
Java_com_sergey_pisarev_occt_OcctNative_releaseMesh(JNIEnv*, jclass, jlong handle) {
    delete castHandle(handle);
}

} // extern "C"
