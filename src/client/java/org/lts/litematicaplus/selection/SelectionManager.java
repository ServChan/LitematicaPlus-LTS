package org.lts.litematicaplus.selection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.SessionStore;

public final class SelectionManager {
    private static final SelectionManager INSTANCE = new SelectionManager();

    private final List<AreaBox> boxes = new ArrayList<>();
    private String name = "";
    private int selected;
    private Runnable changeListener = () -> { };

    private SelectionManager() {
    }

    public static SelectionManager get() {
        return INSTANCE;
    }

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener == null ? () -> { } : listener;
    }

    public List<AreaBox> boxes() {
        return Collections.unmodifiableList(this.boxes);
    }

    public int selectedIndex() {
        return this.selected;
    }

    public AreaBox selectedBox() {
        return this.selected >= 0 && this.selected < this.boxes.size() ? this.boxes.get(this.selected) : null;
    }

    public AreaBox selectedOrCreate() {
        AreaBox box = selectedBox();
        if (box == null) {
            box = new AreaBox(defaultBoxName(), null, null);
            this.boxes.add(box);
            this.selected = this.boxes.size() - 1;
        }
        return box;
    }

    public void select(int index) {
        if (index >= 0 && index < this.boxes.size()) {
            this.selected = index;
            this.changeListener.run();
        }
    }

    public AreaBox addBox() {
        AreaBox box = new AreaBox(defaultBoxName(), null, null);
        this.boxes.add(box);
        this.selected = this.boxes.size() - 1;
        this.changeListener.run();
        return box;
    }

    public void removeSelected() {
        if (this.selected >= 0 && this.selected < this.boxes.size()) {
            this.boxes.remove(this.selected);
            this.selected = Math.max(0, Math.min(this.selected, this.boxes.size() - 1));
            this.changeListener.run();
        }
    }

    public void clear() {
        this.boxes.clear();
        this.selected = 0;
        this.changeListener.run();
    }

    public void setCorner(boolean first, BlockPos pos) {
        AreaBox box = selectedOrCreate();
        if (first) {
            box.setCorner1(pos);
        } else {
            box.setCorner2(pos);
        }
        this.changeListener.run();
    }

    public void nudge(boolean first, boolean second, Direction direction, int amount) {
        AreaBox box = selectedBox();
        if (box == null) {
            return;
        }
        int dx = direction.getStepX() * amount;
        int dy = direction.getStepY() * amount;
        int dz = direction.getStepZ() * amount;
        if (first && box.corner1() != null) {
            box.setCorner1(box.corner1().offset(dx, dy, dz));
        }
        if (second && box.corner2() != null) {
            box.setCorner2(box.corner2().offset(dx, dy, dz));
        }
        this.changeListener.run();
    }

    public void notifyChanged() {
        this.changeListener.run();
    }

    public List<AreaBox> completeBoxes() {
        List<AreaBox> result = new ArrayList<>();
        for (AreaBox box : this.boxes) {
            if (box.isComplete()) {
                result.add(box);
            }
        }
        return result;
    }

    public long totalVolume() {
        long volume = 0L;
        for (AreaBox box : this.boxes) {
            volume += box.volume();
        }
        return volume;
    }

    public void restore(SessionStore.AreaData data) {
        this.boxes.clear();
        this.name = data.name == null ? "" : data.name;
        for (SessionStore.BoxData box : data.boxes) {
            this.boxes.add(new AreaBox(box.name, pos(box.a), pos(box.b)));
        }
        this.selected = Math.max(0, Math.min(data.selected, Math.max(0, this.boxes.size() - 1)));
    }

    public SessionStore.AreaData snapshot() {
        SessionStore.AreaData data = new SessionStore.AreaData();
        data.name = this.name;
        data.selected = this.selected;
        for (AreaBox box : this.boxes) {
            SessionStore.BoxData entry = new SessionStore.BoxData();
            entry.name = box.name();
            entry.a = array(box.corner1());
            entry.b = array(box.corner2());
            data.boxes.add(entry);
        }
        return data;
    }

    private String defaultBoxName() {
        return Component.translatable("litematicaplus.area.default_box", this.boxes.size() + 1).getString();
    }

    private static BlockPos pos(int[] values) {
        return values == null || values.length != 3 ? null : new BlockPos(values[0], values[1], values[2]);
    }

    private static int[] array(BlockPos pos) {
        return pos == null ? null : new int[]{pos.getX(), pos.getY(), pos.getZ()};
    }
}
