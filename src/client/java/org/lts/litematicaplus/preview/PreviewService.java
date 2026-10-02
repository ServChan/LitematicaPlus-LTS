package org.lts.litematicaplus.preview;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.util.Workers;
import org.lwjgl.system.MemoryUtil;

public final class PreviewService {
    public static final int FULL_SIZE = 512;
    public static final int DRAG_SIZE = 224;
    private static final int MAX_ENTRIES = 12;
    private static final int MAX_MODELS = 3;
    private static final int MAX_CACHE_FILES = 400;
    private static final String ALGORITHM = "litematicaplus-preview-1";
    private static final String SIMPLIFIED_SUFFIX = ".simple.png";

    public enum Status {
        LOADING,
        READY,
        EMPTY,
        FAILED
    }

    public record Source(String key, Path file, long size, long modified, Schematic schematic) {
        public static Source file(Path file, long size, long modified) {
            Path normalized = file.toAbsolutePath().normalize();
            return new Source("file:" + normalized + "|" + size + "|" + modified, normalized, size, modified, null);
        }

        public static Source loaded(String key, Schematic schematic) {
            return new Source("loaded:" + key + "|" + System.identityHashCode(schematic), null, 0L, 0L, schematic);
        }
    }

    public static final class Entry {
        private Status status = Status.LOADING;
        private Identifier id;
        private DynamicTexture texture;
        private int textureSize;
        private boolean simplified;

        public Status status() {
            return this.status;
        }

        public Identifier texture() {
            return this.id;
        }

        public int textureSize() {
            return this.textureSize;
        }

        public boolean simplified() {
            return this.simplified;
        }
    }

    private record Request(PreviewRaster.View view, int size) {
    }

    private static final class Work {
        final String key;
        final Source source;
        final boolean detailed;
        final BlockStateModelSet models;
        final BlockColors colors;
        volatile PreviewModel model;
        volatile Request rendered;
        volatile boolean failed;

        Work(String key, Source source, boolean detailed, BlockStateModelSet models, BlockColors colors) {
            this.key = key;
            this.source = source;
            this.detailed = detailed;
            this.models = models;
            this.colors = colors;
        }
    }

    private static final LinkedHashMap<String, Entry> ENTRIES = new LinkedHashMap<>(16, 0.75F, true);
    private static final Map<String, PreviewRaster.View> VIEWS = new HashMap<>();
    private static final Map<String, Request> DESIRED = new ConcurrentHashMap<>();
    private static final Set<String> QUEUED = ConcurrentHashMap.newKeySet();
    private static final Map<String, Work> WORK = new ConcurrentHashMap<>();
    private static final List<String> WORK_ORDER = new ArrayList<>();
    private static volatile String focus;
    private static int textureCounter;
    private static BlockStateModelSet modelIdentity;

    private PreviewService() {
    }

    public static boolean enabled() {
        return LitematicaPlusConfig.current().schematicPreview;
    }

    private static String key(Source source) {
        return source.key() + (LitematicaPlusConfig.current().previewModels ? "|models" : "|cubes");
    }

    public static Entry get(Source source) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();
        if (models != modelIdentity) {
            if (modelIdentity != null) {
                clear();
            }
            modelIdentity = models;
        }
        String key = key(source);
        focus = key;
        Entry entry = ENTRIES.get(key);
        if (entry == null) {
            entry = new Entry();
            ENTRIES.put(key, entry);
            trim();
            DESIRED.put(key, new Request(view(source), FULL_SIZE));
        }
        Work work = WORK.get(key);
        boolean pending = work == null || !work.failed && !Objects.equals(work.rendered, DESIRED.get(key));
        if (pending) {
            schedule(key, source, LitematicaPlusConfig.current().previewModels, models, minecraft.getBlockColors());
        }
        return entry;
    }

    public static PreviewRaster.View view(Source source) {
        return VIEWS.getOrDefault(key(source), PreviewRaster.View.DEFAULT);
    }

    public static void setView(Source source, PreviewRaster.View view, boolean dragging) {
        String key = key(source);
        VIEWS.put(key, view);
        DESIRED.put(key, new Request(view, dragging ? DRAG_SIZE : FULL_SIZE));
        Minecraft minecraft = Minecraft.getInstance();
        schedule(key, source, LitematicaPlusConfig.current().previewModels,
                minecraft.getModelManager().getBlockStateModelSet(), minecraft.getBlockColors());
    }

    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (Entry entry : ENTRIES.values()) {
            release(minecraft, entry);
        }
        ENTRIES.clear();
        DESIRED.clear();
        WORK.clear();
        synchronized (WORK_ORDER) {
            WORK_ORDER.clear();
        }
        PreviewBuilder.clearTextures();
    }

    private static void trim() {
        Minecraft minecraft = Minecraft.getInstance();
        Iterator<Map.Entry<String, Entry>> iterator = ENTRIES.entrySet().iterator();
        while (ENTRIES.size() > MAX_ENTRIES && iterator.hasNext()) {
            Map.Entry<String, Entry> eldest = iterator.next();
            release(minecraft, eldest.getValue());
            DESIRED.remove(eldest.getKey());
            VIEWS.remove(eldest.getKey());
            WORK.remove(eldest.getKey());
            iterator.remove();
        }
    }

    private static void release(Minecraft minecraft, Entry entry) {
        if (entry.id != null) {
            minecraft.getTextureManager().release(entry.id);
            entry.id = null;
            entry.texture = null;
            entry.textureSize = 0;
        }
    }

    private static void schedule(String key, Source source, boolean detailed, BlockStateModelSet models,
            BlockColors colors) {
        if (!QUEUED.add(key)) {
            return;
        }
        WORK.computeIfAbsent(key, ignored -> new Work(key, source, detailed, models, colors));
        try {
            Workers.preview().execute(() -> pump(key));
        } catch (RejectedExecutionException exception) {
            QUEUED.remove(key);
        }
    }

    private static void pump(String key) {
        QUEUED.remove(key);
        Work work = WORK.get(key);
        Request desired = DESIRED.get(key);
        if (work == null || desired == null || work.failed || desired.equals(work.rendered)) {
            return;
        }
        BooleanSupplier cancelled = () -> !key.equals(focus) || Thread.currentThread().isInterrupted();
        if (cancelled.getAsBoolean()) {
            return;
        }
        boolean defaultView = desired.view().equals(PreviewRaster.View.DEFAULT) && desired.size() == FULL_SIZE;
        Path cacheFile = work.source.file() == null ? null : cacheFile(work);
        try {
            if (work.model == null) {
                if (defaultView && cacheFile != null) {
                    if (publishCached(work, desired, cacheFile, false) || publishCached(work, desired,
                            simplifiedVariant(cacheFile), true)) {
                        return;
                    }
                }
                Schematic schematic = work.source.schematic() != null ? work.source.schematic()
                        : SchematicIo.read(work.source.file());
                work.model = PreviewBuilder.build(schematic, work.models, work.colors, work.detailed, cancelled);
                remember(work);
                if (work.model.isEmpty()) {
                    work.rendered = desired;
                    publishStatus(key, Status.EMPTY);
                    return;
                }
            }
            int[] argb = PreviewRaster.render(work.model, desired.size(), desired.view(), cancelled);
            int[] abgr = toAbgr(argb);
            work.rendered = desired;
            publish(key, abgr, desired.size(), work.model.simplified());
            if (defaultView && cacheFile != null) {
                writeCache(work.model.simplified() ? simplifiedVariant(cacheFile) : cacheFile, abgr, desired.size());
            }
        } catch (CancellationException exception) {
            return;
        } catch (IOException | RuntimeException | OutOfMemoryError exception) {
            LitematicaPlusClient.LOGGER.warn("Could not build the schematic preview for {}", key, exception);
            work.failed = true;
            work.model = null;
            publishStatus(key, Status.FAILED);
            return;
        }
        Request latest = DESIRED.get(key);
        if (latest != null && !latest.equals(work.rendered) && QUEUED.add(key)) {
            try {
                Workers.preview().execute(() -> pump(key));
            } catch (RejectedExecutionException exception) {
                QUEUED.remove(key);
            }
        }
    }

    private static boolean publishCached(Work work, Request desired, Path file, boolean simplified) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try (InputStream stream = Files.newInputStream(file); NativeImage image = NativeImage.read(stream)) {
            if (image.getWidth() != desired.size() || image.getHeight() != desired.size()) {
                return false;
            }
            int[] abgr = image.getPixelsABGR();
            work.rendered = desired;
            publish(work.key, abgr, desired.size(), simplified);
            return true;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private static void remember(Work work) {
        synchronized (WORK_ORDER) {
            WORK_ORDER.remove(work.key);
            WORK_ORDER.add(work.key);
            while (WORK_ORDER.size() > MAX_MODELS) {
                Work old = WORK.get(WORK_ORDER.remove(0));
                if (old != null) {
                    old.model = null;
                    old.rendered = null;
                }
            }
        }
    }

    static int[] toAbgr(int[] argb) {
        int[] result = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            int color = argb[i];
            result[i] = color & 0xFF00FF00 | (color & 0xFF0000) >>> 16 | (color & 0xFF) << 16;
        }
        return result;
    }

    private static void publishStatus(String key, Status status) {
        Minecraft.getInstance().execute(() -> {
            Entry entry = ENTRIES.get(key);
            if (entry != null) {
                entry.status = status;
            }
        });
    }

    private static void publish(String key, int[] abgr, int size, boolean simplified) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            Entry entry = ENTRIES.get(key);
            if (entry == null) {
                return;
            }
            try {
                if (entry.texture == null || entry.textureSize != size) {
                    release(minecraft, entry);
                    NativeImage image = new NativeImage(size, size, false);
                    DynamicTexture texture = new DynamicTexture(() -> "LitematicaPlus schematic preview", image);
                    Identifier id = Identifier.fromNamespaceAndPath(LitematicaPlusClient.MOD_ID,
                            "preview/" + ++textureCounter);
                    minecraft.getTextureManager().register(id, texture);
                    entry.id = id;
                    entry.texture = texture;
                    entry.textureSize = size;
                }
                NativeImage pixels = entry.texture.getPixels();
                if (pixels == null) {
                    return;
                }
                MemoryUtil.memIntBuffer(pixels.getPointer(), abgr.length).put(abgr);
                entry.texture.upload();
                entry.simplified = simplified;
                entry.status = Status.READY;
            } catch (RuntimeException exception) {
                LitematicaPlusClient.LOGGER.warn("Could not upload the schematic preview", exception);
                release(minecraft, entry);
                entry.status = Status.FAILED;
            }
        });
    }

    private static Path cacheDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("litematicaplus").resolve("previews");
    }

    private static Path cacheFile(Work work) {
        String input = ALGORITHM + '\n' + FULL_SIZE + '\n' + work.detailed + '\n' + work.source.file() + '\n'
                + work.source.size() + '\n' + work.source.modified();
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
            return cacheDirectory().resolve(hash + ".png");
        } catch (NoSuchAlgorithmException impossible) {
            return null;
        }
    }

    private static Path simplifiedVariant(Path file) {
        String name = file.getFileName().toString();
        return file.resolveSibling(name.substring(0, name.length() - 4) + SIMPLIFIED_SUFFIX);
    }

    private static void writeCache(Path file, int[] abgr, int size) {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (NativeImage image = new NativeImage(size, size, false)) {
            Files.createDirectories(file.getParent());
            MemoryUtil.memIntBuffer(image.getPointer(), abgr.length).put(abgr);
            image.writeToFile(temp);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            trimCache(file.getParent());
        } catch (IOException | RuntimeException exception) {
            LitematicaPlusClient.LOGGER.debug("Could not write the preview cache {}", file, exception);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
        }
    }

    private static void trimCache(Path directory) throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.list(directory)) {
            files = new ArrayList<>(stream.filter(path -> path.getFileName().toString().endsWith(".png")).toList());
        }
        if (files.size() <= MAX_CACHE_FILES) {
            return;
        }
        Map<Path, Long> times = new HashMap<>();
        for (Path path : files) {
            try {
                times.put(path, Files.getLastModifiedTime(path).toMillis());
            } catch (IOException exception) {
                times.put(path, 0L);
            }
        }
        files.sort(Comparator.comparingLong(times::get));
        for (int i = 0; i < files.size() - MAX_CACHE_FILES; i++) {
            Files.deleteIfExists(files.get(i));
        }
    }
}
