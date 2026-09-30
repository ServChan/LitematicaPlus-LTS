package org.lts.litematicaplus;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.gui.HubScreen;
import org.lts.litematicaplus.hud.InfoHud;
import org.lts.litematicaplus.render.GhostRenderer;
import org.lts.litematicaplus.tool.ToolController;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.util.Workers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LitematicaPlusClient implements ClientModInitializer {
    public static final String MOD_ID = "litematicaplus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static KeyMapping.Category category;

    public static KeyMapping OPEN_MENU_KEY;
    public static KeyMapping TOGGLE_RENDER_KEY;
    public static KeyMapping TOGGLE_EASY_PLACE_KEY;
    public static KeyMapping TOOL_MODE_KEY;
    public static KeyMapping LAYER_MODE_KEY;
    public static KeyMapping LAYER_UP_KEY;
    public static KeyMapping LAYER_DOWN_KEY;

    @Override
    public void onInitializeClient() {
        LitematicaPlusConfig.load();
        Session.init();
        category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        OPEN_MENU_KEY = register("open_menu", InputConstants.KEY_K);
        TOGGLE_RENDER_KEY = register("toggle_render", InputConstants.UNKNOWN.getValue());
        TOGGLE_EASY_PLACE_KEY = register("toggle_easy_place", InputConstants.UNKNOWN.getValue());
        TOOL_MODE_KEY = register("tool_mode", InputConstants.UNKNOWN.getValue());
        LAYER_MODE_KEY = register("layer_mode", InputConstants.UNKNOWN.getValue());
        LAYER_UP_KEY = register("layer_up", InputConstants.UNKNOWN.getValue());
        LAYER_DOWN_KEY = register("layer_down", InputConstants.UNKNOWN.getValue());

        GhostRenderer.register();
        ClientTickEvents.END_CLIENT_TICK.register(LitematicaPlusClient::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            Session.shutdown();
            Workers.shutdown();
        });
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath(MOD_ID, "overlay"),
                (graphics, tickCounter) -> InfoHud.render(Minecraft.getInstance(), graphics));
        LOGGER.info("Litematica Plus LTS initialized");
    }

    private static KeyMapping register(String name, int key) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + MOD_ID + "." + name,
                InputConstants.Type.KEYBOARD, key, category));
    }

    private static void tick(Minecraft minecraft) {
        Session.tick(minecraft);
        GhostEngine.get().tick(minecraft);
        ToolController.get().tick(minecraft);
        InfoHud.tick(minecraft);
        if (minecraft.player == null || minecraft.gui.screen() != null) {
            drain(OPEN_MENU_KEY);
            drain(TOGGLE_RENDER_KEY);
            drain(TOGGLE_EASY_PLACE_KEY);
            drain(TOOL_MODE_KEY);
            drain(LAYER_MODE_KEY);
            drain(LAYER_UP_KEY);
            drain(LAYER_DOWN_KEY);
            return;
        }
        while (OPEN_MENU_KEY.consumeClick()) {
            minecraft.gui.setScreen(new HubScreen(null));
        }
        while (TOGGLE_RENDER_KEY.consumeClick()) {
            LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
            config.renderEnabled = !config.renderEnabled;
            LitematicaPlusConfig.save(config);
            Notify.actionBar(config.renderEnabled ? "litematicaplus.message.render_on" : "litematicaplus.message.render_off");
        }
        while (TOGGLE_EASY_PLACE_KEY.consumeClick()) {
            LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
            config.easyPlaceEnabled = !config.easyPlaceEnabled;
            LitematicaPlusConfig.save(config);
            Notify.actionBar(config.easyPlaceEnabled
                    ? "litematicaplus.message.easy_place_on" : "litematicaplus.message.easy_place_off");
        }
        while (TOOL_MODE_KEY.consumeClick()) {
            ToolController.get().cycleMode(true);
        }
        while (LAYER_MODE_KEY.consumeClick()) {
            ToolController.get().cycleLayerMode(minecraft);
        }
        while (LAYER_UP_KEY.consumeClick()) {
            ToolController.get().shiftLayer(minecraft, 1);
        }
        while (LAYER_DOWN_KEY.consumeClick()) {
            ToolController.get().shiftLayer(minecraft, -1);
        }
    }

    private static void drain(KeyMapping key) {
        while (key.consumeClick()) {
            continue;
        }
    }
}
