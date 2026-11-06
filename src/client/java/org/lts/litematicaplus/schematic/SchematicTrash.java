package org.lts.litematicaplus.schematic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import org.lts.litematicaplus.util.AtomicFilePersistence;

public final class SchematicTrash {
    public static final int KEEP_DAYS = 30;
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final String INDEX = "index.json";
    private static final int MAX_SUFFIX = 999;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
            .withZone(ZoneId.systemDefault());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type LIST = new TypeToken<List<Item>>() { }.getType();

    public static final class Item {
        public String id = "";
        public String name = "";
        public String original = "";
        public long deletedAt;
        public long size;

        public long expiresAt() {
            return this.deletedAt + KEEP_DAYS * DAY_MS;
        }

        public int daysLeft(long now) {
            long left = expiresAt() - now;
            return left <= 0L ? 0 : (int) Math.min(KEEP_DAYS, (left + DAY_MS - 1L) / DAY_MS);
        }

        public Path originalPath() {
            try {
                return this.original.isEmpty() ? null : Path.of(this.original);
            } catch (InvalidPathException exception) {
                return null;
            }
        }
    }

    private static SchematicTrash global;

    private final Path root;
    private List<Item> items;

    public static synchronized SchematicTrash global() {
        if (global == null) {
            global = new SchematicTrash(FabricLoader.getInstance().getGameDir().resolve("litematica-plus").resolve("trash"));
        }
        return global;
    }

    public SchematicTrash(Path root) {
        this.root = root;
    }

    public Path root() {
        return this.root;
    }

    public synchronized List<Item> items() {
        load();
        List<Item> present = new ArrayList<>();
        for (Item item : this.items) {
            if (Files.exists(stored(item))) {
                present.add(item);
            }
        }
        present.sort(Comparator.comparingLong((Item item) -> item.deletedAt).reversed());
        return present;
    }

    public synchronized int count() {
        return items().size();
    }

    public Path stored(Item item) {
        return this.root.resolve(item.id).resolve(item.name);
    }

    public synchronized Item trash(Path file, long now) throws IOException {
        load();
        if (!Files.isRegularFile(file)) {
            throw new IOException("Not a file: " + file);
        }
        String base = STAMP.format(Instant.ofEpochMilli(now));
        String id = base;
        for (int suffix = 2; Files.exists(this.root.resolve(id)) || contains(id); suffix++) {
            if (suffix > MAX_SUFFIX) {
                throw new IOException("Too many deletions in one second");
            }
            id = base + "-" + suffix;
        }
        Path folder = this.root.resolve(id);
        Files.createDirectories(folder);
        Item item = new Item();
        item.id = id;
        item.name = file.getFileName().toString();
        item.original = file.toAbsolutePath().normalize().toString();
        item.deletedAt = now;
        item.size = Files.size(file);
        move(file, folder.resolve(item.name));
        this.items.add(item);
        save();
        return item;
    }

    public synchronized Path restore(Item item, Path fallbackFolder) throws IOException {
        load();
        Path source = stored(item);
        if (!Files.isRegularFile(source)) {
            throw new IOException("Missing in the trash: " + source);
        }
        Path original = item.originalPath();
        Path folder = original != null && original.getParent() != null && Files.isDirectory(original.getParent())
                ? original.getParent() : fallbackFolder;
        Files.createDirectories(folder);
        Path target = free(folder.resolve(item.name));
        move(source, target);
        forget(item);
        return target;
    }

    public synchronized void deleteForever(Item item) throws IOException {
        load();
        removeFolder(this.root.resolve(item.id));
        forget(item);
    }

    public synchronized int empty() throws IOException {
        load();
        int removed = 0;
        for (Item item : List.copyOf(this.items)) {
            removeFolder(this.root.resolve(item.id));
            removed++;
        }
        this.items.clear();
        save();
        return removed;
    }

    public synchronized int purgeExpired(long now) {
        load();
        int removed = 0;
        for (Item item : List.copyOf(this.items)) {
            if (item.expiresAt() <= now || !Files.exists(stored(item))) {
                try {
                    removeFolder(this.root.resolve(item.id));
                    this.items.remove(item);
                    removed++;
                } catch (IOException exception) {
                    continue;
                }
            }
        }
        if (removed > 0) {
            try {
                save();
            } catch (IOException exception) {
                return removed;
            }
        }
        return removed;
    }

    static Path free(Path wanted) {
        if (!Files.exists(wanted)) {
            return wanted;
        }
        String name = wanted.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot <= 0 ? name : name.substring(0, dot);
        String extension = dot <= 0 ? "" : name.substring(dot);
        for (int suffix = 2; suffix <= MAX_SUFFIX; suffix++) {
            Path candidate = wanted.resolveSibling(stem + " (" + suffix + ")" + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return wanted.resolveSibling(stem + " (" + System.currentTimeMillis() + ")" + extension);
    }

    private boolean contains(String id) {
        for (Item item : this.items) {
            if (item.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private void forget(Item item) throws IOException {
        this.items.removeIf(other -> other.id.equals(item.id));
        removeFolder(this.root.resolve(item.id));
        save();
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(from, to);
        } catch (IOException exception) {
            Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES);
            Files.delete(from);
        }
    }

    private static void removeFolder(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                try {
                    Files.delete(directory);
                } catch (DirectoryNotEmptyException exception) {
                    return FileVisitResult.CONTINUE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void load() {
        if (this.items != null) {
            return;
        }
        this.items = new ArrayList<>();
        Path index = this.root.resolve(INDEX);
        if (!Files.isRegularFile(index)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(index)) {
            List<Item> read = GSON.fromJson(reader, LIST);
            if (read != null) {
                for (Item item : read) {
                    if (item != null && item.id != null && !item.id.isBlank() && item.name != null && !item.name.isBlank()
                            && !item.id.contains("..") && !item.name.contains("/") && !item.name.contains("\\")) {
                        item.original = item.original == null ? "" : item.original;
                        this.items.add(item);
                    }
                }
            }
        } catch (IOException | JsonParseException exception) {
            this.items = new ArrayList<>();
        }
    }

    private void save() throws IOException {
        Files.createDirectories(this.root);
        Path index = this.root.resolve(INDEX);
        AtomicFilePersistence.write(index, index.resolveSibling(INDEX + ".bak"), GSON.toJson(this.items, LIST));
    }
}
