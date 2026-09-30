package org.lts.litematicaplus.tool;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.verify.MismatchType;

public final class GhostRaycast {
    public record Hit(BlockPos pos, BlockState state, MismatchType type) {
    }

    @FunctionalInterface
    public interface Filter {
        boolean accept(Hit hit);
    }

    private GhostRaycast() {
    }

    public static Hit trace(Minecraft minecraft, Filter filter) {
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }
        GhostEngine engine = GhostEngine.get();
        if (engine.runtimes().isEmpty()) {
            return null;
        }
        Vec3 eye = minecraft.player.getEyePosition(1.0F);
        Vec3 look = minecraft.player.getViewVector(1.0F);
        double reach = minecraft.player.blockInteractionRange();
        HitResult real = minecraft.hitResult;
        if (real != null && real.getType() != HitResult.Type.MISS) {
            reach = Math.min(reach, eye.distanceTo(real.getLocation()) + 0.01);
        }
        int x = floor(eye.x);
        int y = floor(eye.y);
        int z = floor(eye.z);
        int stepX = look.x > 0.0 ? 1 : look.x < 0.0 ? -1 : 0;
        int stepY = look.y > 0.0 ? 1 : look.y < 0.0 ? -1 : 0;
        int stepZ = look.z > 0.0 ? 1 : look.z < 0.0 ? -1 : 0;
        double deltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / look.x);
        double deltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / look.y);
        double deltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / look.z);
        double maxX = stepX == 0 ? Double.MAX_VALUE : (stepX > 0 ? x + 1 - eye.x : eye.x - x) * deltaX;
        double maxY = stepY == 0 ? Double.MAX_VALUE : (stepY > 0 ? y + 1 - eye.y : eye.y - y) * deltaY;
        double maxZ = stepZ == 0 ? Double.MAX_VALUE : (stepZ > 0 ? z + 1 - eye.z : eye.z - z) * deltaZ;
        double travelled = 0.0;
        for (int guard = 0; guard < 256 && travelled <= reach; guard++) {
            MismatchType type = engine.categoryAt(x, y, z);
            if (type == MismatchType.MISSING || type == MismatchType.WRONG_STATE || type == MismatchType.WRONG_BLOCK) {
                BlockState state = engine.schematicStateAt(x, y, z);
                if (state != null && !state.isAir()) {
                    Hit hit = new Hit(new BlockPos(x, y, z), state, type);
                    if (filter.accept(hit)) {
                        return hit;
                    }
                }
            }
            if (maxX <= maxY && maxX <= maxZ) {
                travelled = maxX;
                maxX += deltaX;
                x += stepX;
            } else if (maxY <= maxZ) {
                travelled = maxY;
                maxY += deltaY;
                y += stepY;
            } else {
                travelled = maxZ;
                maxZ += deltaZ;
                z += stepZ;
            }
        }
        return null;
    }

    private static int floor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }
}
