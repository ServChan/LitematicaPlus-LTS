package org.lts.litematicaplus.compat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.lts.litematicaplus.LitematicaPlusClient;

public final class Addons {
    public static final int MAX_PROBLEMS = 200;

    public record Problem(Addon addon, String key, Object[] args, String detail, long firstTime, long lastTime,
            int repeats) {
        boolean sameAs(Addon otherAddon, String otherKey, String otherDetail) {
            return this.addon == otherAddon && this.key.equals(otherKey) && this.detail.equals(otherDetail);
        }
    }

    private static final Deque<Problem> PROBLEMS = new ArrayDeque<>();
    private static volatile int revision;
    private static volatile boolean jadeRegistered;

    private Addons() {
    }

    public static void report(Addon addon, String key, Throwable failure, Object... args) {
        String detail = failure == null ? "" : failure.toString();
        report(addon, key, detail, args);
    }

    public static void report(Addon addon, String key, String detail, Object... args) {
        String clean = Objects.requireNonNullElse(detail, "");
        LitematicaPlusClient.LOGGER.warn("[{}] {} {}", addon.key(), key, clean);
        long now = System.currentTimeMillis();
        synchronized (PROBLEMS) {
            Iterator<Problem> iterator = PROBLEMS.iterator();
            while (iterator.hasNext()) {
                Problem existing = iterator.next();
                if (existing.sameAs(addon, key, clean)) {
                    iterator.remove();
                    PROBLEMS.addLast(new Problem(addon, key, args, clean, existing.firstTime(), now,
                            existing.repeats() + 1));
                    revision++;
                    return;
                }
            }
            if (PROBLEMS.size() >= MAX_PROBLEMS) {
                PROBLEMS.removeFirst();
            }
            PROBLEMS.addLast(new Problem(addon, key, args, clean, now, now, 1));
            revision++;
        }
    }

    public static List<Problem> problems(Addon addon) {
        List<Problem> result = new ArrayList<>();
        synchronized (PROBLEMS) {
            Iterator<Problem> iterator = PROBLEMS.descendingIterator();
            while (iterator.hasNext()) {
                Problem problem = iterator.next();
                if (addon == null || problem.addon() == addon) {
                    result.add(problem);
                }
            }
        }
        return result;
    }

    public static int count(Addon addon) {
        synchronized (PROBLEMS) {
            int count = 0;
            for (Problem problem : PROBLEMS) {
                if (addon == null || problem.addon() == addon) {
                    count++;
                }
            }
            return count;
        }
    }

    public static void clear(Addon addon) {
        synchronized (PROBLEMS) {
            PROBLEMS.removeIf(problem -> addon == null || problem.addon() == addon);
            revision++;
        }
    }

    public static void markJadeRegistered() {
        jadeRegistered = true;
    }

    public static boolean jadeRegistered() {
        return jadeRegistered;
    }

    public static int revision() {
        return revision;
    }

    public static boolean needsAttention() {
        for (Addon addon : Addon.values()) {
            Addon.State state = addon.state();
            if (state == Addon.State.FAILED || state == Addon.State.CONFLICT || count(addon) > 0) {
                return true;
            }
        }
        return false;
    }
}
