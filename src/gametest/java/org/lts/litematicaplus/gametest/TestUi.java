package org.lts.litematicaplus.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

final class TestUi {
    private static final double EYE_HEIGHT = 1.62;

    private TestUi() {
    }

    static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    static boolean hasLabel(ClientGameTestContext context, String label) {
        return center(context, label, false) != null;
    }

    static void clickLabel(ClientGameTestContext context, String label) {
        click(context, label, false);
    }

    static void clickLastLabel(ClientGameTestContext context, String label) {
        click(context, label, true);
    }

    private static void click(ClientGameTestContext context, String label, boolean last) {
        int[] center = center(context, label, last);
        if (center == null) {
            String visible = context.computeOnClient(client -> {
                StringBuilder names = new StringBuilder();
                if (client.gui.screen() != null) {
                    names.append(client.gui.screen().getClass().getSimpleName()).append(": ");
                    for (GuiEventListener child : client.gui.screen().children()) {
                        if (child instanceof AbstractWidget widget && widget.visible) {
                            names.append('[').append(widget.getMessage().getString()).append("] ");
                        }
                    }
                }
                return names.toString();
            });
            LitematicaPlusGameTest.require(false, "no control labelled '" + label + "' on the screen, visible: " + visible);
        }
        int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
        context.getInput().setCursorPos(center[0] * scale, center[1] * scale);
        context.waitTicks(2);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
    }

    private static int[] center(ClientGameTestContext context, String label, boolean last) {
        return context.computeOnClient(client -> {
            if (client.gui.screen() == null) {
                return null;
            }
            int[] found = null;
            for (GuiEventListener child : client.gui.screen().children()) {
                if (child instanceof AbstractWidget widget && widget.visible
                        && widget.getMessage().getString().equals(label)) {
                    found = new int[] {widget.getX() + widget.getWidth() / 2, widget.getY() + widget.getHeight() / 2};
                    if (!last) {
                        return found;
                    }
                }
            }
            return found;
        });
    }

    static void aim(ClientGameTestContext context, TestSingleplayerContext world, double x, double y, double z,
            double targetX, double targetY, double targetZ) {
        double dx = targetX - x;
        double dy = targetY - (y + EYE_HEIGHT);
        double dz = targetZ - z;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        world.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %.3f %.3f %.3f %.3f %.3f", x, y, z, yaw,
                pitch));
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(8);
    }

    static void use(ClientGameTestContext context, TestSingleplayerContext world, int waitTicks) {
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        world.getConnection().waitForServerboundPackets();
        context.waitTicks(waitTicks);
    }
}
