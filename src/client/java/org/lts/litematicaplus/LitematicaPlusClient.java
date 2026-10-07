package org.lts.litematicaplus;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lts.litematicaplus.compat.LitematicaConflict;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.hud.InfoHud;
import org.lts.litematicaplus.materials.ContainerHelper;
import org.lts.litematicaplus.materials.EnderChestMemory;
import org.lts.litematicaplus.materials.MaterialTooltips;
import org.lts.litematicaplus.render.BoxRenderer;
import org.lts.litematicaplus.render.GhostRenderer;
import org.lts.litematicaplus.render.TopView;
import org.lts.litematicaplus.share.SyncmaticaClient;
import org.lts.litematicaplus.tool.InventoryHighlight;
import org.lts.litematicaplus.tool.Substitutes;
import org.lts.litematicaplus.tool.ToolController;
import org.lts.litematicaplus.util.Workers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LitematicaPlusClient implements ClientModInitializer {
    public static final String MOD_ID = "litematicaplus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LitematicaPlusConfig.load();
        Substitutes.load();
        Session.init();
        Hotkeys.register();
        GhostRenderer.register();
        ContainerHelper.register();
        EnderChestMemory.register();
        MaterialTooltips.register();
        LitematicaConflict.register();
        SyncmaticaClient.register();
        ClientTickEvents.END_CLIENT_TICK.register(LitematicaPlusClient::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            Session.shutdown();
            Workers.shutdown();
            GhostEngine.get().shutdown();
            BoxRenderer.close();
        });
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath(MOD_ID, "overlay"),
                (graphics, tickCounter) -> InfoHud.render(Minecraft.getInstance(), graphics));
        LOGGER.info("Litematica Plus LTS initialized");
    }

    private static void tick(Minecraft minecraft) {
        Session.tick(minecraft);
        GhostEngine.get().tick(minecraft);
        ToolController.get().tick(minecraft);
        InfoHud.tick(minecraft);
        InventoryHighlight.tick(minecraft);
        TopView.tick(minecraft);
        Hotkeys.tick(minecraft);
        SyncmaticaClient.get().tick();
    }
}
