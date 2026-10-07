package org.lts.litematicaplus.share;

import java.util.List;
import java.util.UUID;

public final class SharedPlacement {
    public record Player(UUID uuid, String name) {
        public static final Player MISSING = new Player(UUID.fromString("4c1b738f-56fa-4011-8273-498c972424ea"),
                "No Player");
    }

    public record RegionShift(String name, int x, int y, int z, int rotation, int mirror) {
    }

    private final UUID id;
    private final UUID hash;
    private final String fileName;
    private String displayName;
    private Player owner = Player.MISSING;
    private Player lastModifiedBy = Player.MISSING;
    private int litematicVersion = -1;
    private int dataVersion = -1;
    private int x;
    private int y;
    private int z;
    private String dimension = "minecraft:overworld";
    private int rotation;
    private int mirror;
    private List<RegionShift> regions = List.of();

    public SharedPlacement(UUID id, UUID hash, String fileName, String displayName) {
        this.id = id;
        this.hash = hash;
        this.fileName = fileName == null ? "" : fileName;
        this.displayName = displayName == null ? this.fileName : displayName;
    }

    public UUID id() {
        return this.id;
    }

    public UUID hash() {
        return this.hash;
    }

    public String fileName() {
        return this.fileName;
    }

    public String displayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? "" : displayName;
    }

    public Player owner() {
        return this.owner;
    }

    public void setOwner(Player owner) {
        this.owner = owner == null ? Player.MISSING : owner;
    }

    public Player lastModifiedBy() {
        return this.lastModifiedBy;
    }

    public void setLastModifiedBy(Player player) {
        this.lastModifiedBy = player == null ? Player.MISSING : player;
    }

    public int litematicVersion() {
        return this.litematicVersion;
    }

    public int dataVersion() {
        return this.dataVersion;
    }

    public void setVersions(int litematicVersion, int dataVersion) {
        this.litematicVersion = litematicVersion;
        this.dataVersion = dataVersion;
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

    public String dimension() {
        return this.dimension;
    }

    public int rotation() {
        return this.rotation;
    }

    public int mirror() {
        return this.mirror;
    }

    public void place(int x, int y, int z, String dimension, int rotation, int mirror) {
        if (rotation < 0 || rotation > 3 || mirror < 0 || mirror > 2) {
            throw new IllegalArgumentException("rotation/mirror out of range: " + rotation + "/" + mirror);
        }
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension == null ? "" : dimension;
        this.rotation = rotation;
        this.mirror = mirror;
    }

    public List<RegionShift> regions() {
        return this.regions;
    }

    public void setRegions(List<RegionShift> regions) {
        this.regions = regions == null ? List.of() : List.copyOf(regions);
    }

    public String title() {
        if (!this.displayName.isBlank()) {
            return this.displayName;
        }
        int dot = this.fileName.lastIndexOf('.');
        return dot > 0 ? this.fileName.substring(0, dot) : this.fileName;
    }
}
