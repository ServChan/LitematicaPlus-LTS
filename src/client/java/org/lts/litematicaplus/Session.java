package org.lts.litematicaplus;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.selection.SelectionManager;
import org.lts.litematicaplus.share.SyncmaticaClient;
import org.lts.litematicaplus.tool.ToolController;
import org.lts.litematicaplus.tool.ToolMode;
import org.lts.litematicaplus.world.LayerRange;

public final class Session {
    private static String scope = "";
    private static boolean dirty;
    private static boolean restoring;
    private static int saveCooldown;
    private static volatile boolean seedKnown;
    private static volatile long seed;

    private Session() {
    }

    public static void init() {
        SessionStore.get().load();
        PlacementManager.get().setChangeListener(Session::markDirty);
        SelectionManager.get().setChangeListener(Session::markDirty);
    }

    public static String scope() {
        return scope;
    }

    public static void markDirty() {
        if (!restoring) {
            dirty = true;
        }
    }

    public static void tick(Minecraft minecraft) {
        String current = currentScope(minecraft);
        if (!current.equals(scope)) {
            flush();
            scope = current;
            restore();
        }
        if (dirty && --saveCooldown <= 0) {
            flush();
        }
    }

    public static void flush() {
        if (!dirty || scope.isEmpty()) {
            dirty = false;
            return;
        }
        dirty = false;
        saveCooldown = 20;
        SessionStore.get().save(scope, snapshot());
    }

    public static void shutdown() {
        if (!scope.isEmpty()) {
            dirty = false;
            SessionStore.get().saveBlocking(scope, snapshot());
        }
    }

    private static SessionStore.ScopeData snapshot() {
        SessionStore.ScopeData data = new SessionStore.ScopeData();
        PlacementManager.get().snapshot(data);
        data.area = SelectionManager.get().snapshot();
        LayerRange layer = GhostEngine.get().layer();
        data.layer.mode = layer.mode().name();
        data.layer.axis = layer.axis().name();
        data.layer.single = layer.single();
        data.layer.min = layer.min();
        data.layer.max = layer.max();
        LayerRange.Clip clip = layer.clip();
        data.layer.clipEnabled = clip.enabled();
        data.layer.clipX = clip.xEnabled();
        data.layer.clipMinX = clip.minX();
        data.layer.clipMaxX = clip.maxX();
        data.layer.clipZ = clip.zEnabled();
        data.layer.clipMinZ = clip.minZ();
        data.layer.clipMaxZ = clip.maxZ();
        data.toolMode = ToolController.get().mode().name();
        for (Block block : GhostEngine.get().hiddenBlocks()) {
            data.hiddenBlocks.add(BuiltInRegistries.BLOCK.getKey(block).toString());
        }
        data.hiddenBlocks.sort(null);
        return data;
    }

    private static void restore() {
        restoring = true;
        try {
            if (scope.isEmpty()) {
                PlacementManager.get().restore(new SessionStore.ScopeData());
                SelectionManager.get().restore(new SessionStore.AreaData());
                GhostEngine.get().setLayer(LayerRange.ALL);
                GhostEngine.get().setClip(LayerRange.Clip.NONE);
                GhostEngine.get().setHiddenBlocks(Set.of());
                return;
            }
            SessionStore.ScopeData data = SessionStore.get().scope(scope, legacyScope(Minecraft.getInstance()));
            PlacementManager.get().restore(data);
            SelectionManager.get().restore(data.area);
            GhostEngine.get().setLayer(new LayerRange(parse(LayerRange.Mode.class, data.layer.mode, LayerRange.Mode.ALL),
                    parse(Direction.Axis.class, data.layer.axis, Direction.Axis.Y),
                    data.layer.single, data.layer.min, data.layer.max));
            GhostEngine.get().setClip(new LayerRange.Clip(data.layer.clipEnabled, data.layer.clipX, data.layer.clipMinX,
                    data.layer.clipMaxX, data.layer.clipZ, data.layer.clipMinZ, data.layer.clipMaxZ));
            GhostEngine.get().setHiddenBlocks(blocks(data.hiddenBlocks));
            ToolMode restored = parse(ToolMode.class, data.toolMode, ToolMode.AREA_SELECTION);
            ToolController.get().setMode(restored.changesWorld() ? ToolMode.AREA_SELECTION : restored);
        } finally {
            restoring = false;
            dirty = false;
        }
        SyncmaticaClient.get().reconcile();
    }

    private static List<Block> blocks(List<String> ids) {
        List<Block> blocks = new ArrayList<>();
        for (String id : ids) {
            Identifier parsed = Identifier.tryParse(id);
            if (parsed != null) {
                BuiltInRegistries.BLOCK.getOptional(parsed).ifPresent(blocks::add);
            }
        }
        return blocks;
    }

    private static String currentScope(Minecraft minecraft) {
        if (minecraft.level == null) {
            return "";
        }
        return world(minecraft) + "|" + dimension(minecraft);
    }

    private static String legacyScope(Minecraft minecraft) {
        return minecraft.level == null ? null : address(minecraft) + "|" + dimension(minecraft);
    }

    public static void setSeed(long value) {
        seed = value;
        seedKnown = true;
    }

    public static String seedSuffix() {
        return seedKnown ? "|seed:" + Long.toUnsignedString(seed, 16) : "";
    }

    public static String world(Minecraft minecraft) {
        return address(minecraft) + seedSuffix();
    }

    public static String address(Minecraft minecraft) {
        ServerData data = minecraft.getCurrentServer();
        if (data != null) {
            return data.ip;
        }
        if (minecraft.getSingleplayerServer() != null) {
            Path root = minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            return "singleplayer:" + (root.getFileName() == null ? "world" : root.getFileName().toString());
        }
        return "local";
    }

    private static String labelFor = "";
    private static String label = "";

    public static String label(Minecraft minecraft) {
        if (scope.isEmpty()) {
            return "";
        }
        String key = scope + "|" + System.identityHashCode(Language.getInstance());
        if (!key.equals(labelFor)) {
            labelFor = key;
            label = worldName(minecraft) + " \u00b7 " + dimensionName(minecraft);
        }
        return label;
    }

    private static String worldName(Minecraft minecraft) {
        ServerData data = minecraft.getCurrentServer();
        if (data != null) {
            return data.name == null || data.name.isBlank() || data.name.equals(data.ip) ? data.ip
                    : data.name + " (" + data.ip + ")";
        }
        if (minecraft.getSingleplayerServer() != null) {
            String name = minecraft.getSingleplayerServer().getWorldData().getLevelName();
            return name == null || name.isBlank() ? address(minecraft) : name;
        }
        return address(minecraft);
    }

    private static String dimensionName(Minecraft minecraft) {
        String id = dimension(minecraft);
        String key = "litematicaplus.dimension." + id.replace(':', '.');
        return Language.getInstance().has(key)
                ? Component.translatable(key).getString() : id;
    }

    public static String dimension(Minecraft minecraft) {
        return minecraft.level == null ? "" : minecraft.level.dimension().identifier().toString();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return fallback;
        }
    }
}
