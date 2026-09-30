package org.lts.litematicaplus.verify;

public enum MismatchType {
    NONE(0, "none"),
    MISSING(0xFF2FC7FF, "missing"),
    EXTRA(0xFFFF4FD8, "extra"),
    WRONG_BLOCK(0xFFFF4444, "wrong_block"),
    WRONG_STATE(0xFFFFA62B, "wrong_state"),
    CORRECT(0xFF55FF55, "correct"),
    UNKNOWN(0xFF8FA7BF, "unknown");

    public static final MismatchType[] VALUES = values();

    private final int color;
    private final String key;

    MismatchType(int color, String key) {
        this.color = color;
        this.key = key;
    }

    public int textColor() {
        return switch (this) {
            case WRONG_BLOCK -> 0xFFFF9090;
            case EXTRA -> 0xFFFF8AE6;
            case UNKNOWN -> 0xFF9FB5CB;
            default -> this.color;
        };
    }

    public int color() {
        return this.color;
    }

    public String key() {
        return this.key;
    }

    public boolean isError() {
        return this == MISSING || this == EXTRA || this == WRONG_BLOCK || this == WRONG_STATE;
    }
}
