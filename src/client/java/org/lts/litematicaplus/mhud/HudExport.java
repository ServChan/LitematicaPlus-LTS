package org.lts.litematicaplus.mhud;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.materials.MaterialReport;

public final class HudExport {
    public static final int DISCORD_LIMIT = 2000;
    public static final int ROWS_PER_PAGE = 60;
    static final int SHULKER_SLOTS = 27;
    private static final String MARKDOWN = "\\*_~`|>";

    private static final int WIDTH = 800;
    private static final int PAD = 24;
    private static final int HEADER_H = 112;
    private static final int COLUMNS_H = 30;
    private static final int ROW_H = 40;
    private static final int FOOTER_H = 44;
    private static final int ICON = 32;
    private static final int RADIUS = 16;
    private static final int NAME_X = PAD + ICON + 14;
    private static final int COUNT_RIGHT = 560;
    private static final int BREAKDOWN_X = 580;

    private static final int VIOLET = 0x6D28D9;
    private static final int VIOLET_DEEP = 0x3F1178;
    private static final int VIOLET_NIGHT = 0x2A0B52;
    private static final int VIOLET_ZEBRA = 0x350E65;
    private static final int WHITE = 0xFFFFFF;
    private static final int SUBTITLE = 0xE1C8FF;
    private static final int TEXT_ON_DARK = 0xD4ADFF;
    private static final int BADGE = 0xEFD8FF;
    private static final int OVERLINE = 0xC292FF;
    private static final int GRID = 0x8B5BF7;
    private static final int GREEN = 0x4ADE80;
    private static final int AMBER = 0xFFC36B;
    private static final int RED = 0xFF8A80;

    public record Line(String name, String id, long quantity, long covered, int stackSize) {
        public Line {
            stackSize = Math.max(1, stackSize);
        }
    }

    public record Texts(String summary, String box, String stack, String thousands, String decimal,
            String discordHeader, String discordLine, String discordPart, String listComment, String meta,
            String colItem, String colCount, String colBreakdown, String colHave, String colMissing, String colId,
            String legendHave, String legendPart, String legendNone, String page, String brand) {
        public static Texts english() {
            return new Texts("%s items · %s blocks · ≈ %s shulkers", "%s SB", "%s st", ",", ".", "**%s** — %s",
                    "• %s — %s", " (%s/%s)", "%s — Material HUD", "%s · %s", "Item", "Need", "Breakdown", "Have",
                    "To gather", "ID", "have all", "partly", "none", " · page %s/%s", "Material HUD");
        }

        public static Texts capture() {
            Texts fallback = english();
            return new Texts(t("summary", fallback.summary()), t("box", fallback.box()), t("stack", fallback.stack()),
                    t("thousands", fallback.thousands()), t("decimal", fallback.decimal()),
                    t("discord.header", fallback.discordHeader()), t("discord.line", fallback.discordLine()),
                    t("discord.part", fallback.discordPart()), t("list.comment", fallback.listComment()),
                    t("meta", fallback.meta()), t("column.item", fallback.colItem()), t("column.count", fallback.colCount()),
                    t("column.breakdown", fallback.colBreakdown()), t("column.have", fallback.colHave()),
                    t("column.missing", fallback.colMissing()), t("column.id", fallback.colId()),
                    t("legend.have", fallback.legendHave()), t("legend.part", fallback.legendPart()),
                    t("legend.none", fallback.legendNone()), t("page", fallback.page()), t("brand", fallback.brand()));
        }

        private static String t(String key, String fallback) {
            return Language.getInstance().getOrDefault("litematicaplus.mhud.export." + key, fallback);
        }
    }

    public record Context(Texts texts, Map<String, Identifier> sprites) {
        public static Context capture(MaterialReport report) {
            Map<String, Identifier> sprites = new HashMap<>();
            Minecraft minecraft = Minecraft.getInstance();
            ItemStackRenderState state = new ItemStackRenderState();
            RandomSource random = RandomSource.create(0L);
            for (Line line : lines(report)) {
                Identifier key = Identifier.tryParse(line.id());
                Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
                if (item == null) {
                    continue;
                }
                try {
                    minecraft.getItemModelResolver().updateForTopItem(state, new ItemStack(item), ItemDisplayContext.GUI,
                            minecraft.level, null, 0);
                    Material.Baked material = state.pickParticleMaterial(random);
                    if (material != null && material.sprite() != null) {
                        sprites.put(line.id(), material.sprite().contents().name());
                    }
                } catch (RuntimeException exception) {
                    LitematicaPlusClient.LOGGER.debug("No export icon for {}", line.id(), exception);
                }
            }
            return new Context(Texts.capture(), sprites);
        }

        public static Context plain() {
            return new Context(Texts.english(), Map.of());
        }
    }

    private HudExport() {
    }

    public static List<Line> lines(MaterialReport report) {
        List<Line> lines = new ArrayList<>();
        for (MaterialReport.Row row : report.rows()) {
            if (row.marked()) {
                continue;
            }
            long quantity = Math.max(0L, row.missing() - Math.max(0L, row.collected()));
            if (quantity <= 0L) {
                continue;
            }
            lines.add(new Line(row.name(), row.id(), quantity, Math.min(quantity, Math.max(0L, row.available())),
                    row.stackSize()));
        }
        return lines;
    }

    static String format(String pattern, Object... args) {
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (IllegalFormatException exception) {
            return pattern;
        }
    }

    public static String count(long value, Texts texts) {
        String plain = Long.toString(Math.abs(value));
        StringBuilder out = new StringBuilder(plain.length() + plain.length() / 3);
        for (int i = 0; i < plain.length(); i++) {
            if (i > 0 && (plain.length() - i) % 3 == 0) {
                out.append(texts.thousands());
            }
            out.append(plain.charAt(i));
        }
        return value < 0L ? "-" + out : out.toString();
    }

    static String decimal(double value, Texts texts) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).toPlainString().replace(".", texts.decimal());
    }

    public static String breakdown(long quantity, int stackSize, Texts texts) {
        int size = Math.max(1, stackSize);
        long perBox = (long) size * SHULKER_SLOTS;
        long boxes = quantity / perBox;
        long rest = quantity % perBox;
        long stacks = size > 1 ? rest / size : 0L;
        long items = size > 1 ? rest % size : rest;
        List<String> parts = new ArrayList<>(3);
        if (boxes > 0L) {
            parts.add(format(texts.box(), count(boxes, texts)));
        }
        if (stacks > 0L) {
            parts.add(format(texts.stack(), count(stacks, texts)));
        }
        if (items > 0L || parts.isEmpty()) {
            parts.add(count(items, texts));
        }
        return String.join(" + ", parts);
    }

    public static String summary(List<Line> lines, Texts texts) {
        long blocks = 0L;
        double boxes = 0.0;
        for (Line line : lines) {
            blocks += line.quantity();
            boxes += line.quantity() / ((double) line.stackSize() * SHULKER_SLOTS);
        }
        return format(texts.summary(), count(lines.size(), texts), count(blocks, texts), decimal(boxes, texts));
    }

    public static String tsv(MaterialReport report, Texts texts) {
        StringBuilder out = new StringBuilder();
        tsvLine(out, List.of(texts.colItem(), texts.colCount(), texts.colBreakdown(), texts.colHave(), texts.colMissing(),
                texts.colId()));
        for (Line line : lines(report)) {
            tsvLine(out, List.of(line.name(), Long.toString(line.quantity()),
                    breakdown(line.quantity(), line.stackSize(), texts), Long.toString(line.covered()),
                    Long.toString(line.quantity() - line.covered()), line.id()));
        }
        return out.toString();
    }

    private static void tsvLine(StringBuilder out, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                out.append('\t');
            }
            String value = defuse(fields.get(i)).replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').strip();
            out.append(value.startsWith("\"") ? "'" + value : value);
        }
        out.append('\n');
    }

    static String defuse(String value) {
        if (value.isEmpty()) {
            return value;
        }
        boolean number = value.length() > 1 && value.chars().allMatch(c -> c == '-' || Character.isDigit(c));
        return !number && "=+-@".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }

    public static List<String> discord(MaterialReport report, Texts texts) {
        List<Line> lines = lines(report);
        String head = format(texts.discordHeader(), escape(report.title()), summary(lines, texts));
        List<String> rows = new ArrayList<>(lines.size());
        for (Line line : lines) {
            rows.add(format(texts.discordLine(), escape(line.name()),
                    breakdown(line.quantity(), line.stackSize(), texts)));
        }
        String widest = format(texts.discordPart(), rows.size() + 1, rows.size() + 1);
        int budget = Math.max(40, DISCORD_LIMIT - head.length() - widest.length() - 1);
        List<List<String>> chunks = new ArrayList<>();
        List<String> chunk = new ArrayList<>();
        int used = 0;
        for (String row : rows) {
            String fitted = row.length() > budget - 1 ? row.substring(0, Math.max(1, budget - 2)) + "…" : row;
            int cost = fitted.length() + 1;
            if (!chunk.isEmpty() && used + cost > budget) {
                chunks.add(chunk);
                chunk = new ArrayList<>();
                used = 0;
            }
            chunk.add(fitted);
            used += cost;
        }
        if (!chunk.isEmpty() || chunks.isEmpty()) {
            chunks.add(chunk);
        }
        List<String> parts = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            StringBuilder text = new StringBuilder(head);
            if (chunks.size() > 1) {
                text.append(format(texts.discordPart(), i + 1, chunks.size()));
            }
            for (String row : chunks.get(i)) {
                text.append('\n').append(row);
            }
            parts.add(text.toString());
        }
        return parts;
    }

    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (MARKDOWN.indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c == '\n' || c == '\r' ? ' ' : c);
        }
        return out.toString();
    }

    static Map<String, Long> counts(MaterialReport report) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Line line : lines(report)) {
            counts.merge(line.id(), line.quantity(), Long::sum);
        }
        counts.replaceAll((id, count) -> Math.min(count, Integer.MAX_VALUE));
        return counts;
    }

    public static String litematicaText(MaterialReport report, Texts texts) {
        StringBuilder out = new StringBuilder();
        String title = report.title().replace('\n', ' ').replace('\r', ' ');
        out.append("# ").append(format(texts.listComment(), title)).append('\n');
        counts(report).forEach((id, count) -> out.append(id).append(' ').append(count).append('\n'));
        return out.toString();
    }

    public static String litematicaJson(MaterialReport report) {
        JsonObject root = new JsonObject();
        root.addProperty("name", report.title());
        JsonArray items = new JsonArray();
        counts(report).forEach((id, count) -> {
            JsonObject item = new JsonObject();
            item.addProperty("id", id);
            item.addProperty("count", count);
            items.add(item);
        });
        root.add("items", items);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n";
    }

    public static void writeList(MaterialReport report, Texts texts, Path target) throws IOException {
        Files.writeString(target, litematicaText(report, texts), StandardCharsets.UTF_8);
        String name = target.getFileName().toString();
        int dot = name.lastIndexOf('.');
        Path json = target.resolveSibling((dot < 0 ? name : name.substring(0, dot)) + ".json");
        Files.writeString(json, litematicaJson(report), StandardCharsets.UTF_8);
    }

    public static int pages(int lines) {
        return Math.max(1, (lines + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
    }

    public static List<Path> writeCards(MaterialReport report, Context context, Path target) throws IOException {
        List<Line> lines = lines(report);
        Map<String, BufferedImage> icons = icons(context.sprites());
        int pages = pages(lines.size());
        String name = target.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot < 0 ? name : name.substring(0, dot);
        List<Path> files = new ArrayList<>(pages);
        for (int page = 0; page < pages; page++) {
            BufferedImage image = page(report, lines, page, pages, icons, context.texts());
            Path file = page == 0 ? target : target.resolveSibling(stem + "_" + (page + 1) + ".png");
            if (!ImageIO.write(image, "png", file.toFile())) {
                throw new IOException("No PNG writer");
            }
            files.add(file);
        }
        return files;
    }

    private static Map<String, BufferedImage> icons(Map<String, Identifier> sprites) {
        Map<Identifier, BufferedImage> loaded = new HashMap<>();
        Map<String, BufferedImage> icons = new HashMap<>();
        for (Map.Entry<String, Identifier> entry : sprites.entrySet()) {
            BufferedImage image = loaded.computeIfAbsent(entry.getValue(), HudExport::readSprite);
            if (image != null) {
                icons.put(entry.getKey(), image);
            }
        }
        return icons;
    }

    private static BufferedImage readSprite(Identifier sprite) {
        Identifier file = Identifier.fromNamespaceAndPath(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
            if (resource.isEmpty()) {
                return null;
            }
            BufferedImage image;
            try (InputStream stream = resource.get().open()) {
                image = ImageIO.read(stream);
            }
            if (image == null) {
                return null;
            }
            int side = Math.min(image.getWidth(), image.getHeight());
            return side == image.getWidth() && side == image.getHeight() ? image : image.getSubimage(0, 0, side, side);
        } catch (IOException | RuntimeException exception) {
            LitematicaPlusClient.LOGGER.debug("Could not read the export icon {}", file, exception);
            return null;
        }
    }

    static BufferedImage page(MaterialReport report, List<Line> lines, int page, int pages,
            Map<String, BufferedImage> icons, Texts texts) {
        int from = page * ROWS_PER_PAGE;
        int to = Math.min(lines.size(), from + ROWS_PER_PAGE);
        int height = HEADER_H + COLUMNS_H + Math.max(1, to - from) * ROW_H + FOOTER_H;
        BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setColor(new Color(VIOLET_NIGHT));
            g.fillRect(0, 0, WIDTH, height);
            header(g, report, lines, page, pages, texts);
            columns(g, texts);
            for (int i = from; i < to; i++) {
                row(g, lines.get(i), HEADER_H + COLUMNS_H + (i - from) * ROW_H, (i - from) % 2 == 1, icons, texts);
            }
            footer(g, height, page, pages, texts);
        } finally {
            g.dispose();
        }
        roundCorners(image);
        return image;
    }

    private static void header(Graphics2D g, MaterialReport report, List<Line> lines, int page, int pages, Texts texts) {
        int diagonal = (WIDTH + HEADER_H) / 2;
        g.setPaint(new GradientPaint(0, 0, new Color(VIOLET), diagonal, diagonal, new Color(VIOLET_DEEP)));
        g.fillRect(0, 0, WIDTH, HEADER_H);
        g.setFont(font(22.0F, true));
        g.setColor(new Color(WHITE));
        g.drawString(fit(g, report.title(), WIDTH - 2 * PAD), PAD, 46);
        g.setFont(font(14.0F, false));
        g.setColor(new Color(SUBTITLE));
        g.drawString(fit(g, format(texts.meta(), report.exportedAt(), texts.brand()), WIDTH - 2 * PAD), PAD, 76);
        String summary = summary(lines, texts);
        if (pages > 1) {
            summary += format(texts.page(), page + 1, pages);
        }
        g.setFont(font(13.0F, false));
        g.setColor(new Color(TEXT_ON_DARK));
        g.drawString(fit(g, summary, WIDTH - 2 * PAD), PAD, 98);
    }

    private static void columns(Graphics2D g, Texts texts) {
        int top = HEADER_H;
        g.setFont(font(11.0F, false));
        g.setColor(new Color(OVERLINE));
        int baseline = top + 20;
        g.drawString(texts.colItem().toUpperCase(Locale.ROOT), NAME_X, baseline);
        String count = texts.colCount().toUpperCase(Locale.ROOT);
        g.drawString(count, COUNT_RIGHT - g.getFontMetrics().stringWidth(count), baseline);
        g.drawString(texts.colBreakdown().toUpperCase(Locale.ROOT), BREAKDOWN_X, baseline);
        g.setColor(alpha(GRID, 0.45F));
        g.fillRect(PAD, top + COLUMNS_H - 1, WIDTH - 2 * PAD, 1);
    }

    private static void row(Graphics2D g, Line line, int top, boolean zebra, Map<String, BufferedImage> icons,
            Texts texts) {
        if (zebra) {
            g.setColor(new Color(VIOLET_ZEBRA));
            g.fillRect(0, top, WIDTH, ROW_H);
        }
        icon(g, icons.get(line.id()), PAD, top + (ROW_H - ICON) / 2);
        int baseline = top + 26;
        g.setFont(font(15.0F, false));
        g.setColor(new Color(BADGE));
        g.drawString(fit(g, line.name(), COUNT_RIGHT - 110 - NAME_X), NAME_X, baseline);
        String count = count(line.quantity(), texts);
        g.setFont(font(15.0F, true));
        g.setColor(new Color(coverage(line)));
        g.drawString(count, COUNT_RIGHT - g.getFontMetrics().stringWidth(count), baseline);
        g.setFont(font(13.0F, false));
        g.setColor(new Color(TEXT_ON_DARK));
        g.drawString(fit(g, breakdown(line.quantity(), line.stackSize(), texts), WIDTH - PAD - BREAKDOWN_X), BREAKDOWN_X,
                baseline);
    }

    static int coverage(Line line) {
        if (line.covered() >= line.quantity()) {
            return GREEN;
        }
        return line.covered() > 0L ? AMBER : RED;
    }

    private static void footer(Graphics2D g, int height, int page, int pages, Texts texts) {
        int top = height - FOOTER_H;
        g.setColor(alpha(GRID, 0.45F));
        g.fillRect(PAD, top, WIDTH - 2 * PAD, 1);
        g.setFont(font(12.0F, false));
        FontMetrics metrics = g.getFontMetrics();
        int baseline = top + 27;
        int x = PAD;
        String[] labels = {texts.legendHave(), texts.legendPart(), texts.legendNone()};
        int[] tones = {GREEN, AMBER, RED};
        for (int i = 0; i < labels.length; i++) {
            g.setColor(new Color(tones[i]));
            g.fill(new Ellipse2D.Float(x, baseline - 9, 9, 9));
            x += 15;
            g.setColor(new Color(OVERLINE));
            g.drawString(labels[i], x, baseline);
            x += metrics.stringWidth(labels[i]) + 18;
        }
        String brand = pages > 1 ? texts.brand() + " · " + (page + 1) + "/" + pages : texts.brand();
        g.drawString(brand, WIDTH - PAD - metrics.stringWidth(brand), baseline);
    }

    private static void icon(Graphics2D g, BufferedImage picture, int x, int y) {
        if (picture != null) {
            g.drawImage(picture, x, y, ICON, ICON, null);
            return;
        }
        RoundRectangle2D plate = new RoundRectangle2D.Float(x + 2, y + 2, ICON - 4, ICON - 4, 6, 6);
        g.setColor(new Color(VIOLET_DEEP));
        g.fill(plate);
        g.setStroke(new BasicStroke(1.0F));
        g.setColor(alpha(GRID, 0.6F));
        g.draw(plate);
    }

    private static void roundCorners(BufferedImage image) {
        BufferedImage mask = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D m = mask.createGraphics();
        try {
            m.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            m.setColor(Color.WHITE);
            m.fill(new RoundRectangle2D.Float(0, 0, image.getWidth(), image.getHeight(), RADIUS * 2, RADIUS * 2));
        } finally {
            m.dispose();
        }
        Graphics2D g = image.createGraphics();
        try {
            g.setComposite(AlphaComposite.DstIn);
            g.drawImage(mask, 0, 0, null);
        } finally {
            g.dispose();
        }
    }

    private static Font font(float size, boolean bold) {
        return new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, 1).deriveFont(size);
    }

    private static Color alpha(int rgb, float alpha) {
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, Math.round(alpha * 255.0F));
    }

    private static String fit(Graphics2D g, String text, int width) {
        FontMetrics metrics = g.getFontMetrics();
        if (metrics.stringWidth(text) <= width) {
            return text;
        }
        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + ellipsis) > width) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }
}
