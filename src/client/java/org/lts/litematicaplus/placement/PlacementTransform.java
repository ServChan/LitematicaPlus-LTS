package org.lts.litematicaplus.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

public record PlacementTransform(int originX, int originY, int originZ, Rotation rotation, Mirror mirror) {
    public static final PlacementTransform IDENTITY = new PlacementTransform(0, 0, 0, Rotation.NONE, Mirror.NONE);

    public PlacementTransform {
        rotation = rotation == null ? Rotation.NONE : rotation;
        mirror = mirror == null ? Mirror.NONE : mirror;
    }

    public static int rotateX(Rotation rotation, int x, int z) {
        return switch (rotation) {
            case CLOCKWISE_90 -> -z;
            case CLOCKWISE_180 -> -x;
            case COUNTERCLOCKWISE_90 -> z;
            default -> x;
        };
    }

    public static int rotateZ(Rotation rotation, int x, int z) {
        return switch (rotation) {
            case CLOCKWISE_90 -> x;
            case CLOCKWISE_180 -> -z;
            case COUNTERCLOCKWISE_90 -> -x;
            default -> z;
        };
    }

    public static Rotation inverse(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> rotation;
        };
    }

    public int worldX(int localX, int localZ) {
        int x = this.mirror == Mirror.FRONT_BACK ? -localX : localX;
        int z = this.mirror == Mirror.LEFT_RIGHT ? -localZ : localZ;
        return this.originX + rotateX(this.rotation, x, z);
    }

    public int worldY(int localY) {
        return this.originY + localY;
    }

    public int worldZ(int localX, int localZ) {
        int x = this.mirror == Mirror.FRONT_BACK ? -localX : localX;
        int z = this.mirror == Mirror.LEFT_RIGHT ? -localZ : localZ;
        return this.originZ + rotateZ(this.rotation, x, z);
    }

    public int localX(int worldX, int worldZ) {
        Rotation back = inverse(this.rotation);
        int x = rotateX(back, worldX - this.originX, worldZ - this.originZ);
        return this.mirror == Mirror.FRONT_BACK ? -x : x;
    }

    public int localY(int worldY) {
        return worldY - this.originY;
    }

    public int localZ(int worldX, int worldZ) {
        Rotation back = inverse(this.rotation);
        int z = rotateZ(back, worldX - this.originX, worldZ - this.originZ);
        return this.mirror == Mirror.LEFT_RIGHT ? -z : z;
    }

    public BlockPos toWorld(int localX, int localY, int localZ) {
        return new BlockPos(worldX(localX, localZ), worldY(localY), worldZ(localX, localZ));
    }

    public BlockState apply(BlockState state) {
        BlockState result = state;
        if (this.mirror != Mirror.NONE) {
            result = result.mirror(this.mirror);
        }
        if (this.rotation != Rotation.NONE) {
            result = result.rotate(this.rotation);
        }
        return result;
    }

    public PlacementTransform withOrigin(int x, int y, int z) {
        return new PlacementTransform(x, y, z, this.rotation, this.mirror);
    }
}
