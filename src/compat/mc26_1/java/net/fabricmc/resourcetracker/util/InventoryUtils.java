package net.fabricmc.resourcetracker.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class InventoryUtils {
    public static int countItems(Player player, Item targetItem) {
        if (player == null || targetItem == null) return 0;
        return countItems(player, Set.of(targetItem)).getOrDefault(targetItem, 0);
    }

    public static Map<Item, Integer> countItems(Player player, Set<Item> targetItems) {
        Map<Item, Integer> counts = new HashMap<>();
        if (player == null || targetItems == null || targetItems.isEmpty()) return counts;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                addRecursiveCounts(stack, targetItems, counts);
            }
        }
        return counts;
    }

    private static void addRecursiveCounts(ItemStack stack, Set<Item> targetItems, Map<Item, Integer> counts) {
        Item item = stack.getItem();
        if (targetItems.contains(item)) {
            counts.merge(item, stack.getCount(), Integer::sum);
        }

        ItemContainerContents containerData = stack.get(DataComponents.CONTAINER);
        if (containerData != null) {
            for (ItemStackTemplate template : containerData.nonEmptyItems()) {
                addRecursiveCounts(template.create(), targetItems, counts);
            }
        }

        BundleContents bundleData = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundleData != null) {
            for (ItemStackTemplate template : bundleData.items()) {
                addRecursiveCounts(template.create(), targetItems, counts);
            }
        }
    }
}
