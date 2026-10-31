package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.client.ResourceTrackerClient;
import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.config.TrackerHistory;
import net.fabricmc.resourcetracker.crafting.CraftingPlan;
import net.fabricmc.resourcetracker.crafting.CraftingRuntime;
import net.fabricmc.resourcetracker.crafting.RecipeIndex;
import net.fabricmc.resourcetracker.model.WoodType;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Decisions stay on the source list; ingredient controls belong only to the effective recipe. */
public final class RecipeOptionsScreen extends PagedDataScreen {
    private final TrackerConfig.TrackingList list;
    private final String itemId;
    private CraftingPlan.Result displayedPlan;
    private String query = "";
    private EditBox searchField;
    private record Row(Component title, Runnable action, Component tooltip) {
        private Row(Component title, Runnable action) { this(title, action, title); }
    }

    public RecipeOptionsScreen(Screen parent, TrackerConfig.TrackingList list, String itemId) {
        super(parent, Component.translatable("gui.resourcetracker.recipe.title"));
        this.list = list;
        this.itemId = itemId;
    }

    @Override protected TrackerConfig.TrackingList undoList() { return list; }

    @Override protected void init() {
        boolean searching = searchField != null && searchField.isFocused();
        int cursor = searchField == null ? query.length() : searchField.getCursorPosition();
        layout(itemId == null ? 58 : 54);
        displayedPlan = CraftingRuntime.plan(list);
        List<Row> rows = new ArrayList<>();
        if (itemId == null) {
            searchField = new EditBox(font, left, 32, contentWidth, 20, Component.translatable("gui.resourcetracker.recipe.search"));
            searchField.setHint(Component.translatable("gui.resourcetracker.recipe.search"));
            searchField.setMaxLength(120);
            searchField.setValue(query);
            searchField.setCursorPosition(Math.min(cursor, query.length()));
            searchField.setResponder(value -> {
                query = value; page = 0;
                if (minecraft != null) minecraft.execute(() -> { init(); setFocused(searchField); });
            });
            addRenderableWidget(searchField);
            if (searching) setFocused(searchField);
            String filter = query.trim().toLowerCase(Locale.ROOT);
            for (CraftingPlan.Option option : displayedPlan.options()) {
                String id = option.itemId();
                String name = itemName(id);
                if (!id.toLowerCase(Locale.ROOT).contains(filter) && !name.toLowerCase(Locale.ROOT).contains(filter)) continue;
                rows.add(new Row(Component.literal(name), () -> open(new RecipeOptionsScreen(this, list, id)), Component.literal(name + "\n" + id)));
            }
            if (rows.isEmpty()) addText(rows, Component.translatable("gui.resourcetracker.raw.empty"));
        } else {
            label(Component.literal(itemName(itemId)), left, 32, contentWidth);
            details(rows);
        }
        pagination(rows.size());
        for (int i = page * pageSize; i < Math.min(rows.size(), (page + 1) * pageSize); i++) {
            Row row = rows.get(i);
            int y = rowTop + (i % pageSize) * 24;
            if (row.action() == null) label(row.title(), left, y + 4, contentWidth);
            else button(row.title(), left, y, contentWidth, b -> row.action().run()).setTooltip(Tooltip.create(row.tooltip()));
        }
        int half = (contentWidth - 6) / 2;
        button(Component.translatable("gui.resourcetracker.undo"), left, height - 26, half,
                b -> { if (TrackerHistory.undo(list)) init(); }).active = TrackerHistory.canUndo(list);
        button(Component.translatable("gui.resourcetracker.done"), left + half + 6, height - 26, half, b -> onClose());
    }

    private void details(List<Row> rows) {
        CraftingPlan.Option option = displayedPlan.options().stream().filter(value -> itemId.equals(value.itemId())).findFirst().orElse(null);
        List<RecipeIndex.Recipe> recipes = CraftingRuntime.index().recipesFor(itemId);
        boolean stopped = option == null ? list.terminalItems.contains(itemId) : option.stopped();
        if (recipes.isEmpty()) {
            addText(rows, Component.translatable("gui.resourcetracker.recipe.no_supported"));
            return;
        }
        rows.add(new Row(Component.translatable("gui.resourcetracker.recipe.collect", collectName(stopped)), () ->
                open(new ChoiceScreen<>(this, Component.translatable("gui.resourcetracker.recipe.collect_title"), List.of(true, false), stopped,
                        RecipeOptionsScreen::collectName, ready -> {
                            if (ready == stopped) return;
                            TrackerHistory.record(list);
                            if (ready) list.terminalItems.add(itemId);
                            else {
                                list.terminalItems.remove(itemId);
                                if (option != null && option.defaultStopped() && recipes.stream().noneMatch(r -> r.id().equals(list.recipeChoices.get(itemId))))
                                    list.recipeChoices.put(itemId, recipes.getFirst().id());
                            }
                            changed();
                        }))));
        if (stopped) {
            addText(rows, Component.translatable("gui.resourcetracker.recipe.stopped"));
            return;
        }
        String effectiveId = option == null ? list.recipeChoices.get(itemId) : option.selectedRecipeId();
        RecipeIndex.Recipe selected = recipes.stream().filter(recipe -> recipe.id().equals(effectiveId)).findFirst().orElse(null);
        String explicitId = list.recipeChoices.get(itemId);
        RecipeIndex.Recipe explicit = recipes.stream().filter(recipe -> recipe.id().equals(explicitId)).findFirst().orElse(null);
        Component selectedName = explicit == null ? Component.translatable("gui.resourcetracker.recipe.automatic") : recipeDescription(explicit);
        rows.add(new Row(Component.translatable("gui.resourcetracker.recipe.select", selectedName), () -> {
            List<RecipeIndex.Recipe> choices = new ArrayList<>();
            choices.add(null); choices.addAll(recipes);
            open(new ChoiceScreen<>(this, Component.translatable("gui.resourcetracker.recipe.title"), choices, explicit,
                    recipe -> recipe == null ? Component.translatable("gui.resourcetracker.recipe.automatic") : recipeDescription(recipe), recipe -> {
                        TrackerHistory.record(list);
                        if (recipe == null) list.recipeChoices.remove(itemId); else list.recipeChoices.put(itemId, recipe.id());
                        list.terminalItems.remove(itemId);
                        changed();
                    }));
        }, selectedName));
        if (selected == null) {
            addText(rows, Component.translatable("gui.resourcetracker.raw.loading"));
            return;
        }
        List<Row> summaryRows = new ArrayList<>();
        addText(summaryRows, Component.translatable("gui.resourcetracker.recipe.effective", recipeDescription(selected)));
        addText(summaryRows, Component.translatable("gui.resourcetracker.recipe.source", Component.translatable(explicit != null
                ? "gui.resourcetracker.recipe.source_exact" : "gui.resourcetracker.recipe.automatic")));
        for (int slot = 0; slot < selected.ingredients().size(); slot++) {
            RecipeIndex.Ingredient ingredient = selected.ingredients().get(slot);
            if (ingredient.options().size() <= 1) continue;
            String chosen = list.ingredientChoices.get(ingredient.key());
            if (chosen != null && !ingredient.options().contains(chosen)) chosen = null;
            String current = chosen;
            int number = slot + 1;
            Component value = current == null ? Component.translatable("gui.resourcetracker.recipe.general") : Component.literal(itemName(current));
            rows.add(new Row(Component.translatable("gui.resourcetracker.recipe.ingredient", number, value), () -> {
                List<String> choices = new ArrayList<>(); choices.add(null);
                choices.addAll(ingredient.options().stream().filter(VersionCompat::isValidItemId).toList());
                open(new ChoiceScreen<>(this, Component.translatable("gui.resourcetracker.recipe.ingredient_title", number), choices, current,
                        id -> id == null ? Component.translatable("gui.resourcetracker.recipe.general") : Component.literal(itemName(id)), id -> {
                            TrackerHistory.record(list);
                            if (id == null) list.ingredientChoices.remove(ingredient.key());
                            else {
                                list.recipeChoices.put(itemId, selected.id());
                                list.ingredientChoices.put(ingredient.key(), id);
                            }
                            list.terminalItems.remove(itemId);
                            changed();
                        }));
            }));
            addText(summaryRows, Component.translatable("gui.resourcetracker.recipe.ingredient_effective", ingredientSummary(ingredient), ingredient.count()));
            addText(summaryRows, Component.translatable("gui.resourcetracker.recipe.source", ingredientSource(ingredient)));
        }
        rows.addAll(summaryRows);
    }

    private static Component collectName(boolean ready) {
        return Component.translatable(ready ? "gui.resourcetracker.recipe.collect_ready" : "gui.resourcetracker.recipe.collect_ingredients");
    }
    private void addText(List<Row> rows, Component text) {
        for (String line : RawTextScreen.wrap(font, text.getString(), contentWidth)) rows.add(new Row(Component.literal(line), null));
    }
    private void open(Screen screen) { if (minecraft != null) VersionCompat.setScreen(minecraft, screen); }
    private void changed() { ResourceTrackerClient.invalidateTargetItemCache(); init(); }
    private Component ingredientSource(RecipeIndex.Ingredient ingredient) {
        String explicit = list.ingredientChoices.get(ingredient.key());
        if (explicit != null && ingredient.options().contains(explicit)) return Component.translatable("gui.resourcetracker.recipe.source_exact");
        if (ingredient.options().size() > 1 && list.woodType != null && list.woodType != WoodType.AUTO
                && ingredient.options().stream().anyMatch(list.woodType::matches))
            return Component.translatable("gui.resourcetracker.recipe.source_wood");
        return Component.translatable("gui.resourcetracker.recipe.automatic");
    }
    private Component ingredientSummary(RecipeIndex.Ingredient ingredient) {
        String explicit = list.ingredientChoices.get(ingredient.key());
        if (explicit != null && ingredient.options().contains(explicit)) return Component.literal(itemName(explicit));
        List<String> options = ingredient.options();
        if (options.size() > 1 && list.woodType != null && list.woodType != WoodType.AUTO) {
            List<String> preferred = options.stream().filter(list.woodType::matches).toList();
            if (!preferred.isEmpty()) options = preferred;
        }
        return options.size() == 1 ? Component.literal(itemName(options.getFirst()))
                : Component.translatable("gui.resourcetracker.recipe.compatible_ingredient", options.size());
    }
    private Component recipeDescription(RecipeIndex.Recipe recipe) {
        Map<String, Long> ingredients = new LinkedHashMap<>();
        for (RecipeIndex.Ingredient ingredient : recipe.ingredients())
            ingredients.merge(ingredientSummary(ingredient).getString(), ingredient.count(), Long::sum);
        String inputs = ingredients.entrySet().stream().map(entry -> entry.getKey() + " × " + entry.getValue())
                .collect(java.util.stream.Collectors.joining(", "));
        String method = switch (recipe.kind()) {
            case "crafting_shaped", "crafting_shapeless", "crafting_transmute" -> "crafting";
            default -> recipe.kind();
        };
        return Component.translatable("gui.resourcetracker.recipe.summary", Component.translatable("gui.resourcetracker.recipe.method." + method),
                inputs, itemName(recipe.output()), recipe.outputCount());
    }
    private String itemName(String id) { return new TrackerConfig.TrackedItem(id, 1).getDisplayName(); }
    @Override public void tick() { super.tick(); if (displayedPlan != CraftingRuntime.plan(list)) init(); }
}
