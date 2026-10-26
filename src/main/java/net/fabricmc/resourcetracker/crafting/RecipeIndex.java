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

/** Immutable index of vanilla recipes whose inputs and output can be represented by item IDs. */
public final class RecipeIndex {
    private static final Set<String> SUPPORTED_KINDS = Set.of("crafting_shaped", "crafting_shapeless",
            "stonecutting", "smelting", "blasting", "smoking", "campfire_cooking",
            "smithing_transform", "crafting_transmute");
    private static final Map<String, Set<String>> STORAGE_SOURCES = Map.ofEntries(
            Map.entry("minecraft:bone_meal", Set.of("minecraft:bone_block")),
            Map.entry("minecraft:coal", Set.of("minecraft:coal_block")),
            Map.entry("minecraft:copper_ingot", Set.of("minecraft:copper_block", "minecraft:waxed_copper_block")),
            Map.entry("minecraft:diamond", Set.of("minecraft:diamond_block")),
            Map.entry("minecraft:dried_kelp", Set.of("minecraft:dried_kelp_block")),
            Map.entry("minecraft:emerald", Set.of("minecraft:emerald_block")),
            Map.entry("minecraft:gold_ingot", Set.of("minecraft:gold_block")),
            Map.entry("minecraft:iron_ingot", Set.of("minecraft:iron_block")),
            Map.entry("minecraft:lapis_lazuli", Set.of("minecraft:lapis_block")),
            Map.entry("minecraft:netherite_ingot", Set.of("minecraft:netherite_block")),
            Map.entry("minecraft:raw_copper", Set.of("minecraft:raw_copper_block")),
            Map.entry("minecraft:raw_gold", Set.of("minecraft:raw_gold_block")),
            Map.entry("minecraft:raw_iron", Set.of("minecraft:raw_iron_block")),
            Map.entry("minecraft:redstone", Set.of("minecraft:redstone_block")),
            Map.entry("minecraft:resin_clump", Set.of("minecraft:resin_block")),
            Map.entry("minecraft:slime_ball", Set.of("minecraft:slime_block")),
            Map.entry("minecraft:wheat", Set.of("minecraft:hay_block")));

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
                .sorted(Comparator.comparingInt(RecipeIndex::recipePreference)
                        .thenComparingInt(recipe -> recipe.id().startsWith("minecraft:dye_") ? 1 : 0)
                        .thenComparing(Comparator.comparingLong(Recipe::outputCount).reversed())
                        .thenComparingInt(recipe -> recipe.ingredients().stream()
                                .anyMatch(ingredient -> ingredient.options().stream().anyMatch(input -> input.startsWith("minecraft:raw_"))) ? 0 : 1)
                        .thenComparing(Recipe::id)).toList()));
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

    private static int recipePreference(Recipe recipe) {
        if (recipe.kind().equals("stonecutting")) return 0;
        if (recipe.id().equals(recipe.output())) return 1;
        return switch (recipe.kind()) {
            case "smelting" -> 2;
            case "blasting" -> 3;
            case "smoking" -> 4;
            case "campfire_cooking" -> 5;
            default -> 6;
        };
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
        if (!SUPPORTED_KINDS.contains(kind)) return null;
        // Variable-count transmutation (map cloning) also copies item components. A fixed
        // item-ID recipe would lose both its yield semantics and the identity of the seed.
        if (kind.equals("crafting_transmute") && (json.has("material_count")
                || json.has("add_material_count_to_result"))) return null;
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
        } else if (kind.equals("smithing_transform")) {
            for (String slot : List.of("template", "base", "addition")) {
                ingredients.add(new Ingredient(id + "#" + slot,
                        ingredientOptions(json.get(slot), tags, resolvedTags), 1));
            }
        } else if (kind.equals("crafting_transmute")) {
            for (String slot : List.of("input", "material")) {
                ingredients.add(new Ingredient(id + "#" + slot,
                        ingredientOptions(json.get(slot), tags, resolvedTags), 1));
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
                "minecraft:shulker_box", "minecraft:bundle", "minecraft:sand", "minecraft:quartz_block").contains(item)) return 0;
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
        if (recipe.ingredients().size() != 1 || recipe.ingredients().getFirst().count() != 1) return false;
        Set<String> sources = STORAGE_SOURCES.getOrDefault(recipe.output(), Set.of());
        return recipe.ingredients().getFirst().options().stream().allMatch(sources::contains);
    }

    private static String normalize(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }
}
