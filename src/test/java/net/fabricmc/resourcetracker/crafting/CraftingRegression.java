package net.fabricmc.resourcetracker.crafting;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

/** Standalone regression checks; run with JavaExec, no game or test framework required. */
public final class CraftingRegression {
    private static final String P = "data/minecraft/recipe/";

    public static void main(String[] args) throws Exception {
        Map<String, String> resources = new LinkedHashMap<>();
        resources.put("data/minecraft/tags/item/oak_logs.json", "{\"values\":[\"minecraft:oak_log\",\"minecraft:oak_wood\"]}");
        resources.put("data/minecraft/tags/item/planks.json", "{\"values\":[\"minecraft:oak_planks\",\"minecraft:birch_planks\",\"minecraft:spruce_planks\"]}");
        resources.put(P + "oak_planks.json", recipe("oak_planks", 4, "\"#minecraft:oak_logs\""));
        resources.put(P + "birch_planks.json", recipe("birch_planks", 4, "\"minecraft:birch_log\""));
        resources.put(P + "spruce_planks.json", recipe("spruce_planks", 4, "\"minecraft:spruce_log\""));
        resources.put(P + "chest.json", "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"PPP\",\"P P\",\"PPP\"],\"key\":{\"P\":\"#minecraft:planks\"},\"result\":{\"id\":\"minecraft:chest\"}}");
        resources.put(P + "spruce_door.json", "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"PP\",\"PP\",\"PP\"],\"key\":{\"P\":\"minecraft:spruce_planks\"},\"result\":{\"id\":\"minecraft:spruce_door\",\"count\":3}}");
        resources.put(P + "birch_door.json", "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"PP\",\"PP\",\"PP\"],\"key\":{\"P\":\"minecraft:birch_planks\"},\"result\":{\"id\":\"minecraft:birch_door\",\"count\":3}}");
        resources.put(P + "stick.json", recipe("stick", 4, "\"#minecraft:planks\",\"#minecraft:planks\""));
        resources.put(P + "crafting_table.json", recipe("crafting_table", 1, "\"#minecraft:planks\",\"#minecraft:planks\",\"#minecraft:planks\",\"#minecraft:planks\""));
        resources.put(P + "a.json", recipe("a", 1, "\"minecraft:oak_planks\""));
        resources.put(P + "b.json", recipe("b", 1, "\"minecraft:oak_planks\""));
        resources.put(P + "bucket.json", recipe("bucket", 1, "\"minecraft:iron_ingot\",\"minecraft:iron_ingot\",\"minecraft:iron_ingot\""));
        resources.put(P + "cake.json", recipe("cake", 1, "\"minecraft:milk_bucket\",\"minecraft:milk_bucket\",\"minecraft:milk_bucket\""));
        resources.put(P + "stone_stairs.json", recipe("stone_stairs", 4, "\"minecraft:stone\",\"minecraft:stone\",\"minecraft:stone\",\"minecraft:stone\",\"minecraft:stone\",\"minecraft:stone\""));
        resources.put(P + "stone_stairs_stonecutting.json", "{\"type\":\"minecraft:stonecutting\",\"ingredient\":\"minecraft:stone\",\"result\":{\"id\":\"minecraft:stone_stairs\"}}");
        resources.put(P + "iron_unpack.json", recipe("iron_ingot", 9, "\"minecraft:iron_block\""));
        resources.put(P + "iron_block.json", recipe("iron_block", 1, "\"minecraft:iron_ingot\""));
        resources.put(P + "iron_nugget.json", recipe("iron_nugget", 9, "\"minecraft:iron_ingot\""));
        resources.put(P + "gold_unpack.json", recipe("gold_ingot", 9, "\"minecraft:gold_block\""));
        resources.put(P + "gold_nugget.json", recipe("gold_nugget", 9, "\"minecraft:gold_ingot\""));
        resources.put(P + "smelt.json", "{\"type\":\"minecraft:smelting\",\"ingredient\":\"minecraft:iron_ore\",\"result\":{\"id\":\"minecraft:iron_ingot\"}}");
        RecipeIndex index = RecipeIndex.fromJson(resources);
        check(index.diagnostics().isEmpty(), "valid synthetic recipes: " + index.diagnostics());

        var result = plan(index, Map.of("minecraft:oak_planks", 64L), Map.of("minecraft:oak_planks", 16L, "minecraft:oak_log", 5L));
        leaf(result, "oak_log", 12, 5);
        check(result.options().stream().anyMatch(option -> option.itemId().equals("minecraft:oak_planks")), "goal choice");
        result = plan(index, Map.of("minecraft:oak_planks", 64L), Map.of("minecraft:oak_planks", 64L));
        check(result.leaves().isEmpty(), "completed intermediate needs no raw materials");
        check(result.options().size() == 2, "choices remain reachable when inventory is complete");

        result = plan(index, Map.of("minecraft:chest", 1L), Map.of("minecraft:spruce_planks", 8L));
        check(result.valid() && result.leaves().isEmpty(), "compatible stock supplies a chest without oak logs");
        check(result.options().stream().anyMatch(option -> option.itemId().equals("minecraft:spruce_planks")), "stock selects spruce route");
        result = CraftingPlan.calculate(index, Map.of("minecraft:chest", 1L), Set.of(), Map.of("minecraft:spruce_planks", 8L),
                Map.of(), Map.of("minecraft:chest#P", "minecraft:oak_planks"), Set.of());
        leaf(result, "oak_log", 2, 0);
        result = plan(index, Map.of("minecraft:chest", 1L, "minecraft:spruce_planks", 8L), Map.of("minecraft:spruce_planks", 8L));
        leaf(result, "oak_log", 2, 0);
        result = plan(index, Map.of("minecraft:chest", 1L), Map.of("minecraft:spruce_planks", 8L, "minecraft:oak_planks", 8L));
        check(result.options().stream().anyMatch(option -> option.itemId().equals("minecraft:oak_planks")), "equal stock preserves default order");
        result = plan(index, Map.of("minecraft:chest", 1L, "minecraft:stick", 4L), Map.of("minecraft:spruce_planks", 8L));
        leaf(result, "spruce_log", 1, 0);
        result = plan(index, Map.of("minecraft:chest", 1L), Map.of("minecraft:oak_planks", 4L, "minecraft:spruce_planks", 4L));
        check(result.valid() && result.leaves().isEmpty(), "mixed compatible stock supplies all chest slots");
        result = plan(index, Map.of("minecraft:chest", 1L), Map.of("minecraft:oak_planks", 4L, "minecraft:spruce_planks", 2L));
        leaf(result, "oak_log", 1, 0);

        checkCompatibleSurplus(index);
        result = plan(index, Map.of("minecraft:chest", 1L, "minecraft:spruce_door", 3L),
                Map.of("minecraft:oak_planks", 8L, "minecraft:spruce_planks", 6L));
        check(result.valid() && result.leaves().isEmpty(), "flexible chest preserves fixed spruce door supplies");
        result = plan(index, Map.of("minecraft:chest", 1L, "minecraft:spruce_door", 3L),
                Map.of("minecraft:oak_planks", 5L, "minecraft:spruce_planks", 8L));
        leaf(result, "spruce_log", 1, 0);
        check(result.leaves().size() == 1, "mixed stock leaves only one additional spruce log");
        result = CraftingPlan.calculate(index, Map.of("minecraft:chest", 1L), Set.of(), Map.of("minecraft:oak_planks", 4L, "minecraft:spruce_planks", 4L),
                Map.of(), Map.of("minecraft:chest#P", "minecraft:oak_planks"), Set.of());
        leaf(result, "oak_log", 1, 0);

        result = plan(index, Map.of("minecraft:a", 1L, "minecraft:b", 1L), Map.of());
        leaf(result, "oak_log", 1, 0);
        check(result.leftovers().get("minecraft:oak_planks") == 2, "shared demands round after aggregation");
        result = plan(index, Map.of("minecraft:oak_planks", 4L, "minecraft:stick", 4L), Map.of("minecraft:oak_planks", 4L));
        leaf(result, "oak_log", 1, 0);
        result = plan(index, Map.of("minecraft:a", 1L, "minecraft:b", 1L), Map.of("minecraft:oak_planks", 1L));
        leaf(result, "oak_log", 1, 0);
        check(result.leftovers().get("minecraft:oak_planks") == 3, "global intermediate stock consumed once");

        result = plan(index, Map.of("minecraft:stone_stairs", 5L), Map.of());
        leaf(result, "stone", 5, 0);
        check(index.recipesFor("minecraft:iron_ingot").isEmpty(), "storage unpacking and smelting excluded");
        check(index.recipesFor("minecraft:gold_ingot").isEmpty(), "gold block unpacking excluded");
        check(!index.recipesFor("minecraft:iron_nugget").isEmpty(), "iron ingots unpack to nuggets");
        check(!index.recipesFor("minecraft:gold_nugget").isEmpty(), "gold ingots unpack to nuggets");
        result = CraftingPlan.calculate(index, Map.of("minecraft:oak_planks", 64L), Set.of(), Map.of(),
                Map.of(), Map.of("minecraft:oak_planks#0", "minecraft:oak_wood"), Set.of("minecraft:oak_wood"));
        leaf(result, "oak_wood", 16, 0);
        result = CraftingPlan.calculate(index, Map.of("minecraft:oak_planks", 64L), Set.of("minecraft:oak_planks"), Map.of(), Map.of(), Map.of(), Set.of());
        check(result.leaves().isEmpty(), "manual completion");
        result = CraftingPlan.calculate(index, Map.of("minecraft:oak_planks", 64L), Set.of(), Map.of("minecraft:oak_planks", 16L), Map.of(), Map.of(), Set.of("minecraft:oak_planks"));
        leaf(result, "oak_planks", 64, 16);

        result = plan(index, Map.of("minecraft:cake", 1L, "minecraft:bucket", 4L), Map.of());
        leaf(result, "iron_ingot", 3, 0);
        leaf(result, "milk_bucket", 3, 0);
        result = plan(index, Map.of("minecraft:cake", 1L), Map.of());
        check(result.leftovers().get("minecraft:bucket") == 3, "returned containers reported");
        result = plan(index, Map.of("minecraft:cake", 1L, "minecraft:bucket", 1L), Map.of("minecraft:bucket", 1L));
        check(!result.leaves().containsKey("minecraft:iron_ingot"), "finished container goal reserves stock first");
        check(result.leftovers().get("minecraft:bucket") == 3, "returned buckets remain after reserved goal");

        result = plan(index, Map.of("minecraft:bucket", Long.MAX_VALUE), Map.of());
        check(!result.valid() && result.leaves().isEmpty(), "overflow invalidates whole result");
        Map<String, String> cycles = Map.of(P + "x.json", recipe("x", 1, "\"minecraft:y\""),
                P + "y.json", recipe("y", 1, "\"minecraft:x\""));
        result = plan(RecipeIndex.fromJson(cycles), Map.of("minecraft:x", 1L), Map.of());
        check(!result.valid() && result.leaves().isEmpty(), "cycles invalidate whole result");

        Map<String, String> legacy = Map.of("data/minecraft/recipes/legacy.json",
                "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"AA\",\" A\"],\"key\":{\"A\":[{\"item\":\"minecraft:stone\"},{\"item\":\"minecraft:cobblestone\"}]},\"result\":{\"item\":\"minecraft:legacy\",\"count\":2}}",
                "data/minecraft/recipes/cut.json", "{\"type\":\"minecraft:stonecutting\",\"ingredient\":{\"item\":\"minecraft:stone\"},\"result\":\"minecraft:cut\",\"count\":2}");
        RecipeIndex legacyIndex = RecipeIndex.fromJson(legacy);
        check(legacyIndex.diagnostics().isEmpty(), "legacy format loads");
        check(legacyIndex.recipesFor("minecraft:legacy").getFirst().ingredients().getFirst().count() == 3, "shaped multiplicity");
        leaf(plan(legacyIndex, Map.of("minecraft:cut", 3L), Map.of()), "stone", 2, 0);

        RecipeIndex loaded = RecipeIndex.loadVanilla();
        if (CraftingRegression.class.getClassLoader().getResource("data/minecraft/recipe/oak_planks.json") != null
                || CraftingRegression.class.getClassLoader().getResource("data/minecraft/recipes/oak_planks.json") != null) {
            check(loaded.available() && !loaded.recipesFor("minecraft:oak_planks").isEmpty(), "classpath vanilla index loading");
            checkCompatibleSurplus(loaded);
            checkNuggets(loaded);
        } else {
            check(!loaded.available(), "missing resource archive reported");
            check(!plan(loaded, Map.of("minecraft:oak_planks", 1L), Map.of()).valid(), "missing index invalidates plans");
        }

        for (String argument : args) checkVanilla(Path.of(argument));
        System.out.println("Crafting regression checks passed");
    }

    private static void checkVanilla(Path path) throws Exception {
        Map<String, String> resources = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(path.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.getName().startsWith("data/minecraft/") && entry.getName().endsWith(".json")) {
                    try (var stream = jar.getInputStream(entry)) {
                        resources.put(entry.getName(), new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        }
        RecipeIndex vanilla = RecipeIndex.fromJson(resources);
        check(vanilla.diagnostics().isEmpty(), path + " diagnostics: " + vanilla.diagnostics());
        leaf(plan(vanilla, Map.of("minecraft:oak_planks", 64L), Map.of("minecraft:oak_planks", 16L, "minecraft:oak_log", 5L)), "oak_log", 12, 5);
        var stairsAndCobblestone = plan(vanilla,
                Map.of("minecraft:stone_brick_stairs", 20L, "minecraft:cobblestone", 64L),
                Map.of("minecraft:stone_bricks", 3L, "minecraft:cobblestone", 8L));
        leaf(stairsAndCobblestone, "stone", 17, 0);
        leaf(stairsAndCobblestone, "cobblestone", 64, 8);
        check(stairsAndCobblestone.leaves().size() == 2, "stone brick inventory allocated exactly once");
        if (path.getParent().getFileName().toString().equals("1.21.11")) {
            System.out.println("Stone stair exact-stock plan: " + stairsAndCobblestone.leaves());
            System.out.println("Stone stair recipe choices: " + stairsAndCobblestone.options().stream()
                    .map(option -> option.itemId() + "=" + option.selectedRecipeId()).toList());
        }
        check(plan(vanilla, Map.of("minecraft:chest", 1L), Map.of("minecraft:spruce_planks", 8L)).leaves().isEmpty(), "vanilla chest uses compatible stock");
        checkCompatibleSurplus(vanilla);
        checkNuggets(vanilla);
        check(plan(vanilla, Map.of("minecraft:black_banner", 1L), Map.of("minecraft:blue_wool", 6L, "minecraft:red_wool", 7L)).valid(), "stock preferences avoid recoloring dependency cycles");
        check(!vanilla.recipesFor("minecraft:stone_stairs").isEmpty(), "vanilla stonecutter recipes");
        Set<String> outputs = new java.util.TreeSet<>();
        for (var entry : resources.entrySet()) {
            if (!entry.getKey().contains("/recipe/") && !entry.getKey().contains("/recipes/")) continue;
            var json = com.google.gson.JsonParser.parseString(entry.getValue()).getAsJsonObject();
            if (!json.has("result")) continue;
            var output = json.get("result");
            if (output.isJsonPrimitive()) outputs.add(output.getAsString());
            else if (output.isJsonObject()) {
                var object = output.getAsJsonObject();
                if (object.has("id")) outputs.add(object.get("id").getAsString());
                else if (object.has("item")) outputs.add(object.get("item").getAsString());
            }
        }
        for (String item : outputs) {
            var plan = plan(vanilla, Map.of(item, 65L), Map.of());
            check(plan.valid(), "valid default vanilla plan for " + item + ": " + plan.diagnostics());
        }
        for (String item : new String[]{"chest", "cake", "oak_stairs", "cut_copper_stairs", "waxed_cut_copper_stairs", "bamboo_mosaic", "iron_block", "stone_bricks"}) {
            var plan = plan(vanilla, Map.of("minecraft:" + item, 65L), Map.of());
            check(plan.valid(), "valid vanilla plan for " + item + ": " + plan.diagnostics());
        }
        System.out.println("Validated vanilla archive " + path.getParent().getFileName());
    }

    private static CraftingPlan.Result plan(RecipeIndex index, Map<String, Long> goals, Map<String, Long> stock) {
        return CraftingPlan.calculate(index, goals, Set.of(), stock, Map.of(), Map.of(), Set.of());
    }

    private static void checkCompatibleSurplus(RecipeIndex index) {
        Map<String, Long> goals = Map.of("minecraft:chest", 1L, "minecraft:birch_door", 3L);
        var result = plan(index, goals, Map.of("minecraft:oak_planks", 7L, "minecraft:birch_log", 2L));
        leaf(result, "birch_log", 2, 2);
        check(result.leaves().size() == 1 && result.leftovers().equals(Map.of("minecraft:birch_planks", 1L)),
                "automatic chest consumes the fixed door's compatible surplus: " + result);
        result = plan(index, goals, Map.of("minecraft:oak_planks", 7L));
        leaf(result, "birch_log", 2, 0);
        check(result.leaves().size() == 1 && result.leftovers().equals(Map.of("minecraft:birch_planks", 1L)),
                "missing producer materials still expose its exact reusable surplus");

        result = plan(index, Map.of("minecraft:chest", 1L, "minecraft:birch_door", 3L, "minecraft:crafting_table", 1L),
                Map.of("minecraft:oak_planks", 7L, "minecraft:birch_log", 2L));
        leaf(result, "birch_log", 2, 2);
        leaf(result, "oak_log", 1, 0);
        check(result.leaves().size() == 2 && result.leftovers().getOrDefault("minecraft:oak_planks", 0L) == 1
                        && result.leftovers().getOrDefault("minecraft:birch_planks", 0L) == 0,
                "competing flexible consumers spend each surplus plank only once: " + result);

        result = CraftingPlan.calculate(index, goals, Set.of(),
                Map.of("minecraft:oak_planks", 7L, "minecraft:birch_log", 2L), Map.of(),
                Map.of(index.recipesFor("minecraft:chest").getFirst().ingredients().getFirst().key(), "minecraft:oak_planks"), Set.of());
        leaf(result, "birch_log", 2, 2);
        leaf(result, "oak_log", 1, 0);
        check(result.leftovers().equals(Map.of("minecraft:birch_planks", 2L, "minecraft:oak_planks", 3L)),
                "explicit oak choice cannot consume compatible birch surplus");

        result = plan(index, Map.of("minecraft:chest", 1L, "minecraft:birch_planks", 8L),
                Map.of("minecraft:oak_planks", 7L, "minecraft:birch_log", 2L));
        leaf(result, "birch_log", 2, 2);
        leaf(result, "oak_log", 1, 0);
        check(!result.leftovers().containsKey("minecraft:birch_planks"), "crafted source goal is reserved before flexible consumers");
        result = plan(index, goals, Map.of("minecraft:oak_planks", 7L, "minecraft:birch_planks", 6L));
        leaf(result, "oak_log", 1, 0);
        check(result.leaves().size() == 1, "fixed door stock is not also available to a flexible chest");
    }

    private static void checkNuggets(RecipeIndex index) {
        for (String metal : new String[]{"iron", "gold"}) {
            check(index.recipesFor("minecraft:" + metal + "_ingot").stream()
                    .noneMatch(recipe -> recipe.ingredients().stream().anyMatch(ingredient -> ingredient.options().contains("minecraft:" + metal + "_block"))),
                    metal + " storage blocks do not create a reverse unpacking route");
            leaf(plan(index, Map.of("minecraft:" + metal + "_nugget", 9L), Map.of()), metal + "_ingot", 1, 0);
        }
        var result = plan(index, Map.of("minecraft:lantern", 1L), Map.of("minecraft:iron_ingot", 1L, "minecraft:torch", 1L));
        leaf(result, "iron_ingot", 1, 1);
        check(result.leaves().size() == 1 && result.leaves().values().stream().allMatch(leaf -> leaf.remaining() == 0),
                "one ingot and one torch supply a lantern");
        result = plan(index, Map.of("minecraft:lantern", 1L), Map.of("minecraft:torch", 1L));
        leaf(result, "iron_ingot", 1, 0);
        check(result.leaves().size() == 1, "missing lantern metal is one iron ingot");
    }

    private static String recipe(String result, long count, String ingredients) {
        return "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[" + ingredients
                + "],\"result\":{\"id\":\"minecraft:" + result + "\",\"count\":" + count + "}}";
    }

    private static void leaf(CraftingPlan.Result result, String item, long required, long available) {
        check(result.valid(), "valid plan: " + result.diagnostics());
        CraftingPlan.Leaf leaf = result.leaves().get("minecraft:" + item);
        check(leaf != null && leaf.required() == required && leaf.available() == available,
                "expected " + item + " required=" + required + " available=" + available + ", got " + result.leaves());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
