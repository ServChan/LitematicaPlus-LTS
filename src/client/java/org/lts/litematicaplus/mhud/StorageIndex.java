package org.lts.litematicaplus.mhud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.Session;
import org.lts.litematicaplus.util.AtomicFilePersistence;
import org.lts.litematicaplus.util.Workers;

public final class StorageIndex {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Type DATA_TYPE = new TypeToken<LinkedHashMap<String, Record>>() { }.getType();
    private static final int LIMIT = 20000;
    private static final StorageIndex INSTANCE = new StorageIndex();

    public static final class Record {
        public String scope = "";
        public int x;
        public int y;
        public int z;
        public int pairX;
        public int pairY;
        public int pairZ;
        public boolean paired;
        public Map<String, Long> counts = new LinkedHashMap<>();
        public long updatedAt;

        public BlockPos pos() {
            return new BlockPos(this.x, this.y, this.z);
        }

        public BlockPos pair() {
            return this.paired ? new BlockPos(this.pairX, this.pairY, this.pairZ) : null;
        }
    }

    private final LinkedHashMap<String, Record> records = new LinkedHashMap<>();
    private boolean loaded;
    private boolean dirty;
    private long revision;

    private StorageIndex() {
    }

    public static StorageIndex get() {
        return INSTANCE;
    }

    public static String scope(Minecraft minecraft) {
        return minecraft.level == null ? "" : Session.world(minecraft) + "|" + Session.dimension(minecraft);
    }

    public static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    public synchronized long revision() {
        return this.revision;
    }

    public synchronized void record(Minecraft minecraft, BlockPos pos, BlockPos pair, Reference2LongMap<Item> counts) {
        ensureLoaded();
        String scope = scope(minecraft);
        if (scope.isEmpty()) {
            return;
        }
        BlockPos canonical = pair != null && compare(pair, pos) < 0 ? pair : pos;
        BlockPos other = pair == null ? null : canonical.equals(pos) ? pair : pos;
        Map<String, Long> ids = new LinkedHashMap<>();
        for (Reference2LongMap.Entry<Item> entry : counts.reference2LongEntrySet()) {
            ids.merge(itemId(entry.getKey()), entry.getLongValue(), Long::sum);
        }
        String key = key(scope, canonical);
        if (other != null) {
            this.records.remove(key(scope, other));
        }
        Record previous = this.records.get(key);
        if (previous != null && previous.counts.equals(ids) && previous.paired == (other != null)) {
            return;
        }
        Record record = new Record();
        record.scope = scope;
        record.x = canonical.getX();
        record.y = canonical.getY();
        record.z = canonical.getZ();
        if (other != null) {
            record.paired = true;
            record.pairX = other.getX();
            record.pairY = other.getY();
            record.pairZ = other.getZ();
        }
        record.counts = ids;
        record.updatedAt = System.currentTimeMillis();
        this.records.remove(key);
        this.records.put(key, record);
        while (this.records.size() > LIMIT) {
            this.records.remove(this.records.keySet().iterator().next());
        }
        this.dirty = true;
        this.revision++;
    }

    public synchronized void forget(Minecraft minecraft, BlockPos pos) {
        ensureLoaded();
        if (this.records.remove(key(scope(minecraft), pos)) != null) {
            this.dirty = true;
            this.revision++;
        }
    }

    public synchronized Record at(String scope, BlockPos pos) {
        ensureLoaded();
        return this.records.get(key(scope, pos));
    }

    public synchronized List<Record> inScope(String scope) {
        ensureLoaded();
        List<Record> result = new ArrayList<>();
        for (Record record : this.records.values()) {
            if (scope.equals(record.scope)) {
                result.add(record);
            }
        }
        return result;
    }

    public synchronized long stored(String scope, String itemId) {
        ensureLoaded();
        long sum = 0L;
        for (Record record : this.records.values()) {
            if (scope.equals(record.scope)) {
                Long count = record.counts.get(itemId);
                if (count != null) {
                    sum += count;
                }
            }
        }
        return sum;
    }

    public void saveIfDirty() {
        String json;
        synchronized (this) {
            if (!this.dirty) {
                return;
            }
            this.dirty = false;
            json = GSON.toJson(new LinkedHashMap<>(this.records), DATA_TYPE);
        }
        try {
            Workers.writer().execute(() -> write(json));
        } catch (RejectedExecutionException exception) {
            write(json);
        }
    }

    private static void write(String json) {
        Path path = path();
        try {
            AtomicFilePersistence.write(path, backup(path), json);
        } catch (IOException exception) {
            LitematicaPlusClient.LOGGER.warn("Could not save the storage index {}", path, exception);
        }
    }

    public synchronized void preload() {
        ensureLoaded();
    }

    private void ensureLoaded() {
        if (this.loaded) {
            return;
        }
        this.loaded = true;
        Path path = path();
        Map<String, Record> read = read(path);
        if (read == null) {
            read = read(backup(path));
        }
        if (read != null) {
            read.forEach((key, record) -> {
                if (key != null && record != null && record.scope != null && record.counts != null) {
                    this.records.put(key, record);
                }
            });
        }
    }

    private static Map<String, Record> read(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            return GSON.fromJson(reader, DATA_TYPE);
        } catch (IOException | JsonParseException exception) {
            LitematicaPlusClient.LOGGER.warn("Could not read the storage index {}", path, exception);
            return null;
        }
    }

    private static String key(String scope, BlockPos pos) {
        return scope + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static int compare(BlockPos a, BlockPos b) {
        int x = Integer.compare(a.getX(), b.getX());
        if (x != 0) {
            return x;
        }
        int y = Integer.compare(a.getY(), b.getY());
        return y != 0 ? y : Integer.compare(a.getZ(), b.getZ());
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("litematicaplus-storage-index.json");
    }

    private static Path backup(Path path) {
        return path.resolveSibling(path.getFileName() + ".bak");
    }
}
