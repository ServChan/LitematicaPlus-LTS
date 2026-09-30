package org.lts.litematicaplus.materials;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

public record MaterialReport(String title, String exportedAt, List<Row> rows) {
    public static final int SHULKER_SLOTS = 27;
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public record Row(String name, String id, long total, long missing, long available, int stackSize) {
        public Row {
            stackSize = Math.max(1, stackSize);
        }

        public long toGather() {
            return Math.max(0L, this.missing - this.available);
        }

        public long stacks() {
            return (toGather() + this.stackSize - 1) / this.stackSize;
        }

        public double shulkerBoxes() {
            return Math.round(toGather() * 100.0 / ((double) this.stackSize * SHULKER_SLOTS)) / 100.0;
        }
    }

    public MaterialReport {
        rows = List.copyOf(rows);
    }

    public static MaterialReport of(String title, List<MaterialList.Entry> entries, LocalDateTime now) {
        List<Row> rows = new ArrayList<>(entries.size());
        for (MaterialList.Entry entry : entries) {
            ItemStack stack = new ItemStack(entry.item());
            rows.add(new Row(stack.getHoverName().getString(), BuiltInRegistries.ITEM.getKey(entry.item()).toString(),
                    entry.total(), entry.missing(), entry.available(), stack.getMaxStackSize()));
        }
        return new MaterialReport(title == null || title.isBlank() ? "materials" : title, DISPLAY_TIME.format(now), rows);
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
