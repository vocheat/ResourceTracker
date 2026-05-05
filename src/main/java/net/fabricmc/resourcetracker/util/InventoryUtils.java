/*
 * MIT License
 *
 * Copyright (c) 2026 vocheat
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.resourcetracker.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for analyzing and counting items within a player's inventory.
 * <p>
 * This class provides methods to traverse complex inventory structures, including
 * nested containers like Shulker Boxes and Bundles, relying on DataComponent types.
 * </p>
 *
 * @author vocheat
 */
public class InventoryUtils {

    /**
     * Counts the total number of a specific item in the player's inventory.
     * <p>
     * This method iterates through the main inventory and recursively checks inside
     * container items (e.g., Shulker Boxes) and Bundles.
     * </p>
     *
     * @param player     The player entity whose inventory will be scanned.
     * @param targetItem The specific Item type to count.
     * @return The total count of the item found, including nested items. Returns 0 if player or item is null.
     */
    public static int countItems(Player player, Item targetItem) {
        if (player == null || targetItem == null) return 0;
        return countItems(player, Set.of(targetItem)).getOrDefault(targetItem, 0);
    }

    /**
     * Counts all target items with one inventory traversal.
     */
    public static Map<Item, Integer> countItems(Player player, Set<Item> targetItems) {
        Map<Item, Integer> counts = new HashMap<>();
        if (player == null || targetItems == null || targetItems.isEmpty()) return counts;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            addRecursiveCounts(stack, targetItems, counts);
        }
        return counts;
    }

    /**
     * Recursively calculates the count of a specific item within a single ItemStack.
     * <p>
     * Handles three cases:
     * 1. The stack itself matches the target item.
     * 2. The stack is a container (e.g., Shulker Box) holding the target item.
     * 3. The stack is a Bundle holding the target item.
     * </p>
     *
     * @param stack      The ItemStack to inspect.
     * @param targetItem The item type to look for.
     * @return The count of the target item found within this stack hierarchy.
     */
    private static void addRecursiveCounts(ItemStack stack, Set<Item> targetItems, Map<Item, Integer> counts) {
        Item item = stack.getItem();
        if (targetItems.contains(item)) {
            counts.merge(item, stack.getCount(), Integer::sum);
        }

        ItemContainerContents containerData = stack.get(DataComponents.CONTAINER);
        if (containerData != null) {
            for (ItemStack subStack : containerData.nonEmptyItems()) {
                addRecursiveCounts(subStack, targetItems, counts);
            }
        }

        BundleContents bundleData = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundleData != null) {
            for (ItemStack subStack : bundleData.items()) {
                addRecursiveCounts(subStack, targetItems, counts);
            }
        }
    }
}
