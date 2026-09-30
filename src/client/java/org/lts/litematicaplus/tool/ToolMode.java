package org.lts.litematicaplus.tool;

public enum ToolMode {
    AREA_SELECTION("area_selection"),
    PLACEMENT("placement"),
    PASTE("paste"),
    DELETE("delete");

    private final String key;

    ToolMode(String key) {
        this.key = key;
    }

    public String translationKey() {
        return "litematicaplus.tool.mode." + this.key;
    }

    public ToolMode cycle(boolean forward) {
        ToolMode[] values = values();
        int next = (ordinal() + (forward ? 1 : values.length - 1)) % values.length;
        return values[next];
    }
}
