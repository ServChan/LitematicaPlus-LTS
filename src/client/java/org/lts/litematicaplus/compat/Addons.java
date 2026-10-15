package org.lts.litematicaplus.compat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.lts.litematicaplus.ModLog;

public final class Addons {
    public static final int MAX_PROBLEMS = 200;

    public record Problem(Addon addon, String key, Object[] args, String detail, long firstTime, long lastTime,
            int repeats) {
        boolean sameAs(Addon otherAddon, String otherKey, String otherDetail) {
            return this.addon == otherAddon && this.key.equals(otherKey) && this.detail.equals(otherDetail);
        }
    }

    private static final Deque<Problem> PROBLEMS = new ArrayDeque<>();
    private static final EnumMap<Addon, ModLog> LOGGERS = loggers();
    private static volatile int revision;
    private static volatile boolean jadeRegistered;

    private Addons() {
    }

    public static void report(Addon addon, String key, Throwable failure, Object... args) {
        String detail = failure == null ? "" : failure.toString();
        record(addon, key, detail, failure, args);
    }

    public static void report(Addon addon, String key, String detail, Object... args) {
        record(addon, key, detail, null, args);
    }

    private static void record(Addon addon, String key, String detail, Throwable failure, Object[] args) {
        String clean = Objects.requireNonNullElse(detail, "");
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
        log(addon, key, failure, args);
    }

    private static void log(Addon addon, String key, Throwable failure, Object[] args) {
        String problem = key.startsWith("litematicaplus.addon.problem.")
                ? key.substring("litematicaplus.addon.problem.".length()) : key;
        String message = switch (problem) {
            case "unreadable" -> "Could not connect to the mod; the integration is off";
            case "call_failed" -> "Could not call into the mod";
            case "lod_unreadable" -> "Could not reach the Voxy LOD depth";
            case "lod_read_failed" -> "Could not read the Voxy LOD depth";
            case "lod_merge_failed" -> "Could not merge the Voxy LOD depth; using the fallback";
            case "world_cache_unreadable" -> "Could not connect to VoxyMap world data for checking";
            case "share_prepare_failed" -> "Could not prepare shared schematic {} for sending";
            case "cache_unreadable" -> "Could not read the downloaded shared schematics folder";
            case "save_failed" -> "Could not save shared schematic {}";
            case "rejected" -> "Server rejected the connection; Syncmatica {} is too old";
            case "share_failed" -> "Server did not accept shared schematic {}";
            case "download_failed" -> "Could not download shared schematic {}";
            case "bad_packet" -> "Could not read Syncmatica packet {}";
            default -> "Problem " + problem + " {}";
        };
        int holes = message.contains("{}") ? 1 : 0;
        Object[] values = new Object[holes + (failure == null ? 0 : 1)];
        for (int index = 0; index < holes; index++) {
            values[index] = args == null || index >= args.length ? "?" : args[index];
        }
        if (failure != null) {
            values[holes] = failure;
        }
        LOGGERS.get(addon).warn(message, values);
    }

    private static EnumMap<Addon, ModLog> loggers() {
        EnumMap<Addon, ModLog> loggers = new EnumMap<>(Addon.class);
        for (Addon addon : Addon.values()) {
            String area = switch (addon) {
                case SYNCMATICA -> "Syncmatica";
                case VOXYMAP -> "VoxyMap";
                case VOXY -> "Voxy";
                case IRIS -> "Iris";
                case SODIUM -> "Sodium";
                case JADE -> "Jade";
                case STACK_CALCULATOR -> "StackCalculator";
                case INVENTORY_PROFILES -> "InventoryProfiles";
                case MOD_MENU -> "ModMenu";
                case AXIOM -> "Axiom";
                case LITEMATICA -> "Litematica";
            };
            loggers.put(addon, ModLog.of(area));
        }
        return loggers;
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
