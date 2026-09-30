package org.lts.litematicaplus.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;

public final class BlockEntityAssist {
    private static final int TIMEOUT_TICKS = 60;
    private static final int RETRY_TICKS = 15;
    private static final int CRAFTER_ATTEMPTS = 3;
    private static final Map<BlockPos, SignJob> SIGNS = new HashMap<>();
    private static int crafterOpens;
    private static int swallowCrafters;
    private static long swallowUntil;
    private static CrafterJob crafter;

    private record SignJob(List<String> lines, long expiryTick) {
    }

    private record CrafterJob(BlockPos pos, int[] disabled, long expiryTick) {
    }

    private BlockEntityAssist() {
    }

    public static void clear() {
        SIGNS.clear();
        crafter = null;
    }

    private static CompoundTag data(BlockPos pos) {
        PlacementRuntime runtime = GhostEngine.get().runtimeAt(pos.getX(), pos.getY(), pos.getZ());
        return runtime == null || runtime.volume() == null ? null : runtime.volume().blockEntities().get(pos.asLong());
    }

    public static void afterPlace(Minecraft minecraft, BlockPos pos, BlockState target) {
        if (target.getBlock() instanceof SignBlock) {
            armSign(minecraft, pos);
        } else if (target.getBlock() instanceof CrafterBlock) {
            armCrafter(minecraft, pos);
        }
    }

    private static void armSign(Minecraft minecraft, BlockPos pos) {
        List<String> lines = new ArrayList<>(List.of("", "", "", ""));
        CompoundTag data = data(pos);
        if (data != null && minecraft.level != null) {
            data.getCompound("front_text").ifPresent(front -> SignText.CODEC
                    .parse(minecraft.level.registryAccess().createSerializationContext(NbtOps.INSTANCE), front)
                    .result().ifPresent(text -> {
                        List<Component> messages = text.getMessages(false);
                        for (int i = 0; i < Math.min(4, messages.size()); i++) {
                            lines.set(i, messages.get(i).getString());
                        }
                    }));
        }
        if (SIGNS.size() > 64) {
            SIGNS.entrySet().removeIf(entry -> entry.getValue().expiryTick() < TickQueue.now());
        }
        SIGNS.put(pos.immutable(), new SignJob(lines, TickQueue.now() + TIMEOUT_TICKS + FollowUps.latencyTicks(minecraft)));
    }

    public static boolean onOpenSignEditor(Minecraft minecraft, ClientboundOpenSignEditorPacket packet) {
        SignJob job = SIGNS.remove(packet.pos());
        LocalPlayer player = minecraft.player;
        if (job == null || player == null || job.expiryTick() < TickQueue.now()) {
            return false;
        }
        List<String> lines = packet.slot() == SignTextSlot.FRONT ? job.lines() : List.of("", "", "", "");
        player.connection.send(new ServerboundSignUpdatePacket(packet.pos(), lines, packet.slot()));
        return true;
    }

    private static void armCrafter(Minecraft minecraft, BlockPos pos) {
        CompoundTag data = data(pos);
        int[] disabled = data == null ? new int[0] : data.getIntArray("disabled_slots").orElse(new int[0]);
        if (disabled.length == 0) {
            return;
        }
        BlockPos key = pos.immutable();
        TickQueue.after(2 + FollowUps.latencyTicks(minecraft), () -> openCrafter(minecraft, key, disabled, CRAFTER_ATTEMPTS));
    }

    private static void openCrafter(Minecraft minecraft, BlockPos pos, int[] disabled, int attemptsLeft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || player.isSecondaryUseActive()
                || !(minecraft.level.getBlockState(pos).getBlock() instanceof CrafterBlock)
                || !player.isWithinBlockInteractionRange(pos, 1.0)) {
            return;
        }
        if (!TickQueue.worldReady(minecraft)) {
            return;
        }
        crafter = new CrafterJob(pos, disabled, TickQueue.now() + TIMEOUT_TICKS + FollowUps.latencyTicks(minecraft));
        crafterOpens++;
        Direction face = Direction.getApproximateNearest(player.getEyePosition().subtract(Vec3.atCenterOf(pos)));
        BlockHitResult hit = new BlockHitResult(new Vec3(pos.getX() + 0.5 + face.getStepX() * 0.5,
                pos.getY() + 0.5 + face.getStepY() * 0.5, pos.getZ() + 0.5 + face.getStepZ() * 0.5), face, pos, false);
        minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        if (attemptsLeft > 1) {
            TickQueue.after(RETRY_TICKS + FollowUps.latencyTicks(minecraft) * 2, () -> {
                if (crafter != null && crafter.pos().equals(pos)) {
                    openCrafter(minecraft, pos, disabled, attemptsLeft - 1);
                }
            });
        }
    }

    public static void userInteracted() {
        crafter = null;
        crafterOpens = 0;
        swallowCrafters = 0;
    }

    public static boolean onOpenScreen(Minecraft minecraft, ClientboundOpenScreenPacket packet) {
        CrafterJob job = crafter;
        LocalPlayer player = minecraft.player;
        if (player != null && job == null && swallowCrafters > 0 && packet.getType() == MenuType.CRAFTER_3x3) {
            if (TickQueue.now() <= swallowUntil) {
                swallowCrafters--;
                player.connection.send(new ServerboundContainerClosePacket(packet.getContainerId()));
                return true;
            }
            swallowCrafters = 0;
        }
        if (job == null || player == null) {
            return false;
        }
        if (job.expiryTick() < TickQueue.now() || packet.getType() != MenuType.CRAFTER_3x3) {
            if (job.expiryTick() < TickQueue.now()) {
                crafter = null;
            }
            return false;
        }
        crafter = null;
        swallowCrafters = Math.max(0, crafterOpens - 1);
        swallowUntil = TickQueue.now() + RETRY_TICKS + FollowUps.latencyTicks(minecraft) * 2L;
        crafterOpens = 0;
        for (int slot : job.disabled()) {
            if (slot >= 0 && slot < 9) {
                player.connection.send(new ServerboundContainerSlotStateChangedPacket(slot, packet.getContainerId(), false));
            }
        }
        player.connection.send(new ServerboundContainerClosePacket(packet.getContainerId()));
        return true;
    }
}
