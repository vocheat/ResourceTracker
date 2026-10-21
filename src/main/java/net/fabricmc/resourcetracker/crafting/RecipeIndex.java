package net.fabricmc.resourcetracker.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Immutable index of ordinary vanilla crafting and stonecutter recipes. */
public final class RecipeIndex {
    public record Ingredient(String key, List<String> options, long count) {
        public Ingredient {
            options = List.copyOf(options);
        }
    }

    public record Recipe(String id, String output, long outputCount, String kind,
                         List<Ingredient> ingredients) {
        public Recipe {
            ingredients = List.copyOf(ingredients);
        }
    }

    private final Map<String, List<Recipe>> recipes;
    private final List<String> diagnostics;
    private final boolean available;

    private RecipeIndex(Map<String, List<Recipe>> recipes, List<String> diagnostics) {
        this(recipes, diagnostics, true);
    }

    private RecipeIndex(Map<String, List<Recipe>> recipes, List<String> diagnostics, boolean available) {
        Map<String, List<Recipe>> copy = new TreeMap<>();
        recipes.forEach((item, values) -> copy.put(item, values.stream()
                .sorted(Comparator.comparingInt((Recipe recipe) -> recipe.kind().equals("stonecutting") ? 0 : 1)
                        .thenComparingInt(recipe -> recipe.id().startsWith("minecraft:dye_") ? 1 : 0)
                        .thenComparingLong(Recipe::outputCount).thenComparing(Recipe::id)).toList()));
        this.recipes = Collections.unmodifiableMap(copy);
        this.diagnostics = List.copyOf(diagnostics);
        this.available = available;
    }

    public List<Recipe> recipesFor(String itemId) {
        return recipes.getOrDefault(itemId, List.of());
    }

    public List<String> diagnostics() {
        return diagnostics;
    }

    public boolean available() {
        return available;
    }

    /** Reads the archive containing a known vanilla recipe, never the server resource manager. */
    public static RecipeIndex loadVanilla() {
        String probe = "data/minecraft/recipe/oak_planks.json";
        ClassLoader loader = RecipeIndex.class.getClassLoader();
        URL url = loader.getResource(probe);
        if (url == null) {
            probe = "data/minecraft/recipes/oak_planks.json";
            url = loader.getResource(probe);
        }
        if (url == null) return new RecipeIndex(Map.of(), List.of("Vanilla recipe resources are unavailable"), false);
        try {
            Map<String, String> resources = new TreeMap<>();
            if (url.openConnection() instanceof JarURLConnection connection) {
                connection.setUseCaches(false);
                try (JarFile jar = connection.getJarFile()) {
                    var entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        if (isResource(entry.getName())) {
                            try (InputStream stream = jar.getInputStream(entry)) {
                                resources.put(entry.getName(), new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                            }
                        }
                    }
                }
            } else if (url.getProtocol().equals("file")) {
                Path root = Path.of(url.toURI());
                for (int i = 0; i < probe.split("/").length; i++) root = root.getParent();
                Path data = root.resolve("data/minecraft");
                try (var paths = Files.walk(data)) {
                    Path resourceRoot = root;
                    for (Path path : paths.filter(Files::isRegularFile).toList()) {
                        String name = resourceRoot.relativize(path).toString().replace('\\', '/');
                        if (isResource(name)) resources.put(name, Files.readString(path));
                    }
                }
            } else {
                return new RecipeIndex(Map.of(), List.of("Unsupported vanilla resource URL: " + url.getProtocol()), false);
            }
            return fromJson(resources);
        } catch (Exception exception) {
            return new RecipeIndex(Map.of(), List.of("Cannot load vanilla recipes: " + exception.getMessage()), false);
        }
    }

    private static boolean isResource(String path) {
        return path.endsWith(".json") && (path.startsWith("data/minecraft/recipe/")
                || path.startsWith("data/minecraft/recipes/") || path.startsWith("data/minecraft/tags/item/")
                || path.startsWith("data/minecraft/tags/items/"));
    }

    /** Resource keys use their jar-relative paths, allowing deterministic tests without Minecraft. */
    public static RecipeIndex fromJson(Map<String, String> resources) {
        List<String> diagnostics = new ArrayList<>();
        Map<String, JsonObject> tags = new HashMap<>();
        Map<String, JsonObject> rawRecipes = new TreeMap<>();
        for (var entry : new TreeMap<>(resources).entrySet()) {
            String path = entry.getKey();
            if (!isResource(path)) continue;
            try {
                JsonObject json = JsonParser.parseString(entry.getValue()).getAsJsonObject();
                for (String prefix : List.of("data/minecraft/tags/item/", "data/minecraft/tags/items/")) {
                    if (path.startsWith(prefix)) tags.put("minecraft:" + path.substring(prefix.length(), path.length() - 5), json);
                }
                for (String prefix : List.of("data/minecraft/recipe/", "data/minecraft/recipes/")) {
                    if (path.startsWith(prefix)) rawRecipes.put("minecraft:" + path.substring(prefix.length(), path.length() - 5), json);
                }
            } catch (RuntimeException exception) {
                diagnostics.add("Invalid resource " + path + ": " + exception.getMessage());
            }
        }
        Map<String, List<Recipe>> recipes = new TreeMap<>();
        Map<String, List<String>> resolvedTags = new HashMap<>();
        for (var entry : rawRecipes.entrySet()) {
            try {
                Recipe recipe = parseRecipe(entry.getKey(), entry.getValue(), tags, resolvedTags);
                if (recipe != null && !isStorageUnpacking(recipe)) {
                    recipes.computeIfAbsent(recipe.output(), ignored -> new ArrayList<>()).add(recipe);
                }
            } catch (RuntimeException exception) {
                diagnostics.add("Invalid recipe " + entry.getKey() + ": " + exception.getMessage());
            }
        }
        return new RecipeIndex(recipes, diagnostics);
    }

    private static Recipe parseRecipe(String id, JsonObject json, Map<String, JsonObject> tags,
                                      Map<String, List<String>> resolvedTags) {
        String type = json.get("type").getAsString();
        String kind = type.startsWith("minecraft:") ? type.substring(10) : type;
        if (!Set.of("crafting_shaped", "crafting_shapeless", "stonecutting").contains(kind)) return null;
        JsonElement result = json.get("result");
        String output;
        long count;
        if (result.isJsonPrimitive()) {
            output = normalize(result.getAsString());
            count = json.has("count") ? positive(json.get("count")) : 1;
        } else {
            JsonObject object = result.getAsJsonObject();
            output = normalize(object.get(object.has("id") ? "id" : "item").getAsString());
            count = object.has("count") ? positive(object.get("count")) : 1;
        }
        List<Ingredient> ingredients = new ArrayList<>();
        if (kind.equals("crafting_shaped")) {
            Map<Character, Long> quantities = new LinkedHashMap<>();
            for (JsonElement row : json.getAsJsonArray("pattern")) {
                for (char symbol : row.getAsString().toCharArray()) {
                    if (symbol != ' ') quantities.merge(symbol, 1L, Math::addExact);
                }
            }
            JsonObject keys = json.getAsJsonObject("key");
            quantities.forEach((symbol, quantity) -> ingredients.add(new Ingredient(id + "#" + symbol,
                    ingredientOptions(keys.get(symbol.toString()), tags, resolvedTags), quantity)));
        } else if (kind.equals("crafting_shapeless")) {
            JsonArray values = json.getAsJsonArray("ingredients");
            for (int i = 0; i < values.size(); i++) {
                ingredients.add(new Ingredient(id + "#" + i, ingredientOptions(values.get(i), tags, resolvedTags), 1));
            }
        } else {
            ingredients.add(new Ingredient(id + "#0", ingredientOptions(json.get("ingredient"), tags, resolvedTags), 1));
        }
        if (ingredients.isEmpty() || ingredients.stream().anyMatch(ingredient -> ingredient.options().isEmpty())) {
            throw new IllegalArgumentException("An ingredient has no vanilla items");
        }
        return new Recipe(id, output, count, kind, ingredients);
    }

    private static long positive(JsonElement value) {
        long count = value.getAsBigDecimal().longValueExact();
        if (count <= 0) throw new IllegalArgumentException("Nonpositive item count");
        return count;
    }

    private static List<String> ingredientOptions(JsonElement value, Map<String, JsonObject> tags,
                                                   Map<String, List<String>> resolvedTags) {
        Set<String> items = new LinkedHashSet<>();
        collectIngredient(value, tags, resolvedTags, items, new HashSet<>());
        // Prefer an ordinary log over wood/stripped variants, and oak in broad wood tags.
        return items.stream().sorted(Comparator.comparingInt(RecipeIndex::ingredientPreference)
                .thenComparing(item -> item)).toList();
    }

    private static int ingredientPreference(String item) {
        if (Set.of("minecraft:oak_log", "minecraft:oak_planks", "minecraft:white_wool",
                "minecraft:white_carpet", "minecraft:glass", "minecraft:terracotta",
                "minecraft:shulker_box").contains(item)) return 0;
        if (item.endsWith("_log") && !item.contains("stripped_")) return 1;
        return 2;
    }

    private static void collectIngredient(JsonElement value, Map<String, JsonObject> tags,
                                          Map<String, List<String>> cache, Set<String> items, Set<String> visiting) {
        if (value == null || value.isJsonNull()) return;
        if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(element -> collectIngredient(element, tags, cache, items, visiting));
            return;
        }
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("item")) items.add(normalize(object.get("item").getAsString()));
            else if (object.has("tag")) collectIngredient(new com.google.gson.JsonPrimitive("#" + object.get("tag").getAsString()), tags, cache, items, visiting);
            else if (object.has("id")) collectIngredient(object.get("id"), tags, cache, items, visiting);
            return;
        }
        String id = value.getAsString();
        if (!id.startsWith("#")) {
            items.add(normalize(id));
            return;
        }
        String tag = normalize(id.substring(1));
        if (cache.containsKey(tag)) {
            items.addAll(cache.get(tag));
            return;
        }
        if (!visiting.add(tag)) throw new IllegalArgumentException("Cyclic item tag " + tag);
        Set<String> resolved = new LinkedHashSet<>();
        JsonObject definition = tags.get(tag);
        if (definition != null && definition.has("values")) {
            definition.getAsJsonArray("values").forEach(element -> collectIngredient(element, tags, cache, resolved, visiting));
        }
        visiting.remove(tag);
        cache.put(tag, List.copyOf(resolved));
        items.addAll(resolved);
    }

    private static boolean isStorageUnpacking(Recipe recipe) {
        if (recipe.output().equals("minecraft:honey_bottle") && recipe.ingredients().stream()
                .anyMatch(ingredient -> ingredient.options().contains("minecraft:honey_block"))) return true;
        if (recipe.ingredients().size() != 1 || recipe.outputCount() < 4 || recipe.ingredients().getFirst().count() != 1) return false;
        return recipe.ingredients().getFirst().options().stream().allMatch(item -> item.endsWith("_block"));
    }

    private static String normalize(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }
}
