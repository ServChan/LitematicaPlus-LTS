package org.lts.litematicaplus.zones;

import com.mojang.authlib.GameProfile;
import java.util.ArrayDeque;
import java.util.List;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.util.Notify;

public final class ZoneChat {
    private static final int COMMAND_INTERVAL_TICKS = 25;
    private static final int MAX_QUEUE = 48;
    private static final ArrayDeque<String> QUEUE = new ArrayDeque<>();
    private static int cooldown;

    private ZoneChat() {
    }

    public static void register() {
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, timestamp) ->
                receive(message, sender));
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || receive(message, null));
    }

    public static ZoneProtocol.Transport transport(LitematicaPlusConfig config) {
        try {
            return ZoneProtocol.Transport.valueOf(config.zoneTransport);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return ZoneProtocol.Transport.MESSAGE;
        }
    }

    public static String template(LitematicaPlusConfig config) {
        return ZoneProtocol.template(transport(config), config.zoneCommand);
    }

    public static String self(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        return player == null ? "" : player.getGameProfile().name();
    }

    public static List<String> team(Minecraft minecraft) {
        return ZoneProtocol.team(LitematicaPlusConfig.current().zoneTeam, self(minecraft));
    }

    public static Component problem(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return Component.translatable("litematicaplus.zones.problem_offline");
        }
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        String template = template(config);
        if (ZoneProtocol.broadcasts(template)) {
            return Component.translatable("litematicaplus.zones.problem_broadcast", "/" + ZoneProtocol.head(template));
        }
        if (template.contains("{player}") && team(minecraft).isEmpty()) {
            return Component.translatable("litematicaplus.zones.problem_no_team");
        }
        String head = ZoneProtocol.head(template);
        if (player.connection.getCommands().getRoot().getChild(head) == null) {
            return Component.translatable("litematicaplus.zones.problem_no_command", "/" + head);
        }
        return null;
    }

    public static boolean send(Minecraft minecraft, String text) {
        Component problem = problem(minecraft);
        if (problem != null) {
            Notify.actionBar(problem);
            return false;
        }
        for (String command : ZoneProtocol.commands(template(LitematicaPlusConfig.current()), team(minecraft), text)) {
            if (QUEUE.size() >= MAX_QUEUE) {
                QUEUE.pollFirst();
            }
            QUEUE.addLast(command);
        }
        return true;
    }

    public static int pending() {
        return QUEUE.size();
    }

    public static void tick(Minecraft minecraft) {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null) {
            QUEUE.clear();
            return;
        }
        String command = QUEUE.pollFirst();
        if (command != null) {
            player.connection.sendCommand(command);
            cooldown = COMMAND_INTERVAL_TICKS;
        }
    }

    private static boolean receive(Component message, GameProfile sender) {
        if (message == null) {
            return true;
        }
        String text = message.getString();
        if (!ZoneProtocol.mentions(text)) {
            return true;
        }
        ZoneProtocol.Message parsed = ZoneProtocol.parse(text);
        Minecraft minecraft = Minecraft.getInstance();
        String self = self(minecraft);
        if (parsed != null && !parsed.sender().equalsIgnoreCase(self)
                && (sender == null || sender.name().equalsIgnoreCase(parsed.sender()))) {
            List<String> team = team(minecraft);
            if (team.isEmpty() || ZoneProtocol.inTeam(team, parsed.sender())) {
                ZoneManager.get().receive(minecraft, parsed);
            }
        }
        return !LitematicaPlusConfig.current().zoneHideMessages;
    }
}
