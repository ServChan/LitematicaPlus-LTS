package org.lts.litematicaplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LanguageFilesTest {
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() { }.getType();
    private static final Path LANG = Path.of("src/client/resources/assets/litematicaplus/lang");
    private static final Pattern KEY = Pattern.compile("\"((?:key\\.)?litematicaplus\\.[a-z0-9_.]+)\"");
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?s");
    private static final String[] LOCALES = {"en_us.json", "ru_ru.json", "es_es.json", "es_mx.json"};

    @Test
    void allLocalesHaveTheSameKeys() throws IOException {
        Set<String> expected = read("en_us.json").keySet();
        for (String locale : LOCALES) {
            assertEquals(expected, read(locale).keySet(), locale);
        }
    }

    @Test
    void allLocalesUseTheSamePlaceholders() throws IOException {
        Map<String, String> english = read("en_us.json");
        for (String locale : LOCALES) {
            Map<String, String> other = read(locale);
            for (Map.Entry<String, String> entry : english.entrySet()) {
                assertEquals(count(entry.getValue()), count(other.get(entry.getKey())), locale + " " + entry.getKey());
            }
        }
    }

    @Test
    void noTranslationIsBlank() throws IOException {
        for (String locale : LOCALES) {
            for (Map.Entry<String, String> entry : read(locale).entrySet()) {
                assertFalse(entry.getValue().isBlank(), locale + " " + entry.getKey());
            }
        }
    }

    @Test
    void everyKeyReferencedInSourcesIsTranslated() throws IOException {
        Set<String> known = read("en_us.json").keySet();
        Set<String> missing = new TreeSet<>();
        List<Path> sources;
        try (Stream<Path> stream = Files.walk(Path.of("src/client/java"))) {
            sources = stream.filter(path -> path.toString().endsWith(".java")).toList();
        }
        for (Path source : sources) {
            Matcher matcher = KEY.matcher(Files.readString(source));
            while (matcher.find()) {
                String key = matcher.group(1);
                if (key.endsWith(".json")) {
                    continue;
                }
                if (key.endsWith(".")) {
                    if (known.stream().noneMatch(candidate -> candidate.startsWith(key))) {
                        missing.add(key + "*");
                    }
                } else if (!known.contains(key)) {
                    missing.add(key);
                }
            }
        }
        assertTrue(missing.isEmpty(), "Untranslated keys: " + missing);
    }

    @Test
    void everySettingsRowAndHotkeyIsTranslated() throws IOException {
        Set<String> known = read("en_us.json").keySet();
        Set<String> missing = new TreeSet<>();
        Matcher rows = Pattern.compile("(?:toggle|number|percent)\\(\"([a-z0-9_]+)\"")
                .matcher(Files.readString(Path.of("src/client/java/org/lts/litematicaplus/gui/SettingsView.java")));
        while (rows.find()) {
            if (!known.contains("litematicaplus.option." + rows.group(1))) {
                missing.add("litematicaplus.option." + rows.group(1));
            }
        }
        String hotkeys = Files.readString(Path.of("src/client/java/org/lts/litematicaplus/Hotkeys.java"));
        Matcher keys = Pattern.compile("(?:add|toggle)\\(\"([a-z0-9_]+)\"").matcher(hotkeys);
        while (keys.find()) {
            if (!known.contains("key.litematicaplus." + keys.group(1))) {
                missing.add("key.litematicaplus." + keys.group(1));
            }
        }
        Matcher options = Pattern.compile("toggle\\(\"[a-z0-9_]+\", \"([a-z0-9_]+)\"").matcher(hotkeys);
        while (options.find()) {
            if (!known.contains("litematicaplus.option." + options.group(1))) {
                missing.add("litematicaplus.option." + options.group(1));
            }
        }
        assertTrue(missing.isEmpty(), "Untranslated keys: " + missing);
    }

    private static int count(String value) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        int total = 0;
        while (matcher.find()) {
            total++;
        }
        return total;
    }

    private static Map<String, String> read(String name) throws IOException {
        try (Reader reader = Files.newBufferedReader(LANG.resolve(name))) {
            return new Gson().fromJson(reader, MAP_TYPE);
        }
    }
}
