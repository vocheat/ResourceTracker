package net.fabricmc.resourcetracker.crafting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;

/** Quantitative checks for the optional cooking expansion of collection boundaries. */
public final class SmeltingRegression {
    private static final String P = "data/minecraft/recipe/";

    public static void main(String[] args) throws Exception {
        RecipeIndex synthetic = syntheticIndex();
        require(synthetic.diagnostics().isEmpty(), "synthetic recipe index: " + synthetic.diagnostics());
        checkMaterials(synthetic);
        checkAllocation(synthetic);
        checkChoices(synthetic);
        checkFlexibleStock(synthetic);
        for (String kind : new String[]{"smelting", "blasting", "smoking", "campfire_cooking"}) {
            RecipeIndex cooking = RecipeIndex.fromJson(Map.of(P + "test.json", cooking(kind, "stone", "cobblestone")));
            leaf(plan(cooking, Map.of("minecraft:stone", 2L), Map.of()), "cobblestone", 2, 0);
        }
        RecipeIndex vanilla = RecipeIndex.loadVanilla();
        if (SmeltingRegression.class.getClassLoader().getResource(P + "oak_planks.json") != null
                || SmeltingRegression.class.getClassLoader().getResource("data/minecraft/recipes/oak_planks.json") != null) {
            require(vanilla.available(), "vanilla recipe resources load");
            checkVanilla(vanilla);
        }
        for (String argument : args) {
            Map<String, String> resources = new LinkedHashMap<>();
            try (JarFile jar = new JarFile(argument)) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (!entry.getName().startsWith("data/minecraft/") || !entry.getName().endsWith(".json")) continue;
                    try (var stream = jar.getInputStream(entry)) {
                        resources.put(entry.getName(), new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
            RecipeIndex index = RecipeIndex.fromJson(resources);
            require(!index.recipesFor("minecraft:iron_ingot").isEmpty(), "recipe archive contains vanilla metal recipes: " + argument);
            checkVanilla(index);
        }
        System.out.println("Smelting regression checks passed");
    }

    private static void checkVanilla(RecipeIndex index) {
        checkMaterials(index);
        checkAllocation(index);
        checkChoices(index);
        var beef = plan(index, Map.of("minecraft:cooked_beef", 4L), Map.of("minecraft:cooked_beef", 1L, "minecraft:beef", 2L));
        leaf(beef, "beef", 3, 2);
        require(beef.leaves().size() == 1, "cooking food has no fuel requirement");
    }

    private static void checkMaterials(RecipeIndex index) {
        for (String metal : new String[]{"iron", "gold", "copper"}) {
            String ingot = "minecraft:" + metal + "_ingot";
            var old = CraftingPlan.calculate(index, Map.of(ingot, 64L), Set.of(), Map.of(), Map.of(), Map.of(), Set.of());
            leaf(old, metal + "_ingot", 64, 0);
            var disabled = CraftingPlan.calculate(index, Map.of(ingot, 64L), Set.of(), Map.of(), Map.of(), Map.of(), Set.of(), false);
            require(disabled.equals(old), "disabled cooking preserves the original API: " + metal);
            var enabled = plan(index, Map.of(ingot, 64L), Map.of());
            leaf(enabled, "raw_" + metal, 64, 0);
            require(enabled.leaves().size() == 1, "one raw item per ingot without fuel: " + enabled);
            require(enabled.options().stream().filter(option -> option.itemId().equals(ingot))
                    .allMatch(option -> !option.defaultStopped() && !option.stopped()), "enabled metal boundary expands");

            var ore = plan(index, Map.of(ingot, 4L), Map.of("minecraft:" + metal + "_ore", 4L));
            leaf(ore, metal + "_ore", 4, 4);
            require(ore.leaves().size() == 1, "an on-hand ore selects its cooking route: " + ore);
            var raw = plan(index, Map.of("minecraft:raw_" + metal, 4L), Map.of());
            leaf(raw, "raw_" + metal, 4, 0);
        }
        var stone = plan(index, Map.of("minecraft:stone", 12L), Map.of("minecraft:stone", 2L, "minecraft:cobblestone", 4L));
        leaf(stone, "cobblestone", 10, 4);
        require(stone.leaves().size() == 1, "stone uses its existing finished stock before cobblestone");
        var stairs = plan(index, Map.of("minecraft:stone_stairs", 5L), Map.of());
        leaf(stairs, "cobblestone", 5, 0);
        require(stairs.leaves().size() == 1, "nested stonecutting reaches unsmelted cobblestone");
        var netherite = plan(index, Map.of("minecraft:netherite_ingot", 2L), Map.of());
        leaf(netherite, "raw_gold", 8, 0);
        leaf(netherite, "ancient_debris", 8, 0);
        require(netherite.leaves().size() == 2, "netherite expands gold and scrap cooking without furnace or fuel");
    }

    private static void checkAllocation(RecipeIndex index) {
        var partial = plan(index, Map.of("minecraft:iron_block", 2L),
                Map.of("minecraft:iron_block", 1L, "minecraft:iron_ingot", 3L, "minecraft:raw_iron", 4L));
        leaf(partial, "raw_iron", 6, 4);
        require(partial.leaves().size() == 1, "finished block and partial metal are deducted before raw stock");
        var shared = plan(index, Map.of("minecraft:iron_block", 1L, "minecraft:bucket", 1L),
                Map.of("minecraft:iron_ingot", 2L, "minecraft:raw_iron", 7L));
        leaf(shared, "raw_iron", 10, 7);
        require(shared.leaves().size() == 1, "shared ingot and raw stock are allocated exactly once");
        var reserved = plan(index, Map.of("minecraft:iron_ingot", 3L, "minecraft:bucket", 1L),
                Map.of("minecraft:iron_ingot", 3L, "minecraft:raw_iron", 2L));
        leaf(reserved, "raw_iron", 3, 2);
        require(reserved.leaves().size() == 1, "finished ingot goal reserves its stock before bucket crafting");
        var rawGoal = plan(index, Map.of("minecraft:iron_ingot", 3L, "minecraft:raw_iron", 3L),
                Map.of("minecraft:raw_iron", 3L));
        leaf(rawGoal, "raw_iron", 6, 3);
        var complete = plan(index, Map.of("minecraft:iron_block", 1L), Map.of("minecraft:iron_block", 1L));
        require(complete.valid() && complete.leaves().isEmpty(), "completed goal requires no cooking inputs");
        var manual = CraftingPlan.calculate(index, Map.of("minecraft:iron_block", 1L), Set.of("minecraft:iron_block"),
                Map.of(), Map.of(), Map.of(), Set.of(), true);
        require(manual.valid() && manual.leaves().isEmpty(), "manual goal completion is respected");
    }

    private static void checkChoices(RecipeIndex index) {
        var stoneStopped = CraftingPlan.calculate(index, Map.of("minecraft:stone", 12L), Set.of(),
                Map.of("minecraft:stone", 2L, "minecraft:cobblestone", 10L), Map.of(), Map.of(),
                Set.of("minecraft:stone"), true);
        leaf(stoneStopped, "stone", 12, 2);
        require(stoneStopped.leaves().size() == 1, "Silk Touch stone remains a direct collection target");
        String stoneRecipe = index.recipesFor("minecraft:stone").stream()
                .filter(recipe -> recipe.kind().equals("smelting")).findFirst().orElseThrow().id();
        var stoneChosen = CraftingPlan.calculate(index, Map.of("minecraft:stone", 12L), Set.of(),
                Map.of("minecraft:stone", 2L, "minecraft:cobblestone", 4L),
                Map.of("minecraft:stone", stoneRecipe), Map.of(), Set.of(), false);
        leaf(stoneChosen, "cobblestone", 10, 4);
        require(stoneChosen.leaves().size() == 1, "stone smelting can be chosen independently of the global toggle");
        String ingot = "minecraft:iron_ingot";
        var stopped = CraftingPlan.calculate(index, Map.of("minecraft:bucket", 1L), Set.of(),
                Map.of(ingot, 1L, "minecraft:raw_iron", 10L), Map.of(), Map.of(), Set.of(ingot), true);
        leaf(stopped, "iron_ingot", 3, 1);
        require(stopped.leaves().size() == 1, "manual stop wins over automatic smelting");
        String oreRecipe = index.recipesFor(ingot).stream().filter(recipe -> recipe.kind().equals("smelting")
                && recipe.ingredients().stream().anyMatch(input -> input.options().contains("minecraft:iron_ore")))
                .findFirst().orElseThrow().id();
        var explicit = CraftingPlan.calculate(index, Map.of(ingot, 3L), Set.of(), Map.of("minecraft:raw_iron", 3L),
                Map.of(ingot, oreRecipe), Map.of(), Set.of(), true);
        leaf(explicit, "iron_ore", 3, 0);
        require(explicit.leaves().size() == 1, "explicit ore recipe wins over raw stock preference");
        var stoppedExplicit = CraftingPlan.calculate(index, Map.of(ingot, 3L), Set.of(), Map.of(),
                Map.of(ingot, oreRecipe), Map.of(), Set.of(ingot), true);
        leaf(stoppedExplicit, "iron_ingot", 3, 0);
        String nuggetRecipe = index.recipesFor(ingot).stream().filter(recipe -> recipe.kind().startsWith("crafting_")
                && recipe.ingredients().stream().anyMatch(input -> input.options().contains("minecraft:iron_nugget")))
                .findFirst().orElseThrow().id();
        var explicitCraft = CraftingPlan.calculate(index, Map.of(ingot, 2L), Set.of(), Map.of(),
                Map.of(ingot, nuggetRecipe), Map.of(), Set.of("minecraft:iron_nugget"), true);
        leaf(explicitCraft, "iron_nugget", 18, 0);
        require(explicitCraft.leaves().size() == 1, "explicit non-cooking recipe remains selectable");
    }

    private static void checkFlexibleStock(RecipeIndex index) {
        var mixed = plan(index, Map.of("minecraft:metal_pair", 1L),
                Map.of("minecraft:raw_copper", 1L, "minecraft:iron_ore", 1L));
        leaf(mixed, "raw_copper", 1, 1);
        leaf(mixed, "iron_ore", 1, 1);
        require(mixed.leaves().size() == 2, "flexible ingredient stock conversion cooks each raw input once");
        var stopped = CraftingPlan.calculate(index, Map.of("minecraft:metal_pair", 1L), Set.of(),
                Map.of("minecraft:raw_copper", 1L, "minecraft:iron_ore", 1L), Map.of(), Map.of(),
                Set.of("minecraft:iron_ingot"), true);
        leaf(stopped, "raw_copper", 2, 1);
        require(stopped.leaves().size() == 1, "manual stop also applies during compatible stock conversion");
        // Even with a reversible nugget route ranked before cooking, unrelated stock must not
        // make preview follow ingot -> nugget -> ingot or add a phantom source of metal.
        var unrelated = plan(index, Map.of("minecraft:bucket", 1L), Map.of("minecraft:dirt", 64L));
        leaf(unrelated, "raw_iron", 3, 0);
    }

    private static RecipeIndex syntheticIndex() {
        Map<String, String> resources = new LinkedHashMap<>();
        for (String metal : new String[]{"iron", "gold", "copper"}) {
            resources.put(P + metal + "_ingot.json", craft(metal + "_ingot", 1, metal + "_nugget", 9));
            resources.put(P + metal + "_nugget.json", craft(metal + "_nugget", 9, metal + "_ingot", 1));
            resources.put(P + metal + "_ingot_from_raw.json", cooking("smelting", metal + "_ingot", "raw_" + metal));
            resources.put(P + metal + "_ingot_from_ore.json", cooking("smelting", metal + "_ingot", metal + "_ore"));
        }
        resources.put(P + "stone.json", cooking("smelting", "stone", "cobblestone"));
        resources.put(P + "stone_stairs.json", "{\"type\":\"minecraft:stonecutting\",\"ingredient\":\"minecraft:stone\","
                + "\"result\":{\"id\":\"minecraft:stone_stairs\"}}");
        resources.put(P + "iron_block.json", craft("iron_block", 1, "iron_ingot", 9));
        resources.put(P + "bucket.json", craft("bucket", 1, "iron_ingot", 3));
        resources.put(P + "netherite_scrap.json", cooking("smelting", "netherite_scrap", "ancient_debris"));
        resources.put(P + "netherite_ingot.json", "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"SSS\",\"SGG\",\"GG \"],"
                + "\"key\":{\"S\":\"minecraft:netherite_scrap\",\"G\":\"minecraft:gold_ingot\"},"
                + "\"result\":{\"id\":\"minecraft:netherite_ingot\"}}");
        resources.put("data/minecraft/tags/item/test_metals.json", "{\"values\":[\"minecraft:copper_ingot\",\"minecraft:iron_ingot\"]}");
        resources.put(P + "metal_pair.json", "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"MM\"],"
                + "\"key\":{\"M\":\"#minecraft:test_metals\"},\"result\":{\"id\":\"minecraft:metal_pair\"}}");
        // A non-cooking recipe alone must not remove the raw-metal collection boundary.
        resources.put(P + "raw_iron.json", craft("raw_iron", 1, "test_ore", 1));
        return RecipeIndex.fromJson(resources);
    }

    private static String craft(String output, int count, String input, int inputCount) {
        String ingredient = "\"minecraft:" + input + "\"";
        return "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":["
                + String.join(",", java.util.Collections.nCopies(inputCount, ingredient))
                + "],\"result\":{\"id\":\"minecraft:" + output + "\",\"count\":" + count + "}}";
    }

    private static String cooking(String kind, String output, String input) {
        return "{\"type\":\"minecraft:" + kind + "\",\"ingredient\":\"minecraft:" + input
                + "\",\"result\":{\"id\":\"minecraft:" + output + "\"}}";
    }

    private static CraftingPlan.Result plan(RecipeIndex index, Map<String, Long> goals, Map<String, Long> stock) {
        return CraftingPlan.calculate(index, goals, Set.of(), stock, Map.of(), Map.of(), Set.of(), true);
    }

    private static void leaf(CraftingPlan.Result result, String item, long required, long available) {
        var leaf = result.leaves().get("minecraft:" + item);
        require(result.valid() && leaf != null && leaf.required() == required && leaf.available() == available,
                "expected " + item + "=" + required + "/" + available + ", got " + result);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
