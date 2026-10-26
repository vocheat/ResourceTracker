package net.fabricmc.resourcetracker.crafting;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

/** Standalone regression checks; run with JavaExec, no game or test framework required. */
public final class CraftingRegression {
    private static final String P = "data/minecraft/recipe/";

    public static void main(String[] args) throws Exception {
        PlannerRoutingRegression.main(new String[0]);
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
        resources.put(P + "bookshelf.json", "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"PPP\",\"BBB\",\"PPP\"],\"key\":{\"P\":\"#minecraft:planks\",\"B\":\"minecraft:book\"},\"result\":{\"id\":\"minecraft:bookshelf\"}}");
        resources.put(P + "book.json", recipe("book", 1, "\"minecraft:paper\",\"minecraft:paper\",\"minecraft:paper\",\"minecraft:leather\""));
        resources.put(P + "paper.json", recipe("paper", 3, "\"minecraft:sugar_cane\",\"minecraft:sugar_cane\",\"minecraft:sugar_cane\""));
        resources.put(P + "leather.json", recipe("leather", 1, "\"minecraft:rabbit_hide\",\"minecraft:rabbit_hide\",\"minecraft:rabbit_hide\",\"minecraft:rabbit_hide\""));
        resources.put(P + "white_wool_from_string.json", recipe("white_wool", 1, "\"minecraft:string\",\"minecraft:string\",\"minecraft:string\",\"minecraft:string\""));
        resources.put(P + "black_wool.json", recipe("black_wool", 1, "\"minecraft:white_wool\",\"minecraft:black_dye\""));
        resources.put(P + "black_dye.json", recipe("black_dye", 1, "\"minecraft:ink_sac\""));
        resources.put(P + "netherite_ingot.json", "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"SSS\",\"SGG\",\"GG \"],\"key\":{\"S\":\"minecraft:netherite_scrap\",\"G\":\"minecraft:gold_ingot\"},\"result\":{\"id\":\"minecraft:netherite_ingot\"}}");
        resources.put(P + "mossy_cobblestone_from_moss_block.json", recipe("mossy_cobblestone", 1, "\"minecraft:cobblestone\",\"minecraft:moss_block\""));
        resources.put(P + "mossy_cobblestone_from_vine.json", recipe("mossy_cobblestone", 1, "\"minecraft:cobblestone\",\"minecraft:vine\""));
        RecipeIndex index = RecipeIndex.fromJson(resources);
        check(index.diagnostics().isEmpty(), "valid synthetic recipes: " + index.diagnostics());
        checkStockDependencies(index);
        checkBookshelves(index);
        checkMaterialDefaults(index);
        checkRecipeStock(index);

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
        check(index.recipesFor("minecraft:iron_ingot").size() == 1
                && index.recipesFor("minecraft:iron_ingot").getFirst().kind().equals("smelting"),
                "storage unpacking is excluded but smelting is supported");
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
            checkBookshelves(loaded);
            checkMaterialDefaults(loaded);
            checkRecipeStock(loaded);
            URL probe = CraftingRegression.class.getClassLoader().getResource("data/minecraft/recipe/oak_planks.json");
            if (probe == null) probe = CraftingRegression.class.getClassLoader().getResource("data/minecraft/recipes/oak_planks.json");
            if (probe.openConnection() instanceof JarURLConnection connection) {
                checkVanilla(Path.of(connection.getJarFileURL().toURI()));
            }
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
        VanillaRecipeCatalogRegression.check(resources, vanilla);
        PlannerRoutingRegression.check(vanilla);
        checkVanillaQuantities(vanilla);
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
        checkBookshelves(vanilla);
        checkMaterialDefaults(vanilla);
        checkRecipeStock(vanilla);
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
            if (vanilla.recipesFor(item).size() > 1) {
                for (RecipeIndex.Recipe recipe : vanilla.recipesFor(item)) {
                    Map<String, Long> stock = new LinkedHashMap<>();
                    for (RecipeIndex.Ingredient ingredient : recipe.ingredients()) {
                        stock.merge(ingredient.options().getFirst(), ingredient.count(), Math::addExact);
                    }
                    var supplied = plan(vanilla, Map.of(item, 65L), stock);
                    check(supplied.valid(), "valid stock-preferred vanilla plan for " + recipe.id() + ": " + supplied.diagnostics());
                }
            }
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

    private static void checkStockDependencies(RecipeIndex index) {
        Set<String> dependencies = CraftingRuntime.stockDependencies(index,
                List.of("minecraft:chest", "minecraft:cake", "minecraft:bucket", "minecraft:stone_stairs"));
        check(dependencies.containsAll(Set.of("minecraft:chest", "minecraft:oak_planks", "minecraft:oak_log",
                        "minecraft:oak_wood", "minecraft:birch_planks", "minecraft:birch_log", "minecraft:spruce_planks",
                        "minecraft:spruce_log", "minecraft:cake", "minecraft:milk_bucket", "minecraft:bucket",
                        "minecraft:iron_ingot", "minecraft:stone_stairs", "minecraft:stone")),
                "cache dependencies include alternate ingredients, ancestors, shared stock and container goals");
        check(java.util.Collections.disjoint(dependencies,
                        CraftingRuntime.changedItems(Map.of(), Map.of("minecraft:diamond", 1L))),
                "unrelated stock change preserves a cached plan");
        check(!java.util.Collections.disjoint(dependencies,
                        CraftingRuntime.changedItems(Map.of(), Map.of("minecraft:spruce_planks", 8L))),
                "new automatic ingredient stock invalidates the plan");
        check(!java.util.Collections.disjoint(dependencies,
                        CraftingRuntime.changedItems(Map.of("minecraft:bucket", 1L), Map.of())),
                "removed finished goal stock invalidates the plan");
        check(CraftingRuntime.changedItems(Map.of("minecraft:stone", 0L), Map.of()).isEmpty(),
                "absent and zero stock are equivalent");

        Map<String, String> alternateRecipes = Map.of(P + "first.json", recipe("choice", 1, "\"minecraft:stone\""),
                P + "second.json", recipe("choice", 1, "\"minecraft:stick\""),
                P + "stick.json", recipe("stick", 1, "\"minecraft:choice\""));
        Set<String> alternateDependencies = CraftingRuntime.stockDependencies(RecipeIndex.fromJson(alternateRecipes),
                List.of("minecraft:choice"));
        check(alternateDependencies.equals(Set.of("minecraft:choice", "minecraft:stone", "minecraft:stick")),
                "all recipe alternatives are included and dependency cycles terminate");
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

    private static void checkBookshelves(RecipeIndex index) {
        Map<String, Long> goals = Map.of("minecraft:bookshelf", 15L);
        var result = plan(index, goals, Map.of());
        leaf(result, "oak_log", 23, 0);
        leaf(result, "leather", 45, 0);
        leaf(result, "sugar_cane", 135, 0);
        check(result.leaves().size() == 3, "bookshelves stop at naturally obtainable leather");

        result = plan(index, goals, Map.of("minecraft:book", 3L, "minecraft:leather", 12L));
        leaf(result, "oak_log", 23, 0);
        leaf(result, "leather", 42, 12);
        leaf(result, "sugar_cane", 126, 0);
        check(result.leaves().size() == 3, "bookshelf intermediates and leather stock are counted");

        String leatherRecipe = index.recipesFor("minecraft:leather").getFirst().id();
        result = CraftingPlan.calculate(index, goals, Set.of(), Map.of(),
                Map.of("minecraft:leather", leatherRecipe), Map.of(), Set.of());
        leaf(result, "oak_log", 23, 0);
        leaf(result, "rabbit_hide", 180, 0);
        leaf(result, "sugar_cane", 135, 0);
        check(result.leaves().size() == 3, "explicit rabbit hide recipe remains available");
    }

    private static void checkMaterialDefaults(RecipeIndex index) {
        var result = plan(index, Map.of("minecraft:white_wool", 6L, "minecraft:black_wool", 2L,
                "minecraft:black_dye", 3L), Map.of("minecraft:white_wool", 1L));
        leaf(result, "white_wool", 6, 1);
        leaf(result, "black_wool", 2, 0);
        leaf(result, "black_dye", 3, 0);
        check(result.leaves().size() == 3, "wool and dyes are gathered materials by default");
        check(result.options().stream().allMatch(CraftingPlan.Option::stopped), "material defaults are visibly stopped");
        check(result.options().stream().allMatch(CraftingPlan.Option::defaultStopped), "natural materials resume through an explicit recipe");

        String woolRecipe = index.recipesFor("minecraft:white_wool").getFirst().id();
        result = CraftingPlan.calculate(index, Map.of("minecraft:white_wool", 6L), Set.of(), Map.of(),
                Map.of("minecraft:white_wool", woolRecipe), Map.of(), Set.of());
        leaf(result, "string", 24, 0);
        result = CraftingPlan.calculate(index, Map.of("minecraft:white_wool", 6L), Set.of(), Map.of(),
                Map.of("minecraft:white_wool", woolRecipe), Map.of(), Set.of("minecraft:white_wool"));
        leaf(result, "white_wool", 6, 0);

        result = plan(index, Map.of("minecraft:netherite_ingot", 2L), Map.of("minecraft:gold_ingot", 3L));
        leaf(result, index.recipesFor("minecraft:netherite_scrap").isEmpty() ? "netherite_scrap" : "ancient_debris", 8, 0);
        leaf(result, "gold_ingot", 8, 3);
        check(result.leaves().size() == 2, "netherite is a crafted alloy, ordinary ingots are base materials");
    }

    private static void checkRecipeStock(RecipeIndex index) {
        Map<String, Long> goals = Map.of("minecraft:mossy_cobblestone", 4L);
        Map<String, Long> stock = Map.of("minecraft:cobblestone", 4L, "minecraft:vine", 4L);
        var result = plan(index, goals, stock);
        leaf(result, "cobblestone", 4, 4);
        leaf(result, "vine", 4, 4);
        check(result.leaves().size() == 2, "automatic recipe uses supplied vines rather than requesting moss");
        var stopped = CraftingPlan.calculate(index, goals, Set.of(), stock, Map.of(), Map.of(),
                Set.of("minecraft:mossy_cobblestone"));
        check(stopped.options().stream().anyMatch(option -> option.itemId().equals("minecraft:mossy_cobblestone")
                        && option.stopped() && !option.defaultStopped()),
                "manual stop remains distinct from the natural material boundary when resuming");

        result = plan(index, goals, Map.of("minecraft:vine", 1L));
        leaf(result, "cobblestone", 4, 0);
        leaf(result, "vine", 4, 1);
        check(result.leaves().size() == 2, "partial stock also influences recipe choice");

        result = CraftingPlan.calculate(index, goals, Set.of(), stock,
                Map.of("minecraft:mossy_cobblestone", "minecraft:mossy_cobblestone_from_moss_block"), Map.of(), Set.of());
        leaf(result, "moss_block", 4, 0);
        check(!result.leaves().containsKey("minecraft:vine"), "explicit recipe wins over inventory preference");

        result = plan(index, Map.of("minecraft:mossy_cobblestone", 4L, "minecraft:vine", 4L), stock);
        leaf(result, "vine", 4, 4);
        leaf(result, "moss_block", 4, 0);
        check(result.leaves().size() == 3, "finished goals are reserved before stock-based recipe selection");
    }

    private static void checkVanillaQuantities(RecipeIndex index) {
        leaf(plan(index, Map.of("minecraft:stick", 64L), Map.of()), "oak_log", 8, 0);
        for (String item : new String[]{"cut_copper", "cut_copper_stairs", "copper_grate", "chiseled_copper"}) {
            leaf(plan(index, Map.of("minecraft:" + item, 64L), Map.of()), "copper_ingot", 144, 0);
        }
        leaf(plan(index, Map.of("minecraft:cut_copper_slab", 64L), Map.of()), "copper_ingot", 72, 0);
        var waxed = plan(index, Map.of("minecraft:waxed_cut_copper", 64L), Map.of());
        leaf(waxed, "copper_ingot", 144, 0);
        leaf(waxed, "honeycomb", 16, 0);
        leaf(plan(index, Map.of("minecraft:glass_pane", 16L), Map.of()), "sand", 6, 0);
        leaf(plan(index, Map.of("minecraft:brick", 4L), Map.of()), "clay_ball", 4, 0);
        leaf(plan(index, Map.of("minecraft:cooked_beef", 3L), Map.of()), "beef", 3, 0);
        var upgrade = plan(index, Map.of("minecraft:netherite_pickaxe", 1L), Map.of());
        leaf(upgrade, "diamond", 3, 0);
        leaf(upgrade, "ancient_debris", 4, 0);
        leaf(upgrade, "gold_ingot", 4, 0);
        leaf(upgrade, "netherite_upgrade_smithing_template", 1, 0);
        leaf(upgrade, "oak_log", 1, 0);
        if (!index.recipesFor("minecraft:blue_bundle").isEmpty()) {
            var bundle = plan(index, Map.of("minecraft:blue_bundle", 1L), Map.of());
            leaf(bundle, "leather", 1, 0);
            leaf(bundle, "string", 1, 0);
            leaf(bundle, "blue_dye", 1, 0);
            check(bundle.leaves().size() == 3, "colored bundle uses the ordinary undyed bundle");
        }
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
