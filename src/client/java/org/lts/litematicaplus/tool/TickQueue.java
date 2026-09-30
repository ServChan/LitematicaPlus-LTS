package org.lts.litematicaplus.tool;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.lts.litematicaplus.LitematicaPlusClient;

public final class TickQueue {
    private static final int MAX_PENDING = 512;
    private static final List<Entry> PENDING = new ArrayList<>();
    private static long currentTick;

    private record Entry(long dueTick, Runnable task) {
    }

    private TickQueue() {
    }

    public static boolean worldReady(Minecraft minecraft) {
        return minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.isAlive()
                && !minecraft.player.isSpectator() && minecraft.level != null && minecraft.gameMode != null;
    }

    public static long now() {
        return currentTick;
    }

    public static int pending() {
        return PENDING.size();
    }

    public static boolean after(int ticks, Runnable task) {
        if (PENDING.size() >= MAX_PENDING) {
            return false;
        }
        PENDING.add(new Entry(currentTick + Math.max(1, ticks), task));
        return true;
    }

    public static void clear() {
        PENDING.clear();
    }

    public static void tick(Minecraft minecraft) {
        currentTick++;
        if (PENDING.isEmpty()) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) {
            PENDING.clear();
            return;
        }
        List<Runnable> due = new ArrayList<>();
        Iterator<Entry> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.dueTick() <= currentTick) {
                iterator.remove();
                due.add(entry.task());
            }
        }
        for (Runnable task : due) {
            try {
                task.run();
            } catch (RuntimeException exception) {
                LitematicaPlusClient.LOGGER.error("A delayed easy place step failed", exception);
            }
        }
    }
}
