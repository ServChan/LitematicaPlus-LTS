package org.lts.litematicaplus.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.device.GpuDevice;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class MeshArena {
    private static final long MAX_PAGE = 1L << 28;
    private final String label;
    private final int vertexSize;
    private final long firstPage;
    private final List<Page> pages = new ArrayList<>();
    private long nextPage;
    private int nextId;

    public MeshArena(String label, int vertexSize, long firstPage) {
        this.label = label;
        this.vertexSize = vertexSize;
        this.firstPage = firstPage;
        this.nextPage = firstPage;
    }

    static final class Page {
        final int id;
        final GpuBuffer buffer;
        final ArenaAllocator allocator;

        Page(int id, GpuBuffer buffer, long capacity) {
            this.id = id;
            this.buffer = buffer;
            this.allocator = new ArenaAllocator(capacity);
        }

        GpuBufferSlice slice() {
            return this.buffer.slice();
        }
    }

    public static final class Slice {
        private final MeshArena arena;
        final Page page;
        private final long offset;
        private final long bytes;
        final int baseVertex;

        private Slice(MeshArena arena, Page page, long offset, long bytes) {
            this.arena = arena;
            this.page = page;
            this.offset = offset;
            this.bytes = bytes;
            this.baseVertex = (int) (offset / arena.vertexSize);
        }

        public void release() {
            this.arena.free(this);
        }
    }

    public Slice upload(ByteBuffer data, int vertices) {
        long bytes = (long) vertices * this.vertexSize;
        Page page = null;
        long offset = -1L;
        for (Page candidate : this.pages) {
            offset = candidate.allocator.allocate(bytes);
            if (offset >= 0L) {
                page = candidate;
                break;
            }
        }
        if (page == null) {
            page = addPage(bytes);
            offset = page.allocator.allocate(bytes);
        }
        Slice slice = new Slice(this, page, offset, bytes);
        try {
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(page.buffer.slice(offset, bytes),
                    data.duplicate().order(data.order()).position(0).limit((int) bytes));
        } catch (RuntimeException exception) {
            free(slice);
            throw exception;
        }
        return slice;
    }

    private Page addPage(long bytes) {
        GpuDevice device = RenderSystem.getDevice();
        long limit = Math.min(MAX_PAGE, device.getDeviceInfo().limits().maxMemoryAllocationSize());
        long capacity = Math.max(Math.min(this.nextPage, limit), bytes);
        capacity -= capacity % this.vertexSize;
        GpuBuffer buffer = device.createBuffer(() -> this.label, GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, capacity);
        this.nextPage = Math.min(MAX_PAGE, this.nextPage * 2L);
        Page page = new Page(this.nextId++, buffer, capacity);
        this.pages.add(page);
        return page;
    }

    private void free(Slice slice) {
        Page page = slice.page;
        page.allocator.free(slice.offset, slice.bytes);
        if (page.allocator.empty() && this.pages.size() > 1) {
            this.pages.remove(page);
            page.buffer.close();
        }
    }

    public void close() {
        for (Page page : this.pages) {
            page.buffer.close();
        }
        this.pages.clear();
        this.nextPage = this.firstPage;
    }
}
