package net.fabricmc.resourcetracker.crafting;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

/** Quantitative routing checks that can run without a Minecraft client. */
public final class PlannerRoutingRegression {
    private static final String P = "data/minecraft/recipe/";

    public static void main(String[] args) throws Exception {
        Map<String, String> resources = new LinkedHashMap<>();
        resources.put("data/minecraft/tags/item/planks.json",
                "{\"values\":[\"minecraft:oak_planks\",\"minecraft:birch_planks\"]}");
        resources.put(P + "oak_planks.json", recipe("oak_planks", 4, "\"minecraft:oak_log\""));
        resources.put(P + "birch_planks.json", recipe("birch_planks", 4, "\"minecraft:birch_log\""));
        resources.put(P + "chest.json", "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"PPP\",\"P P\",\"PPP\"],"
                + "\"key\":{\"P\":\"#minecraft:planks\"},\"result\":{\"id\":\"minecraft:chest\"}}");
        resources.put(P + "mossy_cobblestone_from_moss_block.json", recipe("mossy_cobblestone", 1,
                "\"minecraft:cobblestone\",\"minecraft:moss_block\""));
        resources.put(P + "mossy_cobblestone_from_vine.json", recipe("mossy_cobblestone", 1,
                "\"minecraft:cobblestone\",\"minecraft:vine\""));
        for (String item : new String[]{"routing_a", "routing_b"}) {
            resources.put(P + item + ".json", recipe(item, 1,
                    "\"minecraft:mossy_cobblestone\",\"minecraft:mossy_cobblestone\","
                            + "\"minecraft:mossy_cobblestone\",\"minecraft:mossy_cobblestone\""));
        }
        resources.put(P + "netherite_upgrade_smithing_template.json",
                "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"DSD\",\"DND\",\"DDD\"],"
                        + "\"key\":{\"D\":\"minecraft:diamond\",\"S\":\"minecraft:netherite_upgrade_smithing_template\","
                        + "\"N\":\"minecraft:netherrack\"},\"result\":{\"id\":\"minecraft:netherite_upgrade_smithing_template\",\"count\":2}}");
        resources.put(P + "bundle.json", recipe("bundle", 1, "\"minecraft:leather\",\"minecraft:string\""));
        String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        StringBuilder bundles = new StringBuilder("{\"values\":[\"minecraft:bundle\"");
        for (String color : colors) {
            bundles.append(",\"minecraft:").append(color).append("_bundle\"");
            resources.put(P + color + "_bundle.json", "{\"type\":\"minecraft:crafting_transmute\","
                    + "\"input\":\"#minecraft:bundles\",\"material\":\"minecraft:" + color
                    + "_dye\",\"result\":{\"id\":\"minecraft:" + color + "_bundle\"}}");
        }
        resources.put("data/minecraft/tags/item/bundles.json", bundles.append("]}").toString());
        for (String item : new String[]{"coal", "diamond", "emerald", "lapis_lazuli", "redstone", "quartz",
                "raw_iron", "raw_gold", "raw_copper"}) {
            resources.put(P + item + ".json", recipe(item, 1, "\"minecraft:test_ore\""));
        }
        check(RecipeIndex.fromJson(resources));
        for (String argument : args) {
            Map<String, String> vanilla = new LinkedHashMap<>();
            try (JarFile jar = new JarFile(Path.of(argument).toFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (entry.getName().startsWith("data/minecraft/") && entry.getName().endsWith(".json")) {
                        try (var stream = jar.getInputStream(entry)) {
                            vanilla.put(entry.getName(), new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                        }
                    }
                }
            }
            check(RecipeIndex.fromJson(vanilla));
        }
        System.out.println("Planner routing regression checks passed");
    }

    static void check(RecipeIndex index) {
        var result = plan(index, Map.of("minecraft:mossy_cobblestone", 4L),
                Map.of("minecraft:cobblestone", 4L, "minecraft:moss_block", 1L, "minecraft:vine", 4L), Map.of());
        complete(result, "full-demand recipe choice uses four supplied vines");
        require(result.options().stream().anyMatch(option -> "minecraft:mossy_cobblestone_from_vine".equals(option.selectedRecipeId())),
                "vine route selected");

        result = plan(index, Map.of("minecraft:chest", 1L), Map.of("minecraft:birch_log", 2L), Map.of());
        complete(result, "ingredient lookahead recognizes craftable birch planks");
        leaf(result, "birch_log", 2, 2);
        require(result.leaves().size() == 1, "only the supplied birch logs are required");

        result = plan(index, Map.of("minecraft:chest", 1L),
                Map.of("minecraft:oak_log", 1L, "minecraft:birch_log", 1L), Map.of());
        complete(result, "mixed compatible precursor stock supplies a chest");
        leaf(result, "oak_log", 1, 1);
        leaf(result, "birch_log", 1, 1);
        require(result.leaves().size() == 2, "each mixed precursor stack is spent once");
        String chestSlot = index.recipesFor("minecraft:chest").getFirst().ingredients().getFirst().key();
        result = CraftingPlan.calculate(index, Map.of("minecraft:chest", 1L), Set.of(),
                Map.of("minecraft:oak_log", 1L, "minecraft:birch_log", 1L), Map.of(),
                Map.of(chestSlot, "minecraft:oak_planks"), Set.of());
        leaf(result, "oak_log", 2, 1);
        require(result.leaves().size() == 1, "explicit plank choice forbids mixed precursor conversion");

        if (!index.recipesFor("minecraft:routing_a").isEmpty()) {
            result = plan(index, Map.of("minecraft:routing_a", 1L, "minecraft:routing_b", 1L),
                    Map.of("minecraft:cobblestone", 8L, "minecraft:moss_block", 4L, "minecraft:vine", 8L), Map.of());
            complete(result, "shared downstream demand chooses the route supplied for all eight items");
            require(result.options().stream().anyMatch(option -> "minecraft:mossy_cobblestone_from_vine".equals(option.selectedRecipeId())),
                    "shared child's combined demand selects vines");
        }

        result = plan(index, Map.of("minecraft:chest", 1L, "minecraft:birch_planks", 8L),
                Map.of("minecraft:oak_planks", 7L, "minecraft:birch_log", 2L), Map.of());
        leaf(result, "birch_log", 2, 2);
        leaf(result, "oak_log", 1, 0);
        require(result.leaves().size() == 2, "a finished plank goal claims its logs before another route is chosen");

        String template = "minecraft:netherite_upgrade_smithing_template";
        Map<String, String> duplication = Map.of(template, "minecraft:netherite_upgrade_smithing_template");
        result = plan(index, Map.of(template, 2L), Map.of(template, 1L), duplication);
        leaf(result, "netherite_upgrade_smithing_template", 1, 1);
        leaf(result, "diamond", 7, 0);
        leaf(result, "netherrack", 1, 0);
        require(result.leaves().size() == 3, "one existing seed creates exactly one additional template");

        result = plan(index, Map.of(template, 3L), Map.of(), duplication);
        leaf(result, "netherite_upgrade_smithing_template", 1, 0);
        leaf(result, "diamond", 14, 0);
        leaf(result, "netherrack", 2, 0);
        require(result.leaves().size() == 3, "one collected seed is reused for two duplication batches");
        result = plan(index, Map.of(template, 1L), Map.of(), duplication);
        leaf(result, "netherite_upgrade_smithing_template", 1, 0);
        require(result.leaves().size() == 1, "a lone missing template needs a seed without duplication costs");
        result = plan(index, Map.of(template, 2L), Map.of(template, 2L), duplication);
        require(result.valid() && result.leaves().isEmpty(), "completed seeded goal has no additional inputs");

        if (!index.recipesFor("minecraft:blue_bundle").isEmpty()) {
            result = plan(index, Map.of("minecraft:blue_bundle", 1L), Map.of("minecraft:diamond", 1L), Map.of());
            require(result.valid() && !result.leaves().containsKey("minecraft:blue_bundle"),
                    "recoloring preview with unrelated stock terminates through the undyed route");
            result = plan(index, Map.of("minecraft:blue_bundle", 1L),
                    Map.of("minecraft:red_bundle", 1L, "minecraft:blue_dye", 1L), Map.of());
            complete(result, "a supplied colored bundle can be recolored directly");
        }

        for (String item : new String[]{"coal", "diamond", "emerald", "lapis_lazuli", "redstone", "quartz",
                "raw_iron", "raw_gold", "raw_copper"}) {
            result = plan(index, Map.of("minecraft:" + item, 3L), Map.of(), Map.of());
            leaf(result, item, 3, 0);
            require(result.leaves().size() == 1, "natural mineral stays a collection boundary: " + item);
        }
    }

    private static CraftingPlan.Result plan(RecipeIndex index, Map<String, Long> goals, Map<String, Long> stock,
                                             Map<String, String> recipes) {
        return CraftingPlan.calculate(index, goals, Set.of(), stock, recipes, Map.of(), Set.of());
    }

    private static void complete(CraftingPlan.Result result, String message) {
        require(result.valid() && result.leaves().values().stream().allMatch(leaf -> leaf.remaining() == 0),
                message + ": " + result);
    }

    private static void leaf(CraftingPlan.Result result, String item, long required, long available) {
        var leaf = result.leaves().get("minecraft:" + item);
        require(result.valid() && leaf != null && leaf.required() == required && leaf.available() == available,
                "expected " + item + "=" + required + "/" + available + ", got " + result);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String recipe(String output, long count, String ingredients) {
        return "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[" + ingredients
                + "],\"result\":{\"id\":\"minecraft:" + output + "\",\"count\":" + count + "}}";
    }
}
