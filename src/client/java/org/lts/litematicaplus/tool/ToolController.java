package org.lts.litematicaplus.tool;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.Session;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.selection.AreaBox;
import org.lts.litematicaplus.selection.SelectionManager;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.world.LayerRange;

public final class ToolController {
    private static final ToolController INSTANCE = new ToolController();
    private static final double TARGET_RANGE = 160.0;
    private static final int CONFIRM_TICKS = 80;

    private ToolMode mode = ToolMode.AREA_SELECTION;
    private int clickCooldown;
    private boolean useReleased = true;
    private double scrollRemainder;
    private ToolMode armedMode;
    private String armedFingerprint = "";
    private int armedTicks;
    private String toolItemId = "";
    private Item toolItem = Items.AIR;

    private ToolController() {
    }

    public static ToolController get() {
        return INSTANCE;
    }

    public ToolMode mode() {
        return this.mode;
    }

    public void setMode(ToolMode next) {
        this.mode = next == null ? ToolMode.AREA_SELECTION : next;
        disarm();
    }

    public void cycleMode(boolean forward) {
        this.mode = this.mode.cycle(forward);
        disarm();
        Session.markDirty();
        Notify.actionBar(Component.translatable("litematicaplus.message.tool_mode",
                Component.translatable(this.mode.translationKey())));
    }

    public void tick(Minecraft minecraft) {
        if (this.clickCooldown > 0) {
            this.clickCooldown--;
        }
        if (this.armedTicks > 0 && --this.armedTicks == 0) {
            this.armedMode = null;
        }
        if (!minecraft.options.keyUse.isDown()) {
            this.useReleased = true;
        }
        TickQueue.tick(minecraft);
        EntityAssist.tick(minecraft);
        EasyPlace.tick(minecraft);
        PasteController.tick(minecraft);
    }

    public boolean holdingTool(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (player == null || !config.toolEnabled || player.isSpectator()) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return false;
        }
        return stack.getItem() == toolItem(config);
    }

    private Item toolItem(LitematicaPlusConfig config) {
        if (!config.toolItem.equals(this.toolItemId)) {
            this.toolItemId = config.toolItem;
            Identifier id = Identifier.tryParse(config.toolItem);
            this.toolItem = id == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        }
        return this.toolItem;
    }

    public boolean onAttack(Minecraft minecraft) {
        if (!holdingTool(minecraft)) {
            return false;
        }
        if (this.clickCooldown > 0) {
            return true;
        }
        this.clickCooldown = 4;
        switch (this.mode) {
            case AREA_SELECTION -> setCorner(minecraft, true);
            case PLACEMENT -> movePlacement(minecraft);
            case PASTE -> pasteClick(minecraft);
            case DELETE -> deleteClick(minecraft);
        }
        return true;
    }

    private void pasteClick(Minecraft minecraft) {
        SchematicPlacement placement = PlacementManager.get().selected();
        PlacementRuntime runtime = placement == null ? null : GhostEngine.get().runtime(placement.id());
        if (runtime == null || runtime.volume() == null) {
            Notify.actionBar("litematicaplus.message.no_placement");
            return;
        }
        if (!PasteController.upToDate(runtime)) {
            disarm();
            Notify.actionBar("litematicaplus.message.paste_rebuilding");
            return;
        }
        if (!confirmed(ToolMode.PASTE, placement.id() + "@" + placement.transform() + placement.disabledRegions())) {
            Notify.actionBar("litematicaplus.message.confirm_paste", placement.name(), runtime.volume().totalBlocks());
            return;
        }
        PasteController.startPaste(minecraft, runtime);
    }

    private void deleteClick(Minecraft minecraft) {
        List<AreaBox> boxes = new ArrayList<>();
        long volume = 0L;
        for (AreaBox box : SelectionManager.get().boxes()) {
            if (box.isComplete()) {
                boxes.add(box);
                volume += box.volume();
            }
        }
        if (boxes.isEmpty()) {
            Notify.actionBar("litematicaplus.message.no_selection");
            return;
        }
        if (volume > PasteController.MAX_DELETE_VOLUME) {
            Notify.actionBar("litematicaplus.message.delete_too_large", volume, PasteController.MAX_DELETE_VOLUME);
            return;
        }
        StringBuilder fingerprint = new StringBuilder();
        for (AreaBox box : boxes) {
            fingerprint.append(box.minX()).append(',').append(box.minY()).append(',').append(box.minZ()).append(':')
                    .append(box.maxX()).append(',').append(box.maxY()).append(',').append(box.maxZ()).append(';');
        }
        if (!confirmed(ToolMode.DELETE, fingerprint.toString())) {
            Notify.actionBar("litematicaplus.message.confirm_delete", boxes.size(), volume);
            return;
        }
        PasteController.startDelete(minecraft, boxes);
    }

    private boolean confirmed(ToolMode wanted, String fingerprint) {
        if (this.armedMode == wanted && this.armedTicks > 0 && fingerprint.equals(this.armedFingerprint)) {
            disarm();
            return true;
        }
        this.armedMode = wanted;
        this.armedTicks = CONFIRM_TICKS;
        this.armedFingerprint = fingerprint;
        return false;
    }

    private void disarm() {
        this.armedMode = null;
        this.armedTicks = 0;
        this.armedFingerprint = "";
    }

    public boolean onUse(Minecraft minecraft) {
        if (!holdingTool(minecraft)) {
            boolean consumed = EasyPlace.onUse(minecraft);
            if (!consumed && ToolGuard.blocks(minecraft)) {
                return true;
            }
            if (!consumed) {
                BlockEntityAssist.userInteracted();
            }
            return consumed;
        }
        if (!this.useReleased || this.clickCooldown > 0) {
            return true;
        }
        this.useReleased = false;
        this.clickCooldown = 4;
        switch (this.mode) {
            case AREA_SELECTION -> setCorner(minecraft, false);
            case PLACEMENT -> rotatePlacement();
            case PASTE, DELETE -> {
                disarm();
                PasteController.cancel();
            }
        }
        return true;
    }

    public boolean onPick(Minecraft minecraft) {
        if (holdingTool(minecraft)) {
            cycleMode(!minecraft.hasShiftDown());
            return true;
        }
        return EasyPlace.onPick(minecraft);
    }

    public boolean onScroll(Minecraft minecraft, double amount) {
        if (amount == 0.0 || minecraft.player == null || !holdingTool(minecraft)) {
            return false;
        }
        boolean control = minecraft.hasControlDown();
        if (!control && !minecraft.hasAltDown()) {
            this.scrollRemainder = 0.0;
            return false;
        }
        if (Math.signum(amount) != Math.signum(this.scrollRemainder)) {
            this.scrollRemainder = 0.0;
        }
        this.scrollRemainder += amount;
        int steps = (int) this.scrollRemainder;
        if (steps == 0) {
            return true;
        }
        this.scrollRemainder -= steps;
        disarm();
        boolean forward = steps > 0;
        if (control) {
            cycleMode(forward);
            return true;
        }
        Direction direction = Direction.getApproximateNearest(minecraft.player.getViewVector(1.0F));
        int step = Math.max(-8, Math.min(8, steps));
        if (this.mode == ToolMode.AREA_SELECTION || this.mode == ToolMode.DELETE) {
            boolean sneaking = minecraft.player.isShiftKeyDown();
            SelectionManager.get().nudge(true, !sneaking, direction, step);
        } else {
            SchematicPlacement placement = PlacementManager.get().selected();
            if (placement == null) {
                Notify.actionBar("litematicaplus.message.no_placement");
            } else if (placement.locked()) {
                Notify.actionBar("litematicaplus.message.placement_locked");
            } else {
                PlacementManager.get().move(placement, placement.x() + direction.getStepX() * step,
                        placement.y() + direction.getStepY() * step, placement.z() + direction.getStepZ() * step);
            }
        }
        return true;
    }

    public void shiftLayer(Minecraft minecraft, int delta) {
        LayerRange layer = GhostEngine.get().layer();
        if (layer.isAll()) {
            if (minecraft.player == null) {
                return;
            }
            BlockPos pos = minecraft.player.blockPosition();
            int value = pos.get(layer.axis());
            layer = new LayerRange(LayerRange.Mode.SINGLE, layer.axis(), value, value, value);
        } else {
            layer = layer.shifted(delta);
        }
        applyLayer(layer);
    }

    public void cycleLayerMode(Minecraft minecraft) {
        LayerRange layer = GhostEngine.get().layer();
        LayerRange.Mode next = layer.mode().next();
        if (layer.isAll() && minecraft.player != null) {
            int value = minecraft.player.blockPosition().get(layer.axis());
            layer = new LayerRange(next, layer.axis(), value, value - 2, value + 2);
        } else {
            layer = layer.withMode(next);
        }
        applyLayer(layer);
    }

    public void applyLayer(LayerRange layer) {
        GhostEngine.get().setLayer(layer);
        Session.markDirty();
        if (layer.isAll()) {
            Notify.actionBar("litematicaplus.message.layer_all");
        } else if (layer.mode() == LayerRange.Mode.RANGE) {
            Notify.actionBar("litematicaplus.message.layer_range", layer.axis().getName().toUpperCase(java.util.Locale.ROOT),
                    layer.min(), layer.max());
        } else {
            Notify.actionBar("litematicaplus.message.layer_single",
                    Component.translatable("litematicaplus.layer.mode." + layer.mode().name().toLowerCase(java.util.Locale.ROOT)),
                    layer.axis().getName().toUpperCase(java.util.Locale.ROOT), layer.single());
        }
    }

    private BlockPos target(Minecraft minecraft, boolean adjacent) {
        LocalPlayer player = minecraft.player;
        if (minecraft.hasAltDown()) {
            return player.blockPosition();
        }
        HitResult hit = player.pick(TARGET_RANGE, 1.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return adjacent ? blockHit.getBlockPos().relative(blockHit.getDirection()) : blockHit.getBlockPos();
        }
        return player.blockPosition();
    }

    private void setCorner(Minecraft minecraft, boolean first) {
        BlockPos pos = target(minecraft, false);
        SelectionManager.get().setCorner(first, pos);
        Notify.actionBar(first ? "litematicaplus.message.corner_first" : "litematicaplus.message.corner_second",
                pos.getX(), pos.getY(), pos.getZ());
    }

    private void movePlacement(Minecraft minecraft) {
        SchematicPlacement placement = PlacementManager.get().selected();
        if (placement == null) {
            Notify.actionBar("litematicaplus.message.no_placement");
            return;
        }
        if (placement.locked()) {
            Notify.actionBar("litematicaplus.message.placement_locked");
            return;
        }
        BlockPos pos = target(minecraft, true);
        PlacementManager.get().move(placement, pos.getX(), pos.getY(), pos.getZ());
        Notify.actionBar("litematicaplus.message.placement_moved", pos.getX(), pos.getY(), pos.getZ());
    }

    private void rotatePlacement() {
        SchematicPlacement placement = PlacementManager.get().selected();
        if (placement == null) {
            Notify.actionBar("litematicaplus.message.no_placement");
            return;
        }
        if (placement.locked()) {
            Notify.actionBar("litematicaplus.message.placement_locked");
            return;
        }
        Rotation next = placement.rotation().getRotated(Rotation.CLOCKWISE_90);
        PlacementManager.get().rotate(placement, next);
        Notify.actionBar("litematicaplus.message.placement_rotated", next.ordinal() * 90);
    }
}
