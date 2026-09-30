package org.lts.litematicaplus.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class PlacementTransformTest {
    @Test
    void everyOrientationIsABijectionOnTheHorizontalPlane() {
        for (Rotation rotation : Rotation.values()) {
            for (Mirror mirror : Mirror.values()) {
                PlacementTransform transform = new PlacementTransform(100, 64, -37, rotation, mirror);
                java.util.Set<Long> seen = new java.util.HashSet<>();
                for (int x = -4; x <= 4; x++) {
                    for (int z = -4; z <= 4; z++) {
                        int worldX = transform.worldX(x, z);
                        int worldZ = transform.worldZ(x, z);
                        assertEquals(x * x + z * z, (worldX - 100) * (worldX - 100) + (worldZ + 37) * (worldZ + 37));
                        seen.add((long) worldX << 32 | worldZ & 0xFFFFFFFFL);
                    }
                }
                assertEquals(81, seen.size(), rotation + " " + mirror);
                assertEquals(70, transform.worldY(6));
            }
        }
    }

    @Test
    void pointsStayInsideTheBlockTheyBelongTo() {
        double[] fractions = {0.03125, 0.5, 0.96875};
        for (Rotation rotation : Rotation.values()) {
            for (Mirror mirror : Mirror.values()) {
                PlacementTransform transform = new PlacementTransform(100, 64, -37, rotation, mirror);
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        for (double fx : fractions) {
                            for (double fz : fractions) {
                                Vec3 point = transform.worldPoint(x + fx, 3.25, z + fz);
                                assertEquals(transform.worldX(x, z), (int) Math.floor(point.x), rotation + " " + mirror);
                                assertEquals(transform.worldZ(x, z), (int) Math.floor(point.z), rotation + " " + mirror);
                                assertEquals(67.25, point.y);
                            }
                        }
                    }
                }
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
