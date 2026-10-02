package org.lts.litematicaplus.tool;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;
import org.lts.litematicaplus.mixin.LocalPlayerAccessor;

public final class LookLock {
    private static final int MAX_TICKS = 30;
    private static boolean active;
    private static float yaw;
    private static float pitch;
    private static long expiryTick;
    private static LocalPlayer swapped;
    private static float realYaw;
    private static float realPitch;

    private LookLock() {
    }

    public static void lock(float lockYaw, float lockPitch, int ticks) {
        active = true;
        yaw = lockYaw;
        pitch = lockPitch;
        expiryTick = TickQueue.now() + Math.min(MAX_TICKS, Math.max(1, ticks));
    }

    public static void clear() {
        active = false;
    }

    public static boolean locked() {
        if (active && TickQueue.now() > expiryTick) {
            active = false;
        }
        return active;
    }

    public static void beforeSendPosition(LocalPlayer player) {
        afterSendPosition(player);
        if (!locked()) {
            return;
        }
        realYaw = player.getYRot();
        realPitch = player.getXRot();
        player.setYRot(yaw);
        player.setXRot(pitch);
        swapped = player;
    }

    public static void afterSendPosition(LocalPlayer player) {
        LocalPlayer owner = swapped;
        swapped = null;
        if (owner == player) {
            player.setYRot(realYaw);
            player.setXRot(realPitch);
        }
    }

    public static void sendRotation(Minecraft minecraft, float sendYaw, float sendPitch) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        LocalPlayerAccessor last = (LocalPlayerAccessor) player;
        if (last.litematicaplus$getYRotLast() == sendYaw && last.litematicaplus$getXRotLast() == sendPitch) {
            return;
        }
        boolean onGround = player.onGround();
        boolean collision = player.horizontalCollision;
        player.connection.send(new ServerboundMovePlayerPacket.Rot(sendYaw, sendPitch, onGround, collision));
        last.litematicaplus$setYRotLast(sendYaw);
        last.litematicaplus$setXRotLast(sendPitch);
        last.litematicaplus$setLastOnGround(onGround);
        last.litematicaplus$setLastHorizontalCollision(collision);
    }

    public static void sendRealRotation(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player != null) {
            sendRotation(minecraft, player.getYRot(), player.getXRot());
        }
    }

    public static void sendSneak(Minecraft minecraft, boolean sneak) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        Input last = player.getLastSentInput();
        boolean shift = sneak || player.input.keyPresses.shift();
        player.connection.send(new ServerboundPlayerInputPacket(new Input(last.forward(), last.backward(), last.left(),
                last.right(), last.jump(), shift, last.sprint())));
    }
}
