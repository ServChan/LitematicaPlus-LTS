package org.lts.litematicaplus.materials;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.util.Workers;

public final class MaterialExport {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final int MAX_NAME_LENGTH = 64;

    public enum Format {
        XLSX("xlsx"),
        CSV("csv"),
        MARKDOWN("md"),
        JSON("json");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return this.extension;
        }
    }

    public record Labels(String sheetSummary, String sheetNeeded, String sheetAll, String schematic, String exportedAt,
            String itemTypes, String total, String missing, String toGather, String stacks, String shulkers,
            String[] columns) {
        public static Labels capture() {
            return new Labels(text("sheet_summary"), text("sheet_needed"), text("sheet_all"), text("schematic"),
                    text("exported_at"), text("item_types"), text("total"), text("missing"), text("to_gather"),
                    text("stacks"), text("shulkers"), new String[] {text("column.item"), text("column.id"),
                        text("column.total"), text("column.missing"), text("column.available"), text("column.to_gather"),
                        text("column.stacks"), text("column.shulkers"), text("column.stack_size")});
        }

        private static String text(String key) {
            return Component.translatable("litematicaplus.export." + key).getString();
        }
    }

    private MaterialExport() {
    }

    public static Path directory() {
        return FabricLoader.getInstance().getGameDir().resolve("litematica-plus").resolve("materials");
    }

    public static String fileName(String title, LocalDateTime now, Format format) {
        String cleaned = title == null ? "" : title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
        while (cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).strip();
        }
        if (cleaned.isEmpty()) {
            cleaned = "materials";
        }
        if (cleaned.length() > MAX_NAME_LENGTH) {
            cleaned = cleaned.substring(0, MAX_NAME_LENGTH).strip();
        }
        return cleaned + "_" + FILE_TIME.format(now) + "." + format.extension();
    }

    public static CompletableFuture<Path> export(MaterialReport report, Labels labels, Format format, LocalDateTime now) {
        Path first = directory().resolve(fileName(report.title(), now, format));
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path target = unique(first);
                write(report, labels, format, target);
                return target;
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }, Workers.io());
    }

    static Path unique(Path target) {
        if (!java.nio.file.Files.exists(target)) {
            return target;
        }
        String name = target.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot < 0 ? name : name.substring(0, dot);
        String extension = dot < 0 ? "" : name.substring(dot);
        for (int suffix = 2; suffix < 1000; suffix++) {
            Path candidate = target.resolveSibling(stem + "-" + suffix + extension);
            if (!java.nio.file.Files.exists(candidate)) {
                return candidate;
            }
        }
        return target;
    }

    public static void write(MaterialReport report, Labels labels, Format format, Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        switch (format) {
            case XLSX -> workbook(report, labels).write(target);
            case CSV -> Files.writeString(target, csv(report, labels), StandardCharsets.UTF_8);
            case MARKDOWN -> Files.writeString(target, markdown(report, labels), StandardCharsets.UTF_8);
            case JSON -> Files.writeString(target, json(report), StandardCharsets.UTF_8);
        }
    }

    static XlsxWriter workbook(MaterialReport report, Labels labels) {
        XlsxWriter workbook = new XlsxWriter();
        workbook.sheet(labels.sheetSummary()).widths(34, 26)
                .title(report.title())
                .blank()
                .row(labels.schematic(), report.title())
                .row(labels.exportedAt(), report.exportedAt())
                .row(labels.itemTypes(), report.rows().size())
                .row(labels.total(), report.total())
                .row(labels.missing(), report.missing())
                .row(labels.toGather(), report.toGather())
                .row(labels.stacks(), report.stacks())
                .row(labels.shulkers(), report.shulkerBoxes());
        table(workbook.sheet(labels.sheetNeeded()), report.needed(), labels);
        table(workbook.sheet(labels.sheetAll()), report.rows(), labels);
        return workbook;
    }

    private static void table(XlsxWriter.Sheet sheet, List<MaterialReport.Row> rows, Labels labels) {
        sheet.widths(32, 34, 11, 11, 14, 12, 10, 16, 12).header(labels.columns());
        for (MaterialReport.Row row : rows) {
            sheet.row(row.name(), row.id(), row.total(), row.missing(), row.available(), row.toGather(), row.stacks(),
                    row.shulkerBoxes(), row.stackSize());
        }
    }

    static String csv(MaterialReport report, Labels labels) {
        StringBuilder out = new StringBuilder("\uFEFF");
        csvLine(out, labels.columns());
        for (MaterialReport.Row row : report.rows()) {
            csvLine(out, row.name(), row.id(), Long.toString(row.total()), Long.toString(row.missing()),
                    Long.toString(row.available()), Long.toString(row.toGather()), Long.toString(row.stacks()),
                    decimal(row.shulkerBoxes()), Integer.toString(row.stackSize()));
        }
        return out.toString();
    }

    private static void csvLine(StringBuilder out, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            String value = values[i];
            if (i < 2 && !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
                value = "'" + value;
            }
            if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                out.append('"').append(value.replace("\"", "\"\"")).append('"');
            } else {
                out.append(value);
            }
        }
        out.append("\r\n");
    }

    static String markdown(MaterialReport report, Labels labels) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(cell(report.title())).append("\n\n");
        out.append("- ").append(labels.exportedAt()).append(": ").append(report.exportedAt()).append('\n');
        out.append("- ").append(labels.total()).append(": ").append(report.total()).append('\n');
        out.append("- ").append(labels.missing()).append(": ").append(report.missing()).append('\n');
        out.append("- ").append(labels.toGather()).append(": ").append(report.toGather()).append('\n');
        out.append("- ").append(labels.stacks()).append(": ").append(report.stacks()).append('\n');
        out.append("- ").append(labels.shulkers()).append(": ").append(report.shulkerBoxes()).append("\n\n|");
        for (String column : labels.columns()) {
            out.append(' ').append(cell(column)).append(" |");
        }
        out.append("\n|");
        for (int i = 0; i < labels.columns().length; i++) {
            out.append(i < 2 ? " --- |" : " ---: |");
        }
        out.append('\n');
        for (MaterialReport.Row row : report.rows()) {
            out.append("| ").append(cell(row.name())).append(" | `").append(row.id()).append("` | ").append(row.total())
                    .append(" | ").append(row.missing()).append(" | ").append(row.available()).append(" | ")
                    .append(row.toGather()).append(" | ").append(row.stacks()).append(" | ")
                    .append(decimal(row.shulkerBoxes())).append(" | ").append(row.stackSize()).append(" |\n");
        }
        return out.toString();
    }

    private static String cell(String value) {
        return value.replace("|", "\\|").replace('\n', ' ').replace('\r', ' ');
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    static String json(MaterialReport report) {
        JsonObject root = new JsonObject();
        root.addProperty("schematic", report.title());
        root.addProperty("exportedAt", report.exportedAt());
        root.addProperty("total", report.total());
        root.addProperty("missing", report.missing());
        root.addProperty("toGather", report.toGather());
        root.addProperty("stacks", report.stacks());
        root.addProperty("shulkerBoxes", report.shulkerBoxes());
        JsonArray items = new JsonArray();
        for (MaterialReport.Row row : report.rows()) {
            JsonObject item = new JsonObject();
            item.addProperty("id", row.id());
            item.addProperty("name", row.name());
            item.addProperty("total", row.total());
            item.addProperty("missing", row.missing());
            item.addProperty("available", row.available());
            item.addProperty("toGather", row.toGather());
            item.addProperty("stacks", row.stacks());
            item.addProperty("shulkerBoxes", row.shulkerBoxes());
            item.addProperty("stackSize", row.stackSize());
            items.add(item);
        }
        root.add("items", items);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
    }
}
