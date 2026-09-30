package org.lts.litematicaplus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.Direction;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.selection.SelectionManager;
import org.lts.litematicaplus.tool.ToolController;
import org.lts.litematicaplus.tool.ToolMode;
import org.lts.litematicaplus.world.LayerRange;

public final class Session {
    private static String scope = "";
    private static boolean dirty;
    private static boolean restoring;
    private static int saveCooldown;

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
        if (dirty && !scope.isEmpty()) {
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
        data.toolMode = ToolController.get().mode().name();
        return data;
    }

    private static void restore() {
        restoring = true;
        try {
            if (scope.isEmpty()) {
                PlacementManager.get().restore(new SessionStore.ScopeData());
                SelectionManager.get().restore(new SessionStore.AreaData());
                GhostEngine.get().setLayer(LayerRange.ALL);
                return;
            }
            SessionStore.ScopeData data = SessionStore.get().scope(scope);
            PlacementManager.get().restore(data);
            SelectionManager.get().restore(data.area);
            GhostEngine.get().setLayer(new LayerRange(parse(LayerRange.Mode.class, data.layer.mode, LayerRange.Mode.ALL),
                    parse(Direction.Axis.class, data.layer.axis, Direction.Axis.Y),
                    data.layer.single, data.layer.min, data.layer.max));
            ToolController.get().setMode(parse(ToolMode.class, data.toolMode, ToolMode.AREA_SELECTION));
        } finally {
            restoring = false;
            dirty = false;
        }
    }

    private static String currentScope(Minecraft minecraft) {
        if (minecraft.level == null) {
            return "";
        }
        String server;
        ServerData data = minecraft.getCurrentServer();
        if (data != null) {
            server = data.ip;
        } else if (minecraft.getSingleplayerServer() != null) {
            server = "singleplayer:" + minecraft.getSingleplayerServer().getWorldData().getLevelName();
        } else {
            server = "local";
        }
        return server + "|" + minecraft.level.dimension().identifier();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return fallback;
        }
    }
}
