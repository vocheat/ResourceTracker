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
import net.fabricmc.resourcetracker.client.gui.MainScreen;
import net.fabricmc.resourcetracker.compat.HudCompat;
import net.fabricmc.resourcetracker.client.render.HudOverlay;
import net.fabricmc.resourcetracker.client.render.HudRenderCache;
import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.crafting.CraftingRuntime;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

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
    public static KeyMapping openMenuKey;

    /**
     * Key binding to toggle global HUD visibility (all tracking lists at once).
     * No default key — configurable in MC Controls settings.
     */
    public static KeyMapping toggleHudKey;
    private static int inventoryUpdateTicks;
    private static TrackerConfig.ActiveContext lastContext = TrackerConfig.ActiveContext.none();

    /**
     * Discards clicks already handled directly by an open screen.
     */
    public static void discardHandledOpenMenuKeyClicks() {
        while (openMenuKey != null && openMenuKey.consumeClick()) {
            // Drain the click queue so END_CLIENT_TICK does not reopen the screen.
        }
    }

    @Override
    public void onInitializeClient() {
        TrackerConfig.load();
        CraftingRuntime.initialize();
        QuickAddController.initialize();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> TrackerConfig.save());

        // Register keybindings — via VersionCompat for cross-version support
        openMenuKey = VersionCompat.registerOpenKey();
        toggleHudKey = VersionCompat.registerToggleHudKey();
        VersionCompat.registerClientResourceReloadListener(() -> {
            TrackerConfig.invalidateDisplayNameCache();
            CraftingRuntime.invalidate();
            HudRenderCache.clear();
        });

        // Register the client tick event to handle input and update inventory counts
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            updateActiveListContext(client);

            // Always use the registered KeyMapping so the Controls screen remains the
            // single source of truth for the binding, including user reassignment.
            while (openMenuKey.consumeClick()) {
                Screen currentScreen = client.gui.screen();
                if (currentScreen instanceof MainScreen) {
                    currentScreen.onClose();
                } else if (currentScreen == null) {
                    client.gui.setScreen(new MainScreen(null));
                }
            }

            // Toggle global HUD visibility
            while (toggleHudKey.consumeClick()) {
                TrackerConfig.INSTANCE.hudVisible = !TrackerConfig.INSTANCE.hudVisible;
                invalidateTargetItemCache();
                TrackerConfig.saveGlobalSettingsOnly();
            }

            if (client.player != null && client.level != null && (++inventoryUpdateTicks % 10 == 0 || CraftingRuntime.needsUpdate())) {
                updateCachedCounts(client);
            }
        });

        // Register the HUD renderer through the profile-specific Fabric API.
        HudCompat.register(new HudOverlay());
    }


    public static void invalidateTargetItemCache() {
        CraftingRuntime.invalidate();
        HudRenderCache.clear();
    }

    private static void updateCachedCounts(Minecraft client) {
        CraftingRuntime.update(client.player);
        CompletionNotifications.update(client);
    }

    private static void updateActiveListContext(Minecraft client) {
        TrackerConfig.ActiveContext context = getCurrentListContext(client);
        if (!lastContext.equals(context)) {
            TrackerConfig.setActiveContext(context);
            CraftingRuntime.reset();
            CompletionNotifications.reset();
            invalidateTargetItemCache();
            lastContext = context;
        }
    }

    private static TrackerConfig.ActiveContext getCurrentListContext(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            return TrackerConfig.ActiveContext.none();
        }

        if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
            try {
                String worldFolderName = getWorldFolderName(client.getSingleplayerServer().getWorldPath(LevelResource.ROOT));
                if (worldFolderName != null) {
                    return TrackerConfig.makeSingleplayerContext(worldFolderName);
                }
            } catch (Exception ignored) {
            }
            return TrackerConfig.makeSingleplayerContext(client.getSingleplayerServer().getWorldData().getLevelName());
        }

        ServerData server = client.getCurrentServer();
        if (server != null) {
            return TrackerConfig.makeServerContext(server.ip);
        }

        return TrackerConfig.ActiveContext.none();
    }

    private static String getWorldFolderName(Path worldRootPath) {
        if (worldRootPath == null) return null;

        Path normalized = worldRootPath.toAbsolutePath().normalize();
        Path fileName = normalized.getFileName();
        if (isUsablePathName(fileName)) {
            return fileName.toString();
        }

        Path parent = normalized.getParent();
        if (parent != null && isUsablePathName(parent.getFileName())) {
            return parent.getFileName().toString();
        }

        return null;
    }

    private static boolean isUsablePathName(Path path) {
        if (path == null) return false;
        String name = path.toString();
        return !name.isBlank() && !name.equals(".") && !name.equals("..");
    }
}
