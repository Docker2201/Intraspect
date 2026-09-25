package com.sergey.pisarev.controller;

import com.sergey.pisarev.model.CncToolDefinition;
import com.sergey.pisarev.model.GCodeMoveData;
import com.sergey.pisarev.model.ToolTypeCatalog;
import com.sergey.pisarev.model.ToolTypeCategory;
import com.sergey.pisarev.model.ToolTypeEntry;
import java.util.List;
import java.util.Locale;
import javafx.scene.DepthTest;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;

final class ToolModel3dFactory {
    private static final PhongMaterial HOLDER_DARK = material("#334155", "#e2e8f0", 48.0);
    private static final PhongMaterial HOLDER_LIGHT = material("#64748b", "#f8fafc", 52.0);
    private static final PhongMaterial HOLDER_EDGE = material("#94a3b8", "#ffffff", 68.0);
    private static final PhongMaterial CARBIDE = material("#d4a017", "#fff7ed", 96.0);
    private static final PhongMaterial CARBIDE_DARK = material("#b7791f", "#fde68a", 82.0);
    private static final PhongMaterial CUTTING_EDGE = material("#ef4444", "#fecaca", 116.0);
    private static final PhongMaterial STEEL = material("#94a3b8", "#f8fafc", 78.0);
    private static final PhongMaterial DARK_STEEL = material("#475569", "#dbeafe", 56.0);
    private static final PhongMaterial RUBY = material("#dc2626", "#fecaca", 160.0);
    private static final PhongMaterial BLACK = material("#111827", "#e5e7eb", 64.0);
    private static final PhongMaterial BLUE = material("#38bdf8", "#e0f2fe", 90.0);

    private ToolModel3dFactory() {
    }

    static Group create(CncToolDefinition tool, GCodeMoveData move, boolean darkTheme) {
        ToolTypeEntry entry = entryFor(tool);
        int code = entry != null ? entry.typeCode() : typeCode(tool);
        double axial = axialSide(tool, move);
        Group group;
        Group assetModel = ToolAssetModelLoader.load(code);
        if (assetModel != null) {
            if (axial < 0.0) {
                assetModel.getTransforms().add(new javafx.scene.transform.Scale(1.0, -1.0, 1.0));
            }
            group = assetModel;
        } else if (entry != null && entry.category() == ToolTypeCategory.MILLING) {
            group = buildMillingTool(code, tool, axial);
        } else if ((entry != null && entry.category() == ToolTypeCategory.DRILL) || code == 560) {
            group = buildDrillFamilyTool(code, tool, axial);
        } else if (entry != null && entry.category() == ToolTypeCategory.SPECIAL) {
            group = buildSpecialTool(code, tool, axial);
        } else {
            group = buildTurningTool(code, tool, move, axial, darkTheme);
        }
        group.setDepthTest(DepthTest.ENABLE);
        group.setMouseTransparent(false);
        group.setPickOnBounds(true);
        group.getProperties().put("tool-model-code", code);
        return group;
    }

    private static Group buildTurningTool(
            int code,
            CncToolDefinition tool,
            GCodeMoveData move,
            double axial,
            boolean darkTheme
    ) {
        return switch (code) {
            case 520 -> buildPlungeTool(tool, axial);
            case 530 -> buildCutoffTool(tool, axial);
            case 540 -> buildThreadingTool(tool, axial);
            case 550 -> buildButtonTool(tool, axial);
            case 560 -> buildDrillFamilyTool(code, tool, axial);
            case 580, 585 -> buildProbeTool(tool, axial, code == 585);
            case 500 -> buildExternalTurningTool(tool, axial, true, darkTheme);
            case 510 -> buildExternalTurningTool(tool, axial, false, darkTheme);
            default -> buildExternalTurningTool(tool, axial, false, darkTheme);
        };
    }

    private static Group buildExternalTurningTool(
            CncToolDefinition tool,
            double axial,
            boolean roughing,
            boolean darkTheme
    ) {
        Group group = new Group();
        double plate = plateLength(tool, roughing ? 13.0 : 11.0);
        double insertAngle = Math.max(35.0, Math.min(95.0, tool != null ? tool.getInsertAngleDeg() : 55.0));
        double holderAngle = Math.max(0.0, Math.min(110.0, tool != null ? tool.getHolderAngleDeg() : 93.0));
        double insertDepth = roughing ? 8.0 : 6.6;
        double shankWidth = roughing ? 22.0 : 18.0;
        double shankLength = roughing ? 92.0 : 78.0;

        PhongMaterial holder = darkTheme ? HOLDER_DARK : HOLDER_LIGHT;
        Box shank = box(shankWidth, shankLength, shankWidth * 0.78, 48.0, -axial * 48.0, 0.0, holder);
        shank.getTransforms().add(new Rotate(axial * (holderAngle - 90.0) * 0.12, Rotate.Z_AXIS));
        group.getChildren().add(shank);

        Box head = box(shankWidth * 1.28, 23.0, shankWidth * 0.92, 19.0, -axial * 13.0, 0.0, HOLDER_DARK);
        head.getTransforms().add(new Rotate(axial * 6.0, Rotate.Z_AXIS));
        group.getChildren().add(head);

        MeshView pocket = prism(List.of(
                p(4.0, axial * 4.0),
                p(plate * 1.75, -axial * 5.0),
                p(plate * 1.15, -axial * (plate + 12.0)),
                p(-1.0, -axial * (plate * 0.65))
        ), insertDepth + 1.0, BLACK);
        pocket.setTranslateZ(-0.35);
        group.getChildren().add(pocket);

        MeshView insert = roughing
                ? rhombusInsert(plate * 2.25, plate * 1.35, insertDepth, axial, CARBIDE_DARK)
                : diamondInsert(plate * 1.85, plate * 1.05, insertDepth, axial, CARBIDE);
        insert.getTransforms().add(new Rotate(axial * (insertAngle - 55.0) * 0.12, Rotate.Z_AXIS));
        group.getChildren().add(insert);

        Sphere nose = sphere(Math.max(1.2, radius(tool, roughing ? 0.8 : 0.35) * 3.4), 0.0, 0.0, 0.0, CUTTING_EDGE);
        group.getChildren().add(nose);
        Box edge = box(plate * 0.72, 2.0, insertDepth + 1.0, plate * 0.22, -axial * 1.8, 0.0, CUTTING_EDGE);
        edge.getTransforms().add(new Rotate(axial * -15.0, Rotate.Z_AXIS));
        group.getChildren().add(edge);

        Cylinder screw = cylinderZ(Math.max(2.0, plate * 0.18), insertDepth + 2.4,
                plate * 0.95, -axial * plate * 0.48, 0.0, STEEL, 40);
        group.getChildren().add(screw);

        return group;
    }

    private static Group buildPlungeTool(CncToolDefinition tool, double axial) {
        Group group = new Group();
        double width = Math.max(4.0, cutWidth(tool, 5.0));
        double plate = plateLength(tool, 12.0);
        group.getChildren().add(box(18.0, 92.0, 17.0, 46.0, -axial * 44.0, 0.0, HOLDER_DARK));
        group.getChildren().add(box(54.0, width * 1.05, 8.0, 29.0, 0.0, 0.0, HOLDER_EDGE));
        group.getChildren().add(box(plate, width * 1.65, 9.0, plate * 0.42, 0.0, 0.0, CARBIDE));
        group.getChildren().add(box(4.2, width * 1.9, 10.0, 0.0, 0.0, 0.0, CUTTING_EDGE));
        group.getChildren().add(cylinderZ(2.4, 10.5, plate * 0.65, -axial * width * 0.15, 0.0, STEEL, 32));
        return group;
    }

    private static Group buildCutoffTool(CncToolDefinition tool, double axial) {
        Group group = new Group();
        double width = Math.max(2.2, cutWidth(tool, 3.0));
        double bladeLength = Math.max(72.0, plateLength(tool, 18.0) * 5.8);
        group.getChildren().add(box(16.0, 92.0, 16.0, 55.0, -axial * 44.0, 0.0, HOLDER_DARK));
        group.getChildren().add(box(bladeLength, width, 8.0, bladeLength * 0.5 - 1.0, 0.0, 0.0, HOLDER_EDGE));
        group.getChildren().add(box(11.0, width * 1.35, 8.6, 3.8, 0.0, 0.0, CARBIDE));
        group.getChildren().add(box(3.2, width * 1.55, 9.2, 0.0, 0.0, 0.0, CUTTING_EDGE));
        group.getChildren().add(box(bladeLength * 0.45, 1.0, 9.0, bladeLength * 0.34, axial * width * 0.72, 0.0, BLACK));
        return group;
    }

    private static Group buildThreadingTool(CncToolDefinition tool, double axial) {
        Group group = new Group();
        double plate = plateLength(tool, 11.0);
        group.getChildren().add(box(18.0, 88.0, 16.0, 48.0, -axial * 42.0, 0.0, HOLDER_DARK));
        group.getChildren().add(box(27.0, 17.0, 13.0, 19.0, -axial * 10.0, 0.0, BLACK));
        MeshView insert = triangleInsert(plate * 2.7, plate * 2.25, 8.0, axial, CARBIDE);
        group.getChildren().add(insert);
        Box v1 = box(plate * 1.45, 2.2, 9.4, plate * 0.42, -axial * plate * 0.38, 0.0, CUTTING_EDGE);
        v1.getTransforms().add(new Rotate(axial * 28.0, Rotate.Z_AXIS));
        Box v2 = box(plate * 1.45, 2.2, 9.4, plate * 0.42, axial * plate * 0.38, 0.0, CUTTING_EDGE);
        v2.getTransforms().add(new Rotate(axial * -28.0, Rotate.Z_AXIS));
        group.getChildren().addAll(v1, v2, cylinderZ(2.4, 9.4, plate, -axial * 3.0, 0.0, STEEL, 32));
        return group;
    }

    private static Group buildButtonTool(CncToolDefinition tool, double axial) {
        Group group = new Group();
        double r = Math.max(7.0, radius(tool, 6.0) * 1.8);
        double depth = Math.max(6.0, r * 0.72);
        group.getChildren().add(box(20.0, 86.0, 18.0, 46.0, -axial * 42.0, 0.0, HOLDER_DARK));
        group.getChildren().add(box(r * 2.7, r * 1.3, depth + 1.0, r * 1.35, -axial * r * 0.68, 0.0, BLACK));
        Cylinder insert = cylinderZ(r, depth, r, -axial * r, 0.0, CARBIDE, 72);
        group.getChildren().add(insert);
        group.getChildren().add(cylinderZ(r * 0.32, depth + 1.4, r, -axial * r, 0.0, STEEL, 42));
        group.getChildren().add(sphere(Math.max(1.4, r * 0.17), 0.0, 0.0, 0.0, CUTTING_EDGE));
        return group;
    }

    private static Group buildProbeTool(CncToolDefinition tool, double axial, boolean calibrating) {
        Group group = new Group();
        double ball = Math.max(4.0, radius(tool, calibrating ? 5.0 : 3.0));
        double stem = Math.max(52.0, lengthZ(tool, calibrating ? 68.0 : 84.0));
        group.getChildren().add(cylinderY(ball * 0.34, stem, 0.0, -axial * (stem * 0.5 + ball), 0.0, STEEL, 32));
        group.getChildren().add(cylinderY(ball * 0.88, ball * 1.15, 0.0, -axial * (ball * 1.7), 0.0, DARK_STEEL, 40));
        group.getChildren().add(sphere(ball, 0.0, 0.0, 0.0, calibrating ? BLUE : RUBY));
        if (!calibrating) {
            group.getChildren().add(cylinderX(ball * 0.22, ball * 5.2, 0.0, -axial * (stem * 0.5), 0.0, STEEL, 24));
            group.getChildren().add(sphere(ball * 0.55, ball * 2.65, -axial * (stem * 0.5), 0.0, RUBY));
            group.getChildren().add(sphere(ball * 0.55, -ball * 2.65, -axial * (stem * 0.5), 0.0, RUBY));
        }
        return group;
    }

    private static Group buildDrillFamilyTool(int code, CncToolDefinition tool, double axial) {
        return switch (code) {
            case 210 -> buildBoringBar(tool, axial);
            case 220 -> buildCenterDrill(tool, axial);
            case 230 -> buildCountersink(tool, axial);
            case 231 -> buildCounterbore(tool, axial);
            case 240, 241, 242 -> buildTap(tool, axial);
            case 250 -> buildReamer(tool, axial);
            default -> buildTwistDrill(tool, axial, code == 205 || code == 560);
        };
    }

    private static Group buildTwistDrill(CncToolDefinition tool, double axial, boolean solid) {
        Group group = new Group();
        double diameter = Math.max(8.0, cutWidth(tool, radius(tool, 4.0) * 2.0));
        double length = Math.max(88.0, lengthZ(tool, 112.0));
        group.getChildren().add(cylinderY(diameter * 0.48, length * 0.72, 0.0, -axial * (length * 0.36 + diameter * 0.72), 0.0, solid ? DARK_STEEL : STEEL, 48));
        group.getChildren().add(cylinderY(diameter * 0.42, length * 0.52, 0.0, -axial * (length * 0.28 + diameter * 0.35), 0.0, DARK_STEEL, 48));
        addFlutes(group, diameter, length, axial, 2, solid ? "#0f172a" : "#475569");
        group.getChildren().add(coneY(diameter * 0.48, diameter * 0.75, 0.0, axial * diameter * 0.38, 0.0, CUTTING_EDGE, -axial, 48));
        group.getChildren().add(box(diameter * 0.85, 1.6, diameter * 0.16, 0.0, 0.0, 0.0, CARBIDE));
        return group;
    }

    private static Group buildCenterDrill(CncToolDefinition tool, double axial) {
        Group group = new Group();
        double diameter = Math.max(8.0, cutWidth(tool, 11.0));
        group.getChildren().add(cylinderY(diameter * 0.42, 74.0, 0.0, -axial * 44.0, 0.0, STEEL, 42));
        group.getChildren().add(coneY(diameter * 0.58, diameter * 1.05, 0.0, -axial * 5.0, 0.0, CARBIDE, -axial, 48));
        group.getChildren().add(coneY(diameter * 0.25, diameter * 0.65, 0.0, axial * 2.5, 0.0, CUTTING_EDGE, -axial, 42));
        return group;
    }

    private static Group buildCountersink(CncToolDefinition tool, double axial) {
        Group group = new Group();
        double diameter = Math.max(14.0, cutWidth(tool, 18.0));
        group.getChildren().add(cylinderY(diameter * 0.25, 70.0, 0.0, -axial * 48.0, 0.0, STEEL, 40));
        group.getChildren().add(coneY(diameter * 0.72, diameter * 0.92, 0.0, -axial * 5.0, 0.0, CARBIDE, -axial, 56));
        addRadialTeeth(group, diameter * 0.72, 5.0, 6, axial);
        return group;
    }

    private static Group buildCounterbore(CncToolDefinition tool, double axial) {
        Group group = new Group();
        double diameter = Math.max(12.0, cutWidth(tool, 16.0));
        group.getChildren().add(cylinderY(diameter * 0.28, 74.0, 0.0, -axial * 48.0, 0.0, STEEL, 40));
        group.getChildren().add(cylinderY(diameter * 0.62, diameter * 1.15, 0.0, -axial * diameter * 0.65, 0.0, CARBIDE, 56));
        group.getChildren().add(cylinderY(diameter * 0.22, diameter * 0.8, 0.0, axial * diameter * 0.25, 0.0, DARK_STEEL, 36));
        addRadialTeeth(group, diameter * 0.62, diameter * 0.35, 4, axial);
        return group;
    }

    private static Group buildTap(CncToolDefinition tool, double axial) {
        Group group = new Group();
        double diameter = Math.max(8.0, cutWidth(tool, 10.0));
        double length = Math.max(78.0, lengthZ(tool, 96.0));
        group.getChildren().add(cylinderY(diameter * 0.44, length * 0.32, 0.0, -axial * (length * 0.68), 0.0, STEEL, 40));
        group.getChildren().add(cylinderY(diameter * 0.46, length * 0.62, 0.0, -axial * (length * 0.29), 0.0, DARK_STEEL, 48));
        for (int i = 0; i < 11; i++) {
            double y = -axial * (8.0 + i * length * 0.045);
            Box ridge = box(diameter * 0.86, 1.2, 1.8, 0.0, y, diameter * 0.46, STEEL);
            ridge.getTransforms().add(new Rotate(i * 28.0, Rotate.Y_AXIS));
            group.getChildren().add(ridge);
        }
        group.getChildren().add(coneY(diameter * 0.43, diameter * 0.45, 0.0, axial * diameter * 0.18, 0.0, CUTTING_EDGE, -axial, 40));
        return group;
    }

    private static Group buildReamer(CncToolDefinition tool, double axial) {
        Group group = new Group();
        double diameter = Math.max(8.0, cutWidth(tool, 12.0));
        double length = Math.max(82.0, lengthZ(tool, 98.0));
        group.getChildren().add(cylinderY(diameter * 0.44, length, 0.0, -axial * length * 0.5, 0.0, STEEL, 56));
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(i * 60.0);
            Box flute = box(1.4, length * 0.72, 2.2, Math.cos(a) * diameter * 0.45,
                    -axial * length * 0.34, Math.sin(a) * diameter * 0.45, DARK_STEEL);
            flute.getTransforms().add(new Rotate(Math.toDegrees(a), Rotate.Y_AXIS));
            group.getChildren().add(flute);
        }
        group.getChildren().add(coneY(diameter * 0.42, diameter * 0.32, 0.0, axial * diameter * 0.12, 0.0, CUTTING_EDGE, -axial, 48));
        return group;
    }

    private static Group buildBoringBar(CncToolDefinition tool, double axial) {
        Group group = new Group();
        double diameter = Math.max(9.0, cutWidth(tool, 12.0));
        double length = Math.max(98.0, lengthZ(tool, 120.0));
        group.getChildren().add(cylinderY(diameter * 0.5, length, 0.0, -axial * (length * 0.5 + 5.0), 0.0, DARK_STEEL, 48));
        group.getChildren().add(box(diameter * 1.2, diameter * 0.9, diameter * 0.55, 0.0, -axial * 2.0, 0.0, CARBIDE));
        group.getChildren().add(sphere(Math.max(1.2, radius(tool, 0.4) * 3.0), 0.0, 0.0, 0.0, CUTTING_EDGE));
        return group;
    }

    private static Group buildMillingTool(int code, CncToolDefinition tool, double axial) {
        return switch (code) {
            case 110 -> buildEndMill(tool, axial, true, false);
            case 111 -> buildEndMill(tool, axial, true, true);
            case 121, 155, 156, 157 -> buildProfileMill(tool, axial, code);
            case 130, 131 -> buildAngleHeadMill(tool, axial, code == 131);
            case 140 -> buildFaceMill(tool, axial);
            case 145, 160 -> buildTap(tool, axial);
            case 150, 151, 700 -> buildSideOrSawMill(tool, axial, code == 151 || code == 700);
            default -> buildEndMill(tool, axial, false, false);
        };
    }

    private static Group buildEndMill(CncToolDefinition tool, double axial, boolean ball, boolean conical) {
        Group group = new Group();
        double diameter = Math.max(10.0, cutWidth(tool, 14.0));
        double length = Math.max(82.0, lengthZ(tool, 104.0));
        double cutterLength = length * 0.52;
        group.getChildren().add(cylinderY(diameter * 0.46, length * 0.46, 0.0, -axial * (length * 0.76), 0.0, STEEL, 48));
        if (conical) {
            group.getChildren().add(coneY(diameter * 0.48, cutterLength, 0.0, -axial * cutterLength * 0.45, 0.0, DARK_STEEL, -axial, 56));
        } else {
            group.getChildren().add(cylinderY(diameter * 0.48, cutterLength, 0.0, -axial * cutterLength * 0.5, 0.0, DARK_STEEL, 56));
        }
        addFlutes(group, diameter, cutterLength, axial, 4, "#1f2937");
        if (ball) {
            group.getChildren().add(sphere(diameter * 0.48, 0.0, 0.0, 0.0, CARBIDE));
        } else {
            group.getChildren().add(box(diameter * 0.92, 1.4, diameter * 0.18, 0.0, 0.0, 0.0, CUTTING_EDGE));
        }
        return group;
    }

    private static Group buildProfileMill(CncToolDefinition tool, double axial, int code) {
        Group group = buildEndMill(tool, axial, code == 156 || code == 157, false);
        double diameter = Math.max(11.0, cutWidth(tool, 15.0));
        Cylinder collar = cylinderY(diameter * 0.7, diameter * 0.35, 0.0, -axial * diameter * 0.32, 0.0, CARBIDE, 56);
        group.getChildren().add(collar);
        return group;
    }

    private static Group buildAngleHeadMill(CncToolDefinition tool, double axial, boolean rounded) {
        Group group = new Group();
        double diameter = Math.max(11.0, cutWidth(tool, 15.0));
        group.getChildren().add(cylinderY(diameter * 0.44, 82.0, 0.0, -axial * 48.0, 0.0, STEEL, 40));
        group.getChildren().add(box(diameter * 2.4, diameter * 1.2, diameter * 1.1, diameter * 0.9, -axial * 8.0, 0.0, DARK_STEEL));
        group.getChildren().add(cylinderX(diameter * 0.42, diameter * 3.0, diameter * 2.2, -axial * 8.0, 0.0, CARBIDE, 48));
        if (rounded) {
            group.getChildren().add(sphere(diameter * 0.42, diameter * 3.7, -axial * 8.0, 0.0, CUTTING_EDGE));
        }
        return group;
    }

    private static Group buildFaceMill(CncToolDefinition tool, double axial) {
        Group group = new Group();
        double diameter = Math.max(26.0, cutWidth(tool, 32.0));
        group.getChildren().add(cylinderY(diameter * 0.22, 74.0, 0.0, -axial * 55.0, 0.0, STEEL, 42));
        group.getChildren().add(cylinderY(diameter * 0.5, diameter * 0.42, 0.0, -axial * diameter * 0.22, 0.0, DARK_STEEL, 72));
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45.0);
            Box insert = box(diameter * 0.16, diameter * 0.08, 4.5,
                    Math.cos(a) * diameter * 0.42,
                    -axial * diameter * 0.04,
                    Math.sin(a) * diameter * 0.42,
                    CARBIDE);
            insert.getTransforms().add(new Rotate(Math.toDegrees(a), Rotate.Y_AXIS));
            group.getChildren().add(insert);
        }
        return group;
    }

    private static Group buildSideOrSawMill(CncToolDefinition tool, double axial, boolean saw) {
        Group group = new Group();
        double diameter = Math.max(28.0, cutWidth(tool, 34.0));
        group.getChildren().add(cylinderY(diameter * 0.18, 66.0, 0.0, -axial * 46.0, 0.0, STEEL, 42));
        group.getChildren().add(cylinderY(diameter * 0.52, saw ? 4.5 : 10.0, 0.0, 0.0, 0.0, CARBIDE, 96));
        int teeth = saw ? 18 : 8;
        for (int i = 0; i < teeth; i++) {
            double a = Math.toRadians(i * (360.0 / teeth));
            MeshView tooth = trianglePrism(diameter * 0.16, diameter * 0.22, 4.2, 1.0, CARBIDE_DARK);
            tooth.setTranslateX(Math.cos(a) * diameter * 0.54);
            tooth.setTranslateZ(Math.sin(a) * diameter * 0.54);
            tooth.getTransforms().add(new Rotate(-Math.toDegrees(a), Rotate.Y_AXIS));
            group.getChildren().add(tooth);
        }
        return group;
    }

    private static Group buildSpecialTool(int code, CncToolDefinition tool, double axial) {
        return switch (code) {
            case 730 -> buildStopTool(axial);
            case 731 -> buildMandrelTool(tool, axial);
            case 732 -> buildSteadyRestTool(axial);
            default -> buildProbeTool(tool, axial, false);
        };
    }

    private static Group buildStopTool(double axial) {
        Group group = new Group();
        group.getChildren().add(box(34.0, 16.0, 34.0, 17.0, 0.0, 0.0, HOLDER_DARK));
        group.getChildren().add(box(10.0, 74.0, 10.0, 42.0, -axial * 40.0, 0.0, STEEL));
        group.getChildren().add(sphere(4.0, 0.0, 0.0, 0.0, CUTTING_EDGE));
        return group;
    }

    private static Group buildMandrelTool(CncToolDefinition tool, double axial) {
        Group group = new Group();
        double diameter = Math.max(18.0, cutWidth(tool, 24.0));
        group.getChildren().add(cylinderY(diameter * 0.35, 84.0, 0.0, -axial * 46.0, 0.0, STEEL, 48));
        group.getChildren().add(coneY(diameter * 0.55, 34.0, 0.0, -axial * 16.0, 0.0, DARK_STEEL, -axial, 56));
        group.getChildren().add(cylinderY(diameter * 0.16, 22.0, 0.0, axial * 7.0, 0.0, CUTTING_EDGE, 32));
        return group;
    }

    private static Group buildSteadyRestTool(double axial) {
        Group group = new Group();
        group.getChildren().add(box(58.0, 18.0, 12.0, 32.0, -axial * 10.0, 0.0, HOLDER_DARK));
        group.getChildren().add(box(12.0, 48.0, 12.0, 10.0, -axial * 34.0, 20.0, STEEL));
        group.getChildren().add(box(12.0, 48.0, 12.0, 10.0, -axial * 34.0, -20.0, STEEL));
        group.getChildren().add(sphere(4.0, 0.0, 0.0, 0.0, CUTTING_EDGE));
        return group;
    }

    private static void addFlutes(Group group, double diameter, double length, double axial, int count, String color) {
        PhongMaterial flute = material(color, "#e2e8f0", 44.0);
        for (int i = 0; i < count; i++) {
            double phase = i * (360.0 / count);
            for (int s = 0; s < 4; s++) {
                Box slot = box(diameter * 0.12, length * 0.18, 1.4,
                        Math.cos(Math.toRadians(phase + s * 24.0)) * diameter * 0.42,
                        -axial * (length * (0.12 + s * 0.14)),
                        Math.sin(Math.toRadians(phase + s * 24.0)) * diameter * 0.42,
                        flute);
                slot.getTransforms().add(new Rotate(phase + s * 24.0, Rotate.Y_AXIS));
                slot.getTransforms().add(new Rotate(axial * 17.0, Rotate.Z_AXIS));
                group.getChildren().add(slot);
            }
        }
    }

    private static void addRadialTeeth(Group group, double radius, double yOffset, int teeth, double axial) {
        for (int i = 0; i < teeth; i++) {
            double a = Math.toRadians(i * 360.0 / teeth);
            Box tooth = box(radius * 0.2, 2.8, 4.0, Math.cos(a) * radius,
                    -axial * yOffset, Math.sin(a) * radius, CUTTING_EDGE);
            tooth.getTransforms().add(new Rotate(Math.toDegrees(a), Rotate.Y_AXIS));
            group.getChildren().add(tooth);
        }
    }

    private static MeshView diamondInsert(double length, double width, double thickness, double axial, PhongMaterial material) {
        return prism(List.of(
                p(0.0, 0.0),
                p(length * 0.48, axial * width * 0.46),
                p(length, -axial * width * 0.12),
                p(length * 0.48, -axial * width)
        ), thickness, material);
    }

    private static MeshView rhombusInsert(double length, double width, double thickness, double axial, PhongMaterial material) {
        return prism(List.of(
                p(0.0, 0.0),
                p(length * 0.36, axial * width * 0.34),
                p(length, -axial * width * 0.28),
                p(length * 0.62, -axial * width * 1.02)
        ), thickness, material);
    }

    private static MeshView triangleInsert(double length, double width, double thickness, double axial, PhongMaterial material) {
        return prism(List.of(
                p(0.0, 0.0),
                p(length, axial * width * 0.5),
                p(length, -axial * width * 0.5)
        ), thickness, material);
    }

    private static MeshView trianglePrism(double length, double width, double thickness, double axial, PhongMaterial material) {
        MeshView mesh = triangleInsert(length, width, thickness, axial, material);
        mesh.setCullFace(CullFace.NONE);
        return mesh;
    }

    private static MeshView prism(List<double[]> points, double thickness, PhongMaterial material) {
        TriangleMesh mesh = new TriangleMesh();
        float half = (float) Math.max(0.5, thickness * 0.5);
        for (double[] point : points) {
            mesh.getPoints().addAll((float) point[0], (float) point[1], -half);
        }
        for (double[] point : points) {
            mesh.getPoints().addAll((float) point[0], (float) point[1], half);
        }
        mesh.getTexCoords().addAll(0f, 0f);
        int n = points.size();
        for (int i = 1; i < n - 1; i++) {
            mesh.getFaces().addAll(0, 0, i, 0, i + 1, 0);
            mesh.getFaces().addAll(n, 0, n + i + 1, 0, n + i, 0);
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            mesh.getFaces().addAll(i, 0, j, 0, n + i, 0);
            mesh.getFaces().addAll(j, 0, n + j, 0, n + i, 0);
        }
        MeshView view = new MeshView(mesh);
        view.setCullFace(CullFace.NONE);
        view.setMaterial(material);
        return view;
    }

    private static MeshView coneY(
            double radius,
            double height,
            double x,
            double y,
            double z,
            PhongMaterial material,
            double direction,
            int segments
    ) {
        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().addAll(0f, 0f, 0f);
        double baseY = direction * Math.max(1.0, height);
        for (int i = 0; i < segments; i++) {
            double a = Math.toRadians((360.0 * i) / segments);
            mesh.getPoints().addAll((float) (Math.cos(a) * radius), (float) baseY, (float) (Math.sin(a) * radius));
        }
        int center = segments + 1;
        mesh.getPoints().addAll(0f, (float) baseY, 0f);
        mesh.getTexCoords().addAll(0f, 0f);
        for (int i = 0; i < segments; i++) {
            int a = i + 1;
            int b = ((i + 1) % segments) + 1;
            mesh.getFaces().addAll(0, 0, a, 0, b, 0);
            mesh.getFaces().addAll(center, 0, b, 0, a, 0);
        }
        MeshView view = new MeshView(mesh);
        view.setCullFace(CullFace.NONE);
        view.setMaterial(material);
        view.setTranslateX(x);
        view.setTranslateY(y);
        view.setTranslateZ(z);
        return view;
    }

    private static Box box(double w, double h, double d, double x, double y, double z, PhongMaterial material) {
        Box box = new Box(w, h, d);
        box.setTranslateX(x);
        box.setTranslateY(y);
        box.setTranslateZ(z);
        box.setMaterial(material);
        return box;
    }

    private static Cylinder cylinderY(double radius, double height, double x, double y, double z, PhongMaterial material, int div) {
        Cylinder cylinder = new Cylinder(radius, height, Math.max(12, div));
        cylinder.setTranslateX(x);
        cylinder.setTranslateY(y);
        cylinder.setTranslateZ(z);
        cylinder.setMaterial(material);
        return cylinder;
    }

    private static Cylinder cylinderX(double radius, double length, double x, double y, double z, PhongMaterial material, int div) {
        Cylinder cylinder = cylinderY(radius, length, x, y, z, material, div);
        cylinder.getTransforms().add(new Rotate(90.0, Rotate.Z_AXIS));
        return cylinder;
    }

    private static Cylinder cylinderZ(double radius, double length, double x, double y, double z, PhongMaterial material, int div) {
        Cylinder cylinder = cylinderY(radius, length, x, y, z, material, div);
        cylinder.getTransforms().add(new Rotate(90.0, Rotate.X_AXIS));
        return cylinder;
    }

    private static Sphere sphere(double radius, double x, double y, double z, PhongMaterial material) {
        Sphere sphere = new Sphere(radius, 32);
        sphere.setTranslateX(x);
        sphere.setTranslateY(y);
        sphere.setTranslateZ(z);
        sphere.setMaterial(material);
        return sphere;
    }

    private static double[] p(double x, double y) {
        return new double[]{x, y};
    }

    private static ToolTypeEntry entryFor(CncToolDefinition tool) {
        if (tool == null) {
            return ToolTypeCatalog.defaultTurning();
        }
        ToolTypeEntry entry = ToolTypeCatalog.findByCode(tool.getTypeCode());
        if (entry != null) {
            return entry;
        }
        for (ToolTypeEntry candidate : ToolTypeCatalog.all()) {
            if (candidate.identifier().equalsIgnoreCase(tool.getTypeIdentifier())) {
                return candidate;
            }
        }
        return null;
    }

    private static int typeCode(CncToolDefinition tool) {
        return tool != null && tool.getTypeCode() > 0 ? tool.getTypeCode() : 500;
    }

    private static double axialSide(CncToolDefinition tool, GCodeMoveData move) {
        int position = Math.max(1, Math.min(9, tool != null ? tool.getToolPosition() : 1));
        if (position == 2 || position == 3 || position == 7) {
            return 1.0;
        }
        if (position == 1 || position == 4 || position == 8) {
            return -1.0;
        }
        if (move != null && Math.abs(move.endZ() - move.startZ()) > 0.0001) {
            return move.endZ() >= move.startZ() ? -1.0 : 1.0;
        }
        return position == 6 ? 1.0 : -1.0;
    }

    private static double radius(CncToolDefinition tool, double fallback) {
        return tool != null && tool.getRadius() > 0.0 ? tool.getRadius() : fallback;
    }

    private static double plateLength(CncToolDefinition tool, double fallback) {
        return tool != null && tool.getPlateLength() > 0.0 ? tool.getPlateLength() : fallback;
    }

    private static double cutWidth(CncToolDefinition tool, double fallback) {
        return tool != null && tool.getCutWidth() > 0.0 ? tool.getCutWidth() : fallback;
    }

    private static double lengthZ(CncToolDefinition tool, double fallback) {
        return tool != null && tool.getLengthZ() > 0.0 ? Math.min(160.0, tool.getLengthZ()) : fallback;
    }

    private static PhongMaterial material(String diffuse, String specular, double power) {
        PhongMaterial material = new PhongMaterial(Color.web(diffuse));
        material.setSpecularColor(Color.web(specular, 0.65));
        material.setSpecularPower(power);
        return material;
    }
}
