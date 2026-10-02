package org.lts.litematicaplus.schematic;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class RegionBuilder {
    static final long STAGED_VOLUME_LIMIT = 1L << 24;
    private static final int SHORT_PALETTE_LIMIT = 1 << 16;

    private final String name;
    private final int posX;
    private final int posY;
    private final int posZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final long volume;
    private final List<BlockState> palette = new ArrayList<>();
    private final Reference2IntMap<BlockState> lookup = new Reference2IntOpenHashMap<>();
    private final Long2ObjectMap<CompoundTag> blockEntities = new Long2ObjectOpenHashMap<>();
    private final List<CompoundTag> entities = new ArrayList<>();
    private short[] stagedShort;
    private int[] stagedInt;
    private BitPackedArray states;
    private BlockState lastState;
    private int lastIndex;

    public RegionBuilder(String name, int posX, int posY, int posZ, int sizeX, int sizeY, int sizeZ) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("Region size must be positive");
        }
        this.name = name;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.volume = (long) sizeX * sizeY * sizeZ;
        if (this.volume > LitematicCodec.MAX_REGION_VOLUME || LitematicCodec.tooLong(sizeX, sizeY, sizeZ)) {
            throw new IllegalArgumentException("Region is too large: " + this.volume + " blocks");
        }
        this.lookup.defaultReturnValue(-1);
        BlockState air = Blocks.AIR.defaultBlockState();
        this.palette.add(air);
        this.lookup.put(air, 0);
        this.lastState = air;
        this.lastIndex = 0;
        if (this.volume <= STAGED_VOLUME_LIMIT) {
            this.stagedShort = new short[(int) this.volume];
        } else {
            this.states = new BitPackedArray(2, this.volume);
        }
    }

    public void set(int x, int y, int z, BlockState state) {
        int index;
        if (state == this.lastState) {
            index = this.lastIndex;
        } else {
            index = this.lookup.getInt(state);
            if (index < 0) {
                index = this.palette.size();
                this.palette.add(state);
                this.lookup.put(state, index);
                widen();
            }
            this.lastState = state;
            this.lastIndex = index;
        }
        store(((long) y * this.sizeZ + z) * this.sizeX + x, index);
    }

    public void fill(BlockState state) {
        set(0, 0, 0, state);
        int index = this.lastIndex;
        if (this.stagedInt != null) {
            Arrays.fill(this.stagedInt, index);
        } else if (this.stagedShort != null) {
            Arrays.fill(this.stagedShort, (short) index);
        } else {
            for (long i = 1L; i < this.volume; i++) {
                this.states.set(i, index);
            }
        }
    }

    public void blockEntity(int x, int y, int z, CompoundTag tag) {
        this.blockEntities.put(SchematicRegion.localKey(x, y, z), tag);
    }

    public void entity(CompoundTag tag) {
        this.entities.add(tag);
    }

    public SchematicRegion build() {
        return new SchematicRegion(this.name, this.posX, this.posY, this.posZ, this.sizeX, this.sizeY, this.sizeZ,
                this.palette.toArray(BlockState[]::new), packed(), this.blockEntities, this.entities, null, null);
    }

    private void store(long index, int value) {
        if (this.stagedInt != null) {
            this.stagedInt[(int) index] = value;
        } else if (this.stagedShort != null) {
            this.stagedShort[(int) index] = (short) value;
        } else {
            this.states.set(index, value);
        }
    }

    private void widen() {
        if (this.stagedShort != null) {
            if (this.palette.size() > SHORT_PALETTE_LIMIT) {
                int[] wider = new int[this.stagedShort.length];
                for (int i = 0; i < wider.length; i++) {
                    wider[i] = Short.toUnsignedInt(this.stagedShort[i]);
                }
                this.stagedInt = wider;
                this.stagedShort = null;
            }
            return;
        }
        if (this.stagedInt != null) {
            return;
        }
        int bits = BitPackedArray.bitsFor(this.palette.size());
        if (bits > this.states.bits()) {
            BitPackedArray wider = new BitPackedArray(bits, this.volume);
            for (long i = 0L; i < this.volume; i++) {
                int value = this.states.get(i);
                if (value != 0) {
                    wider.set(i, value);
                }
            }
            this.states = wider;
        }
    }

    private BitPackedArray packed() {
        if (this.states != null) {
            return this.states;
        }
        BitPackedArray result = new BitPackedArray(BitPackedArray.bitsFor(this.palette.size()), this.volume);
        if (this.stagedInt != null) {
            for (int i = 0; i < this.stagedInt.length; i++) {
                int value = this.stagedInt[i];
                if (value != 0) {
                    result.set(i, value);
                }
            }
        } else {
            for (int i = 0; i < this.stagedShort.length; i++) {
                int value = Short.toUnsignedInt(this.stagedShort[i]);
                if (value != 0) {
                    result.set(i, value);
                }
            }
        }
        return result;
    }
}
