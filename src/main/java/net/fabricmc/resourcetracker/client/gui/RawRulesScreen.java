package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.config.TrackerHistory;
import net.fabricmc.resourcetracker.crafting.CraftingRuntime;
import net.fabricmc.resourcetracker.crafting.RecipeIndex;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** The three list-wide planner policies, kept separate from the derived material table. */
final class RawRulesScreen extends PagedDataScreen {
    private static final String STONE = "minecraft:stone";
    private final TrackerConfig.TrackingList list;
    RawRulesScreen(Screen parent, TrackerConfig.TrackingList list) {
        super(parent, Component.translatable("gui.resourcetracker.raw.rules")); this.list = list;
    }
    @Override protected TrackerConfig.TrackingList undoList() { return list; }

    @Override protected void init() {
        layout(34);
        label(Component.translatable("gui.resourcetracker.raw.smelting_label"), left, 34, contentWidth - 58)
                .setTooltip(Tooltip.create(Component.translatable("gui.resourcetracker.raw.smelting_hint")));
        button(RawMaterialsScreen.state(list.includeSmelting), left + contentWidth - 54, 32, 54, b -> {
            TrackerHistory.record(list); list.includeSmelting = !list.includeSmelting; RawMaterialsScreen.changed(); init();
        }).setTooltip(Tooltip.create(Component.translatable("gui.resourcetracker.raw.smelting_hint")));
        label(Component.translatable("gui.resourcetracker.raw.wood_label"), left, 62, contentWidth);
        button(WoodChoiceScreen.name(list.woodType), left, 78, contentWidth,
                b -> { if (minecraft != null) VersionCompat.setScreen(minecraft, new WoodChoiceScreen(this, list)); })
                .setTooltip(Tooltip.create(Component.translatable("gui.resourcetracker.wood.hint")));
        label(Component.translatable("gui.resourcetracker.raw.stone_label"), left, 108, contentWidth);
        var stone = button(Component.translatable(stoneExpanded(list) ? "gui.resourcetracker.raw.stone_smelt" : "gui.resourcetracker.raw.stone_ready"),
                left, 124, contentWidth, b -> {
                    if (minecraft != null) VersionCompat.setScreen(minecraft, new ChoiceScreen<>(this,
                            Component.translatable("gui.resourcetracker.raw.stone_label"), List.of(false, true), stoneExpanded(list),
                            expanded -> Component.translatable(expanded ? "gui.resourcetracker.raw.stone_smelt" : "gui.resourcetracker.raw.stone_ready"), expanded -> {
                                RecipeIndex.Recipe recipe = stoneRecipe();
                                if (expanded && recipe == null) return;
                                TrackerHistory.record(list);
                                if (!expanded) list.terminalItems.add(STONE);
                                else { list.terminalItems.remove(STONE); list.recipeChoices.put(STONE, recipe.id()); }
                                RawMaterialsScreen.changed();
                            }));
                });
        stone.active = stoneRecipe() != null;
        stone.setTooltip(Tooltip.create(Component.translatable(stone.active ? "gui.resourcetracker.raw.stone_hint" : "gui.resourcetracker.raw.stone_unavailable")));
        if (!stone.active) wrappedLabel(Component.translatable("gui.resourcetracker.raw.stone_unavailable"), left, 150, contentWidth, UiTheme.MUTED);
        int half = (contentWidth - 6) / 2;
        button(Component.translatable("gui.resourcetracker.undo"), left, height - 26, half,
                b -> { if (TrackerHistory.undo(list)) init(); }).active = TrackerHistory.canUndo(list);
        button(Component.translatable("gui.resourcetracker.raw.back"), left + half + 6, height - 26, half, b -> onClose());
    }
    private static RecipeIndex.Recipe stoneRecipe() {
        return CraftingRuntime.index().recipesFor(STONE).stream()
                .filter(recipe -> (recipe.kind().equals("smelting") || recipe.kind().equals("blasting"))
                        && recipe.ingredients().stream().anyMatch(input -> input.options().contains("minecraft:cobblestone")))
                .findFirst().orElse(null);
    }
    static boolean stoneExpanded(TrackerConfig.TrackingList list) {
        return !list.terminalItems.contains(STONE) && (list.includeSmelting || CraftingRuntime.index().recipesFor(STONE).stream()
                .anyMatch(recipe -> recipe.id().equals(list.recipeChoices.get(STONE))));
    }
}
