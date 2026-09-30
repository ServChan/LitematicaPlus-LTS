package org.lts.litematicaplus.render;

import java.nio.ByteBuffer;
import org.lts.litematicaplus.verify.SectionVerdict;
import org.lwjgl.system.MemoryUtil;

public final class MeshResult {
    public final long sectionKey;
    public final int generation;
    public final int revision;
    public final SectionVerdict verdict;
    public final int[] specials;
    public final Throwable failure;
    private ByteBuffer ghostData;
    private final int ghostVertices;
    private ByteBuffer fillData;
    private final int fillVertices;
    private ByteBuffer lineData;
    private final int lineVertices;

    MeshResult(long sectionKey, int generation, int revision, SectionVerdict verdict, int[] specials,
            ByteBuffer ghostData, int ghostVertices, ByteBuffer fillData, int fillVertices,
            ByteBuffer lineData, int lineVertices, Throwable failure) {
        this.sectionKey = sectionKey;
        this.generation = generation;
        this.revision = revision;
        this.verdict = verdict;
        this.specials = specials;
        this.ghostData = ghostData;
        this.ghostVertices = ghostVertices;
        this.fillData = fillData;
        this.fillVertices = fillVertices;
        this.lineData = lineData;
        this.lineVertices = lineVertices;
        this.failure = failure;
    }

    static MeshResult failed(SectionJob job, Throwable failure) {
        return new MeshResult(job.sectionKey(), job.generation(), job.revision(), null, new int[0],
                null, 0, null, 0, null, 0, failure);
    }

    public ByteBuffer ghostData() {
        return this.ghostData;
    }

    public int ghostVertices() {
        return this.ghostVertices;
    }

    public ByteBuffer fillData() {
        return this.fillData;
    }

    public int fillVertices() {
        return this.fillVertices;
    }

    public ByteBuffer lineData() {
        return this.lineData;
    }

    public int lineVertices() {
        return this.lineVertices;
    }

    public void free() {
        if (this.ghostData != null) {
            MemoryUtil.memFree(this.ghostData);
            this.ghostData = null;
        }
        if (this.fillData != null) {
            MemoryUtil.memFree(this.fillData);
            this.fillData = null;
        }
        if (this.lineData != null) {
            MemoryUtil.memFree(this.lineData);
            this.lineData = null;
        }
    }
}
