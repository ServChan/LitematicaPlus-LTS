package org.lts.litematicaplus.placement;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.lts.litematicaplus.schematic.Schematic;

public final class SchematicPlacement {
    public enum LoadState {
        LOADING,
        READY,
        FAILED
    }

    private final UUID id;
    private String name;
    private String file;
    private int x;
    private int y;
    private int z;
    private Rotation rotation = Rotation.NONE;
    private Mirror mirror = Mirror.NONE;
    private boolean enabled = true;
    private boolean locked;
    private final Set<String> disabledRegions = new LinkedHashSet<>();
    private Schematic schematic;
    private LoadState loadState = LoadState.LOADING;
    private String error = "";

    public SchematicPlacement(UUID id, String name, String file, BlockPos origin) {
        this.id = id;
        this.name = name;
        this.file = file;
        this.x = origin.getX();
        this.y = origin.getY();
        this.z = origin.getZ();
    }

    public UUID id() {
        return this.id;
    }

    public String name() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String file() {
        return this.file;
    }

    public int x() {
        return this.x;
    }

    public int y() {
        return this.y;
    }

    public int z() {
        return this.z;
    }

    public BlockPos origin() {
        return new BlockPos(this.x, this.y, this.z);
    }

    public void setOrigin(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Rotation rotation() {
        return this.rotation;
    }

    public void setRotation(Rotation rotation) {
        this.rotation = rotation == null ? Rotation.NONE : rotation;
    }

    public Mirror mirror() {
        return this.mirror;
    }

    public void setMirror(Mirror mirror) {
        this.mirror = mirror == null ? Mirror.NONE : mirror;
    }

    public boolean enabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean locked() {
        return this.locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public Set<String> disabledRegions() {
        return this.disabledRegions;
    }

    public Schematic schematic() {
        return this.schematic;
    }

    public LoadState loadState() {
        return this.loadState;
    }

    public String error() {
        return this.error;
    }

    public void markReady(Schematic loaded) {
        this.schematic = loaded;
        this.loadState = LoadState.READY;
        this.error = "";
    }

    public void markFailed(String message) {
        this.schematic = null;
        this.loadState = LoadState.FAILED;
        this.error = message == null ? "" : message;
    }

    public void markLoading() {
        this.loadState = LoadState.LOADING;
        this.error = "";
    }

    public PlacementTransform transform() {
        return new PlacementTransform(this.x, this.y, this.z, this.rotation, this.mirror);
    }
}
