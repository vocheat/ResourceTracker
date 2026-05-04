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

package net.fabricmc.resourcetracker.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the configuration for the Resource Tracker mod.
 * <p>
 * This class handles serialization and deserialization of the configuration
 * to/from the {@code resourcetracker.json} file using GSON.
 * </p>
 *
 * @author vocheat
 */
public class TrackerConfig {

    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("resourcetracker.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * The singleton instance of the configuration.
     */
    public static TrackerConfig INSTANCE = new TrackerConfig();

    /**
     * The list of tracking groups configured by the user.
     */
    public List<TrackingList> lists = new ArrayList<>();

    /**
     * Global HUD visibility toggle. When false, no tracking lists are rendered.
     */
    public boolean hudVisible = true;

    /**
     * Represents a grouping of items to be tracked and displayed on the UI.
     * Contains visual settings (position, colors) and the list of items.
     */
    public static class TrackingList {
        public String name = "New List";
        public boolean isVisible = true;
        public int x = 10;
        public int y = 10;

        public float scale = 1.0f;
        public boolean showRemaining = false;

        /** Determines if item icons should be rendered next to the text. Default: true. */
        public boolean showIcons = true;

        /** Number of columns for the list display. 0 = auto (fit screen height), 1+ = fixed. Default: 0. */
        public int columns = 0;

        /** Text color for items (ARGB). Default: White (0xFFFFFFFF). */
        public int textColor = 0xFFFFFFFF;

        /** Text color for the list header (ARGB). Default: White (0xFFFFFFFF). */
        public int nameColor = 0xFFFFFFFF;

        /** Background color for the list panel (ARGB). Default: Semi-transparent dark gray (0xA0505050). */
        public int backgroundColor = 0xA0505050;

        public List<TrackedItem> items = new ArrayList<>();
    }

    /**
     * Represents a single item being tracked within a list.
     */
    public static class TrackedItem {
        public String itemId;
        public int targetCount;

        public transient int cachedCount = 0;
        private transient Item cachedItem = null;
        private transient ItemStack cachedStack = null;
        private transient String displayName = null;

        public TrackedItem(String itemId, int targetCount) {
            this.itemId = itemId;
            this.targetCount = targetCount;
        }

        /** Returns the resolved Item, cached after first lookup. */
        public Item getItem() {
            if (cachedItem == null) {
                cachedItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(this.itemId));
            }
            return cachedItem;
        }

        /** Returns a reusable display ItemStack (count=1), avoiding per-frame allocations. */
        public ItemStack getStack() {
            if (cachedStack == null) {
                cachedStack = new ItemStack(getItem());
            }
            return cachedStack;
        }

        /** Returns the cached display name string. */
        public String getDisplayName() {
            if (displayName == null) {
                displayName = getItem().getName().getString();
            }
            return displayName;
        }

        /** Returns true if this item ID resolves to a real item in the registry. */
        public boolean isValid() {
            return BuiltInRegistries.ITEM.containsKey(Identifier.parse(this.itemId));
        }
    }

    /**
     * Loads the configuration from the disk.
     * <p>
     * If the configuration file exists, it populates the {@link #INSTANCE}.
     * If not, it creates a new file with default values.
     * </p>
     */
    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                TrackerConfig loaded = GSON.fromJson(reader, TrackerConfig.class);
                if (loaded != null) {
                    if (loaded.lists == null) loaded.lists = new ArrayList<>();
                    INSTANCE = loaded;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            save();
        }
    }

    /**
     * Saves the current configuration state to the disk.
     */
    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}