package com.sergey.pisarev.model;

import com.sergey.pisarev.util.I18n;

/**
 * Категории инструментов SinuTrain (диапазоны номеров типа).
 */
public enum ToolTypeCategory {
    MILLING(I18n.text("toolcat.001"), 100, 199),
    DRILL(I18n.text("toolcat.002"), 200, 299),
    TURNING(I18n.text("toolcat.003"), 500, 599),
    SPECIAL(I18n.text("toolcat.004"), 700, 999);

    private final String title;
    private final int minCode;
    private final int maxCode;

    ToolTypeCategory(String title, int minCode, int maxCode) {
        this.title = title;
        this.minCode = minCode;
        this.maxCode = maxCode;
    }

    public String getTitle() {
        return this.title;
    }

    public boolean contains(int typeCode) {
        return typeCode >= this.minCode && typeCode <= this.maxCode;
    }
}
