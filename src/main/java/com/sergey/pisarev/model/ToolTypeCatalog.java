package com.sergey.pisarev.model;

import com.sergey.pisarev.util.I18n;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/**
 * Справочник типов инструментов SINUMERIK Operate / SinuTrain.
 */
public final class ToolTypeCatalog {
    private static final List<ToolTypeEntry> ENTRIES = List.of(
            entry(100, "Milling tool", ToolTypeCategory.MILLING, 4),
            entry(110, "Ball nose end mill", ToolTypeCategory.MILLING, 4),
            entry(111, "Conical ball end", ToolTypeCategory.MILLING, 4),
            entry(120, "End mill", ToolTypeCategory.MILLING, 4),
            entry(121, "End mill corner rounding", ToolTypeCategory.MILLING, 4),
            entry(130, "Angle head cutter", ToolTypeCategory.MILLING, 4),
            entry(131, "Corn.round.ang.hd.cut", ToolTypeCategory.MILLING, 4),
            entry(140, "Facing tool", ToolTypeCategory.MILLING, 4),
            entry(145, "Thread cutter", ToolTypeCategory.MILLING, 4),
            entry(150, "Side mill", ToolTypeCategory.MILLING, 4),
            entry(151, "Saw", ToolTypeCategory.MILLING, 4),
            entry(155, "Bevelled cutter", ToolTypeCategory.MILLING, 4),
            entry(156, "Bevelled cutter corner", ToolTypeCategory.MILLING, 4),
            entry(157, "Tap. die-sink. cutter", ToolTypeCategory.MILLING, 4),
            entry(160, "Drill & thread cut.", ToolTypeCategory.MILLING, 4),
            entry(200, "Twist drill", ToolTypeCategory.DRILL, 4),
            entry(205, "Solid drill", ToolTypeCategory.DRILL, 4),
            entry(210, "Boring bar", ToolTypeCategory.DRILL, 4),
            entry(220, "Center drill", ToolTypeCategory.DRILL, 4),
            entry(230, "Countersink", ToolTypeCategory.DRILL, 4),
            entry(231, "Counterbore", ToolTypeCategory.DRILL, 4),
            entry(240, "Tap", ToolTypeCategory.DRILL, 4),
            entry(241, "Fine tap", ToolTypeCategory.DRILL, 4),
            entry(242, "Whitworth tap", ToolTypeCategory.DRILL, 4),
            entry(250, "Reamer", ToolTypeCategory.DRILL, 4),
            entry(500, "Roughing tool", ToolTypeCategory.TURNING, 8),
            entry(510, "Finishing tool", ToolTypeCategory.TURNING, 8),
            entry(520, "Plunge cutter", ToolTypeCategory.TURNING, 8),
            entry(530, "Cutting tool", ToolTypeCategory.TURNING, 8),
            entry(540, "Threading tool", ToolTypeCategory.TURNING, 8),
            entry(550, "Button tool", ToolTypeCategory.TURNING, 9),
            entry(560, "Rotary drill", ToolTypeCategory.TURNING, 4),
            entry(580, "3D turning probe", ToolTypeCategory.TURNING, 4),
            entry(585, "Calibrating tool", ToolTypeCategory.TURNING, 4),
            entry(700, "Slotting saw", ToolTypeCategory.SPECIAL, 4),
            entry(710, "3D probe", ToolTypeCategory.SPECIAL, 4),
            entry(711, "Edge finder", ToolTypeCategory.SPECIAL, 4),
            entry(712, "Mono probe", ToolTypeCategory.SPECIAL, 4),
            entry(713, "L probe", ToolTypeCategory.SPECIAL, 4),
            entry(714, "Star probe", ToolTypeCategory.SPECIAL, 4),
            entry(725, "Calibrating tool", ToolTypeCategory.SPECIAL, 4),
            entry(730, "Stop", ToolTypeCategory.SPECIAL, 1),
            entry(731, "Mandrel", ToolTypeCategory.SPECIAL, 4),
            entry(732, "Steady rest", ToolTypeCategory.SPECIAL, 1),
            entry(900, "Auxiliary tools", ToolTypeCategory.SPECIAL, 4)
    );

    private ToolTypeCatalog() {
    }

    private static ToolTypeEntry entry(int code, String id, ToolTypeCategory category, int positions) {
        return new ToolTypeEntry(code, id, category, positions);
    }

    public static List<ToolTypeEntry> all() {
        return ENTRIES;
    }

    public static List<ToolTypeEntry> byCategory(ToolTypeCategory category) {
        ArrayList<ToolTypeEntry> list = new ArrayList<>();
        for (ToolTypeEntry entry : ENTRIES) {
            if (entry.category() == category) {
                list.add(entry);
            }
        }
        return list;
    }

    public static ToolTypeEntry findByCode(int typeCode) {
        for (ToolTypeEntry entry : ENTRIES) {
            if (entry.typeCode() == typeCode) {
                return entry;
            }
        }
        return null;
    }

    public static ToolTypeEntry defaultTurning() {
        return findByCode(500);
    }

    public static boolean supportsCutDirection(CncToolDefinition tool) {
        if (tool == null || isReferenceTool(tool)) {
            return false;
        }
        return switch (tool.getTypeCode()) {
            case 500, 510, 520, 530, 540, 550 -> true;
            default -> false;
        };
    }

    public static boolean supportsInsertGeometry(CncToolDefinition tool) {
        if (tool == null || isReferenceTool(tool)) {
            return false;
        }
        return switch (tool.getTypeCode()) {
            case 500, 510, 520, 530, 540, 550 -> true;
            default -> false;
        };
    }

    public static boolean supportsPlateLength(CncToolDefinition tool) {
        return supportsInsertGeometry(tool);
    }

    public static boolean supportsCutWidth(CncToolDefinition tool) {
        if (tool == null || isReferenceTool(tool)) {
            return false;
        }
        return switch (tool.getTypeCode()) {
            case 520, 530 -> true;
            default -> false;
        };
    }

    private static boolean isReferenceTool(CncToolDefinition tool) {
        String text = (tool.getName() + " " + tool.getTypeIdentifier()).toUpperCase(Locale.US);
        return text.contains("CENTRAL") || text.contains("CENTERING") || text.contains("REFERENCE");
    }

    public static String russianName(ToolTypeEntry entry) {
        if (entry == null) {
            return "";
        }
        int typeCode = entry.typeCode();
        switch (typeCode) {
            case 241:
                return I18n.text("tooltype.001");
            case 242:
                return I18n.text("tooltype.002");
            case 712:
                return I18n.text("tooltype.003");
            case 713:
                return I18n.text("tooltype.004");
            case 714:
                return I18n.text("tooltype.005");
            case 725:
                return I18n.text("tooltype.006");
            default:
                break;
        }
        return switch (typeCode) {
            case 100 -> I18n.text("tooltype.007");
            case 110 -> I18n.text("tooltype.008");
            case 111 -> I18n.text("tooltype.009");
            case 120 -> I18n.text("tooltype.010");
            case 121 -> I18n.text("tooltype.011");
            case 130 -> I18n.text("tooltype.012");
            case 131 -> I18n.text("tooltype.013");
            case 140 -> I18n.text("tooltype.014");
            case 145 -> I18n.text("tooltype.015");
            case 150 -> I18n.text("tooltype.016");
            case 151 -> I18n.text("tooltype.017");
            case 155 -> I18n.text("tooltype.018");
            case 156 -> I18n.text("tooltype.019");
            case 157 -> I18n.text("tooltype.020");
            case 160 -> I18n.text("tooltype.021");
            case 200 -> I18n.text("tooltype.022");
            case 205 -> I18n.text("tooltype.023");
            case 210 -> I18n.text("tooltype.024");
            case 220 -> I18n.text("tooltype.025");
            case 230 -> I18n.text("tooltype.026");
            case 231 -> I18n.text("tooltype.027");
            case 240 -> I18n.text("tooltype.028");
            case 241 -> I18n.text("tooltype.001");
            case 242 -> I18n.text("tooltype.002");
            case 250 -> I18n.text("tooltype.029");
            case 500 -> I18n.text("tooltype.030");
            case 510 -> I18n.text("tooltype.031");
            case 520 -> I18n.text("tooltype.032");
            case 530 -> I18n.text("tooltype.033");
            case 540 -> I18n.text("tooltype.034");
            case 550 -> I18n.text("tooltype.035");
            case 560 -> I18n.text("tooltype.036");
            case 580 -> I18n.text("tooltype.037");
            case 585 -> I18n.text("tooltype.006");
            case 700 -> I18n.text("tooltype.038");
            case 710 -> I18n.text("tooltype.039");
            case 711 -> I18n.text("tooltype.040");
            case 712 -> I18n.text("tooltype.003");
            case 713 -> I18n.text("tooltype.004");
            case 714 -> I18n.text("tooltype.005");
            case 725 -> I18n.text("tooltype.006");
            case 730 -> I18n.text("tooltype.041");
            case 731 -> I18n.text("tooltype.042");
            case 732 -> I18n.text("tooltype.043");
            case 900 -> I18n.text("tooltype.044");
            default -> entry.identifier();
        };
    }
}
