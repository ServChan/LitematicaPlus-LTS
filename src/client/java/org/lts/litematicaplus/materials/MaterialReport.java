package org.lts.litematicaplus.materials;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

public record MaterialReport(String title, String exportedAt, List<Row> rows, RawSection raw) {
    public static final int SHULKER_SLOTS = 27;
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public record Row(String name, String id, long total, long missing, long available, int stackSize, long collected) {
        public Row {
            stackSize = Math.max(1, stackSize);
        }

        public Row(String name, String id, long total, long missing, long available, int stackSize) {
            this(name, id, total, missing, available, stackSize, 0L);
        }

        public boolean marked() {
            return this.collected == MaterialList.Entry.ALL;
        }

        public long toGather() {
            if (marked()) {
                return 0L;
            }
            return Math.max(0L, Math.max(0L, this.missing - this.available) - Math.max(0L, this.collected));
        }

        public long stacks() {
            return (toGather() + this.stackSize - 1) / this.stackSize;
        }

        public double shulkerBoxes() {
            return Math.round(toGather() * 100.0 / ((double) this.stackSize * SHULKER_SLOTS)) / 100.0;
        }
    }

    public record RawRow(String name, String id, long need, long fromStock, long toGet, int stackSize) {
        public RawRow {
            stackSize = Math.max(1, stackSize);
        }

        public long stacks() {
            return (this.toGet + this.stackSize - 1) / this.stackSize;
        }
    }

    public record StepRow(String name, String id, long demand, long fromStock, long crafts, long produced, long leftover,
            String recipe) {
    }

    public record RawSection(List<RawRow> raw, List<StepRow> steps) {
        public RawSection {
            raw = List.copyOf(raw);
            steps = List.copyOf(steps);
        }
    }

    public MaterialReport {
        rows = List.copyOf(rows);
    }

    public MaterialReport(String title, String exportedAt, List<Row> rows) {
        this(title, exportedAt, rows, null);
    }

    public static MaterialReport of(String title, List<MaterialList.Entry> entries, LocalDateTime now) {
        List<Row> rows = new ArrayList<>(entries.size());
        for (MaterialList.Entry entry : entries) {
            ItemStack stack = new ItemStack(entry.item());
            rows.add(new Row(stack.getHoverName().getString(), BuiltInRegistries.ITEM.getKey(entry.item()).toString(),
                    entry.total(), entry.missing(), entry.available(), stack.getMaxStackSize(), entry.collected()));
        }
        return new MaterialReport(title == null || title.isBlank() ? "materials" : title, DISPLAY_TIME.format(now), rows);
    }

    public MaterialReport withRaw(RawSection section) {
        return new MaterialReport(this.title, this.exportedAt, this.rows, section);
    }

    public boolean anyCollected() {
        for (Row row : this.rows) {
            if (row.collected() != 0L) {
                return true;
            }
        }
        return false;
    }

    public List<Row> needed() {
        List<Row> needed = new ArrayList<>();
        for (Row row : this.rows) {
            if (row.toGather() > 0L) {
                needed.add(row);
            }
        }
        return needed;
    }

    public long total() {
        long sum = 0L;
        for (Row row : this.rows) {
            sum += row.total();
        }
        return sum;
    }

    public long missing() {
        long sum = 0L;
        for (Row row : this.rows) {
            sum += row.missing();
        }
        return sum;
    }

    public long toGather() {
        long sum = 0L;
        for (Row row : this.rows) {
            sum += row.toGather();
        }
        return sum;
    }

    public long stacks() {
        long sum = 0L;
        for (Row row : this.rows) {
            sum += row.stacks();
        }
        return sum;
    }

    public long shulkerBoxes() {
        return (stacks() + SHULKER_SLOTS - 1) / SHULKER_SLOTS;
    }
}
