package com.sergey.pisarev.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.scene.DepthTest;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;

final class ToolAssetModelLoader {
    private static final String BASE_PATH = "/tool-models/";
    private static final String DEFAULT_MATERIAL = "__default__";
    private static final Map<String, Group> TEMPLATES = new java.util.concurrent.ConcurrentHashMap<>();

    private ToolAssetModelLoader() {
    }

    static Group load(int typeCode) {
        return load(String.format(Locale.ROOT, "tool_%03d.obj", typeCode),typeCode);
    }

    static Group load(String objName,int typeCode) {
        Group template = TEMPLATES.computeIfAbsent(objName, name -> readAsset(name,typeCode));
        if (template == null) return null;
        Group copy = new Group();
        for (var child : template.getChildren()) {
            var source = (MeshView)child;
            var view = new MeshView(source.getMesh());
            view.setMaterial(source.getMaterial()); view.setCullFace(source.getCullFace());
            copy.getChildren().add(view);
        }
        copy.setDepthTest(DepthTest.ENABLE);
        copy.getProperties().putAll(template.getProperties());
        return copy;
    }

    private static Group readAsset(String objName,int typeCode) {
        InputStream stream = ToolAssetModelLoader.class.getResourceAsStream(BASE_PATH + objName);
        if (stream == null) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return readObj(reader, objName, typeCode);
        } catch (RuntimeException | IOException ex) {
            System.err.println("Cannot load tool model asset " + objName + ": " + ex.getMessage());
            return null;
        }
    }

    private static Group readObj(BufferedReader reader, String objName, int typeCode) throws IOException {
        List<Vertex> vertices = new ArrayList<>();
        List<Vertex> normals = new ArrayList<>();
        List<TexCoord> texCoords = new ArrayList<>();
        Map<String, PhongMaterial> materials = new LinkedHashMap<>();
        Map<String, MeshAccumulator> meshes = new LinkedHashMap<>();
        String currentMaterial = DEFAULT_MATERIAL;
        materials.put(DEFAULT_MATERIAL, defaultMaterial(typeCode));
        meshes.put(DEFAULT_MATERIAL, new MeshAccumulator());

        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            switch (parts[0]) {
                case "mtllib" -> materials.putAll(readMtl(objName, parts, typeCode));
                case "usemtl" -> {
                    currentMaterial = parts.length > 1 ? parts[1] : DEFAULT_MATERIAL;
                    meshes.computeIfAbsent(currentMaterial, key -> new MeshAccumulator());
                }
                case "v" -> {
                    if (parts.length >= 4) {
                        vertices.add(new Vertex(parse(parts[1]), parse(parts[2]), parse(parts[3])));
                    }
                }
                case "vt" -> {
                    if (parts.length >= 3) {
                        texCoords.add(new TexCoord(parse(parts[1]), 1.0 - parse(parts[2])));
                    }
                }
                case "vn" -> {
                    if (parts.length >= 4) normals.add(new Vertex(parse(parts[1]),parse(parts[2]),parse(parts[3])));
                }
                case "f" -> addFace(
                        parts,
                        vertices,
                        normals,
                        texCoords,
                        meshes.computeIfAbsent(currentMaterial, key -> new MeshAccumulator()));
                default -> {
                    // Other OBJ records are optional for these lightweight tool assets.
                }
            }
        }

        Group group = new Group();
        for (Map.Entry<String, MeshAccumulator> entry : meshes.entrySet()) {
            MeshView view = entry.getValue().toMeshView(materials.getOrDefault(entry.getKey(), materials.get(DEFAULT_MATERIAL)));
            if (view != null) {
                group.getChildren().add(view);
            }
        }
        if (group.getChildren().isEmpty()) {
            return null;
        }
        group.setDepthTest(DepthTest.ENABLE);
        group.getProperties().put("tool-asset", objName);
        // Exports put the working end at minimum X; its Y/Z are not centred.
        double front=vertices.stream().mapToDouble(Vertex::x).min().orElse(0);
        double y=0,z=0;int count=0;
        for(var vertex:vertices)if(vertex.x()<=front+1e-5) {
            y+=vertex.y();z+=vertex.z();count++;
        }
        group.getProperties().put("front-tip",new javafx.geometry.Point3D(front,y/count,z/count));
        return group;
    }

    private static Map<String, PhongMaterial> readMtl(String objName, String[] objParts, int typeCode) {
        Map<String, PhongMaterial> result = new LinkedHashMap<>();
        for (int i = 1; i < objParts.length; i++) {
            String mtlName = objParts[i].replace('\\', '/');
            int slash = mtlName.lastIndexOf('/');
            if (slash >= 0) {
                mtlName = mtlName.substring(slash + 1);
            }
            try (InputStream stream = ToolAssetModelLoader.class.getResourceAsStream(BASE_PATH + mtlName)) {
                if (stream != null) {
                    result.putAll(parseMtl(stream, typeCode));
                }
            } catch (IOException ex) {
                System.err.println("Cannot load material library for " + objName + ": " + ex.getMessage());
            }
        }
        return result;
    }

    private static Map<String, PhongMaterial> parseMtl(InputStream stream, int typeCode) throws IOException {
        Map<String, PhongMaterial> result = new LinkedHashMap<>();
        String current = null;
        double[] defaultDiffuse = defaultDiffuse(typeCode);
        double[] diffuse = defaultDiffuse.clone();
        double[] specular = defaultSpecular(typeCode);
        String diffuseMap = null;
        double power = 72.0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+");
                switch (parts[0]) {
                    case "newmtl" -> {
                        if (current != null) {
                            result.put(current, material(diffuse[0], diffuse[1], diffuse[2],
                                    specular[0], specular[1], specular[2], power, diffuseMap));
                        }
                        current = parts.length > 1 ? parts[1] : DEFAULT_MATERIAL;
                        diffuse = defaultDiffuse.clone();
                        specular = defaultSpecular(typeCode);
                        diffuseMap = null;
                        power = 72.0;
                    }
                    case "Kd" -> diffuse = parseRgb(parts, diffuse);
                    case "Ks" -> specular = parseRgb(parts, specular);
                    case "map_Kd" -> diffuseMap = parseTexturePath(parts);
                    case "Ns" -> {
                        if (parts.length > 1) {
                            power = Math.max(1.0, Math.min(128.0, parse(parts[1]) * 0.128));
                        }
                    }
                    default -> {
                    }
                }
            }
        }
        if (current != null) {
            result.put(current, material(diffuse[0], diffuse[1], diffuse[2],
                    specular[0], specular[1], specular[2], power, diffuseMap));
        }
        return result;
    }

    private static PhongMaterial defaultMaterial(int typeCode) {
        double[] diffuse = defaultDiffuse(typeCode);
        double[] specular = defaultSpecular(typeCode);
        double power = typeCode >= 500 && typeCode < 600 ? 86.0 : 74.0;
        return material(diffuse[0], diffuse[1], diffuse[2], specular[0], specular[1], specular[2], power);
    }

    private static double[] defaultDiffuse(int typeCode) {
        if (typeCode >= 500 && typeCode < 550) {
            return rgb("#374151");
        }
        if (typeCode == 550 || typeCode == 151 || typeCode == 700) {
            return rgb("#b7791f");
        }
        if (typeCode >= 200 && typeCode < 260 || typeCode == 560) {
            return rgb("#64748b");
        }
        if (typeCode >= 580 && typeCode < 590 || typeCode >= 710 && typeCode < 725) {
            return rgb("#2563eb");
        }
        if (typeCode >= 100 && typeCode < 200) {
            return rgb("#7c8796");
        }
        return rgb("#526070");
    }

    private static double[] defaultSpecular(int typeCode) {
        if (typeCode == 550 || typeCode == 151 || typeCode == 700) {
            return rgb("#fde68a");
        }
        if (typeCode >= 580 && typeCode < 590 || typeCode >= 710 && typeCode < 725) {
            return rgb("#dbeafe");
        }
        return rgb("#f8fafc");
    }

    private static double[] rgb(String hex) {
        Color color = Color.web(hex);
        return new double[]{color.getRed(), color.getGreen(), color.getBlue()};
    }

    private static void addFace(String[] parts, List<Vertex> vertices, List<Vertex> normals, List<TexCoord> texCoords, MeshAccumulator mesh) {
        if (parts.length < 4) {
            return;
        }
        FaceRef[] face = new FaceRef[parts.length - 1];
        for (int i = 1; i < parts.length; i++) {
            face[i - 1] = faceRef(parts[i], vertices.size(), texCoords.size(),normals.size());
        }
        for (int i = 1; i < face.length - 1; i++) {
            Vertex a=vertices.get(face[0].vertexIndex()),b=vertices.get(face[i].vertexIndex()),c=vertices.get(face[i+1].vertexIndex());
            var ab=new javafx.geometry.Point3D(b.x-a.x,b.y-a.y,b.z-a.z);
            var ac=new javafx.geometry.Point3D(c.x-a.x,c.y-a.y,c.z-a.z);
            var n=ab.crossProduct(ac).normalize();
            Vertex fallback=new Vertex(n.getX(),n.getY(),n.getZ());
            mesh.add(
                    a,normal(face[0],normals,fallback),
                    texCoord(face[0], texCoords),
                    b,normal(face[i],normals,fallback),
                    texCoord(face[i], texCoords),
                    c,normal(face[i+1],normals,fallback),
                    texCoord(face[i + 1], texCoords));
        }
    }

    private static FaceRef faceRef(String token, int vertexCount, int texCoordCount,int normalCount) {
        String[] parts = token.split("/", -1);
        int vertexIndex = objIndex(parts[0], vertexCount);
        int texCoordIndex = -1;
        if (parts.length > 1 && !parts[1].isBlank()) {
            texCoordIndex = objIndex(parts[1], texCoordCount);
        }
        int normalIndex=parts.length>2&&!parts[2].isBlank()?objIndex(parts[2],normalCount):-1;
        return new FaceRef(vertexIndex, texCoordIndex,normalIndex);
    }

    private static Vertex normal(FaceRef ref,List<Vertex> normals,Vertex fallback) {
        if(ref.normalIndex()<0||ref.normalIndex()>=normals.size())return fallback;
        Vertex n=normals.get(ref.normalIndex());double length=Math.sqrt(n.x*n.x+n.y*n.y+n.z*n.z);
        return length>1e-12&&Double.isFinite(length)?new Vertex(n.x/length,n.y/length,n.z/length):fallback;
    }

    private static int objIndex(String raw, int count) {
        int index = Integer.parseInt(raw);
        if (index < 0) {
            return count + index;
        }
        return index - 1;
    }

    private static TexCoord texCoord(FaceRef ref, List<TexCoord> texCoords) {
        int index = ref.texCoordIndex();
        if (index < 0 || index >= texCoords.size()) {
            return TexCoord.ZERO;
        }
        return texCoords.get(index);
    }

    private static double[] parseRgb(String[] parts, double[] fallback) {
        if (parts.length < 4) {
            return fallback;
        }
        return new double[]{clamp(parse(parts[1])), clamp(parse(parts[2])), clamp(parse(parts[3]))};
    }

    private static double parse(String value) {
        return Double.parseDouble(value.replace(',', '.'));
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static PhongMaterial material(double dr, double dg, double db, double sr, double sg, double sb, double power) {
        return material(dr, dg, db, sr, sg, sb, power, null);
    }

    private static PhongMaterial material(
            double dr,
            double dg,
            double db,
            double sr,
            double sg,
            double sb,
            double power,
            String diffuseMap
    ) {
        PhongMaterial material = new PhongMaterial(Color.color(clamp(dr), clamp(dg), clamp(db)));
        material.setSpecularColor(Color.color(clamp(sr), clamp(sg), clamp(sb), 0.72));
        material.setSpecularPower(power);
        Image image = loadTexture(diffuseMap);
        if (image != null) {
            material.setDiffuseMap(image);
            material.setDiffuseColor(Color.WHITE);
        }
        return material;
    }

    private static Image loadTexture(String texturePath) {
        if (texturePath == null || texturePath.isBlank()) {
            return null;
        }
        String normalized = texturePath.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        int slash = normalized.indexOf("/tool-models/");
        if (slash >= 0) {
            normalized = normalized.substring(slash + "/tool-models/".length());
        }
        try (InputStream stream = ToolAssetModelLoader.class.getResourceAsStream(BASE_PATH + normalized)) {
            if (stream == null) {
                return null;
            }
            return new Image(stream);
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private static String parseTexturePath(String[] parts) {
        if (parts.length < 2) {
            return null;
        }
        return parts[parts.length - 1].replace('\\', '/');
    }

    private record Vertex(double x, double y, double z) {
    }

    private record TexCoord(double u, double v) {
        private static final TexCoord ZERO = new TexCoord(0.0, 0.0);
    }

    private record FaceRef(int vertexIndex, int texCoordIndex,int normalIndex) {
    }

    private static final class MeshAccumulator {
        private final TriangleMesh mesh = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
        private int vertexCount;
        private int texCoordCount;

        MeshAccumulator() {
        }

        void add(Vertex a,Vertex na, TexCoord ta, Vertex b,Vertex nb, TexCoord tb, Vertex c,Vertex nc, TexCoord tc) {
            int base = vertexCount;
            int texBase = texCoordCount;
            addPoint(a);
            addPoint(b);
            addPoint(c);
            for(Vertex n:new Vertex[]{na,nb,nc})mesh.getNormals().addAll((float)n.x,(float)n.y,(float)n.z);
            addTexCoord(ta);
            addTexCoord(tb);
            addTexCoord(tc);
            mesh.getFaces().addAll(base,base,texBase,base+1,base+1,texBase+1,base+2,base+2,texBase+2);
        }

        private void addPoint(Vertex vertex) {
            mesh.getPoints().addAll((float) vertex.x, (float) vertex.y, (float) vertex.z);
            vertexCount++;
        }

        private void addTexCoord(TexCoord texCoord) {
            mesh.getTexCoords().addAll((float) texCoord.u(), (float) texCoord.v());
            texCoordCount++;
        }

        MeshView toMeshView(PhongMaterial material) {
            if (vertexCount == 0) {
                return null;
            }
            MeshView view = new MeshView(mesh);
            view.setCullFace(CullFace.NONE);
            view.setDepthTest(DepthTest.ENABLE);
            view.setMaterial(material);
            return view;
        }
    }
}
