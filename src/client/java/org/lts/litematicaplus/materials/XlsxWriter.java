package org.lts.litematicaplus.materials;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class XlsxWriter {
    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    private static final String MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String RELATIONS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String PACKAGE_RELATIONS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String SHEET_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml";
    private static final int STYLE_TEXT = 0;
    private static final int STYLE_HEADER = 1;
    private static final int STYLE_INTEGER = 2;
    private static final int STYLE_DECIMAL = 3;
    private static final int STYLE_TITLE = 4;

    private final List<Sheet> sheets = new ArrayList<>();

    private record Cell(String text, double number, int style) {
    }

    public static final class Sheet {
        private final String name;
        private final List<List<Cell>> rows = new ArrayList<>();
        private double[] widths = new double[0];
        private int headerRow = -1;
        private int headerColumns;

        private Sheet(String name) {
            this.name = name;
        }

        public Sheet widths(double... values) {
            this.widths = values.clone();
            return this;
        }

        public Sheet title(String text) {
            this.rows.add(List.of(new Cell(text, 0.0, STYLE_TITLE)));
            return this;
        }

        public Sheet blank() {
            this.rows.add(List.of());
            return this;
        }

        public Sheet header(String... names) {
            List<Cell> row = new ArrayList<>(names.length);
            for (String value : names) {
                row.add(new Cell(value, 0.0, STYLE_HEADER));
            }
            this.headerRow = this.rows.size();
            this.headerColumns = names.length;
            this.rows.add(row);
            return this;
        }

        public Sheet row(Object... values) {
            List<Cell> row = new ArrayList<>(values.length);
            for (Object value : values) {
                if (value instanceof Double || value instanceof Float) {
                    row.add(new Cell(null, ((Number) value).doubleValue(), STYLE_DECIMAL));
                } else if (value instanceof Number number) {
                    row.add(new Cell(null, number.doubleValue(), STYLE_INTEGER));
                } else {
                    row.add(new Cell(value == null ? "" : value.toString(), 0.0, STYLE_TEXT));
                }
            }
            this.rows.add(row);
            return this;
        }

        private String filterRange() {
            if (this.headerRow < 0 || this.headerColumns == 0) {
                return null;
            }
            return "A" + (this.headerRow + 1) + ":" + column(this.headerColumns - 1)
                    + Math.max(this.rows.size(), this.headerRow + 1);
        }

        private String xml() {
            StringBuilder out = new StringBuilder(XML).append("<worksheet xmlns=\"").append(MAIN).append("\">");
            if (this.headerRow >= 0) {
                out.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"").append(this.headerRow + 1)
                        .append("\" topLeftCell=\"A").append(this.headerRow + 2)
                        .append("\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>");
            }
            if (this.widths.length > 0) {
                out.append("<cols>");
                for (int i = 0; i < this.widths.length; i++) {
                    out.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1).append("\" width=\"")
                            .append(this.widths[i]).append("\" customWidth=\"1\"/>");
                }
                out.append("</cols>");
            }
            out.append("<sheetData>");
            for (int r = 0; r < this.rows.size(); r++) {
                List<Cell> row = this.rows.get(r);
                out.append("<row r=\"").append(r + 1).append("\">");
                for (int c = 0; c < row.size(); c++) {
                    Cell cell = row.get(c);
                    out.append("<c r=\"").append(column(c)).append(r + 1).append("\" s=\"").append(cell.style()).append('"');
                    if (cell.text() == null) {
                        out.append("><v>").append(number(cell.number())).append("</v></c>");
                    } else {
                        out.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(escape(cell.text()))
                                .append("</t></is></c>");
                    }
                }
                out.append("</row>");
            }
            out.append("</sheetData>");
            String filter = filterRange();
            if (filter != null) {
                out.append("<autoFilter ref=\"").append(filter).append("\"/>");
            }
            return out.append("</worksheet>").toString();
        }
    }

    public Sheet sheet(String name) {
        Sheet sheet = new Sheet(sheetName(name, this.sheets));
        this.sheets.add(sheet);
        return sheet;
    }

    public void write(Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream file = Files.newOutputStream(target);
                ZipOutputStream zip = new ZipOutputStream(file, StandardCharsets.UTF_8)) {
            entry(zip, "[Content_Types].xml", contentTypes());
            entry(zip, "_rels/.rels", XML + "<Relationships xmlns=\"" + PACKAGE_RELATIONS + "\"><Relationship Id=\"rId1\" Type=\""
                    + RELATIONS + "/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entry(zip, "xl/workbook.xml", workbook());
            entry(zip, "xl/_rels/workbook.xml.rels", workbookRelations());
            entry(zip, "xl/styles.xml", styles());
            for (int i = 0; i < this.sheets.size(); i++) {
                entry(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", this.sheets.get(i).xml());
            }
        }
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String contentTypes() {
        StringBuilder out = new StringBuilder(XML)
                .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"").append(SHEET_TYPE).append(".sheet.main+xml\"/>")
                .append("<Override PartName=\"/xl/styles.xml\" ContentType=\"").append(SHEET_TYPE).append(".styles+xml\"/>");
        for (int i = 0; i < this.sheets.size(); i++) {
            out.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1).append(".xml\" ContentType=\"")
                    .append(SHEET_TYPE).append(".worksheet+xml\"/>");
        }
        return out.append("</Types>").toString();
    }

    private String workbook() {
        StringBuilder out = new StringBuilder(XML).append("<workbook xmlns=\"").append(MAIN).append("\" xmlns:r=\"")
                .append(RELATIONS).append("\"><sheets>");
        for (int i = 0; i < this.sheets.size(); i++) {
            out.append("<sheet name=\"").append(escape(this.sheets.get(i).name)).append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        out.append("</sheets><definedNames>");
        for (int i = 0; i < this.sheets.size(); i++) {
            Sheet sheet = this.sheets.get(i);
            String range = sheet.filterRange();
            if (range != null) {
                out.append("<definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"").append(i).append("\" hidden=\"1\">'")
                        .append(escape(sheet.name.replace("'", "''"))).append("'!").append(absolute(range))
                        .append("</definedName>");
            }
        }
        return out.append("</definedNames></workbook>").toString();
    }

    private String workbookRelations() {
        StringBuilder out = new StringBuilder(XML).append("<Relationships xmlns=\"").append(PACKAGE_RELATIONS).append("\">");
        for (int i = 0; i < this.sheets.size(); i++) {
            out.append("<Relationship Id=\"rId").append(i + 1).append("\" Type=\"").append(RELATIONS)
                    .append("/worksheet\" Target=\"worksheets/sheet").append(i + 1).append(".xml\"/>");
        }
        return out.append("<Relationship Id=\"rId").append(this.sheets.size() + 1).append("\" Type=\"").append(RELATIONS)
                .append("/styles\" Target=\"styles.xml\"/></Relationships>").toString();
    }

    private static String styles() {
        return XML + "<styleSheet xmlns=\"" + MAIN + "\">"
                + "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"0.00\"/></numFmts>"
                + "<fonts count=\"3\">"
                + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"14\"/><name val=\"Calibri\"/></font>"
                + "</fonts>"
                + "<fills count=\"3\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1F4258\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "</fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"5\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
                + "<xf numFmtId=\"3\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
                + "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }

    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> {
                    if (c == '\t' || c == '\n' || c == '\r' || c >= 0x20 && c != 0xFFFE && c != 0xFFFF) {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    static String sheetName(String wanted, List<Sheet> existing) {
        String cleaned = wanted == null ? "" : wanted.replaceAll("[\\\\/?*\\[\\]:]", " ").strip();
        if (cleaned.isEmpty()) {
            cleaned = "Sheet";
        }
        if (cleaned.length() > 31) {
            cleaned = cleaned.substring(0, 31).strip();
        }
        String candidate = cleaned;
        int suffix = 2;
        while (taken(candidate, existing)) {
            String tail = " " + suffix++;
            candidate = cleaned.substring(0, Math.min(cleaned.length(), 31 - tail.length())) + tail;
        }
        return candidate;
    }

    private static boolean taken(String name, List<Sheet> existing) {
        for (Sheet sheet : existing) {
            if (sheet.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    static String column(int index) {
        StringBuilder name = new StringBuilder();
        for (int value = index + 1; value > 0; value = (value - 1) / 26) {
            name.insert(0, (char) ('A' + (value - 1) % 26));
        }
        return name.toString();
    }

    private static String absolute(String range) {
        StringBuilder out = new StringBuilder();
        for (String cell : range.split(":")) {
            int split = 0;
            while (split < cell.length() && Character.isLetter(cell.charAt(split))) {
                split++;
            }
            if (!out.isEmpty()) {
                out.append(':');
            }
            out.append('$').append(cell, 0, split).append('$').append(cell.substring(split));
        }
        return out.toString();
    }

    private static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0E15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
