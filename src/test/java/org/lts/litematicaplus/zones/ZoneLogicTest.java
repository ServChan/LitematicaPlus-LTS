package org.lts.litematicaplus.zones;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ZoneLogicTest {
    @Test
    void splitCoversTheBoxWithoutGapsOrOverlaps() {
        ZonePlan plan = new ZonePlan(3, 2, 2);
        List<ZonePlan.Zone> zones = plan.split(-5, 60, 10, 26, 79, 40);
        assertEquals(12, zones.size());
        for (int x = -5; x <= 26; x++) {
            for (int y = 60; y <= 79; y++) {
                for (int z = 10; z <= 40; z++) {
                    int owners = 0;
                    for (ZonePlan.Zone zone : zones) {
                        if (zone.contains(x, y, z)) {
                            owners++;
                        }
                    }
                    assertEquals(1, owners, x + " " + y + " " + z);
                }
            }
        }
        assertEquals(1, zones.get(0).index());
        assertEquals(-5, zones.get(0).minX());
        assertEquals(60, zones.get(0).minY());
        assertEquals(12, zones.get(11).index());
        assertEquals(26, zones.get(11).maxX());
    }

    @Test
    void zoneRangeIsTheZoneBox() {
        ZonePlan.Zone zone = new ZonePlan(2, 1, 2).split(0, 0, 0, 9, 9, 9).get(3);
        assertTrue(zone.range().contains(9, 9, 0));
        assertFalse(zone.range().contains(4, 9, 0));
        assertFalse(zone.range().contains(9, 4, 0));
        ZonePlan.Zone flat = new ZonePlan(2, 1, 1).split(0, 0, 0, 9, 9, 9).get(0);
        assertTrue(flat.range().contains(0, 9, 9));
    }

    @Test
    void tinyBoxesNeverProduceEmptyZones() {
        List<ZonePlan.Zone> zones = new ZonePlan(16, 16, 16).split(0, 0, 0, 2, 0, 1);
        assertEquals(3 * 2, zones.size());
    }

    @Test
    void messagesRoundTripInsideAnyChatFormat() {
        String text = ZoneProtocol.encode("a1b2c3", "Builder_7", "claim", 3);
        assertTrue(ZoneProtocol.mentions(text));
        ZoneProtocol.Message message = ZoneProtocol.parse("Builder_7 whispers to you: " + text);
        assertNotNull(message);
        assertEquals("a1b2c3", message.plan());
        assertEquals("Builder_7", message.sender());
        assertEquals("claim", message.op());
        assertArrayEquals(new int[]{3}, message.args());
        ZoneProtocol.Message plan = ZoneProtocol.parse("[Clan] " + ZoneProtocol.encode("ffffff", "A", "plan", 4, 2, 1));
        assertArrayEquals(new int[]{4, 2, 1}, plan.args());
        assertNull(ZoneProtocol.parse("[LP-zones nothex Bob] claim 1"));
        assertNull(ZoneProtocol.parse("plain chat"));
    }

    @Test
    void teamParsingDropsSelfDuplicatesAndJunk() {
        assertEquals(List.of("Ivan", "petr_2"), ZoneProtocol.team("Ivan, petr_2 ;Me ivan  bad-name Petr_2", "me"));
    }

    @Test
    void transportsNeverBroadcast() {
        assertFalse(ZoneProtocol.broadcasts(ZoneProtocol.template(ZoneProtocol.Transport.MESSAGE, "")));
        assertFalse(ZoneProtocol.broadcasts(ZoneProtocol.template(ZoneProtocol.Transport.TEAM, "")));
        assertFalse(ZoneProtocol.broadcasts(ZoneProtocol.template(ZoneProtocol.Transport.CUSTOM, "/clan chat")));
        assertTrue(ZoneProtocol.broadcasts(ZoneProtocol.template(ZoneProtocol.Transport.CUSTOM, "/say")));
        assertTrue(ZoneProtocol.broadcasts(ZoneProtocol.template(ZoneProtocol.Transport.CUSTOM, "minecraft:tellraw @a")));
        assertTrue(ZoneProtocol.broadcasts(ZoneProtocol.template(ZoneProtocol.Transport.CUSTOM, "")));
        assertEquals("clan chat {text}", ZoneProtocol.template(ZoneProtocol.Transport.CUSTOM, "//clan chat"));
    }

    @Test
    void commandsGoToEachTeammateOrOnce() {
        List<String> team = List.of("Ivan", "Petr");
        assertEquals(List.of("msg Ivan hi", "msg Petr hi"),
                ZoneProtocol.commands(ZoneProtocol.template(ZoneProtocol.Transport.MESSAGE, ""), team, "hi"));
        assertEquals(List.of("teammsg hi"),
                ZoneProtocol.commands(ZoneProtocol.template(ZoneProtocol.Transport.TEAM, ""), team, "hi"));
        assertEquals(List.of(), ZoneProtocol.commands("msg {player} {text}", team, "x".repeat(300)));
    }

    @Test
    void planIdsAreShortAndStable() {
        String id = ZoneProtocol.planId("house|120|3x2x1");
        assertEquals(6, id.length());
        assertEquals(id, ZoneProtocol.planId("house|120|3x2x1"));
        assertFalse(id.equals(ZoneProtocol.planId("house|120|3x2x2")));
    }
}
