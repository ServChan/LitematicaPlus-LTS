package org.lts.litematicaplus.zones;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ZoneProtocol {
    public static final String MARKER = "[LP-zones ";
    public static final int MAX_COMMAND_LENGTH = 256;
    public static final int MAX_TEAM = 16;
    private static final Pattern MESSAGE = Pattern.compile(
            "\\[LP-zones ([0-9a-f]{6}) ([A-Za-z0-9_]{1,16})] ([a-z]{1,8})((?: -?[0-9]{1,9}){0,6})");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Set<String> BROADCAST = Set.of("say", "me", "tellraw", "broadcast", "bc", "shout",
            "announce", "title", "bcast", "alert", "minecraft:say", "minecraft:me", "minecraft:tellraw",
            "minecraft:title");

    public enum Transport {
        MESSAGE,
        TEAM,
        CUSTOM;

        public Transport next() {
            Transport[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public record Message(String plan, String sender, String op, int[] args) {
        public int arg(int index) {
            return index < this.args.length ? this.args[index] : 0;
        }
    }

    private ZoneProtocol() {
    }

    public static String encode(String plan, String sender, String op, int... args) {
        StringBuilder builder = new StringBuilder(MARKER).append(plan).append(' ').append(sender).append("] ")
                .append(op);
        for (int arg : args) {
            builder.append(' ').append(arg);
        }
        return builder.toString();
    }

    public static boolean mentions(String text) {
        return text != null && text.contains(MARKER);
    }

    public static Message parse(String text) {
        if (!mentions(text)) {
            return null;
        }
        Matcher matcher = MESSAGE.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String tail = matcher.group(4).trim();
        int[] args;
        if (tail.isEmpty()) {
            args = new int[0];
        } else {
            String[] parts = tail.split(" ");
            args = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                args[i] = Integer.parseInt(parts[i]);
            }
        }
        return new Message(matcher.group(1), matcher.group(2), matcher.group(3), args);
    }

    public static String planId(String identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 3);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static List<String> team(String raw, String self) {
        Set<String> names = new LinkedHashSet<>();
        if (raw != null) {
            for (String part : raw.split("[,;\\s]+")) {
                if (NAME.matcher(part).matches() && !part.equalsIgnoreCase(self) && names.size() < MAX_TEAM) {
                    boolean duplicate = false;
                    for (String known : names) {
                        duplicate |= known.equalsIgnoreCase(part);
                    }
                    if (!duplicate) {
                        names.add(part);
                    }
                }
            }
        }
        return List.copyOf(names);
    }

    public static boolean inTeam(List<String> team, String name) {
        for (String member : team) {
            if (member.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public static String template(Transport transport, String custom) {
        return switch (transport) {
            case MESSAGE -> "msg {player} {text}";
            case TEAM -> "teammsg {text}";
            case CUSTOM -> {
                String value = custom == null ? "" : custom.strip();
                while (value.startsWith("/")) {
                    value = value.substring(1);
                }
                yield value.contains("{text}") ? value : (value + " {text}").strip();
            }
        };
    }

    public static String head(String template) {
        String value = template.strip();
        int space = value.indexOf(' ');
        return (space < 0 ? value : value.substring(0, space)).toLowerCase(Locale.ROOT);
    }

    public static boolean broadcasts(String template) {
        String head = head(template);
        return head.isEmpty() || head.startsWith("{") || BROADCAST.contains(head);
    }

    public static List<String> commands(String template, List<String> team, String text) {
        List<String> commands = new ArrayList<>();
        if (template.contains("{player}")) {
            for (String member : team) {
                commands.add(template.replace("{player}", member).replace("{text}", text));
            }
        } else {
            commands.add(template.replace("{text}", text));
        }
        commands.removeIf(command -> command.length() > MAX_COMMAND_LENGTH);
        return commands;
    }
}
