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
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.resourcetracker.client.gui.MainScreen;
import net.fabricmc.resourcetracker.client.render.HudOverlay;
import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.util.InventoryUtils;
import net.minecraft.client.option.KeyBinding;

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

    /**
     * Key binding to toggle global HUD visibility (all tracking lists at once).
     * No default key — configurable in MC Controls settings.
     */
    public static KeyBinding toggleHudKey;

    @Override
    public void onInitializeClient() {
        TrackerConfig.load();

        // Register keybindings — via VersionCompat for cross-version support
        openMenuKey = VersionCompat.registerOpenKey();
        toggleHudKey = VersionCompat.registerToggleHudKey();

        // Register the client tick event to handle input and update inventory counts
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Check for menu key press
            while (openMenuKey.wasPressed()) {
                client.setScreen(new MainScreen(null));
            }

            // Toggle global HUD visibility
            while (toggleHudKey.wasPressed()) {
                TrackerConfig.INSTANCE.hudVisible = !TrackerConfig.INSTANCE.hudVisible;
                TrackerConfig.save();
            }

            if (client.player != null && client.world != null && client.player.age % 10 == 0) {
                for (TrackerConfig.TrackingList list : TrackerConfig.INSTANCE.lists) {
                    if (!list.isVisible) continue;
                    for (TrackerConfig.TrackedItem trackedItem : list.items) {
                        if (trackedItem.isValid()) {
                            trackedItem.cachedCount = InventoryUtils.countItems(client.player, trackedItem.getItem());
                        } else {
                            trackedItem.cachedCount = 0;
                        }
                    }
                }
            }
        });

        // Register the HUD renderer
        HudRenderCallback.EVENT.register(new HudOverlay());
    }
}