package org.lts.litematicaplus.printer;

import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.Hotkeys;
import org.lts.litematicaplus.ModLog;
import org.lts.litematicaplus.gui.SettingsExtensions;
import org.lts.litematicaplus.util.Notify;

public final class PrinterClient implements ClientModInitializer {
    public static final ModLog LOGGER = ModLog.of("Printer");

    @Override
    public void onInitializeClient() {
        PrinterConfig.load();
        Hotkeys.register("toggle_printer", 0, PrinterClient::toggle);
        registerSettings();
        ClientTickEvents.END_CLIENT_TICK.register(Printer::tick);
        LOGGER.ready("printer {}, range {}", PrinterConfig.current().enabled ? "on" : "off",
                PrinterConfig.current().range);
    }

    public static void registerSettings() {
        SettingsExtensions.register(new SettingsExtensions.Page("printer", () -> List.of(
                new SettingsExtensions.Toggle("printer_enabled", () -> PrinterConfig.current().enabled,
                        value -> PrinterConfig.update(config -> config.enabled = value)),
                new SettingsExtensions.Number("printer_range", () -> PrinterConfig.current().range,
                        value -> PrinterConfig.update(config -> config.range = value),
                        PrinterConfig.MIN_RANGE, PrinterConfig.MAX_RANGE, 1))));
    }

    private static void toggle(Minecraft minecraft) {
        boolean value = !PrinterConfig.current().enabled;
        PrinterConfig.update(config -> config.enabled = value);
        Notify.actionBar("litematicaplus.message.toggled", Component.translatable("litematicaplus.option.printer_enabled"),
                Component.translatable(value ? "litematicaplus.state.on" : "litematicaplus.state.off"));
    }
}
