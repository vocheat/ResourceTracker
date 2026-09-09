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
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("resourcetracker.json");
    private static final Path DATA_DIR = CONFIG_DIR.resolve("resourcetracker");
    private static final Path LISTS_DIR = DATA_DIR.resolve("lists");
    private static final String SINGLEPLAYER_DIR_NAME = "Singleplayer Worlds";
    private static final String SERVERS_DIR_NAME = "Servers";
    private static final Path SINGLEPLAYER_LISTS_DIR = LISTS_DIR.resolve(SINGLEPLAYER_DIR_NAME);
    private static final Path SERVER_LISTS_DIR = LISTS_DIR.resolve(SERVERS_DIR_NAME);
    private static final Path TEMPLATES_DIR = LISTS_DIR.resolve("templates");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final float MIN_SCALE = 0.25f;
    public static final float MAX_SCALE = 4.0f;
    public static final int MAX_COLUMNS = 5;
    public static final int MAX_TARGET_COUNT = 99999;
    private static final int MAX_FILE_BASENAME_LENGTH = 80;
    private static final int MAX_CONTEXT_SEGMENT_LENGTH = 120;

    public static TrackerConfig INSTANCE = new TrackerConfig();

    /** Active context lists. Legacy JSON lists are migrated to the first active context and no longer saved globally. */
    public List<TrackingList> lists = new ArrayList<>();

    /** Global HUD visibility toggle. */
    public boolean hudVisible = true;

    /** Set after legacy JSON lists have been copied to an active context. */
    public boolean legacyListsMigrated = false;

    /** Separates completed active-context migration from older releases that copied lists only to templates. */
    public boolean legacyListsMigratedToActiveContext = false;

    /** Context selected for an in-progress legacy migration; prevents copying the same lists to another world/server. */
    public String legacyMigrationTargetContextKey = null;

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
    private static List<TrackingList> pendingLegacyLists = new ArrayList<>();
    private static ConfigLoadStatus loadStatus = ConfigLoadStatus.NOT_LOADED;
    private static boolean globalWriteBlocked = false;
    private static Path recoveryBackupPath = null;

    public enum ConfigLoadStatus {
        NOT_LOADED,
        MISSING,
        LOADED,
        CORRUPT,
        IO_ERROR,
        RECOVERED
    }

    public enum ConfigRecoveryStatus {
        SUCCESS,
        NOT_NEEDED,
        BACKUP_FAILED,
        WRITE_FAILED
    }

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
        private transient Boolean validItemId = null;

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
            if (validItemId == null) {
                validItemId = VersionCompat.isValidItemId(this.itemId);
            }
            return validItemId;
        }
    }

    public static void load() {
        loadStatus = ConfigLoadStatus.NOT_LOADED;
        globalWriteBlocked = false;
        recoveryBackupPath = null;
        ensureDirectories();
        TrackerConfig loaded = new TrackerConfig();
        if (!Files.exists(CONFIG_FILE)) {
            loadStatus = ConfigLoadStatus.MISSING;
        } else {
            try (BufferedReader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
                TrackerConfig fromJson = GSON.fromJson(reader, TrackerConfig.class);
                if (fromJson != null) {
                    loaded = fromJson;
                    loadStatus = ConfigLoadStatus.LOADED;
                } else {
                    throw new IllegalArgumentException("Configuration JSON is empty");
                }
            } catch (IOException e) {
                loadStatus = ConfigLoadStatus.IO_ERROR;
                globalWriteBlocked = true;
                recoveryBackupPath = backupExistingConfig();
                System.err.println("[ResourceTracker] Failed to load configuration (" + loadStatus + "): " + e.getMessage());
            } catch (Exception e) {
                loadStatus = ConfigLoadStatus.CORRUPT;
                globalWriteBlocked = true;
                recoveryBackupPath = backupExistingConfig();
                System.err.println("[ResourceTracker] Failed to load configuration (" + loadStatus + "): " + e.getMessage());
            }
        }

        if (loaded.lists == null) loaded.lists = new ArrayList<>();
        INSTANCE.hudVisible = loaded.hudVisible;
        INSTANCE.legacyListsMigrated = loaded.legacyListsMigrated;
        INSTANCE.legacyListsMigratedToActiveContext = loaded.legacyListsMigratedToActiveContext;
        INSTANCE.legacyMigrationTargetContextKey = loaded.legacyMigrationTargetContextKey;
        INSTANCE.defaultX = loaded.defaultX;
        INSTANCE.defaultY = loaded.defaultY;
        INSTANCE.defaultScale = clampScale(loaded.defaultScale);
        INSTANCE.defaultShowRemaining = loaded.defaultShowRemaining;
        INSTANCE.defaultShowIcons = loaded.defaultShowIcons;
        INSTANCE.defaultColumns = clampColumns(loaded.defaultColumns);
        INSTANCE.defaultTextColor = loaded.defaultTextColor;
        INSTANCE.defaultNameColor = loaded.defaultNameColor;
        INSTANCE.defaultBackgroundColor = loaded.defaultBackgroundColor;
        INSTANCE.lists = new ArrayList<>();
        pendingLegacyLists = new ArrayList<>();

        if (!INSTANCE.legacyListsMigratedToActiveContext && !loaded.lists.isEmpty()) {
            assignDeterministicLegacyIds(loaded.lists);
            pendingLegacyLists.addAll(loaded.lists);
            return;
        }
        if (loadStatus == ConfigLoadStatus.MISSING) saveGlobalSettingsOnly();
    }

    public static ConfigLoadStatus getLoadStatus() {
        return loadStatus;
    }

    public static boolean isGlobalWriteBlocked() {
        return globalWriteBlocked;
    }

    public static Path getRecoveryBackupPath() {
        return recoveryBackupPath;
    }

    public static ConfigRecoveryStatus recoverConfig() {
        if (!globalWriteBlocked || (loadStatus != ConfigLoadStatus.CORRUPT && loadStatus != ConfigLoadStatus.IO_ERROR)) {
            return ConfigRecoveryStatus.NOT_NEEDED;
        }
        Path backup = backupExistingConfig();
        if (backup == null) return ConfigRecoveryStatus.BACKUP_FAILED;
        recoveryBackupPath = backup;
        if (!saveGlobalSettingsInternal()) return ConfigRecoveryStatus.WRITE_FAILED;
        globalWriteBlocked = false;
        loadStatus = ConfigLoadStatus.RECOVERED;
        return ConfigRecoveryStatus.SUCCESS;
    }

    public static void save() {
        saveGlobalSettingsOnly();
        saveAllActiveContextLists();
    }

    public static void saveGlobalSettingsOnly() {
        saveGlobalSettings();
    }

    public static void saveList(TrackingList list) {
        if (activeContext.isNone() || list == null) return;
        normalizeList(list);
        writeList(getActiveListsDir(), list);
    }

    public static void saveAllActiveContextLists() {
        saveActiveContextLists();
    }

    public static void setActiveContext(ActiveContext context) {
        if (context == null) context = ActiveContext.none();
        if (activeContext.equals(context)) return;

        saveAllActiveContextLists();
        activeContext = context;
        INSTANCE.lists = new ArrayList<>();
        if (!activeContext.isNone()) {
            migrateLegacyContextDirectory(activeContext);
            migratePendingLegacyLists(activeContext);
            loadActiveContextLists();
        }
    }

    public static String getActiveContextKey() {
        return activeContext.key();
    }

    public static boolean hasActiveContext() {
        return !activeContext.isNone();
    }

    /** Clears names resolved from client language resources. */
    public static void invalidateDisplayNameCache() {
        for (TrackingList list : INSTANCE.lists) {
            if (list == null || list.items == null) continue;
            for (TrackedItem item : list.items) {
                if (item != null) item.displayName = null;
            }
        }
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
        list.scale = clampScale(INSTANCE.defaultScale);
        list.showRemaining = INSTANCE.defaultShowRemaining;
        list.showIcons = INSTANCE.defaultShowIcons;
        list.columns = clampColumns(INSTANCE.defaultColumns);
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
        saveGlobalSettingsOnly();
    }

    public static void deleteList(TrackingList list) {
        if (list == null) return;
        INSTANCE.lists.remove(list);
        if (!activeContext.isNone() && list.storageFileName != null && !list.storageFileName.isBlank()) {
            try {
                Path file = resolveListFile(getActiveListsDir(), list.storageFileName);
                if (file != null) {
                    Files.deleteIfExists(file);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static ActiveContext makeSingleplayerContext(String worldFolderName) {
        return new ActiveContext(ContextType.SINGLEPLAYER, sanitizeWindowsPathSegment(worldFolderName, "unknown_world"));
    }

    public static ActiveContext makeServerContext(String serverAddress) {
        return new ActiveContext(ContextType.SERVER, sanitizeWindowsPathSegment(serverAddress, "unknown_server"));
    }

    public static String sanitizePathSegment(String input) {
        String sanitized = (input == null ? "" : input).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")) {
            sanitized = "unnamed";
        }
        return limitLength(sanitized, MAX_FILE_BASENAME_LENGTH);
    }

    private static String sanitizeWindowsPathSegment(String input, String fallback) {
        String sanitized = input == null ? "" : input.replaceAll("[:*?\"<>|\\\\/]", "_")
                .replaceAll("[\\p{Cntrl}]", "_")
                .trim()
                .replaceAll("[. ]+$", "");
        if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..") || isReservedWindowsName(sanitized)) {
            sanitized = fallback;
        }
        sanitized = limitLength(sanitized, MAX_CONTEXT_SEGMENT_LENGTH).replaceAll("[. ]+$", "");
        return sanitized.isBlank() ? fallback : sanitized;
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

    private static boolean saveGlobalSettings() {
        if (globalWriteBlocked) return false;
        return saveGlobalSettingsInternal();
    }

    private static boolean saveGlobalSettingsInternal() {
        ensureDirectories();
        try {
            GlobalSettings settings = new GlobalSettings();
            settings.hudVisible = INSTANCE.hudVisible;
            settings.legacyListsMigrated = INSTANCE.legacyListsMigrated;
            settings.legacyListsMigratedToActiveContext = INSTANCE.legacyListsMigratedToActiveContext;
            settings.legacyMigrationTargetContextKey = INSTANCE.legacyMigrationTargetContextKey;
            if (!INSTANCE.legacyListsMigratedToActiveContext) {
                settings.lists = new ArrayList<>(pendingLegacyLists);
            }
            settings.defaultX = INSTANCE.defaultX;
            settings.defaultY = INSTANCE.defaultY;
            settings.defaultScale = INSTANCE.defaultScale;
            settings.defaultShowRemaining = INSTANCE.defaultShowRemaining;
            settings.defaultShowIcons = INSTANCE.defaultShowIcons;
            settings.defaultColumns = INSTANCE.defaultColumns;
            settings.defaultTextColor = INSTANCE.defaultTextColor;
            settings.defaultNameColor = INSTANCE.defaultNameColor;
            settings.defaultBackgroundColor = INSTANCE.defaultBackgroundColor;
            return writeTextAtomically(CONFIG_FILE, GSON.toJson(settings));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static Path backupExistingConfig() {
        if (!Files.isRegularFile(CONFIG_FILE)) return null;
        Path parent = CONFIG_FILE.getParent();
        if (parent == null) return null;
        String base = CONFIG_FILE.getFileName().toString() + ".recovery";
        for (int index = 1; index <= 1000; index++) {
            String suffix = index == 1 ? "" : "_" + index;
            Path candidate = parent.resolve(base + suffix + ".bak");
            try {
                Files.copy(CONFIG_FILE, candidate);
                return candidate;
            } catch (FileAlreadyExistsException ignored) {
                // A concurrent or earlier recovery already owns this name.
            } catch (IOException e) {
                System.err.println("[ResourceTracker] Failed to back up configuration: " + e.getMessage());
                return null;
            }
        }
        System.err.println("[ResourceTracker] Could not find a free configuration backup name");
        return null;
    }

    private static void migratePendingLegacyLists(ActiveContext context) {
        if (INSTANCE.legacyListsMigratedToActiveContext || context == null || context.isNone()) return;

        String targetContextKey = context.key();
        if (INSTANCE.legacyMigrationTargetContextKey != null
                && !INSTANCE.legacyMigrationTargetContextKey.equals(targetContextKey)) {
            return;
        }

        if (pendingLegacyLists.isEmpty()) {
            pendingLegacyLists.addAll(loadLists(TEMPLATES_DIR));
        }
        if (pendingLegacyLists.isEmpty()) {
            boolean oldMigrated = INSTANCE.legacyListsMigrated;
            boolean oldMigratedToActive = INSTANCE.legacyListsMigratedToActiveContext;
            String oldTarget = INSTANCE.legacyMigrationTargetContextKey;
            INSTANCE.legacyListsMigrated = true;
            INSTANCE.legacyListsMigratedToActiveContext = true;
            INSTANCE.legacyMigrationTargetContextKey = null;
            if (saveGlobalSettings()) {
                pendingLegacyLists = new ArrayList<>();
            } else {
                INSTANCE.legacyListsMigrated = oldMigrated;
                INSTANCE.legacyListsMigratedToActiveContext = oldMigratedToActive;
                INSTANCE.legacyMigrationTargetContextKey = oldTarget;
            }
            return;
        }

        assignDeterministicLegacyIds(pendingLegacyLists);
        if (INSTANCE.legacyMigrationTargetContextKey == null) INSTANCE.legacyMigrationTargetContextKey = targetContextKey;

        Path targetDir = getActiveListsDir();
        for (TrackingList list : pendingLegacyLists) {
            if (list == null) continue;
            normalizeList(list);
            // Check immediately before each write so a retry after a marker failure
            // remains idempotent even when the destination already contains the ID.
            if (listWithIdExists(targetDir, list.id)) continue;
            if (listWithIdExists(targetDir, list.id) || !writeList(targetDir, list)) {
                return;
            }
        }

        boolean oldMigrated = INSTANCE.legacyListsMigrated;
        boolean oldMigratedToActive = INSTANCE.legacyListsMigratedToActiveContext;
        String completedTargetContextKey = INSTANCE.legacyMigrationTargetContextKey;
        INSTANCE.legacyListsMigrated = true;
        INSTANCE.legacyListsMigratedToActiveContext = true;
        INSTANCE.legacyMigrationTargetContextKey = null;
        if (saveGlobalSettings()) {
            pendingLegacyLists = new ArrayList<>();
        } else {
            INSTANCE.legacyListsMigrated = oldMigrated;
            INSTANCE.legacyListsMigratedToActiveContext = oldMigratedToActive;
            INSTANCE.legacyMigrationTargetContextKey = completedTargetContextKey;
        }
    }

    private static void loadActiveContextLists() {
        INSTANCE.lists.addAll(loadLists(getActiveListsDir()));
    }

    private static List<TrackingList> loadLists(Path dir) {
        List<TrackingList> loadedLists = new ArrayList<>();
        try {
            try (var stream = Files.list(dir)) {
                stream.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .forEach(path -> {
                            try {
                                TrackingList list = readList(path);
                                if (list != null) loadedLists.add(list);
                            } catch (Exception e) {
                                System.err.println("[ResourceTracker] Failed to read list file " + path + ": " + e.getMessage());
                            }
                        });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return loadedLists;
    }

    private static void saveActiveContextLists() {
        if (activeContext.isNone()) return;
        Path dir = getActiveListsDir();
        for (TrackingList list : INSTANCE.lists) {
            if (list == null) continue;
            normalizeList(list);
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
                    list.items.add(new TrackedItem(key, clampTargetCount(target)));
                } else {
                    applyListProperty(list, key, value);
                }
            }
        }
        if (list.id == null || list.id.isBlank()) list.id = UUID.randomUUID().toString();
        if (list.name == null || list.name.isBlank()) list.name = stripTxt(file.getFileName().toString());
        normalizeList(list);
        return list;
    }

    private static boolean writeList(Path dir, TrackingList list) {
        if (list == null) return false;
        ensureDirectory(dir);
        normalizeList(list);
        String fileName = list.storageFileName;
        Path file = resolveListFile(dir, fileName);
        if (fileName == null || fileName.isBlank()) {
            fileName = uniqueListFileName(dir, list.name);
            list.storageFileName = fileName;
            file = dir.resolve(fileName);
        } else if (file == null) {
            fileName = uniqueListFileName(dir, stripTxt(fileName));
            list.storageFileName = fileName;
            file = dir.resolve(fileName);
        } else {
            Path existing = findCaseInsensitiveFile(dir, fileName);
            if (existing != null) {
                String existingId = readStoredListId(existing);
                if (!list.id.equals(existingId)) {
                    fileName = uniqueListFileName(dir, stripTxt(fileName));
                    list.storageFileName = fileName;
                    file = dir.resolve(fileName);
                } else {
                    file = existing;
                    list.storageFileName = existing.getFileName().toString();
                }
            }
        }
        // Re-evaluate ownership immediately before replacing an existing path.
        Path existingBeforeWrite = findCaseInsensitiveFile(dir, file.getFileName().toString());
        if (existingBeforeWrite != null && !list.id.equals(readStoredListId(existingBeforeWrite))) {
            fileName = uniqueListFileName(dir, stripTxt(file.getFileName().toString()));
            list.storageFileName = fileName;
            file = dir.resolve(fileName);
        } else if (existingBeforeWrite != null) {
            file = existingBeforeWrite;
            list.storageFileName = existingBeforeWrite.getFileName().toString();
        }
        try {
            StringBuilder contents = new StringBuilder("# ResourceTracker list v1\n")
                    .append("id=").append(safe(list.id)).append('\n')
                    .append("name=").append(safe(list.name)).append('\n')
                    .append("visible=").append(list.isVisible).append('\n')
                    .append("x=").append(list.x).append('\n')
                    .append("y=").append(list.y).append('\n')
                    .append("scale=").append(list.scale).append('\n')
                    .append("showRemaining=").append(list.showRemaining).append('\n')
                    .append("showIcons=").append(list.showIcons).append('\n')
                    .append("columns=").append(list.columns).append('\n')
                    .append("textColor=").append(colorToHex(list.textColor)).append('\n')
                    .append("nameColor=").append(colorToHex(list.nameColor)).append('\n')
                    .append("backgroundColor=").append(colorToHex(list.backgroundColor)).append("\n\n[items]\n");
            for (TrackedItem item : list.items) {
                if (item.itemId != null && !item.itemId.isBlank()) {
                    contents.append(safe(item.itemId)).append('=').append(clampTargetCount(item.targetCount)).append('\n');
                }
            }
            writeTextAtomically(file, contents.toString());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean listWithIdExists(Path dir, String id) {
        if (id == null || id.isBlank()) return false;
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
                    .anyMatch(path -> hasListId(path, id));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean hasListId(Path file, String id) {
        return id.equals(readStoredListId(file));
    }

    private static String readStoredListId(Path file) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.equalsIgnoreCase("[items]")) break;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                if (line.substring(0, eq).trim().equalsIgnoreCase("id")) {
                    String value = line.substring(eq + 1).trim();
                    return value.isBlank() ? null : value;
                }
            }
        } catch (IOException e) {
            System.err.println("[ResourceTracker] Failed to read list file " + file + ": " + e.getMessage());
        }
        return null;
    }

    private static void assignDeterministicLegacyIds(List<TrackingList> legacyLists) {
        for (int index = 0; index < legacyLists.size(); index++) {
            TrackingList list = legacyLists.get(index);
            if (list != null && (list.id == null || list.id.isBlank())) {
                list.id = UUID.nameUUIDFromBytes((index + "\\n" + GSON.toJson(list)).getBytes(StandardCharsets.UTF_8)).toString();
            }
        }
    }

    private static String uniqueListFileName(Path dir, String name) {
        String base = sanitizePathSegment(name == null || name.isBlank() ? "list" : name);
        String candidate = base + ".txt";
        int n = 2;
        while (findCaseInsensitiveFile(dir, candidate) != null) {
            candidate = base + "_" + n + ".txt";
            n++;
        }
        return candidate;
    }

    private static Path findCaseInsensitiveFile(Path dir, String fileName) {
        if (dir == null || fileName == null || fileName.isBlank()) return null;
        try (var stream = Files.list(dir)) {
            return stream.filter(path -> path.getFileName().toString().equalsIgnoreCase(fileName))
                    .findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static void applyListProperty(TrackingList list, String key, String value) {
        switch (key) {
            case "id" -> list.id = value;
            case "name" -> list.name = value;
            case "visible" -> list.isVisible = Boolean.parseBoolean(value);
            case "x" -> list.x = parseInt(value, list.x);
            case "y" -> list.y = parseInt(value, list.y);
            case "scale" -> list.scale = clampScale(parseFloat(value, list.scale));
            case "showRemaining" -> list.showRemaining = Boolean.parseBoolean(value);
            case "showIcons" -> list.showIcons = Boolean.parseBoolean(value);
            case "columns" -> list.columns = clampColumns(parseInt(value, list.columns));
            case "textColor" -> list.textColor = parseColor(value, list.textColor);
            case "nameColor" -> list.nameColor = parseColor(value, list.nameColor);
            case "backgroundColor" -> list.backgroundColor = parseColor(value, list.backgroundColor);
        }
    }

    private static void openFolder(Path path) {
        ensureDirectory(path);
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(path.toFile());
            } else {
                openWithExplorer(path);
            }
        } catch (Exception e) {
            try {
                openWithExplorer(path);
            } catch (IOException fallbackError) {
                fallbackError.printStackTrace();
            }
        }
    }

    private static void openWithExplorer(Path path) throws IOException {
        String systemRoot = System.getenv("SystemRoot");
        Path explorer = systemRoot == null || systemRoot.isBlank()
                ? Path.of("C:", "Windows", "explorer.exe")
                : Path.of(systemRoot, "explorer.exe");
        String executable = Files.isRegularFile(explorer) ? explorer.toString() : "explorer.exe";
        new ProcessBuilder(executable, path.toAbsolutePath().normalize().toString()).start();
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

    public static float clampScale(float value) {
        if (!Float.isFinite(value)) return 1.0f;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    public static int clampColumns(int value) {
        return Math.max(0, Math.min(MAX_COLUMNS, value));
    }

    public static int clampTargetCount(int value) {
        return Math.max(1, Math.min(MAX_TARGET_COUNT, value));
    }

    private static void normalizeList(TrackingList list) {
        if (list == null) return;
        if (list.id == null || list.id.isBlank()) list.id = UUID.randomUUID().toString();
        if (list.name == null || list.name.isBlank()) list.name = "New List";
        list.scale = clampScale(list.scale);
        list.columns = clampColumns(list.columns);

        List<TrackedItem> normalizedItems = new ArrayList<>();
        if (list.items != null) {
            for (TrackedItem item : list.items) {
                if (item == null) continue;
                item.targetCount = clampTargetCount(item.targetCount);
                normalizedItems.add(item);
            }
        }
        list.items = normalizedItems;
    }

    private static Path resolveListFile(Path dir, String fileName) {
        if (dir == null || fileName == null || fileName.isBlank()) return null;

        Path root = dir.toAbsolutePath().normalize();
        Path file = root.resolve(fileName).normalize();
        String lowerName = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);

        if (!file.startsWith(root) || !Objects.equals(file.getParent(), root) || !lowerName.endsWith(".txt")) {
            return null;
        }
        return file;
    }

    private static boolean writeTextAtomically(Path file, String contents) throws IOException {
        Path target = file.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("Configuration parent directory is unavailable: " + target);
        }

        Path temporary = Files.createTempFile(parent, "." + target.getFileName(), ".tmp");
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            forceAndMove(temporary, target);
            return true;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void forceAndMove(Path temporary, Path target) throws IOException {
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isReservedWindowsName(String value) {
        String name = value;
        int dot = name.indexOf('.');
        if (dot >= 0) {
            name = name.substring(0, dot);
        }
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.equals("CON") || upper.equals("PRN") || upper.equals("AUX") || upper.equals("NUL")
                || upper.matches("COM[1-9]") || upper.matches("LPT[1-9]");
    }

    private static String limitLength(String value, int maxLength) {
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (Exception ignored) { return fallback; }
    }

    private static float parseFloat(String value, float fallback) {
        try {
            float parsed = Float.parseFloat(value.trim());
            return Float.isFinite(parsed) ? parsed : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int parseColor(String value, int fallback) {
        try {
            String hex = value.trim().replace("#", "");
            if (hex.length() == 6) {
                hex = "FF" + hex;
            }
            return (int) Long.parseLong(hex, 16);
        } catch (Exception ignored) {
            return fallback;
        }
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
        List<TrackingList> lists = new ArrayList<>();
        boolean hudVisible = true;
        boolean legacyListsMigrated = false;
        boolean legacyListsMigratedToActiveContext = false;
        String legacyMigrationTargetContextKey = null;
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
