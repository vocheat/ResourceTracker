package net.fabricmc.resourcetracker.crafting;

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
import java.util.TreeSet;

/** Pure planner. Every count is an exact long; a failed plan never contains partial totals. */
public final class CraftingPlan {
    private CraftingPlan() { }

    public record Leaf(String itemId, long required, long available) {
        public long remaining() { return required - available; }
    }

    public record Option(String itemId, List<RecipeIndex.Recipe> recipes,
                         String selectedRecipeId, boolean stopped) {
        public Option { recipes = List.copyOf(recipes); }
    }

    public record Result(Map<String, Leaf> leaves, List<Option> options, List<String> diagnostics,
                         Map<String, Long> leftovers, boolean valid) {
        public Result {
            leaves = Collections.unmodifiableMap(new LinkedHashMap<>(leaves));
            options = List.copyOf(options);
            diagnostics = List.copyOf(diagnostics);
            leftovers = Collections.unmodifiableMap(new TreeMap<>(leftovers));
        }
    }

    public static Result calculate(RecipeIndex index, Map<String, Long> goals, Set<String> manuallyDone,
                                   Map<String, Long> stock, Map<String, String> recipeChoices,
                                   Map<String, String> ingredientChoices, Set<String> stops) {
        Planner planner = new Planner(index, recipeChoices, ingredientChoices, stops);
        try {
            return planner.calculate(goals, manuallyDone, stock);
        } catch (ArithmeticException exception) {
            return planner.failure("Crafting quantity exceeds the supported 64-bit range");
        } catch (IllegalArgumentException exception) {
            return planner.failure(exception.getMessage());
        }
    }

    private static final class Planner {
        private final RecipeIndex index;
        private final Map<String, String> recipeChoices;
        private final Map<String, String> ingredientChoices;
        private final Set<String> stops;
        private final List<String> diagnostics;
        private final Map<String, Option> options = new LinkedHashMap<>();
        private final Map<String, RecipeIndex.Recipe> selected = new HashMap<>();
        private final Map<String, Map<String, Long>> ingredients = new HashMap<>();
        private final Map<String, List<ResolvedIngredient>> resolvedIngredients = new HashMap<>();
        private final Set<String> visiting = new LinkedHashSet<>();
        private final Set<String> visited = new HashSet<>();
        private final List<String> order = new ArrayList<>();
        private Map<String, Long> preferenceInventory = Map.of();

        Planner(RecipeIndex index, Map<String, String> recipeChoices,
                Map<String, String> ingredientChoices, Set<String> stops) {
            this.index = index;
            this.recipeChoices = recipeChoices;
            this.ingredientChoices = ingredientChoices;
            this.stops = stops;
            this.diagnostics = new ArrayList<>(index.diagnostics());
        }

        Result failure(String message) {
            diagnostics.add(message);
            return new Result(Map.of(), new ArrayList<>(options.values()), diagnostics, Map.of(), false);
        }

        Result calculate(Map<String, Long> goals, Set<String> manuallyDone, Map<String, Long> stock) {
            if (!index.available()) return failure("Vanilla crafting recipe index is unavailable");
            Map<String, Long> inventory = new TreeMap<>();
            for (var entry : stock.entrySet()) {
                validate(entry.getKey(), entry.getValue());
                if (entry.getValue() > 0) inventory.put(entry.getKey(), entry.getValue());
            }
            Map<String, Long> demands = new HashMap<>();
            Map<String, Long> reserved = new HashMap<>();
            // Reserve all finished goals before any intermediate is allowed to consume inventory.
            for (var entry : new TreeMap<>(goals).entrySet()) {
                validate(entry.getKey(), entry.getValue());
                if (entry.getValue() == 0 || manuallyDone.contains(entry.getKey())) continue;
                demands.put(entry.getKey(), entry.getValue());
                reserved.put(entry.getKey(), take(inventory, entry.getKey(), entry.getValue()));
            }
            // Selection is only a preference. The allocation ledger below consumes each stack once.
            preferenceInventory = Map.copyOf(inventory);
            for (String item : new TreeSet<>(demands.keySet())) visit(item);
            scheduleSupplies();
            Map<String, Leaf> leaves = new TreeMap<>();
            Map<String, Long> leftovers = new HashMap<>();
            for (String item : order) {
                long demand = demands.getOrDefault(item, 0L);
                if (demand == 0) continue;
                long held = reserved.getOrDefault(item, 0L);
                held = Math.addExact(held, take(inventory, item, demand - held));
                long recycled = take(leftovers, item, demand - held);
                long needed = demand - held - recycled;
                RecipeIndex.Recipe recipe = selected.get(item);
                if (recipe == null) {
                    if (demand > recycled) leaves.put(item, new Leaf(item, demand - recycled, held));
                } else if (needed > 0) {
                    // Division avoids the overflow of the usual (needed + outputCount - 1) formula.
                    long batches = needed / recipe.outputCount() + (needed % recipe.outputCount() == 0 ? 0 : 1);
                    long output = Math.multiplyExact(batches, recipe.outputCount());
                    add(leftovers, item, output - needed);
                    for (ResolvedIngredient ingredient : resolvedIngredients.get(item)) {
                        long count = Math.multiplyExact(batches, ingredient.count());
                        // Reserve selected inputs immediately, before another producer can use
                        // them as interchangeable alternatives. Keep their full demand and held
                        // quantity on the child so raw required/available totals stay meaningful.
                        long chosenHeld = take(inventory, ingredient.chosen(), count);
                        add(reserved, ingredient.chosen(), chosenHeld);
                        // Reserve an already returned/produced selected input as well. It is
                        // recycled supply, so it reduces the child's demand instead of its stock.
                        long chosenRecycled = take(leftovers, ingredient.chosen(), count - chosenHeld);
                        count -= chosenRecycled;
                        if (ingredient.automatic()) {
                            // Tag/array slots may mix compatible items. Consume other on-hand
                            // and surplus alternatives now; only the remainder needs the chosen route.
                            long unfilled = count - chosenHeld;
                            for (String alternative : ingredient.alternatives()) {
                                if (alternative.equals(ingredient.chosen())) continue;
                                long consumed = take(inventory, alternative, unfilled);
                                consumed = Math.addExact(consumed, take(leftovers, alternative, unfilled - consumed));
                                count -= consumed;
                                unfilled -= consumed;
                                String returned = remainder(alternative);
                                if (returned != null) add(leftovers, returned, consumed);
                                if (unfilled == 0) break;
                            }
                        }
                        add(demands, ingredient.chosen(), count);
                        String returned = remainder(ingredient.chosen());
                        if (returned != null) add(leftovers, returned, Math.addExact(count, chosenRecycled));
                    }
                }
            }
            return new Result(leaves, new ArrayList<>(options.values()), diagnostics, leftovers, true);
        }

        private void scheduleSupplies() {
            Map<String, Set<String>> edges = new TreeMap<>();
            for (String item : options.keySet()) {
                edges.put(item, new TreeSet<>(ingredients.getOrDefault(item, Map.of()).keySet()));
            }
            // Returned containers can supply other targets. Add ordering constraints only when the
            // return does not itself have to be obtained before the producing recipe can run.
            for (var entry : new TreeMap<>(resolvedIngredients).entrySet()) {
                for (ResolvedIngredient ingredient : entry.getValue()) {
                    for (String candidate : ingredient.automatic() ? ingredient.alternatives() : List.of(ingredient.chosen())) {
                        String returned = remainder(candidate);
                        if (returned != null && edges.containsKey(returned)
                                && !reaches(edges, returned, entry.getKey(), new HashSet<>())) {
                            edges.get(entry.getKey()).add(returned);
                        }
                    }
                }
            }
            // A needed alternative producer may have rounded surplus that can satisfy a flexible
            // slot. Run it first, after all its selected demands have accumulated. Never reverse an
            // existing dependency: a consumer cannot borrow surplus from its own future inputs.
            for (var entry : new TreeMap<>(resolvedIngredients).entrySet()) {
                for (ResolvedIngredient ingredient : entry.getValue()) {
                    if (!ingredient.automatic()) continue;
                    for (String alternative : ingredient.alternatives()) {
                        if (selected.containsKey(alternative)
                                && !reaches(edges, entry.getKey(), alternative, new HashSet<>())) {
                            edges.get(alternative).add(entry.getKey());
                        }
                    }
                }
            }
            Map<String, Integer> incoming = new TreeMap<>();
            edges.keySet().forEach(item -> incoming.put(item, 0));
            edges.values().forEach(children -> children.forEach(child -> incoming.merge(child, 1, Integer::sum)));
            // Fixed inputs have fewer ways to use stock. Let them reserve their children before
            // flexible tag recipes consume compatible alternatives from the shared ledger.
            TreeSet<String> ready = new TreeSet<>(Comparator.comparingInt((String item) ->
                    resolvedIngredients.getOrDefault(item, List.of()).stream()
                            .anyMatch(ingredient -> ingredient.automatic() && ingredient.alternatives().size() > 1) ? 1 : 0)
                    .thenComparing(item -> item));
            incoming.forEach((item, count) -> { if (count == 0) ready.add(item); });
            order.clear();
            while (!ready.isEmpty()) {
                String item = ready.pollFirst();
                order.add(item);
                for (String child : edges.get(item)) {
                    if (incoming.merge(child, -1, Integer::sum) == 0) ready.add(child);
                }
            }
            if (order.size() != edges.size()) throw new IllegalArgumentException("Cyclic crafting dependency");
        }

        private static boolean reaches(Map<String, Set<String>> edges, String from, String target, Set<String> seen) {
            if (from.equals(target)) return true;
            if (!seen.add(from)) return false;
            for (String next : edges.getOrDefault(from, Set.of())) {
                if (reaches(edges, next, target, seen)) return true;
            }
            return false;
        }

        private void visit(String item) {
            if (visiting.contains(item)) {
                throw new IllegalArgumentException("Cyclic crafting dependency: " + String.join(" -> ", visiting) + " -> " + item);
            }
            if (!visited.add(item)) return;
            visiting.add(item);
            List<RecipeIndex.Recipe> choices = index.recipesFor(item);
            String requested = recipeChoices.get(item);
            RecipeIndex.Recipe recipe = null;
            if (requested != null) {
                recipe = choices.stream().filter(candidate -> candidate.id().equals(requested)).findFirst().orElse(null);
                if (recipe == null) diagnostics.add("Recipe choice is unavailable for " + item + ": " + requested);
            }
            boolean needsSeed = !choices.isEmpty() && choices.stream().allMatch(candidate -> candidate.ingredients().stream()
                    .anyMatch(ingredient -> ingredient.options().size() == 1 && ingredient.options().getFirst().equals(item)));
            boolean stopped = stops.contains(item) || (recipe == null && (naturalBase(item) || needsSeed));
            if (!stopped && recipe == null && !choices.isEmpty()) recipe = choices.getFirst();
            if (stopped) recipe = null;
            options.put(item, new Option(item, choices, recipe == null ? null : recipe.id(), stopped));
            if (recipe == null && choices.isEmpty()) diagnostics.add("No supported vanilla crafting recipe for " + item);
            if (recipe == null && needsSeed) diagnostics.add("Crafting requires an existing seed item: " + item);
            if (recipe != null) {
                selected.put(item, recipe);
                Map<String, Long> selectedIngredients = new TreeMap<>();
                List<ResolvedIngredient> resolved = new ArrayList<>();
                for (RecipeIndex.Ingredient ingredient : recipe.ingredients()) {
                    String chosen = ingredientChoices.get(ingredient.key());
                    if (chosen != null && !ingredient.options().contains(chosen)) {
                        diagnostics.add("Ingredient choice is unavailable: " + ingredient.key() + " = " + chosen);
                        chosen = null;
                    }
                    boolean automatic = chosen == null;
                    if (automatic) chosen = preferredIngredient(ingredient);
                    resolved.add(new ResolvedIngredient(chosen, ingredient.options(), ingredient.count(), automatic));
                    add(selectedIngredients, chosen, ingredient.count());
                }
                ingredients.put(item, selectedIngredients);
                resolvedIngredients.put(item, resolved);
                for (String ingredient : selectedIngredients.keySet()) visit(ingredient);
            }
            visiting.remove(item);
            order.add(item);
        }

        private record ResolvedIngredient(String chosen, List<String> alternatives, long count, boolean automatic) { }

        private String preferredIngredient(RecipeIndex.Ingredient ingredient) {
            List<String> candidates = ingredient.options().stream().filter(item -> !visiting.contains(item)).toList();
            if (candidates.isEmpty()) candidates = ingredient.options();
            String chosen = candidates.getFirst();
            long available = preferenceInventory.getOrDefault(chosen, 0L);
            for (String candidate : candidates) {
                long candidateAvailable = preferenceInventory.getOrDefault(candidate, 0L);
                if (candidateAvailable > available) {
                    chosen = candidate;
                    available = candidateAvailable;
                }
            }
            return chosen;
        }
    }

    private static void validate(String item, Long count) {
        if (item == null || item.isBlank() || count == null || count < 0) {
            throw new IllegalArgumentException("Invalid item quantity: " + item + " = " + count);
        }
    }

    private static long take(Map<String, Long> inventory, String item, long wanted) {
        long current = inventory.getOrDefault(item, 0L);
        long taken = Math.min(current, wanted);
        if (taken > 0) inventory.put(item, current - taken);
        return taken;
    }

    private static void add(Map<String, Long> quantities, String item, long count) {
        if (count != 0) quantities.merge(item, count, Math::addExact);
    }

    private static boolean naturalBase(String item) {
        return item.endsWith("_ingot") || item.endsWith("_log")
                || item.endsWith("_stem") || item.equals("minecraft:stone")
                || item.equals("minecraft:cobblestone") || item.equals("minecraft:deepslate")
                || item.equals("minecraft:cobbled_deepslate") || item.equals("minecraft:blackstone")
                || item.equals("minecraft:basalt") || item.equals("minecraft:end_stone")
                || item.equals("minecraft:netherrack");
    }

    private static String remainder(String item) {
        return switch (item) {
            case "minecraft:milk_bucket", "minecraft:water_bucket", "minecraft:lava_bucket",
                    "minecraft:powder_snow_bucket" -> "minecraft:bucket";
            case "minecraft:honey_bottle" -> "minecraft:glass_bottle";
            default -> null;
        };
    }
}
