package org.lts.litematicaplus.world;

import net.minecraft.core.Direction;

public record LayerRange(Mode mode, Direction.Axis axis, int single, int min, int max) {
    public static final LayerRange ALL = new LayerRange(Mode.ALL, Direction.Axis.Y, 0, 0, 0);

    public enum Mode {
        ALL,
        SINGLE,
        ALL_BELOW,
        ALL_ABOVE,
        RANGE;

        public Mode next() {
            Mode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public LayerRange {
        mode = mode == null ? Mode.ALL : mode;
        axis = axis == null ? Direction.Axis.Y : axis;
        if (min > max) {
            int swap = min;
            min = max;
            max = swap;
        }
    }

    public boolean isAll() {
        return this.mode == Mode.ALL;
    }

    public boolean contains(int x, int y, int z) {
        if (this.mode == Mode.ALL) {
            return true;
        }
        int value = switch (this.axis) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
        return containsValue(value);
    }

    public boolean containsValue(int value) {
        return switch (this.mode) {
            case ALL -> true;
            case SINGLE -> value == this.single;
            case ALL_BELOW -> value <= this.single;
            case ALL_ABOVE -> value >= this.single;
            case RANGE -> value >= this.min && value <= this.max;
        };
    }

    public boolean intersects(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (this.mode == Mode.ALL) {
            return true;
        }
        int low = switch (this.axis) {
            case X -> minX;
            case Y -> minY;
            case Z -> minZ;
        };
        int high = switch (this.axis) {
            case X -> maxX;
            case Y -> maxY;
            case Z -> maxZ;
        };
        return switch (this.mode) {
            case ALL -> true;
            case SINGLE -> this.single >= low && this.single <= high;
            case ALL_BELOW -> low <= this.single;
            case ALL_ABOVE -> high >= this.single;
            case RANGE -> high >= this.min && low <= this.max;
        };
    }

    public LayerRange withMode(Mode newMode) {
        return new LayerRange(newMode, this.axis, this.single, this.min, this.max);
    }

    public LayerRange withAxis(Direction.Axis newAxis) {
        return new LayerRange(this.mode, newAxis, this.single, this.min, this.max);
    }

    public LayerRange withSingle(int value) {
        return new LayerRange(this.mode, this.axis, value, this.min, this.max);
    }

    public LayerRange withRange(int newMin, int newMax) {
        return new LayerRange(this.mode, this.axis, this.single, newMin, newMax);
    }

    public LayerRange shifted(int delta) {
        return new LayerRange(this.mode, this.axis, this.single + delta, this.min + delta, this.max + delta);
    }
}
