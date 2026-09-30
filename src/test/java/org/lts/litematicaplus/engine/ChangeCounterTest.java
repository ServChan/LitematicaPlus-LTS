package org.lts.litematicaplus.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChangeCounterTest {
    @Test
    void countsChangesOfTheLastMinuteOnly() {
        ChangeCounter counter = new ChangeCounter();
        assertEquals(0, counter.total(1000));
        counter.record(1000);
        counter.record(1000);
        counter.record(1030);
        assertEquals(3, counter.total(1030));
        assertEquals(3, counter.total(1059));
        assertEquals(1, counter.total(1060));
        assertEquals(1, counter.total(1089));
        assertEquals(0, counter.total(1090));
        counter.record(1200);
        assertEquals(1, counter.total(1200));
        assertEquals(0, counter.total(5000));
    }
}
