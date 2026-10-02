package org.lts.litematicaplus.schematic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import org.lts.litematicaplus.util.AtomicFilePersistence;
import org.lts.litematicaplus.util.LocalizedException;

public final class SchematicIo {
    public static final String LITEMATIC_EXTENSION = ".litematic";
    private static final long NBT_QUOTA = 1L << 30;

    private SchematicIo() {
    }

    public static boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(LITEMATIC_EXTENSION) || name.endsWith(".nbt") || name.endsWith(".schem");
    }

    public static String baseName(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static Schematic read(Path path) throws IOException {
        CompoundTag root;
        try {
            root = NbtIo.readCompressed(path, NbtAccounter.create(NBT_QUOTA));
        } catch (RuntimeException exception) {
            throw new LocalizedException("litematicaplus.error.unreadable", "Unreadable NBT in " + path.getFileName(), exception);
        }
        String fallback = baseName(path);
        Schematic schematic;
        try {
            if (root.contains("Regions") && root.contains("Version")) {
                schematic = LitematicCodec.read(root);
            } else if (SpongeCodec.matches(root)) {
                schematic = SpongeCodec.read(root, fallback);
            } else if (VanillaStructureCodec.matches(root)) {
                schematic = VanillaStructureCodec.read(root, fallback);
            } else {
                throw new LocalizedException("litematicaplus.error.not_schematic", "Unsupported schematic format: " + path.getFileName());
            }
        } catch (RuntimeException exception) {
            throw new LocalizedException("litematicaplus.error.corrupt", "Corrupt schematic " + path.getFileName() + ": " + exception.getMessage(), exception);
        }
        if (schematic.metadata().name.isBlank()) {
            schematic.metadata().name = fallback;
        }
        return schematic;
    }

    public static SchematicMetadata readMetadata(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(LITEMATIC_EXTENSION)) {
            SchematicMetadata metadata = new SchematicMetadata();
            metadata.name = baseName(path);
            metadata.format = name.endsWith(".schem") ? "sponge" : "structure";
            return metadata;
        }
        CollectFields visitor = new CollectFields(
                new FieldSelector(CompoundTag.TYPE, "Metadata"),
                new FieldSelector(IntTag.TYPE, "Version"),
                new FieldSelector(IntTag.TYPE, "MinecraftDataVersion"));
        try {
            NbtIo.parseCompressed(path, visitor, NbtAccounter.create(NBT_QUOTA));
        } catch (RuntimeException exception) {
            throw new LocalizedException("litematicaplus.error.unreadable", "Unreadable NBT in " + path.getFileName(), exception);
        }
        Tag result = visitor.getResult();
        if (!(result instanceof CompoundTag root)) {
            throw new LocalizedException("litematicaplus.error.not_schematic", "Not a litematic file: " + path.getFileName());
        }
        SchematicMetadata metadata = LitematicCodec.readMetadataOnly(root);
        if (metadata.name.isBlank()) {
            metadata.name = baseName(path);
        }
        return metadata;
    }

    public static void write(Path path, Schematic schematic) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            NbtIo.writeCompressed(LitematicCodec.write(schematic), temp);
            AtomicFilePersistence.replace(temp, path);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
