package org.lts.litematicaplus.compat;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import org.lts.litematicaplus.LitematicaPlusClient;

public final class InventoryLocks {
    private static final Object HANDLER;
    private static final Method LOCKED;
    private static boolean failureLogged;

    static {
        Object handler = null;
        Method locked = null;
        if (FabricLoader.getInstance().isModLoaded("inventoryprofilesnext")) {
            try {
                Class<?> type = Class.forName("org.anti_ad.mc.ipnext.event.LockSlotsHandler");
                handler = type.getField("INSTANCE").get(null);
                locked = type.getMethod("isSlotLocked", int.class);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                Addons.report(Addon.INVENTORY_PROFILES, "litematicaplus.addon.problem.unreadable", exception);
                handler = null;
                locked = null;
            }
        }
        HANDLER = handler;
        LOCKED = locked;
    }

    private InventoryLocks() {
    }

    public static boolean available() {
        return LOCKED != null;
    }

    public static boolean locked(int inventorySlot) {
        if (LOCKED == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(LOCKED.invoke(HANDLER, inventorySlot));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            if (!failureLogged) {
                failureLogged = true;
                Addons.report(Addon.INVENTORY_PROFILES, "litematicaplus.addon.problem.call_failed", exception);
            }
            return false;
        }
    }
}
