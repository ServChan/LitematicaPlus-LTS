package org.lts.litematicaplus.materials;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

class MaterialExportTest {
    private static final MaterialExport.Labels LABELS = new MaterialExport.Labels("Summary", "To gather", "All materials",
            "Schematic", "Exported", "Item types", "Items", "Missing", "Still to gather", "Stacks", "Shulker boxes",
            new String[] {"Item", "ID", "Total", "Missing", "In inventory", "To gather", "Stacks", "Shulker boxes",
                "Stack size"});

    private static MaterialReport report() {
        List<MaterialReport.Row> rows = new ArrayList<>();
        rows.add(new MaterialReport.Row("Stone Bricks", "minecraft:stone_bricks", 2000L, 1800L, 72L, 64));
        rows.add(new MaterialReport.Row("Oak Sign, \"quoted\" <b>", "minecraft:oak_sign", 20L, 20L, 0L, 16));
        rows.add(new MaterialReport.Row("Ender Pearl", "minecraft:ender_pearl", 5L, 0L, 0L, 16));
        rows.add(new MaterialReport.Row("=HYPERLINK(\"x\")", "minecraft:test", 1L, 1L, 4L, 1));
        return new MaterialReport("Castle & <Keep>", "2026-09-30 12:00:00", rows);
    }

    @Test
    void reportMathCountsStacksAndShulkerBoxes() {
        MaterialReport report = report();
        MaterialReport.Row bricks = report.rows().get(0);
        assertEquals(1728L, bricks.toGather());
        assertEquals(27L, bricks.stacks());
        assertEquals(1.0, bricks.shulkerBoxes());
        MaterialReport.Row signs = report.rows().get(1);
        assertEquals(2L, signs.stacks());
        assertEquals(0L, report.rows().get(3).toGather());
        assertEquals(2, report.needed().size());
        assertEquals(2026L, report.total());
        assertEquals(1821L, report.missing());
        assertEquals(1748L, report.toGather());
        assertEquals(29L, report.stacks());
        assertEquals(2L, report.shulkerBoxes());
    }

    @Test
    void csvQuotesSeparatorsAndDefusesFormulas() {
        String csv = MaterialExport.csv(report(), LABELS);
        assertTrue(csv.startsWith("﻿Item,ID,Total"));
        String[] lines = csv.split("\r\n");
        assertEquals(5, lines.length);
        assertEquals("Stone Bricks,minecraft:stone_bricks,2000,1800,72,1728,27,1.00,64", lines[1]);
        assertTrue(lines[2].startsWith("\"Oak Sign, \"\"quoted\"\" <b>\",minecraft:oak_sign,"));
        assertTrue(lines[4].startsWith("\"'=HYPERLINK(\"\"x\"\")\","), lines[4]);
    }

    @Test
    void markdownHasOneTableRowPerItem() {
        String markdown = MaterialExport.markdown(report(), LABELS);
        assertTrue(markdown.startsWith("# Castle & <Keep>\n"));
        assertTrue(markdown.contains("| Stone Bricks | `minecraft:stone_bricks` | 2000 | 1800 | 72 | 1728 | 27 | 1.00 | 64 |"));
        long rows = markdown.lines().filter(line -> line.startsWith("| ")).count();
        assertEquals(6L, rows);
    }

    @Test
    void jsonIsParsableAndComplete() {
        JsonObject root = JsonParser.parseString(MaterialExport.json(report())).getAsJsonObject();
        assertEquals("Castle & <Keep>", root.get("schematic").getAsString());
        assertEquals(1748L, root.get("toGather").getAsLong());
        assertEquals(4, root.getAsJsonArray("items").size());
        assertEquals("minecraft:stone_bricks", root.getAsJsonArray("items").get(0).getAsJsonObject().get("id").getAsString());
    }

    @Test
    void xlsxIsAZipOfWellFormedParts(@TempDir Path directory) throws Exception {
        Path target = directory.resolve("list.xlsx");
        MaterialExport.write(report(), LABELS, MaterialExport.Format.XLSX, target);
        Map<String, byte[]> parts = unzip(target);
        for (String name : List.of("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels",
                "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml", "xl/worksheets/sheet3.xml")) {
            assertNotNull(parts.get(name), name);
            assertNotNull(parse(parts.get(name)), name);
        }
        Document all = parse(parts.get("xl/worksheets/sheet3.xml"));
        assertEquals(5, all.getElementsByTagName("row").getLength());
        assertEquals("A1:I5", all.getElementsByTagName("autoFilter").item(0).getAttributes().getNamedItem("ref").getNodeValue());
        Document needed = parse(parts.get("xl/worksheets/sheet2.xml"));
        assertEquals(3, needed.getElementsByTagName("row").getLength());
        Document workbook = parse(parts.get("xl/workbook.xml"));
        assertEquals(3, workbook.getElementsByTagName("sheet").getLength());
        String summary = new String(parts.get("xl/worksheets/sheet1.xml"), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(summary.contains("Castle &amp; &lt;Keep&gt;"));
    }

    @Test
    void otherFormatsAreWrittenToDisk(@TempDir Path directory) throws IOException {
        for (MaterialExport.Format format : MaterialExport.Format.values()) {
            Path target = directory.resolve("nested").resolve("list." + format.extension());
            MaterialExport.write(report(), LABELS, format, target);
            assertTrue(Files.size(target) > 50L, format.name());
        }
    }

    @Test
    void fileNamesAreSafe() {
        LocalDateTime time = LocalDateTime.of(2026, 9, 30, 8, 5, 3);
        assertEquals("My_ Castle__2026-09-30_08-05-03.xlsx",
                MaterialExport.fileName("My: Castle?.", time, MaterialExport.Format.XLSX));
        assertEquals("materials_2026-09-30_08-05-03.md", MaterialExport.fileName("  ", time, MaterialExport.Format.MARKDOWN));
        String name = MaterialExport.fileName("x".repeat(300), time, MaterialExport.Format.CSV);
        assertTrue(name.length() < 100);
        assertFalse(MaterialExport.fileName("a/b\\c", time, MaterialExport.Format.JSON).contains("/"));
    }

    @Test
    void sheetNamesAreValidAndUnique() {
        XlsxWriter writer = new XlsxWriter();
        writer.sheet("To gather");
        List<XlsxWriter.Sheet> existing = List.of(writer.sheet("A very long sheet name that exceeds the limit of Excel"));
        assertEquals("A B", XlsxWriter.sheetName("A/B", List.of()));
        assertEquals("Sheet", XlsxWriter.sheetName("[]", List.of()));
        assertTrue(XlsxWriter.sheetName("A very long sheet name that exceeds the limit of Excel", existing).length() <= 31);
        assertEquals("A", XlsxWriter.column(0));
        assertEquals("Z", XlsxWriter.column(25));
        assertEquals("AA", XlsxWriter.column(26));
        assertEquals("a&amp;b&lt;c", XlsxWriter.escape("a&b<c\u0001"));
    }

    private static Map<String, byte[]> unzip(Path file) throws IOException {
        Map<String, byte[]> parts = new HashMap<>();
        try (InputStream input = Files.newInputStream(file); ZipInputStream zip = new ZipInputStream(input)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                parts.put(entry.getName(), zip.readAllBytes());
            }
        }
        return parts;
    }

    private static Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }
}
