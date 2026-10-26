package net.fabricmc.resourcetracker.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Checks the shipped recipe specifications independently of the index parser. */
final class VanillaRecipeCatalogRegression {
    private static final Set<String> TYPES = Set.of("crafting_shaped", "crafting_shapeless", "stonecutting",
            "smelting", "blasting", "smoking", "campfire_cooking", "smithing_transform", "crafting_transmute");
    private static final Set<String> STORAGE = Set.of("bone_meal_from_bone_block", "coal", "copper_ingot",
            "copper_ingot_from_waxed_copper_block", "diamond", "dried_kelp", "emerald",
            "gold_ingot_from_gold_block", "iron_ingot_from_iron_block", "lapis_lazuli",
            "netherite_ingot_from_netherite_block", "raw_copper", "raw_gold", "raw_iron", "redstone",
            "resin_clump", "slime_ball", "wheat", "honey_bottle");

    private record Input(Set<String> options, long count) { }

    static void check(Map<String, String> resources, RecipeIndex index) {
        Map<String, Integer> types = new TreeMap<>();
        int indexed = 0, storage = 0, special = 0;
        for (var entry : resources.entrySet()) {
            String path = entry.getKey();
            String prefix = path.startsWith("data/minecraft/recipe/") ? "data/minecraft/recipe/"
                    : path.startsWith("data/minecraft/recipes/") ? "data/minecraft/recipes/" : null;
            if (prefix == null) continue;
            String name = path.substring(prefix.length(), path.length() - 5);
            String id = "minecraft:" + name;
            JsonObject json = JsonParser.parseString(entry.getValue()).getAsJsonObject();
            String type = json.get("type").getAsString().replace("minecraft:", "");
            types.merge(type, 1, Integer::sum);
            if (type.equals("crafting_transmute") && (json.has("material_count")
                    || json.has("add_material_count_to_result"))) {
                require(index.recipesFor("minecraft:filled_map").stream().noneMatch(recipe -> recipe.id().equals(id)),
                        "Variable map cloning needs component-sensitive handling");
                special++;
                continue;
            }
            if (!TYPES.contains(type)) {
                require(type.startsWith("crafting_special_") || type.equals("crafting_decorated_pot")
                        || type.equals("smithing_trim") || type.equals("crafting_dye")
                        || type.equals("crafting_imbue") || type.equals("brewing"),
                        "Unclassified recipe type: " + type);
                special++;
                continue;
            }
            JsonElement output = json.get("result");
            String outputId;
            long count;
            if (output.isJsonPrimitive()) {
                outputId = normalize(output.getAsString());
                count = json.has("count") ? json.get("count").getAsLong() : 1;
            } else {
                JsonObject result = output.getAsJsonObject();
                outputId = normalize(result.get(result.has("id") ? "id" : "item").getAsString());
                count = result.has("count") ? result.get("count").getAsLong() : 1;
            }
            RecipeIndex.Recipe recipe = index.recipesFor(outputId).stream()
                    .filter(candidate -> candidate.id().equals(id)).findFirst().orElse(null);
            if (STORAGE.contains(name)) {
                require(recipe == null, "Storage unpacking must not become an automatic route: " + id);
                storage++;
                continue;
            }
            require(recipe != null, "Ordinary recipe omitted from the index: " + id);
            require(recipe.outputCount() == count && recipe.kind().equals(type), "Output mismatch: " + id);
            List<Input> expected = inputs(resources, json, type);
            require(recipe.ingredients().size() == expected.size(), "Ingredient slot count: " + id);
            for (int i = 0; i < expected.size(); i++) {
                var actual = recipe.ingredients().get(i);
                require(actual.count() == expected.get(i).count()
                                && Set.copyOf(actual.options()).equals(expected.get(i).options()),
                        "Ingredient mismatch in " + id + " at slot " + i);
            }
            checkAllocation(index, recipe);
            indexed++;
        }
        require(index.diagnostics().isEmpty(), "Vanilla recipe diagnostics: " + index.diagnostics());
        System.out.println("Recipe catalog checked: " + types.values().stream().mapToInt(Integer::intValue).sum()
                + " total, " + indexed + " indexed, " + storage + " storage conversions, " + special
                + " component-dependent recipes; types=" + types);
    }

    private static void checkAllocation(RecipeIndex index, RecipeIndex.Recipe recipe) {
        Map<String, Long> stock = new LinkedHashMap<>();
        Map<String, String> ingredients = new LinkedHashMap<>();
        long seed = 0;
        for (RecipeIndex.Ingredient input : recipe.ingredients()) {
            String selected = input.options().stream().filter(item -> !item.equals(recipe.output())).findFirst().orElse(null);
            if (selected == null) {
                seed = Math.addExact(seed, input.count());
                stock.merge(recipe.output(), input.count(), Math::addExact);
                continue;
            }
            ingredients.put(input.key(), selected);
            stock.merge(selected, Math.multiplyExact(input.count(), 2), Math::addExact);
        }
        require(seed < recipe.outputCount(), "Recipe must have a positive net yield: " + recipe.id());
        Map<String, Long> goals = Map.of(recipe.output(), Math.addExact(seed,
                Math.multiplyExact(recipe.outputCount() - seed, 2)));
        Set<String> stops = new LinkedHashSet<>(stock.keySet());
        stops.remove(recipe.output());
        var complete = CraftingPlan.calculate(index, goals, Set.of(), stock,
                Map.of(recipe.output(), recipe.id()), ingredients, stops);
        require(complete.valid() && complete.leaves().values().stream().allMatch(leaf -> leaf.remaining() == 0),
                "Two batches with exact declared inputs are not supplied: " + recipe.id() + " " + complete);
        for (String item : stock.keySet()) {
            if (item.equals("minecraft:bucket") || item.equals("minecraft:glass_bottle")) continue;
            Map<String, Long> shortStock = new LinkedHashMap<>(stock);
            shortStock.put(item, stock.get(item) - 1);
            var shortage = CraftingPlan.calculate(index, goals, Set.of(), shortStock,
                    Map.of(recipe.output(), recipe.id()), ingredients, stops);
            require(shortage.valid() && shortage.leaves().values().stream().mapToLong(CraftingPlan.Leaf::remaining).sum() == 1,
                    "Removing one input must leave exactly one missing item: " + recipe.id() + " / " + item + " " + shortage);
        }
    }

    private static List<Input> inputs(Map<String, String> resources, JsonObject json, String type) {
        List<Input> result = new ArrayList<>();
        if (type.equals("crafting_shaped")) {
            Map<Character, Long> counts = new LinkedHashMap<>();
            for (JsonElement row : json.getAsJsonArray("pattern")) {
                for (char symbol : row.getAsString().toCharArray()) if (symbol != ' ') counts.merge(symbol, 1L, Long::sum);
            }
            counts.forEach((symbol, count) -> result.add(new Input(resolve(resources,
                    json.getAsJsonObject("key").get(symbol.toString()), new LinkedHashSet<>()), count)));
        } else if (type.equals("crafting_shapeless")) {
            for (JsonElement value : json.getAsJsonArray("ingredients")) result.add(new Input(resolve(resources, value, new LinkedHashSet<>()), 1));
        } else {
            List<String> slots = switch (type) {
                case "smithing_transform" -> List.of("template", "base", "addition");
                case "crafting_transmute" -> List.of("input", "material");
                default -> List.of("ingredient");
            };
            for (String slot : slots) result.add(new Input(resolve(resources, json.get(slot), new LinkedHashSet<>()), 1));
        }
        return result;
    }

    private static Set<String> resolve(Map<String, String> resources, JsonElement value, Set<String> path) {
        Set<String> result = new LinkedHashSet<>();
        if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) result.addAll(resolve(resources, child, path));
        } else if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("tag")) result.addAll(resolve(resources, new com.google.gson.JsonPrimitive("#" + object.get("tag").getAsString()), path));
            else result.addAll(resolve(resources, object.get(object.has("item") ? "item" : "id"), path));
        } else {
            String id = value.getAsString();
            if (!id.startsWith("#")) result.add(normalize(id));
            else {
                id = normalize(id.substring(1));
                require(path.add(id), "Cyclic tag in shipped data: " + id);
                String tagPath = "data/" + id.replace(":", "/tags/item/") + ".json";
                String text = resources.getOrDefault(tagPath, resources.get(tagPath.replace("/item/", "/items/")));
                require(text != null, "Missing shipped ingredient tag: " + id);
                for (JsonElement child : JsonParser.parseString(text).getAsJsonObject().getAsJsonArray("values")) result.addAll(resolve(resources, child, path));
                path.remove(id);
            }
        }
        return result;
    }

    private static String normalize(String id) { return id.contains(":") ? id : "minecraft:" + id; }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
