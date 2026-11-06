package org.lts.litematicaplus.materials;

import java.util.Locale;

public final class CountParser {
    public static final long INVALID = -1L;
    private static final long LIMIT = 1_000_000_000_000L;
    private static final int SHULKER_SLOTS = 27;

    private CountParser() {
    }

    public static long parse(String text, int stackSize) {
        if (text == null) {
            return INVALID;
        }
        String clean = text.strip().toLowerCase(Locale.ROOT).replace(" ", "").replace(" ", "");
        if (clean.isEmpty()) {
            return 0L;
        }
        int stack = Math.max(1, stackSize);
        long total = 0L;
        for (String term : clean.split("\\+", -1)) {
            long value = term(term, stack);
            if (value < 0L) {
                return INVALID;
            }
            total += value;
            if (total > LIMIT) {
                return INVALID;
            }
        }
        return total;
    }

    private static long term(String term, int stack) {
        if (term.isEmpty()) {
            return INVALID;
        }
        long unit = 1L;
        String body = term;
        String[][] units = {
            {"шалкер", "шалк", "шал", "ш", "shulkers", "shulker", "sh", "sb"},
            {"стаков", "стака", "стак", "ст", "stacks", "stack", "st", "s"}
        };
        long[] sizes = {(long) stack * SHULKER_SLOTS, stack};
        outer:
        for (int kind = 0; kind < units.length; kind++) {
            for (String suffix : units[kind]) {
                if (body.endsWith(suffix) && body.length() > suffix.length()) {
                    unit = sizes[kind];
                    body = body.substring(0, body.length() - suffix.length());
                    break outer;
                }
            }
        }
        long product = 1L;
        for (String factor : body.split("[*xх]", -1)) {
            if (factor.isEmpty() || factor.length() > 13 || !factor.chars().allMatch(Character::isDigit)) {
                return INVALID;
            }
            product *= Long.parseLong(factor);
            if (product > LIMIT) {
                return INVALID;
            }
        }
        long result = product * unit;
        return result > LIMIT ? INVALID : result;
    }
}
