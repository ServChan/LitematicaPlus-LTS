package org.lts.litematicaplus.mhud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.lts.litematicaplus.materials.MaterialList;
import org.lts.litematicaplus.materials.MaterialReport;

class HudExportTest {
    private static final HudExport.Texts TEXTS = HudExport.Texts.english();

    private static MaterialReport report(List<MaterialReport.Row> rows) {
        return new MaterialReport("Tower *v2*", "2026-11-06 12:00:00", rows);
    }

    @Test
    void breakdownSplitsIntoShulkersStacksAndItems() {
        assertEquals("1 SB + 2 st + 5", HudExport.breakdown(27L * 64 + 2 * 64 + 5, 64, TEXTS));
        assertEquals("3 st", HudExport.breakdown(48L, 16, TEXTS));
        assertEquals("12", HudExport.breakdown(12L, 64, TEXTS));
        assertEquals("1 SB + 3", HudExport.breakdown(30L, 1, TEXTS));
        assertEquals("0", HudExport.breakdown(0L, 64, TEXTS));
        assertEquals("1,234", HudExport.count(1234L, TEXTS));
    }

    @Test
    void linesSkipMarkedAndCollected() {
        MaterialReport report = report(List.of(
                new MaterialReport.Row("Stone", "minecraft:stone", 100, 80, 30, 64, 20),
                new MaterialReport.Row("Glass", "minecraft:glass", 10, 10, 0, 64, MaterialList.Entry.ALL),
                new MaterialReport.Row("Dirt", "minecraft:dirt", 5, 0, 0, 64, 0)));
        List<HudExport.Line> lines = HudExport.lines(report);
        assertEquals(1, lines.size());
        assertEquals(60L, lines.get(0).quantity());
        assertEquals(30L, lines.get(0).covered());
    }

    @Test
    void discordPartsStayUnderTheLimit() {
        List<MaterialReport.Row> rows = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            rows.add(new MaterialReport.Row("Block number " + i, "minecraft:block_" + i, 500, 500, 0, 64, 0));
        }
        List<String> parts = HudExport.discord(report(rows), TEXTS);
        assertTrue(parts.size() > 1);
        int lines = 0;
        for (String part : parts) {
            assertTrue(part.length() <= HudExport.DISCORD_LIMIT, "part of " + part.length());
            assertTrue(part.startsWith("**Tower \\*v2\\***"));
            lines += part.split("\n").length - 1;
        }
        assertEquals(300, lines);
        assertTrue(parts.get(0).contains("(1/" + parts.size() + ")"));
    }

    @Test
    void litematicaListAndTsv() {
        MaterialReport report = report(List.of(
                new MaterialReport.Row("=Stone", "minecraft:stone", 70, 70, 10, 64, 0)));
        assertEquals("# Tower *v2* — Material HUD\nminecraft:stone 70\n", HudExport.litematicaText(report, TEXTS));
        assertTrue(HudExport.litematicaJson(report).contains("\"count\": 70"));
        String[] tsv = HudExport.tsv(report, TEXTS).split("\n");
        assertEquals("'=Stone\t70\t1 st + 6\t10\t60\tminecraft:stone", tsv[1]);
    }

    @Test
    void cardPagesHoldSixtyRows() {
        List<MaterialReport.Row> rows = new ArrayList<>();
        for (int i = 0; i < 61; i++) {
            rows.add(new MaterialReport.Row("Item " + i, "minecraft:item_" + i, 10, 10, i % 3 == 0 ? 10 : 0, 64, 0));
        }
        MaterialReport report = report(rows);
        List<HudExport.Line> lines = HudExport.lines(report);
        assertEquals(2, HudExport.pages(lines.size()));
        BufferedImage first = HudExport.page(report, lines, 0, 2, Map.of(), TEXTS);
        BufferedImage second = HudExport.page(report, lines, 1, 2, Map.of(), TEXTS);
        assertEquals(800, first.getWidth());
        assertTrue(first.getHeight() > second.getHeight());
        assertEquals(0, first.getRGB(0, 0) >>> 24);
    }
}
