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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.gui.HubScreen;
import org.lts.litematicaplus.gui.PlacementEditorScreen;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.tool.ToolController;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.world.LayerRange;

public final class Hotkeys {
    public record Entry(String name, KeyMapping mapping, Consumer<Minecraft> action) {
    }

    public static final int CONTROL = 1;
    public static final int SHIFT = 2;
    public static final int ALT = 4;
    private static final int ALL_MODIFIERS = CONTROL | SHIFT | ALT;
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
        add("open_menu", InputConstants.KEY_H, minecraft -> minecraft.gui.setScreen(new HubScreen(null)));
        add("edit_placement", 0, Hotkeys::editPlacement);
        toggle("toggle_render", "render_enabled", config -> config.renderEnabled, (config, value) -> config.renderEnabled = value);
        toggle("toggle_easy_place", "easy_place", config -> config.easyPlaceEnabled,
                (config, value) -> config.easyPlaceEnabled = value);
        toggle("toggle_tool", "tool_enabled", config -> config.toolEnabled, (config, value) -> config.toolEnabled = value);
        add("tool_mode", 0, minecraft -> ToolController.get().cycleMode(true));
        add("layer_mode", 0, minecraft -> ToolController.get().cycleLayerMode(minecraft));
        add("layer_up", 0, minecraft -> ToolController.get().shiftLayer(minecraft, 1));
        add("layer_down", 0, minecraft -> ToolController.get().shiftLayer(minecraft, -1));
        add("open_layers", InputConstants.KEY_F7, minecraft -> minecraft.gui.setScreen(HubScreen.layers(null)));
        add("toggle_clip", 0, Hotkeys::toggleClip);
        add("open_materials", 0, minecraft -> minecraft.gui.setScreen(HubScreen.materials(null)));
        toggle("toggle_hold", "easy_place_hold", config -> config.easyPlaceHold, (config, value) -> config.easyPlaceHold = value);
        toggle("toggle_guard", "excavation_guard", config -> config.excavationGuard,
                (config, value) -> config.excavationGuard = value);
        toggle("toggle_auto_tool", "auto_tool_switch", config -> config.autoToolSwitch,
                (config, value) -> config.autoToolSwitch = value);
        toggle("toggle_break_wrong", "break_wrong_blocks", config -> config.breakWrongBlocks,
                (config, value) -> config.breakWrongBlocks = value);
        toggle("toggle_substitutes", "easy_place_substitutes", config -> config.easyPlaceSubstitutes,
                (config, value) -> config.easyPlaceSubstitutes = value);
        toggle("toggle_inventory_highlight", "inventory_highlight", config -> config.inventoryHighlight,
                (config, value) -> config.inventoryHighlight = value);
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

    private static void toggleClip(Minecraft minecraft) {
        LayerRange.Clip clip = GhostEngine.get().clip();
        if (!clip.xEnabled() && !clip.zEnabled()) {
            minecraft.gui.setScreen(HubScreen.layers(null));
            return;
        }
        GhostEngine.get().setClip(clip.withEnabled(!clip.enabled()));
        Session.markDirty();
        Notify.actionBar("litematicaplus.message.toggled", Component.translatable("litematicaplus.layers.clip_enabled"),
                Component.translatable(!clip.enabled() ? "litematicaplus.state.on" : "litematicaplus.state.off"));
    }

    private static void editPlacement(Minecraft minecraft) {
        SchematicPlacement placement = PlacementManager.get().selected();
        if (placement == null) {
            Notify.actionBar("litematicaplus.message.no_placement");
            return;
        }
        minecraft.gui.setScreen(new PlacementEditorScreen(null, placement));
    }

    public static int modifiers(String name) {
        Integer mask = LitematicaPlusConfig.current().hotkeyModifiers.get(name);
        return mask == null ? 0 : mask & ALL_MODIFIERS;
    }

    public static void setModifiers(String name, int mask) {
        LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
        apply(config, name, mask);
        LitematicaPlusConfig.save(config);
    }

    public static void apply(LitematicaPlusConfig config, String name, int mask) {
        if ((mask & ALL_MODIFIERS) == 0) {
            config.hotkeyModifiers.remove(name);
        } else {
            config.hotkeyModifiers.put(name, mask & ALL_MODIFIERS);
        }
    }

    public static int modifiersOf(KeyEvent event) {
        return (event.hasControlDown() ? CONTROL : 0) | (event.hasShiftDown() ? SHIFT : 0) | (event.hasAltDown() ? ALT : 0);
    }

    public static int heldModifiers() {
        return (InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL)
                ? CONTROL : 0)
                | (InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT)
                ? SHIFT : 0)
                | (InputConstants.isKeyDown(InputConstants.KEY_LALT) || InputConstants.isKeyDown(InputConstants.KEY_RALT)
                ? ALT : 0);
    }

    public static int modifierBit(int key) {
        if (key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL) {
            return CONTROL;
        }
        if (key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT) {
            return SHIFT;
        }
        if (key == InputConstants.KEY_LALT || key == InputConstants.KEY_RALT) {
            return ALT;
        }
        return 0;
    }

    public static Entry entry(KeyMapping mapping) {
        for (Entry entry : ENTRIES) {
            if (entry.mapping() == mapping) {
                return entry;
            }
        }
        return null;
    }

    public static Component label(int mask, Component key) {
        MutableComponent text = Component.empty();
        if ((mask & CONTROL) != 0) {
            text.append(Component.translatable("litematicaplus.hotkeys.modifier.control")).append(" + ");
        }
        if ((mask & SHIFT) != 0) {
            text.append(Component.translatable("litematicaplus.hotkeys.modifier.shift")).append(" + ");
        }
        if ((mask & ALT) != 0) {
            text.append(Component.translatable("litematicaplus.hotkeys.modifier.alt")).append(" + ");
        }
        return key == null ? text : text.append(key);
    }

    public static boolean matches(String name, KeyEvent event) {
        KeyMapping mapping = mapping(name);
        int mask = modifiers(name);
        return !mapping.isUnbound() && mapping.matches(event) && (modifiersOf(event) & mask) == mask;
    }

    public static boolean onKeyPress(Minecraft minecraft, int action, KeyEvent event) {
        if (action == InputConstants.RELEASE || minecraft.player == null || minecraft.gui.screen() != null) {
            return false;
        }
        int held = modifiersOf(event);
        if (held == 0) {
            return false;
        }
        Entry best = null;
        int bestBits = 0;
        for (Entry entry : ENTRIES) {
            int mask = modifiers(entry.name());
            if (mask == 0 || (mask & held) != mask || entry.mapping().isUnbound() || !entry.mapping().matches(event)) {
                continue;
            }
            int bits = Integer.bitCount(mask);
            if (bits > bestBits) {
                best = entry;
                bestBits = bits;
            }
        }
        if (best == null) {
            return false;
        }
        if (action == InputConstants.PRESS) {
            best.action().accept(minecraft);
        }
        return true;
    }

    private static boolean clickAllowed(Entry entry) {
        int mask = modifiers(entry.name());
        if (mask == 0) {
            return true;
        }
        if (InputConstants.getKey(entry.mapping().saveString()).getType() == InputConstants.Type.KEYBOARD) {
            return false;
        }
        return (heldModifiers() & mask) == mask;
    }

    static void tick(Minecraft minecraft) {
        boolean blocked = minecraft.player == null || minecraft.gui.screen() != null;
        for (Entry entry : ENTRIES) {
            while (entry.mapping().consumeClick()) {
                if (!blocked && clickAllowed(entry)) {
                    entry.action().accept(minecraft);
                    blocked = minecraft.gui.screen() != null;
                }
            }
        }
    }
}
