package org.lts.litematicaplus.compat;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Item;
import org.lts.litematicaplus.LitematicaPlusClient;

public final class StackCalculatorBridge {
    private static final Method SELECT;
    private static final Method AMOUNT;
    private static final Method EXPAND;
    private static final Method STACK_MODE;
    private static final Class<?> STACK_MODE_TYPE;

    static {
        Method select = null;
        Method amount = null;
        Method expand = null;
        Method stackMode = null;
        Class<?> stackModeType = null;
        if (FabricLoader.getInstance().isModLoaded("stackcalculator")) {
            try {
                Class<?> memory = Class.forName("org.lts.stackcalculator.CalculatorMemory");
                select = memory.getMethod("setSelectedItemId", String.class);
                amount = memory.getMethod("remember", String.class);
                expand = memory.getMethod("setExpanded", boolean.class);
                stackModeType = Class.forName("org.lts.stackcalculator.StackMode");
                stackMode = memory.getMethod("setStackMode", stackModeType);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                Addons.report(Addon.STACK_CALCULATOR, "litematicaplus.addon.problem.unreadable", exception);
                select = null;
                amount = null;
                expand = null;
                stackMode = null;
                stackModeType = null;
            }
        }
        SELECT = select;
        AMOUNT = amount;
        EXPAND = expand;
        STACK_MODE = stackMode;
        STACK_MODE_TYPE = stackModeType;
    }

    private StackCalculatorBridge() {
    }

    public static boolean available() {
        return SELECT != null && AMOUNT != null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object mode(String name) {
        return Enum.valueOf((Class) STACK_MODE_TYPE, name);
    }

    public static boolean show(Item item, long count) {
        if (!available()) {
            return false;
        }
        try {
            SELECT.invoke(null, "");
            AMOUNT.invoke(null, Long.toString(Math.max(0L, count)));
            if (STACK_MODE != null) {
                int size = item.getDefaultMaxStackSize();
                STACK_MODE.invoke(null, mode(size >= 64 ? "STACK_64" : size >= 16 ? "STACK_16" : "STACK_1"));
            }
            if (EXPAND != null) {
                EXPAND.invoke(null, true);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            Addons.report(Addon.STACK_CALCULATOR, "litematicaplus.addon.problem.call_failed", exception);
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.gui.screen();
        if (screen != null) {
            screen.resize(screen.width, screen.height);
        }
        return true;
    }
}
