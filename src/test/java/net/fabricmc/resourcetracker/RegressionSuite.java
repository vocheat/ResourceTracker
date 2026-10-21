package net.fabricmc.resourcetracker;

import net.fabricmc.resourcetracker.config.ListMetadata;
import net.fabricmc.resourcetracker.crafting.CraftingRegression;
import net.fabricmc.resourcetracker.importing.ImportRegressionTest;
import net.fabricmc.resourcetracker.model.CountFormatter;
import net.fabricmc.resourcetracker.model.HudSelection;
import net.fabricmc.resourcetracker.model.ResourceState;

import java.util.List;

/** Dependency-free regression entry point, run by the Gradle test lifecycle. */
public final class RegressionSuite {
    public static void main(String[] args) throws Exception {
        net.fabricmc.resourcetracker.config.TrackerHistoryRegression.main(new String[0]);
        net.fabricmc.resourcetracker.model.CompletionRegression.main(new String[0]);
        completion();
        units();
        hud();
        metadata();
        CraftingRegression.main(new String[0]);
        ImportRegressionTest.main(new String[]{"--minecraft"});
        System.out.println("ResourceTracker regression suite passed");
    }

    private static void completion() {
        ResourceState state = new ResourceState("minecraft:stone", 64);
        check(!state.isCompleted(), "uncollected target");
        state.toggleCollected();
        check(state.isCompleted() && state.cachedCount == 0 && state.remaining() == 0, "manual intent is not inventory");
        state.setTargetCount(32);
        check(state.isManuallyCollected(), "decreasing target retains annotation");
        state.setTargetCount(48);
        check(!state.isManuallyCollected(), "any subsequent target increase removes annotation");
        state.toggleCollected();
        state.setTargetCount(65);
        check(!state.isManuallyCollected() && state.manuallyCollectedTarget == 0, "increasing target removes annotation");
        state.cachedCount = 65;
        check(state.isCompleted(), "observed inventory completion");
    }

    private static void units() {
        CountFormatter.Labels labels = new CountFormatter.Labels("i", "s", "b");
        check(CountFormatter.format(1729, 64, true, CountFormatter.Unit.SHULKERS, labels).equals("1 b 1 i"), "shulker remainder");
        check(CountFormatter.format(17, 16, true, CountFormatter.Unit.STACKS, labels).equals("1 s 1 i"), "16-stack item");
        check(CountFormatter.format(28, 1, true, CountFormatter.Unit.SHULKERS, labels).equals("1 b 1 i"), "unstackable capacity");
        check(CountFormatter.format(27, 1, false, CountFormatter.Unit.SHULKERS, labels).equals("27 i"), "shulkers cannot nest");
        check(CountFormatter.format(Integer.MAX_VALUE, 64, true, CountFormatter.Unit.ITEMS, labels).equals("2147483647"), "maximum count");
    }

    private static void hud() {
        ResourceState first = new ResourceState("first", 64);
        ResourceState second = new ResourceState("second", 128);
        ResourceState done = new ResourceState("done", 1);
        first.pinned = true;
        done.toggleCollected();
        var selected = HudSelection.select(List.of(second, done, first), true, true, 1);
        check(selected.rows().equals(List.of(first)) && selected.hiddenByLimit() == 1, "pin precedes deficit and cap");
        check(selected.completed() == 1 && selected.total() == 3, "summary includes filtered rows");
        check(HudSelection.select(List.of(done), true, false, 0).rows().isEmpty(), "all complete");
        check(HudSelection.select(List.of(), true, true, 0).total() == 0, "empty list");
    }

    private static void metadata() {
        ListMetadata state = new ListMetadata();
        state.put("item-state", "0", "minecraft:stone|64|true");
        state.put("raw-state", "minecraft:oak_log", "12|false");
        String text = state.encode() + "\n[items]\nminecraft:stone=64\n";
        check(text.indexOf("[item-state]") < text.indexOf("[items]"), "legacy metadata placement");
        ListMetadata decoded = ListMetadata.decode(text);
        check(decoded.section("raw-state").get("minecraft:oak_log").equals("12|false"), "raw state round trip");
        ResourceState loaded = new ResourceState("minecraft:stone", 64);
        decoded.applyItemStates(List.of(loaded));
        check(loaded.isManuallyCollected() && loaded.pinned, "saved row annotation round trip");
        ResourceState edited = new ResourceState("minecraft:oak_log", 64);
        decoded.applyItemStates(List.of(edited));
        check(!edited.isManuallyCollected() && !edited.pinned, "external text edit does not assign another item's state");
        check(ListMetadata.decode("# old list\nname=Legacy\n[items]\nminecraft:stone=64\n").sections.isEmpty(), "legacy TXT needs no metadata");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
