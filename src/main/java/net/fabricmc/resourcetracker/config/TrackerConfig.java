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
import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Manages Resource Tracker configuration and per-world/per-server TXT list storage.
 */
public class TrackerConfig {

    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    private static final File CONFIG_FILE = CONFIG_DIR.resolve("resourcetracker.json").toFile();
    private static final Path DATA_DIR = CONFIG_DIR.resolve("resourcetracker");
    private static final Path LISTS_DIR = DATA_DIR.resolve("lists");
    private static final String SINGLEPLAYER_DIR_NAME = "Singleplayer Worlds";
    private static final String SERVERS_DIR_NAME = "Servers";
    private static final Path SINGLEPLAYER_LISTS_DIR = LISTS_DIR.resolve(SINGLEPLAYER_DIR_NAME);
    private static final Path SERVER_LISTS_DIR = LISTS_DIR.resolve(SERVERS_DIR_NAME);
    private static final Path TEMPLATES_DIR = LISTS_DIR.resolve("templates");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static TrackerConfig INSTANCE = new TrackerConfig();

    /** Active context lists. Legacy JSON lists are migrated to templates and no longer saved globally. */
    public List<TrackingList> lists = new ArrayList<>();

    /** Global HUD visibility toggle. */
    public boolean hudVisible = true;

    /** Set after legacy JSON lists have been copied to lists/templates. */
    public boolean legacyListsMigrated = false;

    public int defaultX = 10;
    public int defaultY = 10;
    public float defaultScale = 1.0f;
    public boolean defaultShowRemaining = false;
    public boolean defaultShowIcons = true;
    public int defaultColumns = 0;
    public int defaultTextColor = 0xFFFFFFFF;
    public int defaultNameColor = 0xFFFFFFFF;
    public int defaultBackgroundColor = 0xA0505050;

    private static ActiveContext activeContext = ActiveContext.none();

    public enum ContextType {
        NONE,
        SINGLEPLAYER,
        SERVER
    }

    public static class ActiveContext {
        public final ContextType type;
        public final String folderName;

        private ActiveContext(ContextType type, String folderName) {
            this.type = type == null ? ContextType.NONE : type;
            this.folderName = folderName;
        }

        public static ActiveContext none() {
            return new ActiveContext(ContextType.NONE, null);
        }

        public boolean isNone() {
            return type == ContextType.NONE || folderName == null || folderName.isBlank();
        }

        public Path resolveUnder(Path listsRoot) {
            if (isNone()) return listsRoot;
            return switch (type) {
                case SINGLEPLAYER -> listsRoot.resolve(SINGLEPLAYER_DIR_NAME).resolve(folderName);
                case SERVER -> listsRoot.resolve(SERVERS_DIR_NAME).resolve(folderName);
                case NONE -> listsRoot;
            };
        }

        public String key() {
            if (isNone()) return null;
            return switch (type) {
                case SINGLEPLAYER -> SINGLEPLAYER_DIR_NAME + "/" + folderName;
                case SERVER -> SERVERS_DIR_NAME + "/" + folderName;
                case NONE -> null;
            };
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ActiveContext that)) return false;
            return type == that.type && Objects.equals(folderName, that.folderName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, folderName);
        }
    }

    public static class TrackingList {
        public String id = UUID.randomUUID().toString();
        public String name = "New List";
        public boolean isVisible = true;
        public int x = 10;
        public int y = 10;
        public float scale = 1.0f;
        public boolean showRemaining = false;
        public boolean showIcons = true;
        public int columns = 0;
        public int textColor = 0xFFFFFFFF;
        public int nameColor = 0xFFFFFFFF;
        public int backgroundColor = 0xA0505050;
        public List<TrackedItem> items = new ArrayList<>();

        public transient String storageFileName = null;
    }

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

        public Item getItem() {
            if (cachedItem == null) {
                cachedItem = VersionCompat.getItem(this.itemId);
            }
            return cachedItem;
        }

        public ItemStack getStack() {
            if (cachedStack == null) {
                Item item = getItem();
                cachedStack = item == null ? ItemStack.EMPTY : new ItemStack(item);
            }
            return cachedStack;
        }

        public String getDisplayName() {
            if (displayName == null) {
                Item item = getItem();
                displayName = item == null ? itemId : VersionCompat.getItemName(item);
            }
            return displayName;
        }

        public boolean isValid() {
            return VersionCompat.isValidItemId(this.itemId);
        }
    }

    public static void load() {
        ensureDirectories();
        TrackerConfig loaded = new TrackerConfig();
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
                TrackerConfig fromJson = GSON.fromJson(reader, TrackerConfig.class);
                if (fromJson != null) loaded = fromJson;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (loaded.lists == null) loaded.lists = new ArrayList<>();
        INSTANCE.hudVisible = loaded.hudVisible;
        INSTANCE.legacyListsMigrated = loaded.legacyListsMigrated;
        INSTANCE.defaultX = loaded.defaultX;
        INSTANCE.defaultY = loaded.defaultY;
        INSTANCE.defaultScale = loaded.defaultScale;
        INSTANCE.defaultShowRemaining = loaded.defaultShowRemaining;
        INSTANCE.defaultShowIcons = loaded.defaultShowIcons;
        INSTANCE.defaultColumns = loaded.defaultColumns;
        INSTANCE.defaultTextColor = loaded.defaultTextColor;
        INSTANCE.defaultNameColor = loaded.defaultNameColor;
        INSTANCE.defaultBackgroundColor = loaded.defaultBackgroundColor;
        INSTANCE.lists = new ArrayList<>();

        if (!INSTANCE.legacyListsMigrated && !loaded.lists.isEmpty()) {
            migrateLegacyListsToTemplates(loaded.lists);
            INSTANCE.legacyListsMigrated = true;
        }
        saveGlobalSettings();
    }

    public static void save() {
        saveGlobalSettings();
        saveActiveContextLists();
    }

    public static void setActiveContext(ActiveContext context) {
        if (context == null) context = ActiveContext.none();
        if (activeContext.equals(context)) return;

        saveActiveContextLists();
        activeContext = context;
        INSTANCE.lists = new ArrayList<>();
        if (!activeContext.isNone()) {
            migrateLegacyContextDirectory(activeContext);
            loadActiveContextLists();
        }
    }

    public static String getActiveContextKey() {
        return activeContext.key();
    }

    public static boolean hasActiveContext() {
        return !activeContext.isNone();
    }

    public static void reloadActiveContextLists() {
        if (activeContext.isNone()) return;
        INSTANCE.lists = new ArrayList<>();
        loadActiveContextLists();
    }

    public static Path getListsRootDir() {
        ensureDirectories();
        return LISTS_DIR;
    }

    public static Path getActiveListsDir() {
        ensureDirectories();
        if (activeContext.isNone()) return LISTS_DIR;
        Path dir = activeContext.resolveUnder(LISTS_DIR);
        ensureDirectory(dir);
        return dir;
    }

    public static void openListsRootFolder() {
        openFolder(getListsRootDir());
    }

    public static void openActiveListsFolder() {
        openFolder(getActiveListsDir());
    }

    public static TrackingList createList(String name) {
        TrackingList list = new TrackingList();
        list.name = name == null || name.isBlank() ? "New List" : name;
        applyDefaults(list);
        return list;
    }

    public static void applyDefaults(TrackingList list) {
        if (list == null) return;
        list.x = INSTANCE.defaultX;
        list.y = INSTANCE.defaultY;
        list.scale = INSTANCE.defaultScale;
        list.showRemaining = INSTANCE.defaultShowRemaining;
        list.showIcons = INSTANCE.defaultShowIcons;
        list.columns = INSTANCE.defaultColumns;
        list.textColor = INSTANCE.defaultTextColor;
        list.nameColor = INSTANCE.defaultNameColor;
        list.backgroundColor = INSTANCE.defaultBackgroundColor;
    }

    public static void resetDefaultListSettings() {
        INSTANCE.defaultX = 10;
        INSTANCE.defaultY = 10;
        INSTANCE.defaultScale = 1.0f;
        INSTANCE.defaultShowRemaining = false;
        INSTANCE.defaultShowIcons = true;
        INSTANCE.defaultColumns = 0;
        INSTANCE.defaultTextColor = 0xFFFFFFFF;
        INSTANCE.defaultNameColor = 0xFFFFFFFF;
        INSTANCE.defaultBackgroundColor = 0xA0505050;
        saveGlobalSettings();
    }

    public static void deleteList(TrackingList list) {
        if (list == null) return;
        INSTANCE.lists.remove(list);
        if (!activeContext.isNone() && list.storageFileName != null && !list.storageFileName.isBlank()) {
            try {
                Files.deleteIfExists(getActiveListsDir().resolve(list.storageFileName));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        save();
    }

    public static ActiveContext makeSingleplayerContext(String worldFolderName) {
        return new ActiveContext(ContextType.SINGLEPLAYER, sanitizeWindowsPathSegment(worldFolderName, "unknown_world"));
    }

    public static ActiveContext makeServerContext(String serverAddress) {
        return new ActiveContext(ContextType.SERVER, sanitizeWindowsPathSegment(serverAddress, "unknown_server"));
    }

    public static String sanitizePathSegment(String input) {
        String sanitized = input.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        return sanitized.isBlank() ? "unnamed" : sanitized;
    }

    private static String sanitizeWindowsPathSegment(String input, String fallback) {
        String sanitized = input == null ? "" : input.replaceAll("[:*?\"<>|\\\\/]", "_")
                .replaceAll("[\\p{Cntrl}]", "_")
                .trim()
                .replaceAll("[. ]+$", "");
        return sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..") ? fallback : sanitized;
    }

    private static void ensureDirectories() {
        ensureDirectory(DATA_DIR);
        ensureDirectory(LISTS_DIR);
        ensureDirectory(SINGLEPLAYER_LISTS_DIR);
        ensureDirectory(SERVER_LISTS_DIR);
        ensureDirectory(TEMPLATES_DIR);
    }

    private static void ensureDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveGlobalSettings() {
        ensureDirectories();
        try (FileWriter writer = new FileWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
            GlobalSettings settings = new GlobalSettings();
            settings.hudVisible = INSTANCE.hudVisible;
            settings.legacyListsMigrated = INSTANCE.legacyListsMigrated;
            settings.defaultX = INSTANCE.defaultX;
            settings.defaultY = INSTANCE.defaultY;
            settings.defaultScale = INSTANCE.defaultScale;
            settings.defaultShowRemaining = INSTANCE.defaultShowRemaining;
            settings.defaultShowIcons = INSTANCE.defaultShowIcons;
            settings.defaultColumns = INSTANCE.defaultColumns;
            settings.defaultTextColor = INSTANCE.defaultTextColor;
            settings.defaultNameColor = INSTANCE.defaultNameColor;
            settings.defaultBackgroundColor = INSTANCE.defaultBackgroundColor;
            GSON.toJson(settings, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void migrateLegacyListsToTemplates(List<TrackingList> legacyLists) {
        ensureDirectories();
        for (TrackingList list : legacyLists) {
            if (list.id == null || list.id.isBlank()) list.id = UUID.randomUUID().toString();
            if (list.items == null) list.items = new ArrayList<>();
            writeList(TEMPLATES_DIR, list);
        }
    }

    private static void loadActiveContextLists() {
        Path dir = getActiveListsDir();
        try {
            try (var stream = Files.list(dir)) {
                stream.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .forEach(path -> {
                            try {
                                TrackingList list = readList(path);
                                if (list != null) INSTANCE.lists.add(list);
                            } catch (Exception e) {
                                System.err.println("[ResourceTracker] Failed to read list file " + path + ": " + e.getMessage());
                            }
                        });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveActiveContextLists() {
        if (activeContext.isNone()) return;
        Path dir = getActiveListsDir();
        for (TrackingList list : INSTANCE.lists) {
            if (list.id == null || list.id.isBlank()) list.id = UUID.randomUUID().toString();
            if (list.items == null) list.items = new ArrayList<>();
            writeList(dir, list);
        }
    }

    private static TrackingList readList(Path file) throws IOException {
        TrackingList list = new TrackingList();
        list.items.clear();
        list.storageFileName = file.getFileName().toString();
        boolean inItems = false;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.equalsIgnoreCase("[items]")) {
                    inItems = true;
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (inItems) {
                    int target = parseInt(value, 1);
                    list.items.add(new TrackedItem(key, Math.max(1, target)));
                } else {
                    applyListProperty(list, key, value);
                }
            }
        }
        if (list.id == null || list.id.isBlank()) list.id = UUID.randomUUID().toString();
        if (list.name == null || list.name.isBlank()) list.name = stripTxt(file.getFileName().toString());
        return list;
    }

    private static void writeList(Path dir, TrackingList list) {
        ensureDirectory(dir);
        String fileName = list.storageFileName;
        if (fileName == null || fileName.isBlank()) {
            fileName = uniqueListFileName(dir, list.name);
            list.storageFileName = fileName;
        }
        Path file = dir.resolve(fileName);
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("# ResourceTracker list v1\n");
            writer.write("id=" + safe(list.id) + "\n");
            writer.write("name=" + safe(list.name) + "\n");
            writer.write("visible=" + list.isVisible + "\n");
            writer.write("x=" + list.x + "\n");
            writer.write("y=" + list.y + "\n");
            writer.write("scale=" + list.scale + "\n");
            writer.write("showRemaining=" + list.showRemaining + "\n");
            writer.write("showIcons=" + list.showIcons + "\n");
            writer.write("columns=" + list.columns + "\n");
            writer.write("textColor=" + colorToHex(list.textColor) + "\n");
            writer.write("nameColor=" + colorToHex(list.nameColor) + "\n");
            writer.write("backgroundColor=" + colorToHex(list.backgroundColor) + "\n\n");
            writer.write("[items]\n");
            for (TrackedItem item : list.items) {
                if (item.itemId != null && !item.itemId.isBlank()) {
                    writer.write(item.itemId + "=" + Math.max(1, item.targetCount) + "\n");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String uniqueListFileName(Path dir, String name) {
        String base = sanitizePathSegment(name == null || name.isBlank() ? "list" : name);
        String candidate = base + ".txt";
        int n = 2;
        while (Files.exists(dir.resolve(candidate))) {
            candidate = base + "_" + n + ".txt";
            n++;
        }
        return candidate;
    }

    private static void applyListProperty(TrackingList list, String key, String value) {
        switch (key) {
            case "id" -> list.id = value;
            case "name" -> list.name = value;
            case "visible" -> list.isVisible = Boolean.parseBoolean(value);
            case "x" -> list.x = parseInt(value, list.x);
            case "y" -> list.y = parseInt(value, list.y);
            case "scale" -> list.scale = parseFloat(value, list.scale);
            case "showRemaining" -> list.showRemaining = Boolean.parseBoolean(value);
            case "showIcons" -> list.showIcons = Boolean.parseBoolean(value);
            case "columns" -> list.columns = parseInt(value, list.columns);
            case "textColor" -> list.textColor = parseColor(value, list.textColor);
            case "nameColor" -> list.nameColor = parseColor(value, list.nameColor);
            case "backgroundColor" -> list.backgroundColor = parseColor(value, list.backgroundColor);
        }
    }

    private static void openFolder(Path path) {
        ensureDirectory(path);
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(path.toFile());
            } else {
                new ProcessBuilder("explorer.exe", path.toAbsolutePath().toString()).start();
            }
        } catch (Exception e) {
            try {
                new ProcessBuilder("explorer.exe", path.toAbsolutePath().toString()).start();
            } catch (IOException fallbackError) {
                fallbackError.printStackTrace();
            }
        }
    }

    private static void migrateLegacyContextDirectory(ActiveContext context) {
        if (context == null || context.isNone()) return;
        String legacyName = switch (context.type) {
            case SINGLEPLAYER -> "singleplayer__" + sanitizePathSegment(context.folderName);
            case SERVER -> "server__" + sanitizePathSegment(context.folderName);
            case NONE -> null;
        };
        if (legacyName == null) return;

        Path legacyDir = LISTS_DIR.resolve(legacyName);
        Path targetDir = context.resolveUnder(LISTS_DIR);
        if (!Files.isDirectory(legacyDir)) return;

        try {
            ensureDirectory(targetDir);
            try (var stream = Files.list(legacyDir)) {
                stream.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
                        .forEach(path -> moveLegacyListFile(path, targetDir.resolve(path.getFileName())));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void moveLegacyListFile(Path source, Path target) {
        try {
            Path destination = target;
            if (Files.exists(destination)) {
                destination = target.resolveSibling(uniqueMigratedFileName(target.getParent(), stripTxt(target.getFileName().toString())));
            }
            Files.move(source, destination);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String uniqueMigratedFileName(Path dir, String baseName) {
        String base = sanitizePathSegment(baseName == null || baseName.isBlank() ? "list" : baseName);
        String candidate = base + ".txt";
        int n = 2;
        while (Files.exists(dir.resolve(candidate))) {
            candidate = base + "_" + n + ".txt";
            n++;
        }
        return candidate;
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (Exception ignored) { return fallback; }
    }

    private static float parseFloat(String value, float fallback) {
        try { return Float.parseFloat(value.trim()); } catch (Exception ignored) { return fallback; }
    }

    private static int parseColor(String value, int fallback) {
        try { return (int) Long.parseLong(value.trim().replace("#", ""), 16); } catch (Exception ignored) { return fallback; }
    }

    private static String colorToHex(int color) {
        return String.format(Locale.ROOT, "%08X", color);
    }

    private static String stripTxt(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".txt") ? fileName.substring(0, fileName.length() - 4) : fileName;
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace("\r", " ").replace("\n", " ");
    }

    private static class GlobalSettings {
        boolean hudVisible = true;
        boolean legacyListsMigrated = false;
        int defaultX = 10;
        int defaultY = 10;
        float defaultScale = 1.0f;
        boolean defaultShowRemaining = false;
        boolean defaultShowIcons = true;
        int defaultColumns = 0;
        int defaultTextColor = 0xFFFFFFFF;
        int defaultNameColor = 0xFFFFFFFF;
        int defaultBackgroundColor = 0xA0505050;
    }
}
