package com.sergey.pisarev.model;

import java.util.List;
import java.util.Map;

/**
 * Данные для 3D-симуляции снятия материала.
 */
public final class SimulationContext {
    private final List<GCodeMoveData> moves;
    /** Контур программы (X = готовая поверхность) для 3D-снятия; траектория Tool Path — {@link #moves}. */
    private final List<GCodeMoveData> contourMoves;
    private final MachineConfiguration machineConfiguration;
    private final WorkpieceDefinition workpiece;
    private final List<CncToolDefinition> tools;
    private final int feedOverridePercent;
    private final int spindleOverridePercent;
    private final String programText;
    private final boolean workpieceFromProgram;
    private final int activeWorkOffsetCode;
    private final Map<Integer, WorkOffsetValues> workOffsets;
    private final SimulationContext precedingSetup;
    private final double turnoverZSum;
    private final List<double[]> initialStockSection;
    private final double preformAllowanceMm;

    public SimulationContext(
            List<GCodeMoveData> moves,
            MachineConfiguration machineConfiguration,
            WorkpieceDefinition workpiece,
            List<CncToolDefinition> tools,
            int feedOverridePercent,
            int spindleOverridePercent,
            String programText,
            boolean workpieceFromProgram
    ) {
        this(moves, moves, machineConfiguration, workpiece, tools, feedOverridePercent, spindleOverridePercent,
                programText, workpieceFromProgram, -1, Map.of());
    }

    public SimulationContext(
            List<GCodeMoveData> moves,
            MachineConfiguration machineConfiguration,
            WorkpieceDefinition workpiece,
            List<CncToolDefinition> tools,
            int feedOverridePercent,
            int spindleOverridePercent,
            String programText,
            boolean workpieceFromProgram,
            int activeWorkOffsetCode,
            Map<Integer, WorkOffsetValues> workOffsets
    ) {
        this(moves, moves, machineConfiguration, workpiece, tools, feedOverridePercent, spindleOverridePercent,
                programText, workpieceFromProgram, activeWorkOffsetCode, workOffsets);
    }

    public SimulationContext(
            List<GCodeMoveData> moves,
            List<GCodeMoveData> contourMoves,
            MachineConfiguration machineConfiguration,
            WorkpieceDefinition workpiece,
            List<CncToolDefinition> tools,
            int feedOverridePercent,
            int spindleOverridePercent,
            String programText,
            boolean workpieceFromProgram,
            int activeWorkOffsetCode,
            Map<Integer, WorkOffsetValues> workOffsets
    ) {
        this(moves, contourMoves, machineConfiguration, workpiece, tools, feedOverridePercent,
                spindleOverridePercent, programText, workpieceFromProgram, activeWorkOffsetCode,
                workOffsets, null, 0);
    }

    private SimulationContext(List<GCodeMoveData> moves, List<GCodeMoveData> contourMoves,
            MachineConfiguration machineConfiguration, WorkpieceDefinition workpiece,
            List<CncToolDefinition> tools, int feedOverridePercent, int spindleOverridePercent,
            String programText, boolean workpieceFromProgram, int activeWorkOffsetCode,
            Map<Integer, WorkOffsetValues> workOffsets, SimulationContext precedingSetup, double turnoverZSum) {
        this(moves,contourMoves,machineConfiguration,workpiece,tools,feedOverridePercent,spindleOverridePercent,
                programText,workpieceFromProgram,activeWorkOffsetCode,workOffsets,precedingSetup,turnoverZSum,List.of(),0);
    }

    private SimulationContext(List<GCodeMoveData> moves, List<GCodeMoveData> contourMoves,
            MachineConfiguration machineConfiguration, WorkpieceDefinition workpiece,
            List<CncToolDefinition> tools, int feedOverridePercent, int spindleOverridePercent,
            String programText, boolean workpieceFromProgram, int activeWorkOffsetCode,
            Map<Integer, WorkOffsetValues> workOffsets, SimulationContext precedingSetup, double turnoverZSum,
            List<double[]> initialStockSection, double preformAllowanceMm) {
        this.initialStockSection=initialStockSection.stream().map(double[]::clone).toList();
        this.preformAllowanceMm=preformAllowanceMm;
        this.moves = List.copyOf(moves);
        this.contourMoves = List.copyOf(contourMoves != null && !contourMoves.isEmpty() ? contourMoves : moves);
        this.machineConfiguration = machineConfiguration;
        this.workpiece = workpiece;
        this.tools = List.copyOf(tools);
        this.feedOverridePercent = feedOverridePercent;
        this.spindleOverridePercent = spindleOverridePercent;
        this.programText = programText != null ? programText : "";
        this.workpieceFromProgram = workpieceFromProgram;
        this.activeWorkOffsetCode = activeWorkOffsetCode;
        this.workOffsets = workOffsets != null ? Map.copyOf(workOffsets) : Map.of();
        this.precedingSetup = precedingSetup;
        this.turnoverZSum = turnoverZSum;
    }

    /** The second setup starts with the actual stock left by the first setup. */
    public SimulationContext afterTurnover(SimulationContext first, double zSum) {
        if (first == null || !Double.isFinite(zSum) || first.getWorkpiece() == null
                || !first.getWorkpiece().isValid()) throw new IllegalArgumentException("Invalid turnover setup");
        WorkpieceDefinition a = first.getWorkpiece();
        var flipped = a.withZRange(zSum-a.getZMax(), zSum-a.getZMin(), a.getLengthMm());
        return new SimulationContext(moves, contourMoves, machineConfiguration, flipped, tools,
                feedOverridePercent, spindleOverridePercent, programText, true, activeWorkOffsetCode,
                workOffsets, first, zSum, List.of(), first.preformAllowanceMm);
    }

    public SimulationContext withWorkpiece(WorkpieceDefinition stock) {
        return new SimulationContext(moves, contourMoves, machineConfiguration, stock, tools,
                feedOverridePercent, spindleOverridePercent, programText, true, activeWorkOffsetCode,
                workOffsets, precedingSetup, turnoverZSum, initialStockSection, preformAllowanceMm);
    }

    public SimulationContext withInitialStockSection(List<double[]> section, double allowance) {
        return new SimulationContext(moves,contourMoves,machineConfiguration,workpiece,tools,
                feedOverridePercent,spindleOverridePercent,programText,workpieceFromProgram,activeWorkOffsetCode,
                workOffsets,precedingSetup,turnoverZSum,section,allowance);
    }
    public List<double[]> getInitialStockSection() { return initialStockSection.stream().map(double[]::clone).toList(); }
    public double getPreformAllowanceMm() { return preformAllowanceMm; }

    public SimulationContext getPrecedingSetup() { return precedingSetup; }
    public double getTurnoverZSum() { return turnoverZSum; }

    public int getActiveWorkOffsetCode() {
        return this.activeWorkOffsetCode;
    }

    public Map<Integer, WorkOffsetValues> getWorkOffsets() {
        return this.workOffsets;
    }

    public List<GCodeMoveData> getMoves() {
        return this.moves;
    }

    public List<GCodeMoveData> getContourMoves() {
        return this.contourMoves;
    }

    public MachineConfiguration getMachineConfiguration() {
        return this.machineConfiguration;
    }

    public WorkpieceDefinition getWorkpiece() {
        return this.workpiece;
    }

    public List<CncToolDefinition> getTools() {
        return this.tools;
    }

    public int getFeedOverridePercent() {
        return this.feedOverridePercent;
    }

    public int getSpindleOverridePercent() {
        return this.spindleOverridePercent;
    }

    public double getFeedRateMultiplier() {
        return Math.max(0.0, this.feedOverridePercent) / 100.0;
    }

    public double getSpindleSpeedMultiplier() {
        return Math.max(0.0, this.spindleOverridePercent) / 100.0;
    }

    public String getProgramText() {
        return this.programText;
    }

    public boolean isWorkpieceFromProgram() {
        return this.workpieceFromProgram;
    }

    public double toolRadiusForNumber(int toolNumber) {
        if (toolNumber <= 0) {
            return 0.0;
        }
        for (CncToolDefinition tool : this.tools) {
            if (tool.getToolNumber() == toolNumber) {
                return Math.max(0.0, tool.getRadius());
            }
        }
        return 0.0;
    }
}
