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
    // Collection boundaries are deliberate, not inferred from the existence of a recipe.
    // Like EMI's vanilla defaults, keep leather, wool, dyes and ordinary metals as materials;
    // their processing recipes remain available through an explicit recipe choice.
    private static final Set<String> BASE_MATERIALS = Set.of(
            "minecraft:leather", "minecraft:stone", "minecraft:cobblestone", "minecraft:deepslate",
            "minecraft:cobbled_deepslate", "minecraft:blackstone", "minecraft:basalt",
            "minecraft:end_stone", "minecraft:netherrack", "minecraft:coal", "minecraft:diamond",
            "minecraft:emerald", "minecraft:lapis_lazuli", "minecraft:redstone", "minecraft:quartz",
            "minecraft:raw_iron", "minecraft:raw_gold", "minecraft:raw_copper");

    private CraftingPlan() { }

    public record Leaf(String itemId, long required, long available) {
        public long remaining() { return required - available; }
    }

    public record Option(String itemId, List<RecipeIndex.Recipe> recipes,
                         String selectedRecipeId, boolean stopped, boolean defaultStopped) {
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
        private final Map<String, Long> seeds = new HashMap<>();
        private final Set<String> visiting = new LinkedHashSet<>();
        private final Set<String> visited = new HashSet<>();
        private final List<String> order = new ArrayList<>();
        private Map<String, Long> preferenceInventory = Map.of();
        private Map<String, Long> demandHints = Map.of();

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
            int initialDiagnostics = diagnostics.size();
            selectRoutes(demands, reserved, inventory);
            // Reconsider shared children once their parents' demands have accumulated. This
            // bounded second pass avoids choosing a four-item route for a combined eight-item need.
            demandHints = forecastDemands(demands, reserved, inventory);
            options.clear();
            selected.clear();
            ingredients.clear();
            resolvedIngredients.clear();
            seeds.clear();
            visited.clear();
            visiting.clear();
            order.clear();
            diagnostics.subList(initialDiagnostics, diagnostics.size()).clear();
            selectRoutes(demands, reserved, inventory);
            Map<String, Leaf> leaves = new TreeMap<>();
            Map<String, Long> leftovers = new HashMap<>();
            Map<String, Long> craftedStockLeaves = new TreeMap<>();
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
                    long seed = seeds.getOrDefault(item, 0L);
                    if (seed > 0) {
                        // A duplication recipe returns its input seed together with the new
                        // output. One seed can be reused for every batch; it is not a cycle.
                        long seedRequired = Math.min(seed, demand);
                        long seedRecycled = Math.min(seedRequired, recycled);
                        long seedHeld = Math.min(seedRequired - seedRecycled, held);
                        leaves.put(item, new Leaf(item, seedRequired - seedRecycled, seedHeld));
                        needed -= Math.min(needed, seedRequired - seedRecycled - seedHeld);
                    }
                    if (needed == 0) continue;
                    // Division avoids the overflow of the usual (needed + outputCount - 1) formula.
                    long yield = recipe.outputCount() - seed;
                    long batches = batches(needed, yield);
                    long output = Math.multiplyExact(batches, yield);
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
                            // Compatible inputs can also be made from different on-hand raw
                            // materials (one oak log plus one birch log supplies a chest).
                            for (String alternative : ingredient.alternatives()) {
                                if (unfilled == 0) break;
                                if (alternative.equals(ingredient.chosen())) continue;
                                long consumed = craftAvailable(alternative, unfilled, item, inventory,
                                        leftovers, craftedStockLeaves);
                                count -= consumed;
                                unfilled -= consumed;
                                String returned = remainder(alternative);
                                if (returned != null) add(leftovers, returned, consumed);
                            }
                        }
                        add(demands, ingredient.chosen(), count);
                        String returned = remainder(ingredient.chosen());
                        if (returned != null) add(leftovers, returned, Math.addExact(count, chosenRecycled));
                    }
                }
            }
            craftedStockLeaves.forEach((item, count) -> leaves.merge(item, new Leaf(item, count, count),
                    (first, second) -> new Leaf(item, Math.addExact(first.required(), second.required()),
                            Math.addExact(first.available(), second.available()))));
            return new Result(leaves, new ArrayList<>(options.values()), diagnostics, leftovers, true);
        }

        private void selectRoutes(Map<String, Long> demands, Map<String, Long> reserved, Map<String, Long> inventory) {
            preferenceInventory = new HashMap<>(inventory);
            for (String item : new TreeSet<>(demands.keySet())) {
                long needed = demands.get(item) - reserved.getOrDefault(item, 0L);
                visit(item, needed);
                previewItem(item, needed, preferenceInventory, new HashSet<>());
            }
            scheduleSupplies();
        }

        private Map<String, Long> forecastDemands(Map<String, Long> goals, Map<String, Long> reserved,
                                                Map<String, Long> inventory) {
            Map<String, Long> demands = new HashMap<>(goals);
            Map<String, Long> stock = new HashMap<>(inventory);
            Map<String, Long> hints = new HashMap<>();
            for (String item : order) {
                long demand = demands.getOrDefault(item, 0L);
                long held = reserved.getOrDefault(item, 0L);
                held = Math.addExact(held, take(stock, item, demand - held));
                long needed = demand - held;
                hints.put(item, needed);
                RecipeIndex.Recipe recipe = selected.get(item);
                if (recipe == null || needed == 0) continue;
                long seed = seeds.getOrDefault(item, 0L);
                needed -= Math.min(needed, Math.max(0, seed - held));
                long batches = batches(needed, recipe.outputCount() - seed);
                for (ResolvedIngredient ingredient : resolvedIngredients.get(item)) {
                    add(demands, ingredient.chosen(), Math.multiplyExact(batches, ingredient.count()));
                }
            }
            return hints;
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

        private void visit(String item, long demand) {
            if (visiting.contains(item)) {
                throw new IllegalArgumentException("Cyclic crafting dependency: " + String.join(" -> ", visiting) + " -> " + item);
            }
            if (!visited.add(item)) return;
            demand = Math.max(demand, demandHints.getOrDefault(item, 0L));
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
            boolean defaultStopped = naturalBase(item) || needsSeed;
            boolean stopped = stops.contains(item) || (recipe == null && defaultStopped);
            if (!stopped && recipe == null && !choices.isEmpty()) recipe = preferredRecipe(choices, Math.max(1, demand));
            if (stopped) recipe = null;
            options.put(item, new Option(item, choices, recipe == null ? null : recipe.id(), stopped, defaultStopped));
            if (recipe == null && choices.isEmpty()) diagnostics.add("No supported vanilla crafting recipe for " + item);
            if (recipe == null && needsSeed) diagnostics.add("Crafting requires an existing seed item: " + item);
            if (recipe != null) {
                selected.put(item, recipe);
                long seed = seedCount(recipe);
                if (seed > 0) seeds.put(item, seed);
                Map<String, Long> selectedIngredients = new TreeMap<>();
                List<ResolvedIngredient> resolved = new ArrayList<>();
                long batches = batches(Math.max(1, demand), recipe.outputCount() - seed);
                for (RecipeIndex.Ingredient ingredient : recipe.ingredients()) {
                    if (isSeed(recipe, ingredient)) continue;
                    String chosen = ingredientChoices.get(ingredient.key());
                    if (chosen != null && !ingredient.options().contains(chosen)) {
                        diagnostics.add("Ingredient choice is unavailable: " + ingredient.key() + " = " + chosen);
                        chosen = null;
                    }
                    boolean automatic = chosen == null;
                    if (automatic) chosen = preferredIngredient(ingredient, Math.multiplyExact(batches, ingredient.count()));
                    resolved.add(new ResolvedIngredient(chosen, ingredient.options(), ingredient.count(), automatic));
                    add(selectedIngredients, chosen, ingredient.count());
                }
                ingredients.put(item, selectedIngredients);
                resolvedIngredients.put(item, resolved);
                for (var ingredient : selectedIngredients.entrySet()) {
                    long childDemand = Math.multiplyExact(batches, ingredient.getValue());
                    childDemand -= Math.min(childDemand, preferenceInventory.getOrDefault(ingredient.getKey(), 0L));
                    visit(ingredient.getKey(), childDemand);
                }
            }
            visiting.remove(item);
            order.add(item);
        }

        private record ResolvedIngredient(String chosen, List<String> alternatives, long count, boolean automatic) { }

        private static final class StockCraft {
            final Map<String, Long> inventory;
            final Map<String, Long> leftovers;
            final Map<String, Long> rawUsed = new TreeMap<>();
            final Map<String, Option> usedOptions = new LinkedHashMap<>();

            StockCraft(Map<String, Long> inventory, Map<String, Long> leftovers) {
                this.inventory = new HashMap<>(inventory);
                this.leftovers = new HashMap<>(leftovers);
            }
        }

        /** Craft only fully supplied batches; failed trials never touch the allocation ledger. */
        private long craftAvailable(String item, long wanted, String consumer, Map<String, Long> inventory,
                                    Map<String, Long> leftovers, Map<String, Long> rawUsed) {
            if (inventory.values().stream().noneMatch(count -> count > 0)
                    && leftovers.values().stream().noneMatch(count -> count > 0)) return 0;
            RecipeIndex.Recipe recipe = stockRecipe(item);
            if (recipe == null || seedCount(recipe) > 0) return 0;
            long low = 0;
            long high = batches(wanted, recipe.outputCount());
            StockCraft best = null;
            while (low < high) {
                long middle = low + (high - low) / 2 + 1;
                StockCraft trial = new StockCraft(inventory, leftovers);
                boolean supplied;
                try {
                    supplied = produceStock(recipe, middle, trial, new HashSet<>(Set.of(consumer)));
                } catch (ArithmeticException exception) {
                    supplied = false;
                }
                if (supplied) {
                    low = middle;
                    best = trial;
                } else {
                    high = middle - 1;
                }
            }
            if (best == null) return 0;
            long supplied = Math.min(wanted, Math.multiplyExact(low, recipe.outputCount()));
            take(best.leftovers, item, supplied);
            inventory.clear();
            inventory.putAll(best.inventory);
            leftovers.clear();
            leftovers.putAll(best.leftovers);
            best.rawUsed.forEach((raw, count) -> add(rawUsed, raw, count));
            best.usedOptions.forEach(options::putIfAbsent);
            return supplied;
        }

        private RecipeIndex.Recipe stockRecipe(String item) {
            if (stops.contains(item)) return null;
            List<RecipeIndex.Recipe> choices = index.recipesFor(item);
            String requested = recipeChoices.get(item);
            if (requested != null) {
                RecipeIndex.Recipe explicit = choices.stream().filter(recipe -> recipe.id().equals(requested))
                        .findFirst().orElse(null);
                if (explicit != null) return explicit;
            }
            if (naturalBase(item) || choices.isEmpty()
                    || choices.stream().allMatch(recipe -> seedCount(recipe) > 0)) return null;
            return selected.getOrDefault(item, choices.getFirst());
        }

        private long consumeStock(String item, long wanted, StockCraft trial) {
            long held = take(trial.inventory, item, wanted);
            long recycled = take(trial.leftovers, item, wanted - held);
            if (held > 0 || recycled > 0) {
                RecipeIndex.Recipe recipe = stockRecipe(item);
                if (held > 0 && recipe == null) add(trial.rawUsed, item, held);
                List<RecipeIndex.Recipe> choices = index.recipesFor(item);
                boolean defaultStopped = naturalBase(item)
                        || !choices.isEmpty() && choices.stream().allMatch(candidate -> seedCount(candidate) > 0);
                trial.usedOptions.putIfAbsent(item, new Option(item, choices,
                        recipe == null ? null : recipe.id(), recipe == null, defaultStopped));
            }
            return Math.addExact(held, recycled);
        }

        private boolean consumeFully(String item, long wanted, StockCraft trial, Set<String> path) {
            long held = consumeStock(item, wanted, trial);
            long needed = wanted - held;
            if (needed > 0) {
                RecipeIndex.Recipe recipe = stockRecipe(item);
                if (recipe == null || seedCount(recipe) > 0
                        || !produceStock(recipe, batches(needed, recipe.outputCount()), trial, path)) return false;
                if (take(trial.leftovers, item, needed) != needed) return false;
            }
            String returned = remainder(item);
            if (returned != null) add(trial.leftovers, returned, wanted);
            return true;
        }

        private boolean produceStock(RecipeIndex.Recipe recipe, long batches, StockCraft trial, Set<String> path) {
            if (!path.add(recipe.output())) return false;
            try {
                trial.usedOptions.putIfAbsent(recipe.output(), new Option(recipe.output(), index.recipesFor(recipe.output()),
                        recipe.id(), false, naturalBase(recipe.output())));
                for (RecipeIndex.Ingredient ingredient : recipe.ingredients()) {
                    long needed = Math.multiplyExact(batches, ingredient.count());
                    String explicit = ingredientChoices.get(ingredient.key());
                    List<String> candidates = explicit != null && ingredient.options().contains(explicit)
                            ? List.of(explicit) : ingredient.options();
                    for (String candidate : candidates) {
                        long consumed = consumeStock(candidate, needed, trial);
                        needed -= consumed;
                        String returned = remainder(candidate);
                        if (returned != null) add(trial.leftovers, returned, consumed);
                        if (needed == 0) break;
                    }
                    if (needed > 0) {
                        // The canonical ancestor keeps this a bounded stock conversion,
                        // rather than exploring combinations of recoloring/processing routes.
                        String candidate = candidates.stream().filter(item -> !path.contains(item)).findFirst().orElse(null);
                        if (candidate == null || !consumeFully(candidate, needed, trial, path)) return false;
                    }
                }
                add(trial.leftovers, recipe.output(), Math.multiplyExact(batches, recipe.outputCount()));
                return true;
            } finally {
                path.remove(recipe.output());
            }
        }

        private RecipeIndex.Recipe preferredRecipe(List<RecipeIndex.Recipe> choices, long demand) {
            RecipeIndex.Recipe chosen = choices.getFirst();
            if (choices.size() == 1 || preferenceInventory.isEmpty()) return chosen;
            double bestCoverage = stockCoverage(chosen, demand);
            for (int i = 1; i < choices.size(); i++) {
                RecipeIndex.Recipe candidate = choices.get(i);
                double coverage = stockCoverage(candidate, demand);
                if (coverage > bestCoverage) {
                    chosen = candidate;
                    bestCoverage = coverage;
                }
            }
            return chosen;
        }

        private double stockCoverage(RecipeIndex.Recipe recipe, long demand) {
            try {
                return previewRecipe(recipe, demand, new HashMap<>(preferenceInventory), new HashSet<>(visiting));
            } catch (ArithmeticException exception) {
                // An unused alternative must not invalidate a representable chosen route.
                return -1;
            }
        }

        private String preferredIngredient(RecipeIndex.Ingredient ingredient, long demand) {
            List<String> candidates = ingredient.options().stream().filter(item -> !visiting.contains(item)).toList();
            if (candidates.isEmpty()) candidates = ingredient.options();
            String chosen = candidates.getFirst();
            long available = preferenceInventory.getOrDefault(chosen, 0L);
            double bestCoverage = previewCoverage(chosen, demand);
            for (String candidate : candidates) {
                long candidateAvailable = preferenceInventory.getOrDefault(candidate, 0L);
                double coverage = previewCoverage(candidate, demand);
                if (coverage > bestCoverage || coverage == bestCoverage && candidateAvailable > available) {
                    chosen = candidate;
                    available = candidateAvailable;
                    bestCoverage = coverage;
                }
            }
            return chosen;
        }

        private double previewCoverage(String item, long demand) {
            try {
                return previewItem(item, demand, new HashMap<>(preferenceInventory), new HashSet<>(visiting));
            } catch (ArithmeticException exception) {
                return -1;
            }
        }

        /** Bounded lookahead: inspect one deterministic route per ancestor, never search recipe combinations. */
        private double previewItem(String item, long demand, Map<String, Long> stock, Set<String> path) {
            if (demand == 0) return 1;
            if (stock.values().stream().noneMatch(count -> count > 0)) return 0;
            long held = take(stock, item, demand);
            long needed = demand - held;
            if (needed == 0) return 1;
            if (stops.contains(item) || !path.add(item)) return (double) held / demand;
            try {
                List<RecipeIndex.Recipe> choices = index.recipesFor(item);
                String requested = recipeChoices.get(item);
                RecipeIndex.Recipe recipe = requested == null ? selected.get(item) : choices.stream()
                        .filter(candidate -> candidate.id().equals(requested)).findFirst().orElse(null);
                if (recipe == null && (naturalBase(item) || choices.isEmpty()
                        || choices.stream().allMatch(candidate -> seedCount(candidate) > 0))) return (double) held / demand;
                if (recipe == null) recipe = choices.getFirst();
                long seed = seedCount(recipe);
                needed -= Math.min(needed, Math.max(0, seed - held));
                double coverage = needed == 0 ? 0 : previewRecipe(recipe, needed, stock, path);
                return (held + needed * coverage) / demand;
            } finally {
                path.remove(item);
            }
        }

        private double previewRecipe(RecipeIndex.Recipe recipe, long demand, Map<String, Long> stock,
                                     Set<String> path) {
            long batches = batches(demand, recipe.outputCount() - seedCount(recipe));
            double supplied = 0;
            double required = 0;
            for (RecipeIndex.Ingredient ingredient : recipe.ingredients()) {
                if (isSeed(recipe, ingredient)) continue;
                long count = Math.multiplyExact(batches, ingredient.count());
                required += count;
                String explicit = ingredientChoices.get(ingredient.key());
                List<String> candidates = explicit != null && ingredient.options().contains(explicit)
                        ? List.of(explicit) : ingredient.options();
                long direct = 0;
                for (String candidate : candidates) direct = Math.addExact(direct, take(stock, candidate, count - direct));
                long needed = count - direct;
                double best = 0;
                Map<String, Long> bestStock = null;
                if (needed > 0) {
                    for (String candidate : candidates) {
                        if (path.contains(candidate)) continue;
                        Map<String, Long> candidateStock = new HashMap<>(stock);
                        double coverage = previewItem(candidate, needed, candidateStock, path);
                        best = coverage;
                        bestStock = candidateStock;
                        // Ancestor lookahead follows one canonical ingredient route. Trying
                        // every recolorable bundle/shulker variant at every depth enumerates
                        // color permutations; direct compatible stock was already used above.
                        break;
                    }
                }
                if (bestStock != null) {
                    stock.clear();
                    stock.putAll(bestStock);
                }
                supplied += direct + needed * best;
            }
            return required == 0 ? 0 : supplied / required;
        }
    }

    private static long batches(long demand, long yield) {
        return demand / yield + (demand % yield == 0 ? 0 : 1);
    }

    private static boolean isSeed(RecipeIndex.Recipe recipe, RecipeIndex.Ingredient ingredient) {
        return ingredient.options().size() == 1 && ingredient.options().getFirst().equals(recipe.output());
    }

    private static long seedCount(RecipeIndex.Recipe recipe) {
        long count = 0;
        for (RecipeIndex.Ingredient ingredient : recipe.ingredients()) {
            if (isSeed(recipe, ingredient)) count = Math.addExact(count, ingredient.count());
        }
        if (count >= recipe.outputCount()) throw new IllegalArgumentException("Recipe has no net output: " + recipe.id());
        return count;
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
        return BASE_MATERIALS.contains(item) || item.endsWith("_log") || item.endsWith("_stem")
                || item.endsWith("_wool") || item.endsWith("_dye")
                || (item.endsWith("_ingot") && !item.equals("minecraft:netherite_ingot"));
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
