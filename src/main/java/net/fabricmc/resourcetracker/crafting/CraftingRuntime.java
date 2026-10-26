package net.fabricmc.resourcetracker.crafting;

import net.fabricmc.resourcetracker.client.render.HudRenderCache;
import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.model.InventorySnapshot;
import net.fabricmc.resourcetracker.util.InventoryUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Client-thread integration. Neither render code nor screen previews read inventory. */
public final class CraftingRuntime {
    private static CompletableFuture<RecipeIndex> loading;
    private static final RecipeIndex EMPTY_INDEX = RecipeIndex.fromJson(Map.of());
    private static final Map<TrackerConfig.TrackingList, Cached> PLANS = new IdentityHashMap<>();
    private static InventorySnapshot previous;
    private static boolean invalidated = true;

    private CraftingRuntime() { }

    public static void initialize() {
        if (loading == null) loading = CompletableFuture.supplyAsync(RecipeIndex::loadVanilla);
    }

    public static RecipeIndex index() {
        initialize();
        return loading.isDone() ? loading.join() : EMPTY_INDEX;
    }

    public static void invalidate() {
        invalidated = true;
        HudRenderCache.clear();
    }

    public static boolean needsUpdate() {
        return invalidated;
    }

    public static void reset() {
        previous = null;
        PLANS.clear();
        invalidate();
    }

    public static CraftingPlan.Result plan(TrackerConfig.TrackingList list) {
        Cached cached = PLANS.get(list);
        return cached == null ? unavailable("gui.resourcetracker.raw.loading") : cached.result;
    }

    public static void update(Player player) {
        if (TrackerConfig.INSTANCE.lists.isEmpty()) {
            previous = null;
            PLANS.clear();
            invalidated = false;
            return;
        }
        initialize();
        Map<String, Long> stock = new HashMap<>();
        InventoryUtils.countAllItems(player).forEach((item, count) ->
                stock.put(VersionCompat.getItemId(item), (long) count));
        InventorySnapshot snapshot = new InventorySnapshot(stock);
        boolean inventoryChanged = !snapshot.equals(previous);
        Set<String> changedItems = inventoryChanged
                ? changedItems(previous == null ? Map.of() : previous.counts(), stock) : Set.of();
        previous = snapshot;
        PLANS.keySet().removeIf(list -> !TrackerConfig.INSTANCE.lists.contains(list));
        boolean ready = loading.isDone();
        RecipeIndex recipes = ready ? loading.join() : EMPTY_INDEX;
        boolean changed = invalidated;
        for (TrackerConfig.TrackingList list : TrackerConfig.INSTANCE.lists) {
            for (TrackerConfig.TrackedItem item : list.items) {
                int count = (int) Math.min(Integer.MAX_VALUE, stock.getOrDefault(item.itemId, 0L));
                if (item.cachedCount != count) changed = true;
                item.cachedCount = count;
                item.validateCollected();
            }
            String signature = signature(list);
            Cached old = PLANS.get(list);
            boolean sameSource = old != null && old.signature.equals(signature) && old.ready == ready;
            if (!invalidated && sameSource && Collections.disjoint(changedItems, old.stockDependencies)) continue;
            list.captureRawState();
            Map<String, Long> goals = new LinkedHashMap<>();
            for (TrackerConfig.TrackedItem item : list.items) {
                if (!item.isValid() || item.isManuallyCollected()) continue;
                goals.merge(item.itemId, (long) item.targetCount, Math::addExact);
            }
            CraftingPlan.Result result = ready
                    ? CraftingPlan.calculate(recipes, goals, Set.of(), stock, list.recipeChoices,
                            list.ingredientChoices, list.terminalItems)
                    : unavailable("gui.resourcetracker.raw.loading");
            List<TrackerConfig.TrackedItem> raw = new ArrayList<>();
            List<String> diagnostics = new ArrayList<>(result.diagnostics());
            boolean valid = result.valid();
            if (valid) {
                for (CraftingPlan.Leaf leaf : result.leaves().values()) {
                    if (leaf.required() > Integer.MAX_VALUE || !VersionCompat.isValidItemId(leaf.itemId())) {
                        diagnostics.add(Component.translatable("gui.resourcetracker.raw.unsupported", leaf.itemId()).getString());
                        valid = false;
                        break;
                    }
                    TrackerConfig.TrackedItem item = new TrackerConfig.TrackedItem(leaf.itemId(), (int) leaf.required());
                    item.cachedCount = (int) Math.min(Integer.MAX_VALUE, leaf.available());
                    TrackerConfig.RawState state = list.rawState.get(leaf.itemId());
                    if (state != null) {
                        item.manuallyCollectedTarget = state.collectedTarget;
                        item.pinned = state.pinned;
                        item.validateCollected();
                    }
                    raw.add(item);
                }
            }
            if (!valid) raw.clear();
            list.rawItems = raw;
            list.rawDiagnostics = diagnostics;
            list.rawPlanValid = valid;
            if (valid) {
                Set<String> present = new HashSet<>();
                raw.forEach(item -> present.add(item.itemId));
                list.rawState.keySet().removeIf(id -> !present.contains(id));
                list.captureRawState();
            }
            if (!valid) result = new CraftingPlan.Result(Map.of(), result.options(), diagnostics,
                    result.leftovers(), false);
            Set<String> dependencies = sameSource ? old.stockDependencies
                    : stockDependencies(recipes, list.items.stream().map(item -> item.itemId).toList());
            PLANS.put(list, new Cached(signature(list), result, ready, dependencies));
            changed = true;
        }
        invalidated = false;
        if (changed) HudRenderCache.clear();
    }

    private static String signature(TrackerConfig.TrackingList list) {
        StringBuilder result = new StringBuilder();
        for (TrackerConfig.TrackedItem item : list.items) result.append(item.itemId).append('=')
                .append(item.targetCount).append(':').append(item.manuallyCollectedTarget).append(';');
        return result.append(list.recipeChoices).append(list.ingredientChoices)
                .append(list.terminalItems).toString();
    }

    private static CraftingPlan.Result unavailable(String key) {
        return new CraftingPlan.Result(Map.of(), List.of(),
                List.of(Component.translatable(key).getString()), Map.of(), false);
    }

    static Set<String> changedItems(Map<String, Long> before, Map<String, Long> after) {
        Set<String> changed = new HashSet<>(before.keySet());
        changed.addAll(after.keySet());
        changed.removeIf(id -> before.getOrDefault(id, 0L).equals(after.getOrDefault(id, 0L)));
        return changed;
    }

    /** Include every route, since on-hand alternatives can change the planner's automatic choices. */
    static Set<String> stockDependencies(RecipeIndex index, List<String> goals) {
        Set<String> dependencies = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>(goals);
        while (!pending.isEmpty()) {
            String item = pending.removeFirst();
            if (!dependencies.add(item)) continue;
            for (RecipeIndex.Recipe recipe : index.recipesFor(item)) {
                for (RecipeIndex.Ingredient ingredient : recipe.ingredients()) pending.addAll(ingredient.options());
            }
        }
        return Set.copyOf(dependencies);
    }

    private record Cached(String signature, CraftingPlan.Result result, boolean ready,
                          Set<String> stockDependencies) { }
}
