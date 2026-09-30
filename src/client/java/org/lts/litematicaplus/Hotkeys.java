package org.lts.litematicaplus;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lts.litematicaplus.gui.HubScreen;
import org.lts.litematicaplus.gui.PlacementEditorScreen;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.tool.ToolController;
import org.lts.litematicaplus.util.Notify;

public final class Hotkeys {
    public record Entry(String name, KeyMapping mapping, Consumer<Minecraft> action) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static KeyMapping.Category category;
    private static KeyMapping takeMaterials;

    private Hotkeys() {
    }

    public static List<Entry> entries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    public static KeyMapping takeMaterials() {
        return takeMaterials;
    }

    public static KeyMapping mapping(String name) {
        for (Entry entry : ENTRIES) {
            if (entry.name().equals(name)) {
                return entry.mapping();
            }
        }
        throw new IllegalArgumentException(name);
    }

    static void register() {
        category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(LitematicaPlusClient.MOD_ID, "main"));
        add("open_menu", InputConstants.KEY_K, minecraft -> minecraft.gui.setScreen(new HubScreen(null)));
        add("edit_placement", 0, Hotkeys::editPlacement);
        toggle("toggle_render", "render_enabled", config -> config.renderEnabled, (config, value) -> config.renderEnabled = value);
        toggle("toggle_easy_place", "easy_place", config -> config.easyPlaceEnabled,
                (config, value) -> config.easyPlaceEnabled = value);
        toggle("toggle_tool", "tool_enabled", config -> config.toolEnabled, (config, value) -> config.toolEnabled = value);
        add("tool_mode", 0, minecraft -> ToolController.get().cycleMode(true));
        add("layer_mode", 0, minecraft -> ToolController.get().cycleLayerMode(minecraft));
        add("layer_up", 0, minecraft -> ToolController.get().shiftLayer(minecraft, 1));
        add("layer_down", 0, minecraft -> ToolController.get().shiftLayer(minecraft, -1));
        toggle("toggle_hold", "easy_place_hold", config -> config.easyPlaceHold, (config, value) -> config.easyPlaceHold = value);
        toggle("toggle_guard", "excavation_guard", config -> config.excavationGuard,
                (config, value) -> config.excavationGuard = value);
        toggle("toggle_auto_tool", "auto_tool_switch", config -> config.autoToolSwitch,
                (config, value) -> config.autoToolSwitch = value);
        toggle("toggle_break_wrong", "break_wrong_blocks", config -> config.breakWrongBlocks,
                (config, value) -> config.breakWrongBlocks = value);
        toggle("toggle_substitutes", "easy_place_substitutes", config -> config.easyPlaceSubstitutes,
                (config, value) -> config.easyPlaceSubstitutes = value);
        takeMaterials = add("take_materials", 0, minecraft -> { });
    }

    private static KeyMapping add(String name, int key, Consumer<Minecraft> action) {
        KeyMapping mapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key." + LitematicaPlusClient.MOD_ID + "." + name, InputConstants.Type.KEYBOARD,
                key == 0 ? InputConstants.UNKNOWN.getValue() : key, category));
        ENTRIES.add(new Entry(name, mapping, action));
        return mapping;
    }

    private static void toggle(String name, String option, Predicate<LitematicaPlusConfig> getter,
            BiConsumer<LitematicaPlusConfig, Boolean> setter) {
        add(name, 0, minecraft -> {
            LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
            boolean value = !getter.test(config);
            setter.accept(config, value);
            LitematicaPlusConfig.save(config);
            Notify.actionBar("litematicaplus.message.toggled", Component.translatable("litematicaplus.option." + option),
                    Component.translatable(value ? "litematicaplus.state.on" : "litematicaplus.state.off"));
        });
    }

    private static void editPlacement(Minecraft minecraft) {
        SchematicPlacement placement = PlacementManager.get().selected();
        if (placement == null) {
            Notify.actionBar("litematicaplus.message.no_placement");
            return;
        }
        minecraft.gui.setScreen(new PlacementEditorScreen(null, placement));
    }

    static void tick(Minecraft minecraft) {
        boolean blocked = minecraft.player == null || minecraft.gui.screen() != null;
        for (Entry entry : ENTRIES) {
            while (entry.mapping().consumeClick()) {
                if (!blocked) {
                    entry.action().accept(minecraft);
                    blocked = minecraft.gui.screen() != null;
                }
            }
        }
    }
}
