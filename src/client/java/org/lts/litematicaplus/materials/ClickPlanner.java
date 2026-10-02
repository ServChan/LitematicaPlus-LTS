package org.lts.litematicaplus.materials;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ClickPlanner {
    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int MAX_SPLIT_CLICKS = 40;

    public record Click(int slot, int button) {
    }

    public record Source(int slot, int count) {
    }

    public sealed interface Step permits Whole, Split {
        int slot();
    }

    public record Whole(int slot, int count) implements Step {
    }

    public record Split(int slot, int count, int amount) implements Step {
    }

    private ClickPlanner() {
    }

    public static List<Step> plan(List<Source> sources, long wanted) {
        List<Step> steps = new ArrayList<>();
        if (wanted <= 0L || sources.isEmpty()) {
            return steps;
        }
        List<Source> ordered = new ArrayList<>(sources);
        ordered.removeIf(source -> source.count() <= 0);
        ordered.sort(Comparator.comparingInt(Source::count).reversed());
        long remaining = wanted;
        List<Source> leftover = new ArrayList<>();
        for (Source source : ordered) {
            if (remaining <= 0L) {
                break;
            }
            if (source.count() <= remaining) {
                steps.add(new Whole(source.slot(), source.count()));
                remaining -= source.count();
            } else {
                leftover.add(source);
            }
        }
        if (remaining > 0L && !leftover.isEmpty()) {
            Source best = leftover.get(0);
            for (Source source : leftover) {
                if (source.count() < best.count()) {
                    best = source;
                }
            }
            steps.add(new Split(best.slot(), best.count(), (int) remaining));
        }
        return steps;
    }

    public static int cost(int count, int amount) {
        if (amount <= 0) {
            return 0;
        }
        if (amount >= count) {
            return 2;
        }
        int half = (count + 1) / 2;
        int best = Math.min(amount + 2, count - amount + 2);
        int halving = half >= amount ? half - amount + 2 : 2 + cost(count - half, amount - half);
        return Math.min(best, halving);
    }

    public static List<Click> split(int source, int count, int amount, int target) {
        List<Click> clicks = new ArrayList<>();
        build(source, count, amount, target, clicks);
        return clicks;
    }

    private static void build(int source, int count, int amount, int target, List<Click> out) {
        if (amount <= 0) {
            return;
        }
        if (amount >= count) {
            out.add(new Click(source, LEFT));
            out.add(new Click(target, LEFT));
            return;
        }
        int half = (count + 1) / 2;
        int placeOut = amount + 2;
        int putBack = count - amount + 2;
        int halving = half >= amount ? half - amount + 2 : 2 + cost(count - half, amount - half);
        if (halving < placeOut && halving < putBack) {
            out.add(new Click(source, RIGHT));
            if (half >= amount) {
                for (int i = 0; i < half - amount; i++) {
                    out.add(new Click(source, RIGHT));
                }
                out.add(new Click(target, LEFT));
            } else {
                out.add(new Click(target, LEFT));
                build(source, count - half, amount - half, target, out);
            }
        } else if (placeOut <= putBack) {
            out.add(new Click(source, LEFT));
            for (int i = 0; i < amount; i++) {
                out.add(new Click(target, RIGHT));
            }
            out.add(new Click(source, LEFT));
        } else {
            out.add(new Click(source, LEFT));
            for (int i = 0; i < count - amount; i++) {
                out.add(new Click(source, RIGHT));
            }
            out.add(new Click(target, LEFT));
        }
    }
}
