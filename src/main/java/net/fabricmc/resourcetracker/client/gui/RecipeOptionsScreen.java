package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.compat.VersionCompat;

import net.fabricmc.resourcetracker.client.ResourceTrackerClient;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.config.TrackerHistory;
import net.fabricmc.resourcetracker.crafting.CraftingRuntime;
import net.fabricmc.resourcetracker.crafting.CraftingPlan;
import net.fabricmc.resourcetracker.crafting.RecipeIndex;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Recipe and ingredient decisions are stored on the source list. */
public final class RecipeOptionsScreen extends PagedDataScreen {
    private final TrackerConfig.TrackingList list;
    private final String itemId;
    private CraftingPlan.Result displayedPlan;
    private record Row(Component title, Runnable action, Component tooltip) {
        private Row(Component title, Runnable action) { this(title, action, null); }
    }

    public RecipeOptionsScreen(Screen parent, TrackerConfig.TrackingList list, String itemId) {
        super(parent, Component.translatable("gui.resourcetracker.recipe.title"));
        this.list = list;
        this.itemId = itemId;
    }

    @Override
    protected void init() {
        layout(82);
        displayedPlan = CraftingRuntime.plan(list);
        label(Component.translatable("gui.resourcetracker.edit.raw_note"), left, 32, contentWidth);
        List<Row> rows = new ArrayList<>();
        if (itemId == null) {
            var plan = displayedPlan;
            for (var option : plan.options()) {
                String id = option.itemId();
                rows.add(new Row(Component.literal(new TrackerConfig.TrackedItem(id, 1).getDisplayName()),
                        () -> { if (minecraft != null) VersionCompat.setScreen(minecraft, new RecipeOptionsScreen(this, list, id)); }));
            }
            for (String diagnostic : plan.diagnostics()) rows.add(new Row(DiagnosticText.of(diagnostic), null));
        } else {
            label(Component.literal(new TrackerConfig.TrackedItem(itemId, 1).getDisplayName()), left, 58, contentWidth);
            CraftingPlan.Option itemOption = displayedPlan.options().stream()
                    .filter(option -> itemId.equals(option.itemId())).findFirst().orElse(null);
            List<RecipeIndex.Recipe> recipes = CraftingRuntime.index().recipesFor(itemId);
            if (!recipes.isEmpty() && itemOption != null) {
                boolean stopped = itemOption.stopped();
                rows.add(new Row(Component.translatable(stopped ? "gui.resourcetracker.recipe.resume" : "gui.resourcetracker.recipe.stop"), () -> {
                    TrackerHistory.record(list);
                    if (stopped) {
                        list.terminalItems.remove(itemId);
                        if (itemOption.defaultStopped() && !list.recipeChoices.containsKey(itemId)) {
                            list.recipeChoices.put(itemId, itemOption.recipes().getFirst().id());
                        }
                    } else {
                        list.terminalItems.add(itemId);
                    }
                    changed();
                }));
            }
            rows.add(new Row(Component.translatable("gui.resourcetracker.recipe.automatic"), () -> {
                TrackerHistory.record(list);
                list.recipeChoices.remove(itemId);
                list.terminalItems.remove(itemId);
                changed();
            }));
            String selectedRecipe = displayedPlan.options().stream()
                    .filter(option -> itemId.equals(option.itemId())).map(option -> option.selectedRecipeId())
                    .filter(java.util.Objects::nonNull).findFirst().orElse(list.recipeChoices.get(itemId));
            for (RecipeIndex.Recipe recipe : recipes) {
                boolean selected = recipe.id().equals(selectedRecipe);
                Component description = recipeDescription(recipe);
                Component recipeTitle = Component.literal(selected ? "✓ " : "").append(description);
                rows.add(new Row(recipeTitle, () -> {
                    TrackerHistory.record(list);
                    list.recipeChoices.put(itemId, recipe.id());
                    list.terminalItems.remove(itemId);
                    changed();
                }, description.copy().append("\n").append(recipe.id())));
                for (RecipeIndex.Ingredient ingredient : recipe.ingredients()) {
                    if (ingredient.options().size() <= 1) continue;
                    String selectedIngredient = list.ingredientChoices.get(ingredient.key());
                    for (String alternative : ingredient.options()) {
                        String name = new TrackerConfig.TrackedItem(alternative, 1).getDisplayName();
                        rows.add(new Row(Component.literal("  " + (alternative.equals(selectedIngredient) ? "✓ " : "") + name + " × " + ingredient.count()), () -> {
                            TrackerHistory.record(list);
                            list.recipeChoices.put(itemId, recipe.id());
                            list.ingredientChoices.put(ingredient.key(), alternative);
                            list.terminalItems.remove(itemId);
                            changed();
                        }));
                    }
                }
            }
        }
        pagination(rows.size());
        for (int i = page * pageSize; i < Math.min(rows.size(), (page + 1) * pageSize); i++) {
            Row row = rows.get(i);
            int y = rowTop + (i % pageSize) * 24;
            if (row.action() == null) label(row.title(), left, y + 4, contentWidth);
            else {
                var button = button(row.title(), left, y, contentWidth, b -> row.action().run());
                if (row.tooltip() != null) button.setTooltip(Tooltip.create(row.tooltip()));
            }
        }
        int footerWidth = Math.max(1, (contentWidth - 6) / 2);
        button(Component.translatable("gui.resourcetracker.undo"), left, height - 26, footerWidth,
                b -> { if (TrackerHistory.undo(list)) init(); }).active = TrackerHistory.canUndo(list);
        button(Component.translatable("gui.resourcetracker.done"), left + footerWidth + 6, height - 26, footerWidth, b -> onClose());
    }

    private void changed() {
        CraftingRuntime.invalidate();
        ResourceTrackerClient.invalidateTargetItemCache();
        init();
    }

    private Component recipeDescription(RecipeIndex.Recipe recipe) {
        Map<String, Long> ingredients = new LinkedHashMap<>();
        for (RecipeIndex.Ingredient ingredient : recipe.ingredients()) {
            String selected = list.ingredientChoices.get(ingredient.key());
            String name;
            if (selected != null && ingredient.options().contains(selected)) {
                name = itemName(selected);
            } else if (ingredient.options().size() == 1) {
                name = itemName(ingredient.options().get(0));
            } else {
                name = Component.translatable("gui.resourcetracker.recipe.compatible_ingredient", ingredient.options().size()).getString();
            }
            ingredients.merge(name, ingredient.count(), Long::sum);
        }
        String inputs = ingredients.entrySet().stream().map(entry -> entry.getKey() + " × " + entry.getValue())
                .collect(java.util.stream.Collectors.joining(", "));
        String methodKey = switch (recipe.kind()) {
            case "crafting_shaped", "crafting_shapeless", "crafting_transmute" -> "crafting";
            default -> recipe.kind();
        };
        Component method = Component.translatable("gui.resourcetracker.recipe.method." + methodKey);
        return Component.translatable("gui.resourcetracker.recipe.summary", method, inputs, itemName(recipe.output()), recipe.outputCount());
    }

    private String itemName(String id) {
        return new TrackerConfig.TrackedItem(id, 1).getDisplayName();
    }

    @Override
    public void tick() {
        super.tick();
        if (displayedPlan != CraftingRuntime.plan(list)) init();
    }
}
