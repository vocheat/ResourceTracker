package net.fabricmc.resourcetracker.crafting;

import net.fabricmc.resourcetracker.model.WoodType;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

/** Pure planner checks, also accepting vanilla client/merged jar paths as arguments. */
public final class WoodPreferenceRegression {
    private static final String P = "data/minecraft/recipe/";

    public static void main(String[] args) throws Exception {
        checkFamilies();
        checkIndex(synthetic());
        RecipeIndex vanilla = RecipeIndex.loadVanilla();
        if (vanilla.available()) checkIndex(vanilla);
        for (String path : args) {
            Map<String, String> resources = new LinkedHashMap<>();
            try (JarFile jar = new JarFile(path)) {
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
            require(!index.recipesFor("minecraft:chest").isEmpty(), "vanilla archive: " + path);
            checkIndex(index);
        }
        System.out.println("Wood preference regression checks passed");
    }

    private static void checkFamilies() {
        require(WoodType.AUTO.planksId() == null && !WoodType.AUTO.matches("minecraft:oak_log"), "automatic family");
        for (WoodType type : WoodType.values()) {
            if (type == WoodType.AUTO) continue;
            require(type.matches(type.planksId()), "planks in family: " + type);
            require(!type.matches(type.planksId().replace("minecraft:", "example:")), "exact namespace: " + type);
            require(!type.matches(type.planksId() + "_unrelated"), "exact suffix: " + type);
        }
        require(WoodType.DARK_OAK.matches("minecraft:stripped_dark_oak_wood"), "dark oak stripped wood");
        require(WoodType.CRIMSON.matches("minecraft:stripped_crimson_hyphae"), "nether hyphae");
        require(WoodType.POPLAR.matches("minecraft:stripped_poplar_wood")
                && WoodType.POPLAR.matches("minecraft:poplar_shelf"), "poplar wood and shelf");
        require(WoodType.BAMBOO.matches("minecraft:bamboo") && WoodType.BAMBOO.matches("minecraft:stripped_bamboo_block"), "bamboo raw inputs");
        require(!WoodType.OAK.matches("minecraft:dark_oak_log") && !WoodType.OAK.matches("minecraft:oak_leaves"), "unrelated wood-like names");
    }

    private static void checkIndex(RecipeIndex index) {
        for (WoodType type : new WoodType[]{WoodType.SPRUCE, WoodType.BIRCH}) {
            String log = type == WoodType.SPRUCE ? "spruce_log" : "birch_log";
            leaf(plan(index, Map.of("minecraft:chest", 1L), Map.of(), type), log, 2, 0);
            leaf(plan(index, Map.of("minecraft:stick", 8L), Map.of(), type), log, 1, 0);
            var otherStock = plan(index, Map.of("minecraft:chest", 1L),
                    Map.of("minecraft:oak_log", 64L, "minecraft:oak_planks", 64L, "minecraft:bamboo", 64L), type);
            leaf(otherStock, log, 2, 0);
            require(otherStock.leaves().size() == 1, "incompatible stock cannot replace selected wood: " + otherStock);
            var sticks = plan(index, Map.of("minecraft:stick", 8L), Map.of("minecraft:bamboo", 64L), type);
            leaf(sticks, log, 1, 0);
            require(sticks.leaves().size() == 1, "wood preference selects the planks recipe over stocked bamboo");
        }
        var auto = plan(index, Map.of("minecraft:chest", 1L), Map.of("minecraft:oak_log", 1L, "minecraft:birch_log", 1L), WoodType.AUTO);
        leaf(auto, "oak_log", 1, 1);
        leaf(auto, "birch_log", 1, 1);
        require(auto.leaves().size() == 2, "AUTO mixes raw wood families");
        var legacy = CraftingPlan.calculate(index, Map.of("minecraft:chest", 1L), Set.of(), Map.of(), Map.of(), Map.of(), Set.of());
        require(legacy.equals(plan(index, Map.of("minecraft:chest", 1L), Map.of(), WoodType.AUTO)), "old API preserves AUTO");
        require(legacy.equals(plan(index, Map.of("minecraft:chest", 1L), Map.of(), null)), "null is AUTO");

        var fixed = plan(index, Map.of("minecraft:oak_stairs", 4L), Map.of("minecraft:birch_log", 64L), WoodType.BIRCH);
        leaf(fixed, "oak_log", 2, 0);
        require(fixed.leaves().size() == 1, "fixed oak recipe retains oak inputs");
        var chest = index.recipesFor("minecraft:chest").getFirst();
        var planks = chest.ingredients().stream().flatMap(ingredient -> ingredient.options().stream())
                .filter(id -> id.endsWith("_planks")).toList();
        require(planks.stream().allMatch(id -> java.util.Arrays.stream(WoodType.values()).anyMatch(type -> type.matches(id))),
                "every available vanilla plank family has a wood choice: " + planks);
        for (WoodType type : WoodType.values()) {
            if (type == WoodType.AUTO || !planks.contains(type.planksId())) continue;
            var chosen = plan(index, Map.of("minecraft:chest", 1L), Map.of(), type);
            require(chosen.valid() && chosen.leaves().size() == 1, "one raw wood source for " + type);
            var raw = chosen.leaves().values().iterator().next();
            require(type.matches(raw.itemId()) && raw.required() == (type == WoodType.BAMBOO ? 36 : 2)
                    && raw.available() == 0, "actual recipe yield for available wood " + type + ": " + chosen);
        }
        String slot = chest.ingredients().stream().filter(input -> input.options().contains("minecraft:oak_planks")).findFirst().orElseThrow().key();
        var explicit = CraftingPlan.calculate(index, Map.of("minecraft:chest", 1L), Set.of(),
                Map.of("minecraft:spruce_log", 2L), Map.of(), Map.of(slot, "minecraft:oak_planks"), Set.of(), false, WoodType.SPRUCE);
        leaf(explicit, "oak_log", 2, 0);
        require(explicit.leaves().size() == 1, "explicit ingredient wins over family");

        var finished = plan(index, Map.of("minecraft:chest", 2L), Map.of("minecraft:chest", 1L, "minecraft:spruce_log", 1L), WoodType.SPRUCE);
        leaf(finished, "spruce_log", 2, 1);
        var shared = plan(index, Map.of("minecraft:chest", 1L, "minecraft:stick", 4L),
                Map.of("minecraft:spruce_planks", 8L, "minecraft:spruce_log", 1L), WoodType.SPRUCE);
        leaf(shared, "spruce_log", 1, 1);
        var reserved = plan(index, Map.of("minecraft:spruce_planks", 8L, "minecraft:chest", 1L),
                Map.of("minecraft:spruce_planks", 8L, "minecraft:spruce_log", 1L), WoodType.SPRUCE);
        leaf(reserved, "spruce_log", 2, 1);
        require(plan(index, Map.of("minecraft:chest", 1L), Map.of("minecraft:chest", 1L), WoodType.SPRUCE).leaves().isEmpty(), "finished goal needs no inputs");
        var stopped = CraftingPlan.calculate(index, Map.of("minecraft:chest", 1L), Set.of(), Map.of(), Map.of(), Map.of(),
                Set.of("minecraft:spruce_planks"), true, WoodType.SPRUCE);
        leaf(stopped, "spruce_planks", 8, 0);
        var cooked = CraftingPlan.calculate(index, Map.of("minecraft:chest", 1L, "minecraft:iron_ingot", 2L), Set.of(),
                Map.of(), Map.of(), Map.of(), Set.of(), true, WoodType.SPRUCE);
        leaf(cooked, "spruce_log", 2, 0);
        leaf(cooked, "raw_iron", 2, 0);

        for (WoodType nether : new WoodType[]{WoodType.CRIMSON, WoodType.WARPED}) {
            String stem = nether == WoodType.CRIMSON ? "crimson_stem" : "warped_stem";
            leaf(plan(index, Map.of("minecraft:chest", 1L), Map.of(), nether), stem, 2, 0);
        }
        var bamboo = plan(index, Map.of("minecraft:chest", 1L), Map.of(), WoodType.BAMBOO);
        leaf(bamboo, "bamboo", 36, 0);
        require(bamboo.leaves().size() == 1, "bamboo follows 9 bamboo -> block -> 2 planks");
        var bambooStickRecipe = index.recipesFor("minecraft:stick").stream()
                .filter(recipe -> recipe.ingredients().stream().anyMatch(input -> input.options().equals(java.util.List.of("minecraft:bamboo"))))
                .findFirst().orElseThrow();
        long input = bambooStickRecipe.ingredients().stream().mapToLong(RecipeIndex.Ingredient::count).sum();
        long batches = (8 + bambooStickRecipe.outputCount() - 1) / bambooStickRecipe.outputCount();
        var explicitRecipe = CraftingPlan.calculate(index, Map.of("minecraft:stick", 8L), Set.of(), Map.of(),
                Map.of("minecraft:stick", bambooStickRecipe.id()), Map.of(), Set.of(), false, WoodType.SPRUCE);
        leaf(explicitRecipe, "bamboo", batches * input, 0);
        require(explicitRecipe.leaves().size() == 1, "explicit bamboo recipe wins over spruce");

        var pale = plan(index, Map.of("minecraft:chest", 1L), Map.of(), WoodType.PALE_OAK);
        require(pale.valid(), "pale oak preference is safe on every supported index");
        boolean paleAvailable = chest.ingredients().stream().anyMatch(ingredient -> ingredient.options().contains(WoodType.PALE_OAK.planksId()));
        if (!paleAvailable) {
            require(pale.equals(legacy), "unavailable family preserves real recipe options");
            require(pale.leaves().keySet().stream().noneMatch(id -> id.contains("pale_oak")), "unavailable family creates no materials");
        } else leaf(pale, "pale_oak_log", 2, 0);
    }

    private static RecipeIndex synthetic() {
        Map<String, String> resources = new LinkedHashMap<>();
        StringBuilder planks = new StringBuilder();
        for (String name : new String[]{"oak", "spruce", "birch", "poplar", "crimson", "warped"}) {
            if (!planks.isEmpty()) planks.append(',');
            planks.append('"').append("minecraft:").append(name).append("_planks\"");
            String raw = name.equals("crimson") || name.equals("warped") ? name + "_stem" : name + "_log";
            resources.put(P + name + "_planks.json", craft(name + "_planks", 4, "minecraft:" + raw, 1));
        }
        planks.append(",\"minecraft:bamboo_planks\"");
        resources.put("data/minecraft/tags/item/planks.json", "{\"values\":[" + planks + "]}");
        resources.put(P + "bamboo_block.json", craft("bamboo_block", 1, "minecraft:bamboo", 9));
        resources.put(P + "bamboo_planks.json", craft("bamboo_planks", 2, "minecraft:bamboo_block", 1));
        resources.put(P + "chest.json", "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"PPP\",\"P P\",\"PPP\"],\"key\":{\"P\":\"#minecraft:planks\"},\"result\":{\"id\":\"minecraft:chest\"}}");
        resources.put(P + "stick.json", craft("stick", 4, "#minecraft:planks", 2));
        resources.put(P + "stick_from_bamboo_item.json", craft("stick", 1, "minecraft:bamboo", 2));
        resources.put(P + "oak_stairs.json", craft("oak_stairs", 4, "minecraft:oak_planks", 6));
        resources.put(P + "iron_ingot.json", "{\"type\":\"minecraft:smelting\",\"ingredient\":\"minecraft:raw_iron\",\"result\":{\"id\":\"minecraft:iron_ingot\"}}");
        RecipeIndex index = RecipeIndex.fromJson(resources);
        require(index.diagnostics().isEmpty(), "synthetic wood index: " + index.diagnostics());
        return index;
    }

    private static String craft(String output, int count, String input, int amount) {
        return "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":["
                + String.join(",", Collections.nCopies(amount, "\"" + input + "\""))
                + "],\"result\":{\"id\":\"minecraft:" + output + "\",\"count\":" + count + "}}";
    }

    private static CraftingPlan.Result plan(RecipeIndex index, Map<String, Long> goals, Map<String, Long> stock, WoodType type) {
        return CraftingPlan.calculate(index, goals, Set.of(), stock, Map.of(), Map.of(), Set.of(), false, type);
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
