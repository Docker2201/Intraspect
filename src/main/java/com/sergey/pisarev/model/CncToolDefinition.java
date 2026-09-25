package com.sergey.pisarev.model;

/**
 * Инструмент для симуляции (аналог строки списка инструментов в SINUMERIK Operate).
 */
public final class CncToolDefinition {
    private int location;
    private String name;
    private String type;
    private int toolNumber;
    private int edgeNumber;
    private double lengthX;
    private double lengthZ;
    private double radius;
    private int typeCode;
    private String typeIdentifier;
    private int toolPosition;
    private ToolCutDirection cutDirection;
    private double holderAngleDeg;
    private double insertAngleDeg;
    private double plateLength;
    private double cutWidth;
    private String modelId = "auto";

    public String getModelId() { return this.modelId; }
    public void setModelId(String value) {
        String id=value==null||value.isBlank()?"auto":value.trim();
        if (!java.util.Set.of("auto","round_straight","round_cranked").contains(id))
            throw new IllegalArgumentException("Unknown library model: " + id);
        this.modelId=id;
    }

    public CncToolDefinition() {
        this.location = 1;
        this.name = "";
        this.type = "turning";
        this.toolNumber = 1;
        this.edgeNumber = 1;
        this.typeCode = 500;
        this.typeIdentifier = "Roughing tool";
        this.toolPosition = 1;
        this.cutDirection = ToolCutDirection.BOTH;
        this.holderAngleDeg = 0.0;
        this.insertAngleDeg = 0.0;
        this.plateLength = 0.0;
        this.cutWidth = 0.0;
    }

    public CncToolDefinition(
            int location,
            String name,
            String type,
            int toolNumber,
            int edgeNumber,
            double lengthX,
            double lengthZ,
            double radius
    ) {
        this(location, name, type, toolNumber, edgeNumber, lengthX, lengthZ, radius, 500, "Roughing tool", 1);
    }

    public CncToolDefinition(
            int location,
            String name,
            String type,
            int toolNumber,
            int edgeNumber,
            double lengthX,
            double lengthZ,
            double radius,
            int typeCode,
            String typeIdentifier,
            int toolPosition
    ) {
        this(location, name, type, toolNumber, edgeNumber, lengthX, lengthZ, radius,
                typeCode, typeIdentifier, toolPosition, ToolCutDirection.BOTH);
    }

    public CncToolDefinition(
            int location,
            String name,
            String type,
            int toolNumber,
            int edgeNumber,
            double lengthX,
            double lengthZ,
            double radius,
            int typeCode,
            String typeIdentifier,
            int toolPosition,
            ToolCutDirection cutDirection
    ) {
        this(location, name, type, toolNumber, edgeNumber, lengthX, lengthZ, radius,
                typeCode, typeIdentifier, toolPosition, cutDirection, 0.0, 0.0, 0.0, 0.0);
    }

    public CncToolDefinition(
            int location,
            String name,
            String type,
            int toolNumber,
            int edgeNumber,
            double lengthX,
            double lengthZ,
            double radius,
            int typeCode,
            String typeIdentifier,
            int toolPosition,
            ToolCutDirection cutDirection,
            double holderAngleDeg,
            double insertAngleDeg,
            double plateLength,
            double cutWidth
    ) {
        this.location = location;
        this.name = name != null ? name : "";
        this.type = type != null ? type : "turning";
        this.toolNumber = toolNumber;
        this.edgeNumber = edgeNumber;
        this.lengthX = lengthX;
        this.lengthZ = lengthZ;
        this.radius = radius;
        this.typeCode = typeCode;
        this.typeIdentifier = typeIdentifier != null ? typeIdentifier : "";
        this.toolPosition = Math.max(1, toolPosition);
        this.cutDirection = cutDirection != null ? cutDirection : ToolCutDirection.BOTH;
        this.holderAngleDeg = holderAngleDeg;
        this.insertAngleDeg = insertAngleDeg;
        this.plateLength = Math.max(0.0, plateLength);
        this.cutWidth = Math.max(0.0, cutWidth);
    }

    public int getLocation() {
        return this.location;
    }

    public void setLocation(int location) {
        this.location = location;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name != null ? name : "";
    }

    public String getType() {
        return this.type;
    }

    public void setType(String type) {
        this.type = type != null ? type : "";
    }

    public int getToolNumber() {
        return this.toolNumber;
    }

    public void setToolNumber(int toolNumber) {
        this.toolNumber = toolNumber;
    }

    public int getEdgeNumber() {
        return this.edgeNumber;
    }

    public void setEdgeNumber(int edgeNumber) {
        this.edgeNumber = edgeNumber;
    }

    public double getLengthX() {
        return this.lengthX;
    }

    public void setLengthX(double lengthX) {
        this.lengthX = lengthX;
    }

    public double getLengthZ() {
        return this.lengthZ;
    }

    public void setLengthZ(double lengthZ) {
        this.lengthZ = lengthZ;
    }

    public double getRadius() {
        return this.radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public int getTypeCode() {
        return this.typeCode;
    }

    public void setTypeCode(int typeCode) {
        this.typeCode = typeCode;
    }

    public String getTypeIdentifier() {
        return this.typeIdentifier;
    }

    public void setTypeIdentifier(String typeIdentifier) {
        this.typeIdentifier = typeIdentifier != null ? typeIdentifier : "";
    }

    public int getToolPosition() {
        return this.toolPosition;
    }

    public void setToolPosition(int toolPosition) {
        this.toolPosition = Math.max(1, toolPosition);
    }

    public ToolCutDirection getCutDirection() {
        return this.cutDirection;
    }

    public void setCutDirection(ToolCutDirection cutDirection) {
        this.cutDirection = cutDirection != null ? cutDirection : ToolCutDirection.BOTH;
    }

    public double getHolderAngleDeg() {
        return this.holderAngleDeg;
    }

    public void setHolderAngleDeg(double holderAngleDeg) {
        this.holderAngleDeg = holderAngleDeg;
    }

    public double getInsertAngleDeg() {
        return this.insertAngleDeg;
    }

    public void setInsertAngleDeg(double insertAngleDeg) {
        this.insertAngleDeg = insertAngleDeg;
    }

    public double getPlateLength() {
        return this.plateLength;
    }

    public void setPlateLength(double plateLength) {
        this.plateLength = Math.max(0.0, plateLength);
    }

    public double getCutWidth() {
        return this.cutWidth;
    }

    public void setCutWidth(double cutWidth) {
        this.cutWidth = Math.max(0.0, cutWidth);
    }

    public String displayType() {
        ToolTypeEntry entry = ToolTypeCatalog.findByCode(this.typeCode);
        String label = entry != null
                ? ToolTypeCatalog.russianName(entry)
                : (this.typeIdentifier != null ? this.typeIdentifier : "");
        return this.typeCode + " \u2014 " + label;
    }
}
