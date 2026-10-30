package org.lts.litematicaplus.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.DeviceInfo;
import java.nio.IntBuffer;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;

final class MultiDraw {
    private static final int CAPACITY = 4096;
    private static final int SEPARATE = 0;
    private static final int INTERLEAVED = 1;
    private static final int SINGLE = 2;
    private static final int INFO_INTS = 3;
    private final PointerBuffer offsets = MemoryUtil.memCallocPointer(CAPACITY);
    private final IntBuffer counts = MemoryUtil.memAllocInt(CAPACITY);
    private final IntBuffer bases = MemoryUtil.memAllocInt(CAPACITY);
    private final IntBuffer info = MemoryUtil.memAllocInt(CAPACITY * INFO_INTS);
    private int mode = -1;
    private int limit = CAPACITY;
    private int size;

    void add(RenderPass pass, int indices, int baseVertex) {
        if (this.mode < 0) {
            detect();
        }
        if (this.mode == SINGLE) {
            pass.drawIndexed(indices, 1, 0, baseVertex, 0);
            return;
        }
        this.counts.put(this.size, indices);
        this.bases.put(this.size, baseVertex);
        this.info.put(this.size * INFO_INTS, 0);
        this.info.put(this.size * INFO_INTS + 1, indices);
        this.info.put(this.size * INFO_INTS + 2, baseVertex);
        this.size++;
        if (this.size >= this.limit) {
            flush(pass);
        }
    }

    void flush(RenderPass pass) {
        int draws = this.size;
        this.size = 0;
        if (draws == 0) {
            return;
        }
        if (draws == 1) {
            pass.drawIndexed(this.counts.get(0), 1, 0, this.bases.get(0), 0);
            return;
        }
        try {
            if (this.mode == SEPARATE) {
                pass.multiDrawIndexed(this.offsets, this.counts, this.bases, draws);
            } else {
                pass.multiDrawIndexed(this.info, 1, 0, draws);
            }
        } catch (RuntimeException exception) {
            LitematicaPlusClient.LOGGER.warn("Multi-draw is unavailable; drawing schematic sections one by one", exception);
            this.mode = SINGLE;
            for (int draw = 0; draw < draws; draw++) {
                pass.drawIndexed(this.counts.get(draw), 1, 0, this.bases.get(draw), 0);
            }
        }
    }

    private void detect() {
        DeviceInfo device = RenderSystem.getDevice().getDeviceInfo();
        if (device.features().multiDrawDirectSeparate()) {
            this.mode = SEPARATE;
        } else if (device.features().multiDrawDirectInterleaved()) {
            this.mode = INTERLEAVED;
            this.limit = Math.max(1, Math.min(CAPACITY, device.limits().maxMultiDrawDirectInterleavedDrawCount()));
        } else {
            this.mode = SINGLE;
        }
    }
}
