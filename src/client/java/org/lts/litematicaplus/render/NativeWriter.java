package org.lts.litematicaplus.render;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

final class NativeWriter {
    private ByteBuffer buffer;

    NativeWriter(int capacity) {
        this.buffer = MemoryUtil.memAlloc(capacity);
    }

    void reset() {
        this.buffer.clear();
    }

    int size() {
        return this.buffer.position();
    }

    void ensure(int bytes) {
        if (this.buffer.remaining() < bytes) {
            int needed = this.buffer.position() + bytes;
            int capacity = Math.max(needed, this.buffer.capacity() * 2);
            int position = this.buffer.position();
            this.buffer = MemoryUtil.memRealloc(this.buffer, capacity);
            this.buffer.position(position);
        }
    }

    void positionColor(float x, float y, float z, int abgr) {
        this.buffer.putFloat(x).putFloat(y).putFloat(z).putInt(abgr);
    }

    void lineVertex(float x, float y, float z, int abgr, byte nx, byte ny, byte nz, float width) {
        this.buffer.putFloat(x).putFloat(y).putFloat(z).putInt(abgr);
        this.buffer.put(nx).put(ny).put(nz).put((byte) 0);
        this.buffer.putFloat(width);
    }

    ByteBuffer view() {
        ByteBuffer duplicate = this.buffer.duplicate().order(this.buffer.order());
        duplicate.flip();
        return duplicate;
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

    void free() {
        MemoryUtil.memFree(this.buffer);
    }
}
