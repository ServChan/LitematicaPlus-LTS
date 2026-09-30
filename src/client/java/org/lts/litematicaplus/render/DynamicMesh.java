package org.lts.litematicaplus.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import java.nio.ByteBuffer;

final class DynamicMesh {
    private final String label;
    private final int vertexSize;
    private GpuBuffer buffer;
    private int capacity;
    private int vertices;

    DynamicMesh(String label, int vertexSize) {
        this.label = label;
        this.vertexSize = vertexSize;
    }

    void upload(NativeWriter writer) {
        int size = writer.size();
        this.vertices = size / this.vertexSize;
        if (size == 0) {
            return;
        }
        if (this.buffer == null || this.capacity < size) {
            if (this.buffer != null) {
                this.buffer.close();
            }
            this.capacity = Math.max(size * 2, 4096);
            this.buffer = RenderSystem.getDevice().createBuffer(() -> this.label,
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, this.capacity);
        }
        ByteBuffer data = writer.view();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.buffer.slice(0L, size), data);
    }

    void close() {
        if (this.buffer != null) {
            this.buffer.close();
            this.buffer = null;
        }
        this.capacity = 0;
        this.vertices = 0;
    }

    GpuBufferSlice slice() {
        return this.buffer.slice(0L, (long) this.vertices * this.vertexSize);
    }

    int vertices() {
        return this.vertices;
    }

    int quadIndices() {
        return this.vertices / 4 * 6;
    }

    boolean isEmpty() {
        return this.vertices < 4 || this.buffer == null;
    }
}
