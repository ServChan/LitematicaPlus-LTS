package org.lts.litematicaplus.schematic;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class SchematicRegion {
    private final String name;
    private final int posX;
    private final int posY;
    private final int posZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int absX;
    private final int absY;
    private final int absZ;
    private final BlockState[] palette;
    private final BitPackedArray states;
    private final Long2ObjectMap<CompoundTag> blockEntities;
    private final List<CompoundTag> entities;
    private final ListTag pendingBlockTicks;
    private final ListTag pendingFluidTicks;

    public SchematicRegion(String name, int posX, int posY, int posZ, int sizeX, int sizeY, int sizeZ,
            BlockState[] palette, BitPackedArray states, Long2ObjectMap<CompoundTag> blockEntities,
            List<CompoundTag> entities, ListTag pendingBlockTicks, ListTag pendingFluidTicks) {
        if (sizeX == 0 || sizeY == 0 || sizeZ == 0) {
            throw new IllegalArgumentException("Region '" + name + "' has an empty axis");
        }
        this.name = name;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.absX = Math.abs(sizeX);
        this.absY = Math.abs(sizeY);
        this.absZ = Math.abs(sizeZ);
        this.palette = palette;
        this.states = states;
        this.blockEntities = blockEntities == null ? new Long2ObjectOpenHashMap<>() : blockEntities;
        this.entities = entities == null ? new ArrayList<>() : entities;
        this.pendingBlockTicks = pendingBlockTicks == null ? new ListTag() : pendingBlockTicks;
        this.pendingFluidTicks = pendingFluidTicks == null ? new ListTag() : pendingFluidTicks;
        long volume = (long) this.absX * this.absY * this.absZ;
        if (states.size() != volume) {
            throw new IllegalArgumentException("Region '" + name + "' state array does not match its volume");
        }
    }

    public static long localKey(int x, int y, int z) {
        return (long) y << 42 | (long) z << 21 | x;
    }

    public static int localKeyX(long key) {
        return (int) (key & 0x1FFFFFL);
    }

    public static int localKeyZ(long key) {
        return (int) (key >>> 21 & 0x1FFFFFL);
    }

    public static int localKeyY(long key) {
        return (int) (key >>> 42 & 0x1FFFFFL);
    }

    public String name() {
        return this.name;
    }

    public int posX() {
        return this.posX;
    }

    public int posY() {
        return this.posY;
    }

    public int posZ() {
        return this.posZ;
    }

    public int sizeX() {
        return this.sizeX;
    }

    public int sizeY() {
        return this.sizeY;
    }

    public int sizeZ() {
        return this.sizeZ;
    }

    public int absX() {
        return this.absX;
    }

    public int absY() {
        return this.absY;
    }

    public int absZ() {
        return this.absZ;
    }

    public int minX() {
        return this.sizeX < 0 ? this.posX + this.sizeX + 1 : this.posX;
    }

    public int minY() {
        return this.sizeY < 0 ? this.posY + this.sizeY + 1 : this.posY;
    }

    public int minZ() {
        return this.sizeZ < 0 ? this.posZ + this.sizeZ + 1 : this.posZ;
    }

    public long volume() {
        return (long) this.absX * this.absY * this.absZ;
    }

    public BlockState[] palette() {
        return this.palette;
    }

    public BitPackedArray states() {
        return this.states;
    }

    public long index(int x, int y, int z) {
        return ((long) y * this.absZ + z) * this.absX + x;
    }

    public int paletteIndex(int x, int y, int z) {
        return this.states.get(index(x, y, z));
    }

    public BlockState state(int x, int y, int z) {
        int id = this.states.get(index(x, y, z));
        return id >= 0 && id < this.palette.length ? this.palette[id] : Blocks.AIR.defaultBlockState();
    }

    public Long2ObjectMap<CompoundTag> blockEntities() {
        return this.blockEntities;
    }

    public List<CompoundTag> entities() {
        return this.entities;
    }

    public ListTag pendingBlockTicks() {
        return this.pendingBlockTicks;
    }

    public ListTag pendingFluidTicks() {
        return this.pendingFluidTicks;
    }

    public long countNonAir() {
        boolean[] air = new boolean[this.palette.length];
        for (int i = 0; i < this.palette.length; i++) {
            air[i] = this.palette[i].isAir();
        }
        long volume = volume();
        long count = 0L;
        for (long i = 0L; i < volume; i++) {
            int id = this.states.get(i);
            if (id < air.length && !air[id]) {
                count++;
            }
        }
        return count;
    }
}
