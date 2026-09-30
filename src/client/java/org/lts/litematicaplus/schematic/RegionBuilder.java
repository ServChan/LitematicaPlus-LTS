package org.lts.litematicaplus.schematic;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class RegionBuilder {
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
        if (this.volume > LitematicCodec.MAX_REGION_VOLUME) {
            throw new IllegalArgumentException("Region is too large: " + this.volume + " blocks");
        }
        this.lookup.defaultReturnValue(-1);
        BlockState air = Blocks.AIR.defaultBlockState();
        this.palette.add(air);
        this.lookup.put(air, 0);
        this.lastState = air;
        this.lastIndex = 0;
        this.states = new BitPackedArray(2, this.volume);
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
                int bits = BitPackedArray.bitsFor(this.palette.size());
                if (bits > this.states.bits()) {
                    grow(bits);
                }
            }
            this.lastState = state;
            this.lastIndex = index;
        }
        if (index != 0) {
            this.states.set(((long) y * this.sizeZ + z) * this.sizeX + x, index);
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
                this.palette.toArray(BlockState[]::new), this.states, this.blockEntities, this.entities, null, null);
    }

    private void grow(int bits) {
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
