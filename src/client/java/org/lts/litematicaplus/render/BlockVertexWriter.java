package org.lts.litematicaplus.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

final class BlockVertexWriter implements VertexConsumer {
    static final int VERTEX_SIZE = 28;
    private static final int FULL_BRIGHT = 15728880;

    private ByteBuffer buffer;
    private int vertexStart = -1;
    private float offsetX;
    private float offsetY;
    private float offsetZ;

    BlockVertexWriter(int capacity) {
        this.buffer = MemoryUtil.memAlloc(capacity);
    }

    void reset() {
        this.buffer.clear();
        this.vertexStart = -1;
    }

    void offset(float x, float y, float z) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
    }

    int vertices() {
        return this.buffer.position() / VERTEX_SIZE;
    }

    ByteBuffer copy() {
        int size = this.buffer.position();
        if (size == 0) {
            return null;
        }
        ByteBuffer result = MemoryUtil.memAlloc(size);
        MemoryUtil.memCopy(MemoryUtil.memAddress(this.buffer, 0), MemoryUtil.memAddress(result), size);
        return result;
    }

    private void ensure() {
        if (this.buffer.remaining() < VERTEX_SIZE) {
            int position = this.buffer.position();
            this.buffer = MemoryUtil.memRealloc(this.buffer, this.buffer.capacity() * 2);
            this.buffer.position(position);
        }
    }

    private static int abgr(int argb) {
        return argb & 0xFF00FF00 | (argb & 0xFF0000) >> 16 | (argb & 0xFF) << 16;
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlayCoords, int lightCoords,
            float nx, float ny, float nz) {
        ensure();
        this.vertexStart = -1;
        this.buffer.putFloat(x + this.offsetX).putFloat(y + this.offsetY).putFloat(z + this.offsetZ).putInt(abgr(color))
                .putFloat(u).putFloat(v).putInt(lightCoords);
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        ensure();
        this.vertexStart = this.buffer.position();
        this.buffer.putFloat(x + this.offsetX).putFloat(y + this.offsetY).putFloat(z + this.offsetZ).putInt(-1)
                .putFloat(0.0F).putFloat(0.0F).putInt(FULL_BRIGHT);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        if (this.vertexStart >= 0) {
            this.buffer.putInt(this.vertexStart + 12, (a & 0xFF) << 24 | (b & 0xFF) << 16 | (g & 0xFF) << 8 | r & 0xFF);
        }
        return this;
    }

    @Override
    public VertexConsumer setColor(int color) {
        if (this.vertexStart >= 0) {
            this.buffer.putInt(this.vertexStart + 12, abgr(color));
        }
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        if (this.vertexStart >= 0) {
            this.buffer.putFloat(this.vertexStart + 16, u).putFloat(this.vertexStart + 20, v);
        }
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        if (this.vertexStart >= 0) {
            this.buffer.putInt(this.vertexStart + 24, (v & 0xFFFF) << 16 | u & 0xFFFF);
        }
        return this;
    }

    @Override
    public VertexConsumer setUv3(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        return this;
    }

    void free() {
        MemoryUtil.memFree(this.buffer);
    }
}
