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

package net.fabricmc.resourcetracker.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.resourcetracker.client.gui.MainScreen;
import net.fabricmc.resourcetracker.client.render.HudOverlay;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.util.InventoryUtils;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * The main client-side entry point for the Resource Tracker mod.
 * <p>
 * This class handles initialization of client-specific features such as:
 * <ul>
 * <li>Loading configuration.</li>
 * <li>Registering keybindings.</li>
 * <li>Handling client tick events (input and data caching).</li>
 * <li>Registering the HUD overlay.</li>
 * </ul>
 * </p>
 *
 * @author vocheat
 */
public class ResourceTrackerClient implements ClientModInitializer {

    /**
     * Key binding to open the main configuration GUI.
     * Default key: M.
     */
    public static KeyBinding openMenuKey;

    @Override
    public void onInitializeClient() {
        TrackerConfig.load();

        // Register the keybinding (Default: M)
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.resourcetracker.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                KeyBinding.Category.MISC
        ));

        // Register the client tick event to handle input and update inventory counts
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Check for menu key press
            while (openMenuKey.wasPressed()) {
                client.setScreen(new MainScreen(null));
            }

            // --- OPTIMIZATION: Update cache every 10 ticks (0.5 seconds) ---
            // Instead of scanning the inventory every single frame (which is expensive),
            // we update the item counts periodically.
            if (client.player != null && client.world != null && client.player.age % 10 == 0) {
                for (TrackerConfig.TrackingList list : TrackerConfig.INSTANCE.lists) {
                    if (!list.isVisible) continue;
                    for (TrackerConfig.TrackedItem trackedItem : list.items) {
                        Item item = Registries.ITEM.get(Identifier.of(trackedItem.itemId));
                        if (item != null) {
                            // Helper method handles Shulker Boxes and Bundles recursively
                            trackedItem.cachedCount = InventoryUtils.countItems(client.player, item);
                        } else {
                            trackedItem.cachedCount = 0;
                        }
                    }
                }
            }
            // ---------------------------------------------------------------
        });

        // Register the HUD renderer
        HudRenderCallback.EVENT.register(new HudOverlay());
    }
}