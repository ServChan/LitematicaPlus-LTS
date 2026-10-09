package org.lts.litematicaplus.placement;

public record PlacementGrid(int negativeX, int positiveX, int negativeY, int positiveY, int negativeZ, int positiveZ,
        int stepX, int stepY, int stepZ) {
    public static final int MAX_REPEAT = 32;
    public static final int MAX_STEP = 4096;
    public static final int MAX_COPIES = 256;
    public static final PlacementGrid NONE = new PlacementGrid(0, 0, 0, 0, 0, 0, 0, 0, 0);

    public PlacementGrid {
        negativeX = clamp(negativeX, MAX_REPEAT);
        positiveX = clamp(positiveX, MAX_REPEAT);
        negativeY = clamp(negativeY, MAX_REPEAT);
        positiveY = clamp(positiveY, MAX_REPEAT);
        negativeZ = clamp(negativeZ, MAX_REPEAT);
        positiveZ = clamp(positiveZ, MAX_REPEAT);
        stepX = clamp(stepX, MAX_STEP);
        stepY = clamp(stepY, MAX_STEP);
        stepZ = clamp(stepZ, MAX_STEP);
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(max, value));
    }

    public boolean active() {
        return copies() > 1;
    }

    public long copies() {
        return (long) (this.negativeX + this.positiveX + 1) * (this.negativeY + this.positiveY + 1)
                * (this.negativeZ + this.positiveZ + 1);
    }

    public int repeat(int axis, boolean positive) {
        return switch (axis) {
            case 0 -> positive ? this.positiveX : this.negativeX;
            case 1 -> positive ? this.positiveY : this.negativeY;
            default -> positive ? this.positiveZ : this.negativeZ;
        };
    }

    public int step(int axis) {
        return switch (axis) {
            case 0 -> this.stepX;
            case 1 -> this.stepY;
            default -> this.stepZ;
        };
    }

    public PlacementGrid withRepeat(int axis, boolean positive, int value) {
        int[] values = {this.negativeX, this.positiveX, this.negativeY, this.positiveY, this.negativeZ, this.positiveZ};
        values[axis * 2 + (positive ? 1 : 0)] = value;
        return new PlacementGrid(values[0], values[1], values[2], values[3], values[4], values[5], this.stepX,
                this.stepY, this.stepZ);
    }

    public PlacementGrid withStep(int axis, int value) {
        return new PlacementGrid(this.negativeX, this.positiveX, this.negativeY, this.positiveY, this.negativeZ,
                this.positiveZ, axis == 0 ? value : this.stepX, axis == 1 ? value : this.stepY,
                axis == 2 ? value : this.stepZ);
    }
}
