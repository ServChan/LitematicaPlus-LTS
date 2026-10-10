package org.lts.litematicaplus.zones;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import org.lts.litematicaplus.world.LayerRange;

public record ZonePlan(int columns, int rows, int floors) {
    public static final int MAX_SPLIT = 16;
    public static final ZonePlan NONE = new ZonePlan(1, 1, 1);

    public ZonePlan {
        columns = clamp(columns);
        rows = clamp(rows);
        floors = clamp(floors);
    }

    private static int clamp(int value) {
        return Math.max(1, Math.min(MAX_SPLIT, value));
    }

    public boolean active() {
        return count() > 1;
    }

    public int count() {
        return this.columns * this.rows * this.floors;
    }

    public ZonePlan withColumns(int value) {
        return new ZonePlan(value, this.rows, this.floors);
    }

    public ZonePlan withRows(int value) {
        return new ZonePlan(this.columns, value, this.floors);
    }

    public ZonePlan withFloors(int value) {
        return new ZonePlan(this.columns, this.rows, value);
    }

    public record Zone(int index, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean splitY) {
        public LayerRange range() {
            LayerRange.Clip clip = new LayerRange.Clip(true, true, this.minX, this.maxX, true, this.minZ, this.maxZ);
            LayerRange base = this.splitY
                    ? new LayerRange(LayerRange.Mode.RANGE, Direction.Axis.Y, this.minY, this.minY, this.maxY)
                    : LayerRange.ALL;
            return base.withClip(clip);
        }

        public boolean contains(int x, int y, int z) {
            return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ
                    && z <= this.maxZ;
        }
    }

    public List<Zone> split(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        List<Zone> zones = new ArrayList<>(count());
        int columns = Math.min(this.columns, maxX - minX + 1);
        int rows = Math.min(this.rows, maxZ - minZ + 1);
        int floors = Math.min(this.floors, maxY - minY + 1);
        int index = 1;
        for (int floor = 0; floor < floors; floor++) {
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    zones.add(new Zone(index++, edge(minX, maxX, columns, column), edge(minY, maxY, floors, floor),
                            edge(minZ, maxZ, rows, row), edge(minX, maxX, columns, column + 1) - 1,
                            edge(minY, maxY, floors, floor + 1) - 1, edge(minZ, maxZ, rows, row + 1) - 1, floors > 1));
                }
            }
        }
        return zones;
    }

    private static int edge(int min, int max, int parts, int part) {
        long size = (long) max - min + 1L;
        return (int) (min + size * part / parts);
    }
}
