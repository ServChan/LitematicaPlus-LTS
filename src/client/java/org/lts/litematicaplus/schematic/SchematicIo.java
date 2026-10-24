package org.lts.litematicaplus.schematic;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ShortTag;
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
        return name.endsWith(LITEMATIC_EXTENSION) || name.endsWith(".nbt") || name.endsWith(".schem")
                || name.endsWith(".schematic") || name.endsWith(AxiomBlueprintCodec.EXTENSION);
    }

    public static String baseName(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static boolean isBlueprint(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(AxiomBlueprintCodec.EXTENSION);
    }

    public static Schematic read(Path path) throws IOException {
        if (isBlueprint(path)) {
            try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
                return AxiomBlueprintCodec.read(input, baseName(path));
            }
        }
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
            } else if (McEditCodec.matches(root)) {
                schematic = McEditCodec.read(root, fallback);
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
        if (isBlueprint(path)) {
            return read(path).metadata().copy();
        }
        if (!name.endsWith(LITEMATIC_EXTENSION)) {
            SchematicMetadata metadata = new SchematicMetadata();
            metadata.name = baseName(path);
            metadata.format = name.endsWith(".schem") ? "sponge" : name.endsWith(".schematic") ? "mcedit" : "structure";
            readDimensions(path, metadata);
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

    private static void readDimensions(Path path, SchematicMetadata metadata) {
        CollectFields visitor = switch (metadata.format) {
            case "structure" -> new CollectFields(new FieldSelector(ListTag.TYPE, "size"));
            case "mcedit" -> new CollectFields(new FieldSelector(ShortTag.TYPE, "Width"),
                    new FieldSelector(ShortTag.TYPE, "Height"), new FieldSelector(ShortTag.TYPE, "Length"));
            default -> new CollectFields(new FieldSelector(ShortTag.TYPE, "Width"),
                    new FieldSelector(ShortTag.TYPE, "Height"), new FieldSelector(ShortTag.TYPE, "Length"),
                    new FieldSelector("Schematic", ShortTag.TYPE, "Width"),
                    new FieldSelector("Schematic", ShortTag.TYPE, "Height"),
                    new FieldSelector("Schematic", ShortTag.TYPE, "Length"),
                    new FieldSelector(CompoundTag.TYPE, "Metadata"),
                    new FieldSelector("Schematic", CompoundTag.TYPE, "Metadata"));
        };
        try {
            NbtIo.parseCompressed(path, visitor, NbtAccounter.create(NBT_QUOTA));
        } catch (IOException | RuntimeException exception) {
            return;
        }
        if (!(visitor.getResult() instanceof CompoundTag root)) {
            return;
        }
        int x;
        int y;
        int z;
        CompoundTag body = root.getCompound("Schematic").orElse(root);
        if ("sponge".equals(metadata.format)) {
            metadata.placement = EmbeddedPlacement.readFrom(body.getCompoundOrEmpty("Metadata"));
        }
        if (body.contains("Width")) {
            x = body.getShortOr("Width", (short) 0) & 0xFFFF;
            y = body.getShortOr("Height", (short) 0) & 0xFFFF;
            z = body.getShortOr("Length", (short) 0) & 0xFFFF;
        } else {
            ListTag size = root.getListOrEmpty("size");
            if (size.size() != 3) {
                return;
            }
            x = size.getIntOr(0, 0);
            y = size.getIntOr(1, 0);
            z = size.getIntOr(2, 0);
        }
        if (x > 0 && y > 0 && z > 0) {
            metadata.enclosingX = x;
            metadata.enclosingY = y;
            metadata.enclosingZ = z;
            metadata.totalVolume = (long) x * y * z;
            metadata.regionCount = 1;
        }
    }

    public static void write(Path path, Schematic schematic) throws IOException {
        SchematicExport.Format format = SchematicExport.Format.of(path.getFileName().toString());
        if (format == SchematicExport.Format.BLUEPRINT) {
            writeBlueprint(path, schematic);
            return;
        }
        writeRoot(path, format == null ? LitematicCodec.write(schematic) : SchematicExport.encode(schematic, format));
    }

    private static void writeBlueprint(Path path, Schematic schematic) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(temp))) {
                AxiomBlueprintCodec.write(output, schematic);
            }
            AtomicFilePersistence.replace(temp, path);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static boolean canEmbedPlacement(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(LITEMATIC_EXTENSION) || name.endsWith(".schem");
    }

    public static void embedPlacement(Path path, EmbeddedPlacement placement) throws IOException {
        if (!canEmbedPlacement(path)) {
            throw new LocalizedException("litematicaplus.error.embed_format",
                    "Only .litematic and .schem files can keep a placement: " + path.getFileName());
        }
        CompoundTag root;
        try {
            root = NbtIo.readCompressed(path, NbtAccounter.create(NBT_QUOTA));
        } catch (RuntimeException exception) {
            throw new LocalizedException("litematicaplus.error.unreadable", "Unreadable NBT in " + path.getFileName(), exception);
        }
        CompoundTag holder;
        if (root.contains("Regions") && root.contains("Version")) {
            holder = root;
        } else if (SpongeCodec.matches(root)) {
            holder = root.getCompound("Schematic").orElse(root);
        } else {
            throw new LocalizedException("litematicaplus.error.embed_format",
                    "Only .litematic and .schem files can keep a placement: " + path.getFileName());
        }
        CompoundTag metadata = holder.getCompoundOrEmpty("Metadata");
        EmbeddedPlacement.writeTo(metadata, placement);
        holder.put("Metadata", metadata);
        writeRoot(path, root);
    }

    public static Path export(Path source, SchematicExport.Format format) throws IOException {
        return export(source, format, source.toAbsolutePath().getParent());
    }

    public static Path export(Path source, SchematicExport.Format format, Path directory) throws IOException {
        Schematic schematic = read(source);
        String base = baseName(source);
        Path target = directory.resolve(base + format.extension());
        for (int suffix = 2; Files.exists(target); suffix++) {
            target = directory.resolve(base + "_" + suffix + format.extension());
        }
        write(target, schematic);
        return target;
    }

    private static void writeRoot(Path path, CompoundTag root) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            NbtIo.writeCompressed(root, temp);
            AtomicFilePersistence.replace(temp, path);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
