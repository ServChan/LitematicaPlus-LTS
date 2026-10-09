package org.lts.litematicaplus.tool;

public enum ToolMode {
    AREA_SELECTION("area_selection"),
    PLACEMENT("placement"),
    PASTE("paste"),
    FILL("fill"),
    REPLACE("replace"),
    DELETE("delete");

    private final String key;

    ToolMode(String key) {
        this.key = key;
    }

    public boolean editsArea() {
        return this == FILL || this == REPLACE || this == DELETE;
    }

    public boolean changesWorld() {
        return this == PASTE || editsArea();
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
