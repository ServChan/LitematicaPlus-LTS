package org.lts.litematicaplus.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.Hotkeys;
import org.lts.litematicaplus.gui.HubScreen;
import org.lts.litematicaplus.gui.PlacementEditorScreen;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.render.TopView;

final class EditorSuite {
    private EditorSuite() {
    }

    static void run(ClientGameTestContext context, SchematicPlacement placement) {
        require(context.computeOnClient(client -> client.gui.screen() instanceof PlacementEditorScreen),
                "placing a schematic should open the placement editor");
        BlockPos start = placement.origin();
        context.waitTicks(10);
        context.takeScreenshot("gui_editor");

        clickLabel(context, "X");
        context.getInput().typeChars("2000");
        context.waitTicks(2);
        context.takeScreenshot("gui_editor_typing");
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        context.waitTicks(3);
        require(placement.x() == 2000, "typing 2000 into X should move the placement, x is " + placement.x());
        require(context.computeOnClient(client -> client.gui.screen() instanceof PlacementEditorScreen),
                "Enter inside a coordinate field must not close the editor");

        clickLabel(context, "Z");
        context.getInput().typeChars("-350");
        context.getInput().pressKey(InputConstants.KEY_TAB);
        context.waitTicks(3);
        require(placement.z() == -350, "typing -350 into Z and leaving the field should apply it, z is " + placement.z());

        String wanted = start.getX() + " " + start.getY() + " " + start.getZ();
        context.runOnClient(client -> client.keyboardHandler.setClipboard(wanted));
        context.waitTicks(2);
        boolean clipboard = wanted.equals(context.computeOnClient(client -> client.keyboardHandler.getClipboard()));
        if (clipboard) {
            context.getInput().holdControl();
            context.getInput().pressKey(InputConstants.KEY_V);
            context.getInput().releaseControl();
            context.waitTicks(3);
            require(placement.origin().equals(start), "Ctrl+V with three numbers should move the placement to " + wanted
                    + ", it is at " + placement.origin());
        } else {
            System.out.println("EDITOR clipboard is not available in this environment, moving back directly");
            context.runOnClient(client -> PlacementManager.get().move(placement, start.getX(), start.getY(), start.getZ()));
        }
        clickFreeArea(context);

        context.getInput().pressKey(InputConstants.KEY_RIGHT);
        context.waitTicks(3);
        BlockPos nudged = placement.origin();
        int distance = Math.abs(nudged.getX() - start.getX()) + Math.abs(nudged.getZ() - start.getZ());
        require(distance == 1 && nudged.getY() == start.getY(), "the right arrow should nudge by one block, moved from "
                + start + " to " + nudged);
        context.getInput().pressKey(InputConstants.KEY_LEFT);
        context.waitTicks(3);
        require(placement.origin().equals(start), "the left arrow should undo the right arrow, got " + placement.origin());
        context.getInput().pressKey(InputConstants.KEY_PAGEUP);
        context.waitTicks(3);
        require(placement.y() == start.getY() + 1, "PgUp should raise the placement, y is " + placement.y());
        context.getInput().pressKey(InputConstants.KEY_PAGEDOWN);
        context.waitTicks(3);

        context.getInput().pressKey(InputConstants.KEY_T);
        context.waitTicks(20);
        require(TopView.active(), "T should switch to the view from above");
        double cameraY = context.computeOnClient(client -> client.gameRenderer.mainCamera().position().y);
        require(Math.abs(cameraY - TopView.cameraY()) < 0.01, "the camera should sit above the schematic, camera y "
                + cameraY + " expected " + TopView.cameraY());
        float pitch = context.computeOnClient(client -> client.gameRenderer.mainCamera().xRot());
        require(pitch > 89.0F, "the camera should look straight down, pitch " + pitch);
        context.takeScreenshot("gui_editor_top");

        int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
        int[] free = context.computeOnClient(client -> new int[]{(client.gui.screen().width - 176) / 2,
                client.gui.screen().height / 2});
        context.getInput().setCursorPos(free[0] * scale, free[1] * scale);
        context.waitTicks(2);
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
        context.getInput().moveCursor(40.0 * scale, 0.0);
        context.waitTicks(3);
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
        BlockPos dragged = placement.origin();
        require(dragged.getX() != start.getX() && dragged.getZ() == start.getZ() && dragged.getY() == start.getY(),
                "dragging sideways in the view from above should move the placement along X only, from " + start + " to "
                        + dragged);
        context.takeScreenshot("gui_editor_top_dragged");
        double before = TopView.distance();
        context.getInput().scroll(1.0);
        context.waitTicks(3);
        require(TopView.distance() < before, "the wheel should zoom the view from above");
        context.runOnClient(client -> PlacementManager.get().move(placement, start.getX(), start.getY(), start.getZ()));
        context.getInput().pressKey(InputConstants.KEY_T);
        context.waitTicks(5);
        require(!TopView.active(), "T should leave the view from above");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(5);
        require(context.computeOnClient(client -> client.gui.screen() == null), "Esc should close the placement editor");
        require(placement.origin().equals(start), "the placement should be back at " + start + ", got " + placement.origin());

        hotkeys(context);
    }

    private static void hotkeys(ClientGameTestContext context) {
        context.setScreen(() -> new HubScreen(null, HubScreen.Tab.SETTINGS));
        context.waitTicks(8);
        clickLabel(context, text("litematicaplus.settings.category.easy_place"));
        context.waitTicks(6);
        context.takeScreenshot("gui_settings_easy_place");
        clickLabel(context, ">");
        context.waitTicks(6);
        context.takeScreenshot("gui_settings_easy_place_2");
        clickLabel(context, text("litematicaplus.settings.category.hotkeys"));
        context.waitTicks(6);
        context.takeScreenshot("gui_settings_hotkeys");
        String name = text("key.litematicaplus.edit_placement");
        clickLabel(context, Component.translatable("litematicaplus.hotkeys.row", name,
                text("litematicaplus.hotkeys.unbound")).getString());
        context.waitTicks(3);
        context.takeScreenshot("gui_settings_hotkeys_listening");
        context.getInput().pressKey(InputConstants.KEY_P);
        context.waitTicks(4);
        String bound = Hotkeys.mapping("edit_placement").saveString();
        require(bound.equals("key.keyboard.p"), "pressing P while listening should bind the hotkey, it is " + bound);
        require(context.computeOnClient(client -> client.gui.screen() instanceof HubScreen),
                "binding a key must keep the settings open");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(5);
        require(context.computeOnClient(client -> client.gui.screen() == null), "Esc should close the settings");
        context.getInput().pressKey(InputConstants.KEY_P);
        context.waitTicks(5);
        require(context.computeOnClient(client -> client.gui.screen() instanceof PlacementEditorScreen),
                "the bound hotkey should open the placement editor");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(5);

        context.setScreen(() -> new HubScreen(null, HubScreen.Tab.SETTINGS));
        context.waitTicks(8);
        clickLabel(context, Component.translatable("litematicaplus.hotkeys.row", name, "P").getString());
        context.waitTicks(3);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(4);
        require(Hotkeys.mapping("edit_placement").isUnbound(), "Esc while listening should clear the binding");
        clickLabel(context, text("litematicaplus.settings.category.rendering"));
        context.waitTicks(3);
        context.setScreen(() -> null);
        context.waitTicks(3);
    }

    private static String text(String key) {
        return TestUi.text(key);
    }

    private static void clickFreeArea(ClientGameTestContext context) {
        int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
        context.getInput().setCursorPos(20.0 * scale, 20.0 * scale);
        context.waitTicks(2);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(2);
    }

    private static void clickLabel(ClientGameTestContext context, String label) {
        TestUi.clickLabel(context, label);
    }

    private static void require(boolean condition, String message) {
        LitematicaPlusGameTest.require(condition, message);
    }
}
