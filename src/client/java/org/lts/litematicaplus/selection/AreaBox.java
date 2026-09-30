package org.lts.litematicaplus.selection;

import net.minecraft.core.BlockPos;

public final class AreaBox {
    private String name;
    private BlockPos corner1;
    private BlockPos corner2;

    public AreaBox(String name, BlockPos corner1, BlockPos corner2) {
        this.name = name == null ? "" : name;
        this.corner1 = corner1;
        this.corner2 = corner2;
    }

    public String name() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public BlockPos corner1() {
        return this.corner1;
    }

    public BlockPos corner2() {
        return this.corner2;
    }

    public void setCorner1(BlockPos pos) {
        this.corner1 = pos == null ? null : pos.immutable();
    }

    public void setCorner2(BlockPos pos) {
        this.corner2 = pos == null ? null : pos.immutable();
    }

    public boolean isEmpty() {
        return this.corner1 == null && this.corner2 == null;
    }

    public boolean isComplete() {
        return this.corner1 != null && this.corner2 != null;
    }

    private BlockPos first() {
        return this.corner1 != null ? this.corner1 : this.corner2;
    }

    private BlockPos second() {
        return this.corner2 != null ? this.corner2 : this.corner1;
    }

    public int minX() {
        return Math.min(first().getX(), second().getX());
    }

    public int minY() {
        return Math.min(first().getY(), second().getY());
    }

    public int minZ() {
        return Math.min(first().getZ(), second().getZ());
    }

    public int maxX() {
        return Math.max(first().getX(), second().getX());
    }

    public int maxY() {
        return Math.max(first().getY(), second().getY());
    }

    public int maxZ() {
        return Math.max(first().getZ(), second().getZ());
    }

    public int sizeX() {
        return maxX() - minX() + 1;
    }

    public int sizeY() {
        return maxY() - minY() + 1;
    }

    public int sizeZ() {
        return maxZ() - minZ() + 1;
    }

    public long volume() {
        return isEmpty() ? 0L : (long) sizeX() * sizeY() * sizeZ();
    }}
