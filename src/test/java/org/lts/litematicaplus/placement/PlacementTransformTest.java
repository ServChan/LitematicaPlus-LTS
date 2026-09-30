package org.lts.litematicaplus.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

class PlacementTransformTest {
    @Test
    void worldAndLocalAreInversesForEveryOrientation() {
        for (Rotation rotation : Rotation.values()) {
            for (Mirror mirror : Mirror.values()) {
                PlacementTransform transform = new PlacementTransform(100, 64, -37, rotation, mirror);
                for (int x = -5; x <= 5; x += 5) {
                    for (int z = -3; z <= 9; z += 4) {
                        int worldX = transform.worldX(x, z);
                        int worldZ = transform.worldZ(x, z);
                        assertEquals(x, transform.localX(worldX, worldZ), rotation + " " + mirror);
                        assertEquals(z, transform.localZ(worldX, worldZ), rotation + " " + mirror);
                    }
                }
                assertEquals(70, transform.worldY(6));
                assertEquals(6, transform.localY(70));
            }
        }
    }

    @Test
    void clockwiseRotationMatchesVanillaStructureRule() {
        PlacementTransform transform = new PlacementTransform(0, 0, 0, Rotation.CLOCKWISE_90, Mirror.NONE);
        assertEquals(-3, transform.worldX(2, 3));
        assertEquals(2, transform.worldZ(2, 3));
    }

    @Test
    void mirrorIsAppliedBeforeRotation() {
        PlacementTransform transform = new PlacementTransform(0, 0, 0, Rotation.CLOCKWISE_90, Mirror.FRONT_BACK);
        assertEquals(-3, transform.worldX(2, 3));
        assertEquals(-2, transform.worldZ(2, 3));
    }
}
