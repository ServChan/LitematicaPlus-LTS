package org.lts.litematicaplus.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.lts.litematicaplus.world.GhostSection;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.world.PlacementVolume;

final class GhostBlockView implements BlockAndTintGetter {
    private final PlacementVolume volume;
    private final LayerRange layer;
    private final ClientLevel level;
    private final BlockState[] palette;
    private final BlockState air = Blocks.AIR.defaultBlockState();
    private final GhostSection[] neighbors = new GhostSection[27];
    private final CardinalLighting cardinalLighting;
    private final int minY;
    private final int height;
    private int baseX;
    private int baseY;
    private int baseZ;

    GhostBlockView(PlacementVolume volume, LayerRange layer, ClientLevel level) {
        this.volume = volume;
        this.layer = layer;
        this.level = level;
        this.palette = volume.palette();
        this.cardinalLighting = level == null ? CardinalLighting.DEFAULT : level.cardinalLighting();
        this.minY = level == null ? -64 : level.getMinY();
        this.height = level == null ? 384 : level.getHeight();
    }

    void bind(int sectionX, int sectionY, int sectionZ) {
        this.baseX = sectionX - 1;
        this.baseY = sectionY - 1;
        this.baseZ = sectionZ - 1;
        for (int dy = 0; dy < 3; dy++) {
            for (int dz = 0; dz < 3; dz++) {
                for (int dx = 0; dx < 3; dx++) {
                    this.neighbors[(dy * 3 + dz) * 3 + dx] = this.volume.section(
                            SectionPos.asLong(this.baseX + dx, this.baseY + dy, this.baseZ + dz));
                }
            }
        }
    }

    BlockState stateAt(int x, int y, int z) {
        if (!this.layer.contains(x, y, z)) {
            return this.air;
        }
        int dx = (x >> 4) - this.baseX;
        int dy = (y >> 4) - this.baseY;
        int dz = (z >> 4) - this.baseZ;
        GhostSection section;
        if ((dx | dy | dz) >= 0 && dx < 3 && dy < 3 && dz < 3) {
            section = this.neighbors[(dy * 3 + dz) * 3 + dx];
        } else {
            section = this.volume.section(SectionPos.asLong(x >> 4, y >> 4, z >> 4));
        }
        if (section == null) {
            return this.air;
        }
        short value = section.get(x, y, z);
        return value == GhostSection.OUTSIDE ? this.air : this.palette[value & 0xFFFF];
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return stateAt(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return stateAt(pos.getX(), pos.getY(), pos.getZ()).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public CardinalLighting cardinalLighting() {
        return this.cardinalLighting;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return LevelLightEngine.EMPTY;
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return 15;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return 15;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver color) {
        if (this.level == null) {
            return -1;
        }
        try {
            return this.level.getBlockTint(pos, color);
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public int getMinY() {
        return this.minY;
    }
}
