package net.fabricmc.resourcetracker.localization;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Protect complete translations, formatting arguments, and selectable custom languages. */
public final class LocalizationRegression {
    private static final List<String> LOCALES = List.of("en_us", "ru_ru", "zh_cn", "hi_in", "es_es", "ar_sa",
            "fr_fr", "bn_bd", "pt_br", "id_id", "ur_pk", "de_de");
    private static final Pattern ARGUMENT = Pattern.compile("%(?:\\d+\\$)?[sd%]");

    public static void main(String[] args) throws Exception {
        URL english = LocalizationRegression.class.getResource("/assets/resourcetracker/lang/en_us.json");
        check(english != null, "English translation resource is missing");
        Map<String, String> source = read(english);
        for (String locale : LOCALES) {
            Map<String, String> translated = read(new URL(english, locale + ".json"));
            check(translated.keySet().equals(source.keySet()), locale + " must cover every English key");
            for (var entry : translated.entrySet()) {
                String value = entry.getValue();
                check(!value.isBlank() && value.indexOf('\uFFFD') < 0, locale + ": invalid text for " + entry.getKey());
                check(arguments(value).equals(arguments(source.get(entry.getKey()))),
                        locale + ": formatting arguments differ for " + entry.getKey());
            }
        }
        for (String locale : List.of("bn_bd", "ur_pk")) {
            Map<String, String> common = read(new URL(english, "../../minecraft/lang/" + locale + ".json"));
            for (String key : List.of("gui.back", "gui.cancel", "gui.done", "gui.yes", "gui.no", "options.on", "options.off"))
                check(common.containsKey(key) && !common.get(key).isBlank(), locale + ": missing shared UI label " + key);
        }
        try (var stream = new URL(english, "../../../pack.mcmeta").openStream();
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject metadata = JsonParser.parseReader(reader).getAsJsonObject();
            // A language-only pack.mcmeta causes Fabric to skip every mod resource.
            check(metadata.has("pack"), "Language registration needs a valid pack section");
            JsonObject pack = metadata.getAsJsonObject("pack");
            check(!pack.get("description").getAsString().isBlank() && pack.get("pack_format").getAsInt() > 0,
                    "Resource pack must declare its description and legacy format");
            JsonObject languages = metadata.getAsJsonObject("language");
            check(!languages.getAsJsonObject("bn_bd").get("bidirectional").getAsBoolean(), "Bengali is left-to-right");
            check(languages.getAsJsonObject("ur_pk").get("bidirectional").getAsBoolean(), "Urdu is right-to-left");
            for (String locale : List.of("bn_bd", "ur_pk")) {
                JsonObject info = languages.getAsJsonObject(locale);
                check(!info.get("name").getAsString().isBlank() && !info.get("region").getAsString().isBlank(),
                        locale + " must be discoverable in the native language menu");
            }
        }
        System.out.println("Localization regression checks passed: " + LOCALES.size() + " locales, " + source.size() + " keys each");
    }

    private static Map<String, String> read(URL resource) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (var stream = resource.openStream(); var reader = new JsonReader(new InputStreamReader(stream, decoder))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                check(reader.peek() == JsonToken.STRING, "Translation must be a string: " + key);
                check(result.putIfAbsent(key, reader.nextString()) == null, "Duplicate translation key: " + key);
            }
            reader.endObject();
            check(reader.peek() == JsonToken.END_DOCUMENT, "Unexpected trailing translation data");
        }
        return result;
    }

    private static List<String> arguments(String text) {
        List<String> result = new ArrayList<>();
        var matcher = ARGUMENT.matcher(text);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
