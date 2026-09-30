package org.lts.litematicaplus.schematic;

public final class SchematicMetadata {
    public String name = "";
    public String author = "";
    public String description = "";
    public long timeCreated;
    public long timeModified;
    public int regionCount;
    public long totalBlocks;
    public long totalVolume;
    public int enclosingX;
    public int enclosingY;
    public int enclosingZ;
    public int dataVersion;
    public int schematicVersion;
    public String format = "";
    public int unknownStates;

    public SchematicMetadata copy() {
        SchematicMetadata copy = new SchematicMetadata();
        copy.name = this.name;
        copy.author = this.author;
        copy.description = this.description;
        copy.timeCreated = this.timeCreated;
        copy.timeModified = this.timeModified;
        copy.regionCount = this.regionCount;
        copy.totalBlocks = this.totalBlocks;
        copy.totalVolume = this.totalVolume;
        copy.enclosingX = this.enclosingX;
        copy.enclosingY = this.enclosingY;
        copy.enclosingZ = this.enclosingZ;
        copy.dataVersion = this.dataVersion;
        copy.schematicVersion = this.schematicVersion;
        copy.format = this.format;
        copy.unknownStates = this.unknownStates;
        return copy;
    }
}
