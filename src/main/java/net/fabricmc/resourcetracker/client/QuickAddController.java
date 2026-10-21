package net.fabricmc.resourcetracker.client;

import net.fabricmc.resourcetracker.client.gui.QuickAddScreen;
import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.mixin.ContainerScreenAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Opens a confirmation from a real hovered inventory slot without touching its contents. */
public final class QuickAddController {
    private static Screen preservedParent;
    private static KeyMapping key;

    private QuickAddController() {}

    public static void initialize() {
        if (key != null) return;
        key = VersionCompat.registerQuickAddKey();
        VersionCompat.registerQuickAddInput(key);
    }

    public static boolean tryOpen(Screen screen) {
        // Leave search/rename input to its text field, even when a slot remains hovered.
        if (screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || !TrackerConfig.hasActiveContext()
                || VersionCompat.currentScreen(client) != screen
                || !(screen instanceof AbstractContainerScreen<?> container)
                || !(screen instanceof ContainerScreenAccessor accessor)) return false;
        Slot slot = accessor.resourcetracker$getHoveredSlot();
        // A tooltip/recipe overlay is not an inventory slot. Also reject inactive/phantom slots.
        if (slot == null || !slot.isActive() || slot.container == null
                || !container.getMenu().slots.contains(slot)) return false;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty() || stack.getCount() < 1) return false;
        String itemId = VersionCompat.getItemId(stack.getItem());
        if (!VersionCompat.isValidItemId(itemId) || "minecraft:air".equals(itemId)) return false;
        QuickAddScreen confirmation = new QuickAddScreen(container, itemId, stack.getCount(),
                TrackerConfig.getActiveContextKey(), client.player.containerMenu);
        preservedParent = screen;
        try {
            VersionCompat.setScreen(client, confirmation);
        } finally {
            preservedParent = null;
        }
        while (key != null && key.consumeClick()) {
            // Do not let this same press act again after returning to the inventory.
        }
        return true;
    }

    /** Used only by the navigation mixin around the synchronous screen transition above. */
    public static boolean isPreservingParent(Screen previous) {
        return previous != null && previous == preservedParent;
    }
}
