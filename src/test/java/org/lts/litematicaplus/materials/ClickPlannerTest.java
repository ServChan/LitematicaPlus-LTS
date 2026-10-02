package org.lts.litematicaplus.materials;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClickPlannerTest {
    private static final int MAX = 64;

    private static int[] simulate(int count, int targetStart, List<ClickPlanner.Click> clicks) {
        int[] slots = {count, targetStart};
        int cursor = 0;
        for (ClickPlanner.Click click : clicks) {
            int index = click.slot();
            if (cursor == 0) {
                int take = click.button() == ClickPlanner.LEFT ? slots[index] : (slots[index] + 1) / 2;
                cursor = take;
                slots[index] -= take;
            } else if (click.button() == ClickPlanner.LEFT) {
                int moved = Math.min(cursor, MAX - slots[index]);
                slots[index] += moved;
                cursor -= moved;
            } else {
                if (slots[index] < MAX) {
                    slots[index]++;
                    cursor--;
                }
            }
        }
        return new int[] {slots[0], slots[1], cursor};
    }

    @Test
    void everySplitLeavesTheCursorEmptyAndMovesExactlyTheAmount() {
        for (int count = 2; count <= MAX; count++) {
            for (int amount = 1; amount < count; amount++) {
                for (int targetStart : new int[] {0, MAX - amount}) {
                    List<ClickPlanner.Click> clicks = ClickPlanner.split(0, count, amount, 1);
                    int[] result = simulate(count, targetStart, clicks);
                    assertEquals(count - amount, result[0], "source " + count + "/" + amount);
                    assertEquals(targetStart + amount, result[1], "target " + count + "/" + amount);
                    assertEquals(0, result[2], "cursor " + count + "/" + amount);
                    assertEquals(ClickPlanner.cost(count, amount), clicks.size(), "cost " + count + "/" + amount);
                    assertTrue(clicks.size() <= ClickPlanner.MAX_SPLIT_CLICKS, "clicks " + count + "/" + amount);
                }
            }
        }
    }

    @Test
    void wholeStacksAreUsedBeforeOneSplit() {
        List<ClickPlanner.Step> steps = ClickPlanner.plan(List.of(new ClickPlanner.Source(3, 64),
                new ClickPlanner.Source(5, 20), new ClickPlanner.Source(7, 64)), 100);
        assertEquals(List.of(new ClickPlanner.Whole(3, 64), new ClickPlanner.Whole(5, 20),
                new ClickPlanner.Split(7, 64, 16)), steps);
        assertEquals(List.of(new ClickPlanner.Whole(5, 20)), ClickPlanner.plan(List.of(new ClickPlanner.Source(5, 20)), 20));
        assertTrue(ClickPlanner.plan(List.of(new ClickPlanner.Source(5, 20)), 0).isEmpty());
    }
}
