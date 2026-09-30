package org.lts.litematicaplus.placement;

import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

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

    public Vec3 worldPoint(double localX, double localY, double localZ) {
        double x = this.mirror == Mirror.FRONT_BACK ? 1.0 - localX : localX;
        double z = this.mirror == Mirror.LEFT_RIGHT ? 1.0 - localZ : localZ;
        return switch (this.rotation) {
            case CLOCKWISE_90 -> new Vec3(this.originX + 1.0 - z, this.originY + localY, this.originZ + x);
            case CLOCKWISE_180 -> new Vec3(this.originX + 1.0 - x, this.originY + localY, this.originZ + 1.0 - z);
            case COUNTERCLOCKWISE_90 -> new Vec3(this.originX + z, this.originY + localY, this.originZ + 1.0 - x);
            default -> new Vec3(this.originX + x, this.originY + localY, this.originZ + z);
        };
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
    }}
