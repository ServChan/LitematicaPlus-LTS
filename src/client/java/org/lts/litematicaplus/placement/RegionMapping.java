package org.lts.litematicaplus.placement;

import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class RegionMapping {
    private final PlacementTransform main;
    private final PlacementTransform sub;
    private final PlacementTransform combined;
    private final int pivotX;
    private final int pivotY;
    private final int pivotZ;
    private final int shiftX;
    private final int shiftY;
    private final int shiftZ;

    public RegionMapping(PlacementTransform main, RegionPlacement region, int pivotX, int pivotY, int pivotZ,
            int shiftX, int shiftY, int shiftZ) {
        this.main = main;
        this.sub = new PlacementTransform(pivotX + region.offsetX(), pivotY + region.offsetY(), pivotZ + region.offsetZ(),
                region.rotation(), region.mirror());
        this.combined = compose(this.sub, main);
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
        this.shiftX = shiftX;
        this.shiftY = shiftY;
        this.shiftZ = shiftZ;
    }

    public int worldX(int x, int z) {
        int relX = x - this.pivotX;
        int relZ = z - this.pivotZ;
        return this.main.worldX(this.sub.worldX(relX, relZ), this.sub.worldZ(relX, relZ)) + this.shiftX;
    }

    public int worldY(int y) {
        return this.main.worldY(this.sub.worldY(y - this.pivotY)) + this.shiftY;
    }

    public int worldZ(int x, int z) {
        int relX = x - this.pivotX;
        int relZ = z - this.pivotZ;
        return this.main.worldZ(this.sub.worldX(relX, relZ), this.sub.worldZ(relX, relZ)) + this.shiftZ;
    }

    public Vec3 worldPoint(double x, double y, double z) {
        Vec3 schematic = this.sub.worldPoint(x - this.pivotX, y - this.pivotY, z - this.pivotZ);
        Vec3 world = this.main.worldPoint(schematic.x, schematic.y, schematic.z);
        return world.add(this.shiftX, this.shiftY, this.shiftZ);
    }

    public BlockState apply(BlockState state) {
        return this.combined.apply(state);
    }

    public Rotation rotation() {
        return this.combined.rotation();
    }

    public Mirror mirror() {
        return this.combined.mirror();
    }

    public static PlacementTransform compose(PlacementTransform first, PlacementTransform second) {
        if (first.rotation() == Rotation.NONE && first.mirror() == Mirror.NONE) {
            return new PlacementTransform(0, 0, 0, second.rotation(), second.mirror());
        }
        int[][] probes = {{1, 0}, {0, 1}};
        PlacementTransform a = new PlacementTransform(0, 0, 0, first.rotation(), first.mirror());
        PlacementTransform b = new PlacementTransform(0, 0, 0, second.rotation(), second.mirror());
        for (Mirror mirror : new Mirror[]{Mirror.NONE, Mirror.FRONT_BACK}) {
            for (Rotation rotation : Rotation.values()) {
                PlacementTransform candidate = new PlacementTransform(0, 0, 0, rotation, mirror);
                boolean matches = true;
                for (int[] probe : probes) {
                    int midX = a.worldX(probe[0], probe[1]);
                    int midZ = a.worldZ(probe[0], probe[1]);
                    if (candidate.worldX(probe[0], probe[1]) != b.worldX(midX, midZ)
                            || candidate.worldZ(probe[0], probe[1]) != b.worldZ(midX, midZ)) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return candidate;
                }
            }
        }
        return b;
    }
}
