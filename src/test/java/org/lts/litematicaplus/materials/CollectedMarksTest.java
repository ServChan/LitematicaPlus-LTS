package org.lts.litematicaplus.materials;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CollectedMarksTest {
    private static final MaterialExport.Labels LABELS = new MaterialExport.Labels("Summary", "To gather", "All materials",
            "Schematic", "Exported", "Item types", "Items", "Missing", "Still to gather", "Stacks", "Shulker boxes",
            new String[] {"Item", "ID", "Total", "Missing", "In inventory", "To gather", "Stacks", "Shulker boxes",
                "Stack size"});

    @Test
    void countsAcceptStacksShulkersAndSums() {
        assertEquals(300L, CountParser.parse("300", 64));
        assertEquals(320L, CountParser.parse("5st", 64));
        assertEquals(330L, CountParser.parse("5 ст + 10", 64));
        assertEquals(64L * 27L * 2L, CountParser.parse("2ш", 64));
        assertEquals(48L, CountParser.parse("3*16", 64));
        assertEquals(32L, CountParser.parse("2stacks", 16));
        assertEquals(0L, CountParser.parse("  ", 64));
        assertEquals(CountParser.INVALID, CountParser.parse("abc", 64));
        assertEquals(CountParser.INVALID, CountParser.parse("5+", 64));
        assertEquals(CountParser.INVALID, CountParser.parse("-3", 64));
        assertEquals(CountParser.INVALID, CountParser.parse("99999999999999999", 64));
    }

    @Test
    void manualCountsAndMarksCoverTheShortfall() {
        MaterialList.Entry plain = new MaterialList.Entry(null, 500L, 400L, 100L, 100L, 0L);
        assertEquals(300L, plain.shortfall());
        MaterialList.Entry manual = new MaterialList.Entry(null, 500L, 400L, 100L, 100L, 120L);
        assertEquals(180L, manual.shortfall());
        assertTrue(manual.manual());
        assertFalse(manual.marked());
        MaterialList.Entry over = new MaterialList.Entry(null, 500L, 400L, 100L, 100L, 1000L);
        assertEquals(0L, over.shortfall());
        MaterialList.Entry marked = new MaterialList.Entry(null, 500L, 400L, 0L, 0L, MaterialList.Entry.ALL);
        assertEquals(0L, marked.shortfall());
        assertTrue(marked.marked());
        assertFalse(marked.manual());
    }

    @Test
    void exportAddsTheCollectedColumnOnlyWhenUsed() {
        List<MaterialReport.Row> rows = new ArrayList<>();
        rows.add(new MaterialReport.Row("Stone", "minecraft:stone", 100L, 80L, 10L, 64));
        MaterialReport plain = new MaterialReport("Test", "2026-11-06 12:00:00", rows);
        assertFalse(MaterialExport.csv(plain, LABELS).contains("Collected by hand"));
        rows.add(new MaterialReport.Row("Glass", "minecraft:glass", 50L, 50L, 0L, 64, 20L));
        rows.add(new MaterialReport.Row("Dirt", "minecraft:dirt", 10L, 10L, 0L, 64, MaterialList.Entry.ALL));
        MaterialReport marked = new MaterialReport("Test", "2026-11-06 12:00:00", rows);
        String csv = MaterialExport.csv(marked, LABELS);
        String[] lines = csv.split("\r\n");
        assertTrue(lines[0].endsWith(",Collected by hand"), lines[0]);
        assertTrue(lines[2].endsWith(",30,1,0.02,64,20"), lines[2]);
        assertTrue(lines[3].endsWith(",0,0,0.00,64,all"), lines[3]);
        assertEquals(70L + 30L, marked.toGather());
    }

    @Test
    void rawSectionIsWrittenToMarkdownAndJson() {
        List<MaterialReport.Row> rows = List.of(new MaterialReport.Row("Slab", "minecraft:pale_oak_slab", 100L, 100L, 0L, 64));
        MaterialReport report = new MaterialReport("Test", "2026-11-06 12:00:00", rows).withRaw(new MaterialReport.RawSection(
                List.of(new MaterialReport.RawRow("Log", "minecraft:pale_oak_log", 13L, 0L, 13L, 64)),
                List.of(new MaterialReport.StepRow("Slab", "minecraft:pale_oak_slab", 100L, 0L, 17L, 102L, 2L, "3 Planks -> 6"))));
        String markdown = MaterialExport.markdown(report, LABELS);
        assertTrue(markdown.contains("## Raw materials"), markdown);
        assertTrue(markdown.contains("| Log | `minecraft:pale_oak_log` | 13 | 0 | 13 | 1 |"), markdown);
        assertTrue(markdown.contains("| 17 | 102 | 2 | 3 Planks -> 6 |"), markdown);
        String json = MaterialExport.json(report);
        assertTrue(json.contains("\"crafting\""));
        assertTrue(json.contains("\"toGet\": 13"));
    }
}
