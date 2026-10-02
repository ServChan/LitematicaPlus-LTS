package org.lts.litematicaplus.world;

import net.minecraft.core.Direction;

public record LayerRange(Mode mode, Direction.Axis axis, int single, int min, int max, Clip clip) {
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

    public record Clip(boolean enabled, boolean xEnabled, int minX, int maxX, boolean zEnabled, int minZ, int maxZ) {
        public static final Clip NONE = new Clip(false, false, 0, 0, false, 0, 0);

        public Clip {
            if (minX > maxX) {
                int swap = minX;
                minX = maxX;
                maxX = swap;
            }
            if (minZ > maxZ) {
                int swap = minZ;
                minZ = maxZ;
                maxZ = swap;
            }
        }

        public boolean active() {
            return this.enabled && (this.xEnabled || this.zEnabled);
        }

        public boolean contains(int x, int z) {
            return !active() || (!this.xEnabled || x >= this.minX && x <= this.maxX)
                    && (!this.zEnabled || z >= this.minZ && z <= this.maxZ);
        }

        public boolean intersects(int lowX, int lowZ, int highX, int highZ) {
            return !active() || (!this.xEnabled || highX >= this.minX && lowX <= this.maxX)
                    && (!this.zEnabled || highZ >= this.minZ && lowZ <= this.maxZ);
        }

        public boolean encloses(int lowX, int lowZ, int highX, int highZ) {
            return !active() || (!this.xEnabled || lowX >= this.minX && highX <= this.maxX)
                    && (!this.zEnabled || lowZ >= this.minZ && highZ <= this.maxZ);
        }

        public Clip withEnabled(boolean value) {
            return new Clip(value, this.xEnabled, this.minX, this.maxX, this.zEnabled, this.minZ, this.maxZ);
        }

        public Clip withX(boolean on, int low, int high) {
            return new Clip(this.enabled, on, low, high, this.zEnabled, this.minZ, this.maxZ);
        }

        public Clip withZ(boolean on, int low, int high) {
            return new Clip(this.enabled, this.xEnabled, this.minX, this.maxX, on, low, high);
        }
    }

    public LayerRange {
        mode = mode == null ? Mode.ALL : mode;
        axis = axis == null ? Direction.Axis.Y : axis;
        clip = clip == null ? Clip.NONE : clip;
        if (min > max) {
            int swap = min;
            min = max;
            max = swap;
        }
    }

    public LayerRange(Mode mode, Direction.Axis axis, int single, int min, int max) {
        this(mode, axis, single, min, max, Clip.NONE);
    }

    public boolean isAll() {
        return this.mode == Mode.ALL;
    }

    public boolean unrestricted() {
        return this.mode == Mode.ALL && !this.clip.active();
    }

    public boolean contains(int x, int y, int z) {
        if (!this.clip.contains(x, z)) {
            return false;
        }
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
        if (!this.clip.intersects(minX, minZ, maxX, maxZ)) {
            return false;
        }
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

    public boolean encloses(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (!this.clip.encloses(minX, minZ, maxX, maxZ)) {
            return false;
        }
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
        return containsValue(low) && containsValue(high);
    }

    public LayerRange withMode(Mode newMode) {
        return new LayerRange(newMode, this.axis, this.single, this.min, this.max, this.clip);
    }

    public LayerRange withClip(Clip newClip) {
        return new LayerRange(this.mode, this.axis, this.single, this.min, this.max, newClip);
    }

    public LayerRange withBounds(int low, int high) {
        return new LayerRange(this.mode, this.axis, this.single, low, high, this.clip);
    }

    public LayerRange shifted(int delta) {
        return new LayerRange(this.mode, this.axis, this.single + delta, this.min + delta, this.max + delta, this.clip);
    }
}
