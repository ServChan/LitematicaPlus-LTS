package org.lts.litematicaplus.engine;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import org.lts.litematicaplus.verify.SectionVerdict;

public final class SectionSlot {
    public final long key;
    public final int originX;
    public final int originY;
    public final int originZ;
    public final boolean hasSolid;
    int revision;
    boolean inFlight;
    boolean pendingGhost;
    boolean ghostBuilt;
    boolean built;
    GpuBuffer ghost;
    int ghostIndices;
    GpuBuffer fill;
    int fillIndices;
    GpuBuffer lines;
    int lineIndices;
    int[] specials = new int[0];
    SectionVerdict verdict;
    int drawIndex = -1;
    int failures;
    public float sortDistance;

    SectionSlot(long key, int originX, int originY, int originZ, boolean hasSolid) {
        this.key = key;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.hasSolid = hasSolid;
    }

    public GpuBuffer ghost() {
        return this.ghost;
    }

    public int ghostIndices() {
        return this.ghostIndices;
    }

    public GpuBuffer fill() {
        return this.fill;
    }

    public int fillIndices() {
        return this.fillIndices;
    }

    public GpuBuffer lines() {
        return this.lines;
    }

    public int lineIndices() {
        return this.lineIndices;
    }

    public int[] specials() {
        return this.specials;
    }

    public SectionVerdict verdict() {
        return this.verdict;
    }

    void closeBuffers() {
        if (this.ghost != null) {
            this.ghost.close();
            this.ghost = null;
        }
        if (this.fill != null) {
            this.fill.close();
            this.fill = null;
        }
        if (this.lines != null) {
            this.lines.close();
            this.lines = null;
        }
        this.ghostIndices = 0;
        this.fillIndices = 0;
        this.lineIndices = 0;
        this.specials = new int[0];
    }
}
