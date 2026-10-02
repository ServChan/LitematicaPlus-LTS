package org.lts.litematicaplus.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.lts.litematicaplus.Session;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.render.TopView;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.tool.PasteController;
import org.lts.litematicaplus.verify.MismatchType;
import org.lts.litematicaplus.world.PlacementVolume;

public final class PlacementEditorScreen extends Screen {
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_PADDING = 8;
    private static final int ROW = Theme.CONTROL_HEIGHT + 2;
    private static final int ARM_TICKS = 80;
    private static final int[] STEPS = {1, 5, 16, 64};
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final int MAX_COORDINATE = 30_000_000;
    private static final double MAX_DRAG = 512.0;
    private static int stepIndex;
    private boolean rebuildPending;

    private final Screen parent;
    private final SchematicPlacement placement;
    private int panelX;
    private int rowsTop;
    private int hintTop;
    private int pasteArmed;
    private boolean dragging;
    private boolean panning;
    private Vec3 dragAnchor;
    private BlockPos dragOrigin;
    private SchematicPlacement.LoadState knownState;

    public PlacementEditorScreen(Screen parent, SchematicPlacement placement) {
        super(Component.translatable("litematicaplus.editor.title"));
        this.parent = parent;
        this.placement = placement;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static Component onOff(boolean value) {
        return tr(value ? "litematicaplus.state.on" : "litematicaplus.state.off");
    }

    private int step() {
        return STEPS[stepIndex % STEPS.length];
    }

    private PlacementManager manager() {
        return PlacementManager.get();
    }

    private boolean editable() {
        return !this.placement.locked() && manager().placements().contains(this.placement);
    }

    @Override
    protected void init() {
        this.knownState = this.placement.loadState();
        this.panelX = this.width - PANEL_WIDTH;
        int x = this.panelX + PANEL_PADDING;
        int inner = PANEL_WIDTH - PANEL_PADDING * 2;
        int third = (inner - Theme.GAP * 2) / 3;
        int half = (inner - Theme.GAP) / 2;
        int lastThird = inner - (third + Theme.GAP) * 2;
        int lastHalf = inner - half - Theme.GAP;
        int secondX = x + half + Theme.GAP;
        this.rowsTop = 38;
        int y = this.rowsTop;
        boolean editable = editable();
        coordinate(x, y, half, 0, editable);
        coordinate(secondX, y, lastHalf, 1, editable);
        y += ROW;
        coordinate(x, y, half, 2, editable);
        addRenderableWidget(FlatButton.secondary(secondX, y, lastHalf, tr("litematicaplus.editor.step", step()), button -> {
            stepIndex = (stepIndex + 1) % STEPS.length;
            rebuildWidgets();
        }));
        y += ROW;
        nudgeButton(x, y, third, "←", -1, 0, 0, editable);
        nudgeButton(x + third + Theme.GAP, y, third, "↑", 0, 0, 1, editable);
        nudgeButton(x + (third + Theme.GAP) * 2, y, lastThird, "→", 1, 0, 0, editable);
        y += ROW;
        nudgeButton(x, y, third, "Y-", 0, -1, 0, editable);
        nudgeButton(x + third + Theme.GAP, y, third, "↓", 0, 0, -1, editable);
        nudgeButton(x + (third + Theme.GAP) * 2, y, lastThird, "Y+", 0, 1, 0, editable);
        y += ROW;
        FlatButton here = addRenderableWidget(FlatButton.secondary(x, y, half, tr("litematicaplus.placements.move_here"),
                button -> {
                    if (this.minecraft.player != null) {
                        BlockPos pos = this.minecraft.player.blockPosition();
                        manager().move(this.placement, pos.getX(), pos.getY(), pos.getZ());
                    }
                }));
        here.active = editable && this.minecraft.player != null;
        addRenderableWidget(FlatButton.secondary(secondX, y, lastHalf,
                tr("litematicaplus.editor.top_view", onOff(TopView.active())), button -> {
                    toggleTopView();
                    rebuildWidgets();
                }).withState(TopView::active));
        y += ROW;
        FlatButton rotate = addRenderableWidget(FlatButton.secondary(x, y, half,
                tr("litematicaplus.rotation." + this.placement.rotation().name().toLowerCase(Locale.ROOT)), button -> {
                    rotate();
                    rebuildWidgets();
                }));
        rotate.active = editable;
        FlatButton mirror = addRenderableWidget(FlatButton.secondary(secondX, y, lastHalf,
                tr("litematicaplus.mirror." + this.placement.mirror().name().toLowerCase(Locale.ROOT)), button -> {
                    mirror();
                    rebuildWidgets();
                }));
        mirror.active = editable;
        y += ROW;
        addRenderableWidget(FlatButton.secondary(x, y, half, tr("litematicaplus.editor.shown", onOff(this.placement.enabled())),
                button -> {
                    manager().setEnabled(this.placement, !this.placement.enabled());
                    rebuildWidgets();
                }));
        addRenderableWidget(FlatButton.secondary(secondX, y, lastHalf,
                tr("litematicaplus.editor.locked", onOff(this.placement.locked())), button -> {
                    manager().setLocked(this.placement, !this.placement.locked());
                    rebuildWidgets();
                }));
        y += ROW;
        PlacementRuntime runtime = GhostEngine.get().runtime(this.placement.id());
        FlatButton paste = addRenderableWidget(FlatButton.secondary(x, y, half,
                tr(this.pasteArmed > 0 ? "litematicaplus.placements.paste_confirm" : "litematicaplus.placements.paste"),
                button -> {
                    if (this.pasteArmed > 0) {
                        this.pasteArmed = 0;
                        PasteController.startPaste(this.minecraft, GhostEngine.get().runtime(this.placement.id()));
                        onClose();
                    } else {
                        this.pasteArmed = ARM_TICKS;
                        rebuildWidgets();
                    }
                }).withWarning(() -> this.pasteArmed > 0));
        paste.active = this.minecraft.player != null && this.minecraft.player.hasInfiniteMaterials()
                && !PasteController.active() && runtime != null && runtime.volume() != null;
        addRenderableWidget(FlatButton.secondary(secondX, y, lastHalf, tr("litematicaplus.editor.menu"),
                button -> this.minecraft.gui.setScreen(new HubScreen(null, HubScreen.Tab.PLACEMENTS))));
        y += ROW;
        addRenderableWidget(FlatButton.primary(x, y, inner, tr("litematicaplus.editor.done"), button -> onClose()));
        this.hintTop = y + ROW + 4;
    }

    private void coordinate(int x, int y, int width, int axis, boolean editable) {
        Stepper field = new Stepper(x, y, width, Component.literal(axis == 0 ? "X" : axis == 1 ? "Y" : "Z"),
                () -> axis == 0 ? this.placement.x() : axis == 1 ? this.placement.y() : this.placement.z(),
                value -> manager().move(this.placement, axis == 0 ? value : this.placement.x(),
                        axis == 1 ? value : this.placement.y(), axis == 2 ? value : this.placement.z()),
                -30000000, 30000000, 1).field();
        field.active = editable;
        field.setTooltip(Tooltip.create(tr("litematicaplus.editor.coordinate_tip")));
        addRenderableWidget(field);
    }

    private void nudgeButton(int x, int y, int width, String label, int right, int up, int forward, boolean editable) {
        FlatButton button = addRenderableWidget(FlatButton.secondary(x, y, width, Component.literal(label),
                pressed -> nudge(right, up, forward, pressed.shiftHeld() ? 10 : 1)));
        button.active = editable;
        Direction direction = direction(right, up, forward);
        button.setTooltip(Tooltip.create(tr("litematicaplus.editor.nudge_tip",
                direction.getAxis().getName().toUpperCase(Locale.ROOT)
                        + (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? "+" : "-"))));
    }

    private Direction forward() {
        if (TopView.active() || this.minecraft.player == null) {
            return Direction.NORTH;
        }
        return Direction.fromYRot(this.minecraft.player.getYRot());
    }

    private Direction direction(int right, int up, int forward) {
        if (up != 0) {
            return up > 0 ? Direction.UP : Direction.DOWN;
        }
        Direction ahead = forward();
        if (forward != 0) {
            return forward > 0 ? ahead : ahead.getOpposite();
        }
        return right > 0 ? ahead.getClockWise() : ahead.getCounterClockWise();
    }

    private void nudge(int right, int up, int forward, int multiplier) {
        if (!editable()) {
            return;
        }
        Direction direction = direction(right, up, forward);
        int amount = step() * multiplier;
        manager().move(this.placement, this.placement.x() + direction.getStepX() * amount,
                this.placement.y() + direction.getStepY() * amount, this.placement.z() + direction.getStepZ() * amount);
    }

    private void rotate() {
        manager().rotate(this.placement, this.placement.rotation().getRotated(Rotation.CLOCKWISE_90));
    }

    private void mirror() {
        Mirror[] values = Mirror.values();
        manager().mirror(this.placement, values[(this.placement.mirror().ordinal() + 1) % values.length]);
    }

    private double tanHalfFov() {
        return Math.tan(Math.toRadians(this.minecraft.gameRenderer.mainCamera().getFov()) / 2.0);
    }

    private void toggleTopView() {
        if (TopView.active()) {
            TopView.disable();
            return;
        }
        int minX = this.placement.x();
        int maxX = minX;
        int minZ = this.placement.z();
        int maxZ = minZ;
        int maxY = this.placement.y();
        Schematic schematic = this.placement.schematic();
        if (schematic != null) {
            List<PlacementVolume.RegionBox> boxes = PlacementVolume.regionBoxes(schematic, this.placement.transform(), null);
            for (PlacementVolume.RegionBox box : boxes) {
                minX = Math.min(minX, box.minX());
                maxX = Math.max(maxX, box.maxX());
                minZ = Math.min(minZ, box.minZ());
                maxZ = Math.max(maxZ, box.maxZ());
                maxY = Math.max(maxY, box.maxY());
            }
        }
        double tan = tanHalfFov();
        double freeWidth = Math.max(80.0, this.width - PANEL_WIDTH);
        double spanZ = (maxZ - minZ + 1) / 2.0 + 4.0;
        double spanX = (maxX - minX + 1) / 2.0 + 4.0;
        double distance = Math.max(spanZ / tan, spanX / (tan * freeWidth / this.height)) * 1.1;
        double unitsPerPixel = 2.0 * distance * tan / this.height;
        TopView.enable((minX + maxX + 1) / 2.0 + PANEL_WIDTH / 2.0 * unitsPerPixel, maxY + 1.0,
                (minZ + maxZ + 1) / 2.0, distance);
    }

    private Vec3 planeHit(double mouseX, double mouseY) {
        Camera camera = this.minecraft.gameRenderer.mainCamera();
        Vec3 origin = camera.position();
        Vector3fc forward = camera.forwardVector();
        Vector3fc up = camera.upVector();
        Vector3fc left = camera.leftVector();
        double tanY = tanHalfFov();
        double tanX = tanY * this.width / this.height;
        double ndcX = mouseX / this.width * 2.0 - 1.0;
        double ndcY = 1.0 - mouseY / this.height * 2.0;
        double dirX = forward.x() - left.x() * ndcX * tanX + up.x() * ndcY * tanY;
        double dirY = forward.y() - left.y() * ndcX * tanX + up.y() * ndcY * tanY;
        double dirZ = forward.z() - left.z() * ndcX * tanX + up.z() * ndcY * tanY;
        if (Math.abs(dirY) < 1.0E-4) {
            return null;
        }
        double travel = (this.placement.y() - origin.y) / dirY;
        if (travel <= 0.0 || travel > 4096.0) {
            return null;
        }
        return new Vec3(origin.x + dirX * travel, this.placement.y(), origin.z + dirZ * travel);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.x() < this.panelX) {
            setFocused(null);
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && editable()) {
                this.dragAnchor = planeHit(event.x(), event.y());
                this.dragOrigin = this.placement.origin();
                this.dragging = this.dragAnchor != null;
                return true;
            }
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && TopView.active()) {
                this.panning = true;
                return true;
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.dragging && this.dragAnchor != null) {
            Vec3 hit = planeHit(event.x(), event.y());
            if (hit != null) {
                double moveX = Math.max(-MAX_DRAG, Math.min(MAX_DRAG, hit.x - this.dragAnchor.x));
                double moveZ = Math.max(-MAX_DRAG, Math.min(MAX_DRAG, hit.z - this.dragAnchor.z));
                manager().move(this.placement, this.dragOrigin.getX() + (int) Math.round(moveX), this.dragOrigin.getY(),
                        this.dragOrigin.getZ() + (int) Math.round(moveZ));
            }
            return true;
        }
        if (this.panning && TopView.active()) {
            double unitsPerPixel = 2.0 * TopView.distance() * tanHalfFov() / this.height;
            TopView.pan(-dx * unitsPerPixel, -dy * unitsPerPixel);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragging = false;
        this.panning = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < this.panelX && scrollY != 0.0) {
            if (TopView.active()) {
                TopView.zoom(scrollY > 0.0 ? 0.88 : 1.0 / 0.88);
            } else {
                nudge(0, scrollY > 0.0 ? 1 : -1, 0, 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean pasteCoordinates() {
        String text = this.minecraft.keyboardHandler.getClipboard();
        if (text == null) {
            return false;
        }
        List<Integer> numbers = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(text.length() > 256 ? text.substring(0, 256) : text);
        while (matcher.find() && numbers.size() < 3) {
            double value = Double.parseDouble(matcher.group());
            numbers.add((int) Math.max(-MAX_COORDINATE, Math.min(MAX_COORDINATE, Math.floor(value))));
        }
        if (numbers.isEmpty() || !editable()) {
            return false;
        }
        Stepper focused = getFocused() instanceof Stepper stepper ? stepper : null;
        if (numbers.size() < 3) {
            if (focused == null) {
                return false;
            }
            focused.enter(numbers.get(0));
            return true;
        }
        if (focused != null) {
            focused.cancel();
        }
        manager().move(this.placement, numbers.get(0), numbers.get(1), numbers.get(2));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean typing = getFocused() instanceof Stepper stepper && stepper.editing();
        if (typing && getFocused().keyPressed(event)) {
            return true;
        }
        int key = event.key();
        if (key == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (event.isPaste() || key == InputConstants.KEY_V && this.minecraft.hasControlDown()) {
            return pasteCoordinates();
        }
        if (typing) {
            return super.keyPressed(event);
        }
        int multiplier = event.hasShiftDown() ? 10 : 1;
        switch (key) {
            case InputConstants.KEY_LEFT -> nudge(-1, 0, 0, multiplier);
            case InputConstants.KEY_RIGHT -> nudge(1, 0, 0, multiplier);
            case InputConstants.KEY_UP -> nudge(0, 0, 1, multiplier);
            case InputConstants.KEY_DOWN -> nudge(0, 0, -1, multiplier);
            case InputConstants.KEY_PAGEUP -> nudge(0, 1, 0, multiplier);
            case InputConstants.KEY_PAGEDOWN -> nudge(0, -1, 0, multiplier);
            case InputConstants.KEY_R -> {
                if (editable()) {
                    rotate();
                    rebuildWidgets();
                }
            }
            case InputConstants.KEY_M -> {
                if (editable()) {
                    mirror();
                    rebuildWidgets();
                }
            }
            case InputConstants.KEY_T -> {
                toggleTopView();
                rebuildWidgets();
            }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                if (getFocused() == null) {
                    onClose();
                } else {
                    return super.keyPressed(event);
                }
            }
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        boolean rebuild = false;
        if (this.pasteArmed > 0 && --this.pasteArmed == 0) {
            rebuild = true;
        }
        if (this.placement.loadState() != this.knownState) {
            rebuild = true;
        }
        if (!manager().placements().contains(this.placement)) {
            onClose();
            return;
        }
        if (rebuild) {
            this.rebuildPending = true;
        }
        if (this.rebuildPending && !HubScreen.typing(getFocused())) {
            this.rebuildPending = false;
            rebuildWidgets();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(this.panelX, 0, this.width, this.height, Theme.HUD_BACKGROUND);
        Blueprint.grid(graphics, this.panelX + 1, 0, this.width - this.panelX - 1, this.height);
        Blueprint.ruler(graphics, this.panelX, 0, this.height, Theme.BORDER);
        int x = this.panelX + PANEL_PADDING;
        int inner = PANEL_WIDTH - PANEL_PADDING * 2;
        Blueprint.marker(graphics, x, 8, Theme.AMBER);
        Ui.clipped(graphics, this.font, this.placement.name(), x + 6, 6, inner - 6, Theme.TEXT_BRIGHT);
        PlacementRuntime runtime = GhostEngine.get().runtime(this.placement.id());
        PlacementVolume volume = runtime == null ? null : runtime.volume();
        if (this.placement.loadState() == SchematicPlacement.LoadState.FAILED) {
            Ui.wrapped(graphics, this.font, tr("litematicaplus.hud.failed"), x, 18, inner, 1, Theme.DANGER, mouseX, mouseY);
        } else if (volume == null) {
            graphics.text(this.font, tr(this.placement.enabled() ? "litematicaplus.hud.loading" : "litematicaplus.hud.disabled"),
                    x, 18, Theme.TEXT_DIM, false);
        } else {
            double progress = runtime.progress();
            Ui.progress(graphics, x, 17, inner, 4, progress, progress >= 0.9999 ? Theme.SUCCESS : Theme.ACCENT);
            Ui.clipped(graphics, this.font, tr("litematicaplus.hud.progress",
                    String.format(Locale.ROOT, "%.1f", progress * 100.0), runtime.count(MismatchType.CORRECT),
                    runtime.totalBlocks()).getString(), x, 25, inner, Theme.TEXT_DIM);
        }
        int hintLines = Math.min(6, (this.height - this.hintTop - 4) / 10);
        if (hintLines > 0) {
            Ui.wrapped(graphics, this.font, tr(TopView.active() ? "litematicaplus.editor.hint_top"
                    : "litematicaplus.editor.hint"), x, this.hintTop, inner, hintLines, Theme.TEXT_DIM, mouseX, mouseY);
        }
        if (TopView.active()) {
            Component north = tr("litematicaplus.editor.north");
            int northWidth = this.font.width(north) + 12;
            int northX = this.panelX / 2 - northWidth / 2;
            graphics.fill(northX, 3, northX + northWidth, 16, Theme.HUD_BACKGROUND);
            Blueprint.frame(graphics, northX, 3, northWidth, 13, Theme.BORDER, Theme.AMBER);
            graphics.centeredText(this.font, north, this.panelX / 2, 6, Theme.TEXT_BRIGHT);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        TopView.disable();
        Session.flush();
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void removed() {
        TopView.disable();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
