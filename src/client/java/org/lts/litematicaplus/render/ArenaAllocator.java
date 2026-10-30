package org.lts.litematicaplus.render;

import java.util.Map;
import java.util.TreeMap;

final class ArenaAllocator {
    private final long capacity;
    private final TreeMap<Long, Long> free = new TreeMap<>();
    private long used;

    ArenaAllocator(long capacity) {
        this.capacity = capacity;
        this.free.put(0L, capacity);
    }

    long capacity() {
        return this.capacity;
    }

    long used() {
        return this.used;
    }

    boolean empty() {
        return this.used == 0L;
    }

    int fragments() {
        return this.free.size();
    }

    long allocate(long size) {
        if (size <= 0L) {
            throw new IllegalArgumentException("size must be positive");
        }
        for (Map.Entry<Long, Long> block : this.free.entrySet()) {
            long length = block.getValue();
            if (length < size) {
                continue;
            }
            long offset = block.getKey();
            this.free.remove(offset);
            if (length > size) {
                this.free.put(offset + size, length - size);
            }
            this.used += size;
            return offset;
        }
        return -1L;
    }

    void free(long offset, long size) {
        long start = offset;
        long length = size;
        Map.Entry<Long, Long> before = this.free.floorEntry(offset);
        if (before != null && before.getKey() + before.getValue() == offset) {
            start = before.getKey();
            length += before.getValue();
            this.free.remove(start);
        }
        Long after = this.free.get(offset + size);
        if (after != null) {
            length += after;
            this.free.remove(offset + size);
        }
        this.free.put(start, length);
        this.used -= size;
    }
}
