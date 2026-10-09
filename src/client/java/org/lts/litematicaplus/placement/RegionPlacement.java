package org.lts.litematicaplus.placement;

import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

public record RegionPlacement(int offsetX, int offsetY, int offsetZ, Rotation rotation, Mirror mirror) {
    public static final RegionPlacement IDENTITY = new RegionPlacement(0, 0, 0, Rotation.NONE, Mirror.NONE);

    public RegionPlacement {
        rotation = rotation == null ? Rotation.NONE : rotation;
        mirror = mirror == null ? Mirror.NONE : mirror;
    }

    public boolean isIdentity() {
        return this.offsetX == 0 && this.offsetY == 0 && this.offsetZ == 0 && this.rotation == Rotation.NONE
                && this.mirror == Mirror.NONE;
    }

    public RegionPlacement withOffset(int x, int y, int z) {
        return new RegionPlacement(x, y, z, this.rotation, this.mirror);
    }

    public RegionPlacement withRotation(Rotation value) {
        return new RegionPlacement(this.offsetX, this.offsetY, this.offsetZ, value, this.mirror);
    }

    public RegionPlacement withMirror(Mirror value) {
        return new RegionPlacement(this.offsetX, this.offsetY, this.offsetZ, this.rotation, value);
    }
}
