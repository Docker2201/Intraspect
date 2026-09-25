package com.sergey.pisarev.model;

/**
 * Тип инструмента SINUMERIK (как в диалоге Tool types).
 */
public record ToolTypeEntry(
        int typeCode,
        String identifier,
        ToolTypeCategory category,
        int positionVariants
) {
    public String displayType() {
        return this.typeCode + " — " + this.identifier;
    }
}
