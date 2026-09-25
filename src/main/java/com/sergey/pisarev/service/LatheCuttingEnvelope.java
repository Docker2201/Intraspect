package com.sergey.pisarev.service;

import com.sergey.pisarev.model.CncToolDefinition;
import com.sergey.pisarev.model.SimulationContext;
import com.sergey.pisarev.model.ToolCutDirection;
import com.sergey.pisarev.model.ToolTypeCatalog;
import com.sergey.pisarev.service.LatheCompensationProcessor.CompensationMode;
import java.util.List;
import java.util.TreeMap;

/**
 * Кэш G41/G42, T/D и радиуса инструмента для расчёта снятия (один раз на программу).
 */
public final class LatheCuttingEnvelope {
    private final List<CncToolDefinition> tools;
    private final TreeMap<Integer, CompensationMode> modeByLine;
    private final TreeMap<Integer, Integer> toolByLine;
    private final TreeMap<Integer, Integer> dByLine;

    public LatheCuttingEnvelope(SimulationContext context) {
        String program = context != null ? context.getProgramText() : "";
        this.tools = context != null ? context.getTools() : List.of();
        this.modeByLine = new TreeMap<>(LatheCompensationProcessor.scanCompensationByLine(program));
        this.toolByLine = new TreeMap<>(GCodeProgramParser.parseToolNumberByLine(program));
        this.dByLine = new TreeMap<>(LatheCompensationProcessor.parseDNumberByLine(program));
    }

    /**
     * Радиус готовой поверхности после снятия с учётом T/D и G41/G42.
     */
    public double removalRadiusAt(int sourceLine, double xDiameterMm, double stockRadiusMm) {
        double programmed = LatheMeshBuilder.toRadius(xDiameterMm);
        CompensationMode mode = this.modeAt(sourceLine);
        double toolRadius = this.toolRadiusMm(sourceLine);
        double surface = programmed;
        if (toolRadius > 0.0 && mode == CompensationMode.NONE) {
            // Turning coordinates describe the imaginary tip P, not the nose
            // centre S. For positions 3/4, S is R above P: a straight G40 pass
            // therefore leaves the programmed diameter, not diameter minus 2R.
            surface = Math.max(0.0, programmed + noseCenterRadialOffsetMm(sourceLine) - toolRadius);
        }
        return Math.min(stockRadiusMm, Math.max(0.0, surface));
    }

    /** Радиус траектории центра резца для 2D/оверлея (внешняя обработка, +X). */
    public double toolPathRadiusAt(int sourceLine, double xDiameterMm) {
        double programmed = LatheMeshBuilder.toRadius(xDiameterMm);
        double toolRadius = toolRadiusMm(sourceLine);
        CompensationMode mode = modeAt(sourceLine);
        if (toolRadius <= 0.0) {
            return programmed;
        }
        if (mode == CompensationMode.NONE) {
            return Math.max(0.0, programmed + noseCenterRadialOffsetMm(sourceLine));
        }
        // Внешняя обработка: центр резца снаружи контура (+R).
        if (mode == CompensationMode.G42) {
            return Math.max(0.0, programmed - toolRadius);
        }
        return programmed + toolRadius;
    }

    public boolean canCutMove(int sourceLine, double startX, double startZ, double endX, double endZ) {
        // Geometry-only callers without NC text explicitly use an ideal point.
        // In an NC program an empty tool station/unknown D cannot remove stock.
        if (!this.toolByLine.isEmpty() && this.activeToolAt(sourceLine) == null) {
            return false;
        }
        // A feed against the preferred direction still intersects the material.
        // Tool orientation is not a license to silently discard that NC move.
        return true;
    }

    public boolean isCompensated(int sourceLine) {
        return modeAt(sourceLine) != CompensationMode.NONE;
    }

    public double noseRadiusMm(int sourceLine) {
        return toolRadiusMm(sourceLine);
    }

    public double noseCenterZOffsetMm(int sourceLine) {
        CncToolDefinition tool = activeToolAt(sourceLine);
        return tool == null ? 0 : -cuttingEdgeVector(tool.getToolPosition())[0] * tool.getRadius();
    }

    public double noseCenterRadialOffsetMm(int sourceLine) {
        CncToolDefinition tool = activeToolAt(sourceLine);
        return tool == null ? 0 : -cuttingEdgeVector(tool.getToolPosition())[1] * tool.getRadius();
    }

    public double zContactHalfWidthMm(int sourceLine) {
        CncToolDefinition tool = this.activeToolAt(sourceLine);
        if (tool == null) {
            return 0.0;
        }
        if (ToolTypeCatalog.supportsCutWidth(tool) && tool.getCutWidth() > 0.0) {
            return Math.max(0.0, tool.getCutWidth() * 0.5);
        }
        double radius = Math.max(0.0, tool.getRadius());
        if (tool.getTypeCode() == 550) {
            return radius;
        }
        if (ToolTypeCatalog.supportsInsertGeometry(tool)) {
            return Math.min(radius, 1.0);
        }
        return 0.0;
    }

    public double zContactFallbackHalfWidthMm(int sourceLine) {
        CncToolDefinition tool = this.activeToolAt(sourceLine);
        if (tool == null) {
            return 0.0;
        }
        boolean insertTool = ToolTypeCatalog.supportsInsertGeometry(tool);
        boolean widthTool = ToolTypeCatalog.supportsCutWidth(tool) && tool.getCutWidth() > 0.0;
        if (!insertTool && !widthTool && tool.getTypeCode() != 550) {
            return 0.0;
        }
        double halfWidth = this.zContactHalfWidthMm(sourceLine);
        double noseRadius = Math.max(0.0, tool.getRadius());
        double cutWidthHalf = widthTool
                ? Math.max(0.0, tool.getCutWidth() * 0.5)
                : 0.0;
        double contact = Math.max(halfWidth, Math.max(noseRadius, cutWidthHalf));
        if (insertTool) {
            contact = Math.max(contact, 0.35);
        }
        return Math.max(0.0, Math.min(1.5, contact));
    }

    public double zShoulderContactHalfWidthMm(int sourceLine) {
        CncToolDefinition tool = this.activeToolAt(sourceLine);
        if (tool == null) {
            return 0.0;
        }
        boolean insertTool = ToolTypeCatalog.supportsInsertGeometry(tool);
        boolean widthTool = ToolTypeCatalog.supportsCutWidth(tool) && tool.getCutWidth() > 0.0;
        if (!insertTool && !widthTool && tool.getTypeCode() != 550) {
            return 0.0;
        }
        double noseRadius = Math.max(0.0, tool.getRadius());
        double cutWidthHalf = widthTool
                ? Math.max(0.0, tool.getCutWidth() * 0.5)
                : 0.0;
        double plateShoulder = insertTool
                ? Math.max(0.0, tool.getPlateLength()) * 0.12
                : 0.0;
        double contact = Math.max(
                this.zContactFallbackHalfWidthMm(sourceLine),
                Math.max(noseRadius * 2.0, Math.max(cutWidthHalf, plateShoulder)));
        if (tool.getTypeCode() == 550) {
            contact = Math.max(contact, noseRadius * 2.5);
        }
        if (insertTool) {
            contact = Math.max(contact, 0.75);
        }
        return Math.max(0.0, Math.min(3.0, contact));
    }

    public double zContactMinOffsetMm(int sourceLine) {
        double halfWidth = this.zContactHalfWidthMm(sourceLine);
        if (halfWidth <= 0.01) {
            return 0.0;
        }
        double centerOffset = this.zContactCenterOffsetMm(sourceLine);
        return switch (this.toolDirection(sourceLine)) {
            case Z_MINUS -> centerOffset - halfWidth;
            case Z_PLUS -> centerOffset;
            default -> centerOffset - halfWidth;
        };
    }

    public double zContactMaxOffsetMm(int sourceLine) {
        double halfWidth = this.zContactHalfWidthMm(sourceLine);
        if (halfWidth <= 0.01) {
            return 0.0;
        }
        double centerOffset = this.zContactCenterOffsetMm(sourceLine);
        return switch (this.toolDirection(sourceLine)) {
            case Z_MINUS -> centerOffset;
            case Z_PLUS -> centerOffset + halfWidth;
            default -> centerOffset + halfWidth;
        };
    }

    private double zContactCenterOffsetMm(int sourceLine) {
        CncToolDefinition tool = this.activeToolAt(sourceLine);
        if (tool == null || !ToolTypeCatalog.supportsInsertGeometry(tool)) {
            return 0.0;
        }
        double radius = Math.max(0.0, tool.getRadius());
        if (radius <= 0.0) {
            return 0.0;
        }
        double[] edge = this.cuttingEdgeVector(tool.getToolPosition());
        return -edge[0] * radius;
    }

    private double[] cuttingEdgeVector(int position) {
        return switch (Math.max(1, Math.min(9, position))) {
            case 2 -> new double[]{-1.0, 1.0};
            case 3 -> new double[]{-1.0, -1.0};
            case 4 -> new double[]{1.0, -1.0};
            case 5 -> new double[]{0.0, 1.0};
            case 6 -> new double[]{0.0, -1.0};
            case 7 -> new double[]{-1.0, 0.0};
            case 8 -> new double[]{1.0, 0.0};
            case 9 -> new double[]{0.0, 0.0}; // SINUMERIK: reference at nose centre S.
            default -> new double[]{1.0, 1.0};
        };
    }

    private CompensationMode modeAt(int sourceLine) {
        var entry = this.modeByLine.floorEntry(sourceLine);
        return entry != null ? entry.getValue() : CompensationMode.NONE;
    }

    private double toolRadiusMm(int sourceLine) {
        if (this.tools.isEmpty()) {
            return 0.0;
        }
        return LatheCompensationProcessor.toolRadiusForLine(
                this.tools,
                this.toolByLine,
                this.dByLine,
                sourceLine);
    }

    public CncToolDefinition activeToolAt(int sourceLine) {
        if (this.tools.isEmpty()) {
            return null;
        }
        return LatheCompensationProcessor.toolForLine(
                this.tools,
                this.toolByLine,
                this.dByLine,
                sourceLine);
    }

    private ToolCutDirection toolDirection(int sourceLine) {
        CncToolDefinition tool = this.activeToolAt(sourceLine);
        return tool != null && ToolTypeCatalog.supportsCutDirection(tool) && tool.getCutDirection() != null
                ? tool.getCutDirection()
                : ToolCutDirection.BOTH;
    }
}
