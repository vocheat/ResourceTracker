package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.compat.VersionCompat;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.resourcetracker.client.ResourceTrackerClient;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.importing.ImportResult;
import net.fabricmc.resourcetracker.importing.ImportService;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

/** A file browser and explicit preview/accept step; opening a file never changes a list. */
public final class ImportScreen extends PagedDataScreen {
    private static final ExecutorService READER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ResourceTracker import");
        thread.setDaemon(true);
        return thread;
    });
    private Path directory = FabricLoader.getInstance().getGameDir();
    private String query = "";
    private String error;
    private ImportResult preview;
    private Future<?> pending;
    private Path cachedDirectory;
    private List<FileEntry> cachedEntries = List.of();
    private long request;
    private boolean searchFocused;
    private final ResourceViewState previewView = new ResourceViewState();
    private String previewName = "";
    private EditBox materialSearch;
    private EditBox nameField;
    private int browserPage;

    private record FileEntry(Path path, boolean folder, String lowerName) { }
    private record DirectoryScan(Path directory, List<FileEntry> entries, String error) { }
    private record PathCheck(Path path, boolean directory, boolean regularFile, String error) { }

    public ImportScreen(net.minecraft.client.gui.screens.Screen parent) {
        super(parent, Component.translatable("gui.resourcetracker.import.title"));
    }

    @Override
    protected void init() {
        if (preview != null) {
            buildPreview();
            return;
        }
        layout(124);
        showPanel();
        EditBox path = new EditBox(font, left, 30, contentWidth - 64, 18,
                Component.translatable("gui.resourcetracker.import.path"));
        path.setMaxLength(32767);
        path.setValue(preview == null ? directory.toString() : preview.source().toString());
        addRenderableWidget(path);
        button(Component.translatable("gui.resourcetracker.import.open"), left + contentWidth - 58, 29, 58, b -> {
            try { navigate(Path.of(path.getValue())); }
            catch (RuntimeException ex) { error = ex.getMessage(); init(); }
        });
        int quickWidth = (contentWidth - 12) / 3;
        button(Component.literal("schematics"), left, 54, quickWidth,
                b -> navigate(FabricLoader.getInstance().getGameDir().resolve("schematics")));
        button(Component.literal("config/litematica"), left + quickWidth + 6, 54, quickWidth,
                b -> navigate(FabricLoader.getInstance().getConfigDir().resolve("litematica")));
        button(Component.translatable("gui.resourcetracker.import.our_lists"), left + (quickWidth + 6) * 2, 54,
                contentWidth - (quickWidth + 6) * 2, b -> navigate(TrackerConfig.getListsRootDir()));
        button(Component.translatable("gui.resourcetracker.import.up"), left, 79, 48, b -> {
            if (directory.getParent() != null) navigate(directory.getParent());
        });
        EditBox search = new EditBox(font, left + 54, 80, contentWidth - 54, 18,
                Component.translatable("gui.resourcetracker.edit.search"));
        search.setValue(query);
        search.setResponder(value -> { query = value; page = 0; searchFocused = true; refreshRows(); });
        addRenderableWidget(search);
        if (searchFocused) setFocused(search);
        if (pending != null) {
            label(Component.translatable("gui.resourcetracker.import.reading"), left, 110, contentWidth);
        } else if (directory.equals(cachedDirectory)) {
            buildFiles();
        } else {
            scanDirectory();
            label(Component.translatable("gui.resourcetracker.import.reading"), left, 110, contentWidth);
        }
        button(Component.translatable("gui.cancel"), left + contentWidth - 90, height - 26, 90, b -> onClose());
    }

    // Rebuilding through the screen's normal initialization keeps filtering and paging identical.
    private void refreshRows() {
        if (minecraft != null) minecraft.execute(this::init);
    }

    private void buildFiles() {
        String filter = query.toLowerCase(Locale.ROOT);
        List<FileEntry> entries = cachedEntries.stream().filter(entry -> entry.lowerName().contains(filter)).toList();
        label(Component.literal(error == null ? directory.toString() : error), left, 110, contentWidth);
        pagination(entries.size());
        for (int i = page * pageSize; i < Math.min(entries.size(), (page + 1) * pageSize); i++) {
            FileEntry entry = entries.get(i);
            String name = (entry.folder() ? "[+] " : "") + entry.path().getFileName();
            button(Component.literal(name), left, rowTop + (i % pageSize) * 24, contentWidth,
                    b -> { if (entry.folder()) navigate(entry.path()); else read(entry.path()); });
        }
        if (entries.isEmpty() && error == null) label(Component.translatable("gui.resourcetracker.edit.no_results"), left, rowTop, contentWidth);
    }

    private boolean supported(String name) {
        return name.endsWith(".litematic") || name.endsWith(".json") || name.endsWith(".txt");
    }

    private void scanDirectory() {
        long token = ++request;
        Path target = directory;
        var client = minecraft;
        pending = READER.submit(() -> {
            DirectoryScan result = readDirectory(target);
            if (client != null && result != null) {
                DirectoryScan completed = result;
                client.execute(() -> completeScan(token, completed));
            }
        });
    }

    private DirectoryScan readDirectory(Path target) {
        List<FileEntry> entries = new ArrayList<>();
        try (Stream<Path> paths = Files.list(target)) {
            Iterator<Path> iterator = paths.iterator();
            while (iterator.hasNext()) {
                if (Thread.currentThread().isInterrupted()) return null;
                Path path = iterator.next();
                boolean folder = Files.isDirectory(path);
                String lowerName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (folder || supported(lowerName)) entries.add(new FileEntry(path, folder, lowerName));
            }
            if (Thread.currentThread().isInterrupted()) return null;
            entries.sort(java.util.Comparator.comparing(FileEntry::folder).reversed()
                    .thenComparing(FileEntry::lowerName));
            return new DirectoryScan(target, List.copyOf(entries), null);
        } catch (IOException | UncheckedIOException | SecurityException ex) {
            return new DirectoryScan(target, List.of(), ex.getMessage());
        }
    }

    private void completeScan(long token, DirectoryScan result) {
        if (token != request || minecraft == null || VersionCompat.currentScreen(minecraft) != this
                || !directory.equals(result.directory())) return;
        pending = null;
        cachedDirectory = result.directory();
        cachedEntries = result.entries();
        error = result.error();
        init();
    }

    private void navigate(Path path) {
        cancelRead();
        error = null;
        Path target = path.toAbsolutePath().normalize();
        long token = ++request;
        var client = minecraft;
        pending = READER.submit(() -> {
            PathCheck check;
            try {
                check = new PathCheck(target, Files.isDirectory(target), Files.isRegularFile(target), null);
            } catch (SecurityException ex) {
                check = new PathCheck(target, false, false, ex.getMessage());
            }
            if (client != null) {
                PathCheck completed = check;
                client.execute(() -> completeNavigation(token, completed));
            }
        });
        init();
    }

    private void completeNavigation(long token, PathCheck check) {
        if (token != request || minecraft == null || VersionCompat.currentScreen(minecraft) != this) return;
        pending = null;
        if (check.directory()) {
            directory = check.path();
            cachedDirectory = null;
            cachedEntries = List.of();
            preview = null;
            error = null;
            query = "";
            page = 0;
            init();
        } else if (check.regularFile()) {
            read(check.path());
        } else {
            error = check.error() == null || check.error().isBlank()
                    ? Component.translatable("gui.resourcetracker.import.missing_folder").getString()
                    : check.error();
            init();
        }
    }

    private void read(Path path) {
        cancelRead();
        browserPage = page;
        previewView.query = "";
        previewView.page = 0;
        preview = null;
        error = null;
        page = 0;
        long token = ++request;
        var client = minecraft;
        pending = READER.submit(() -> {
            try {
                ImportResult result = ImportService.read(path);
                if (client != null) client.execute(() -> complete(token, result, null));
            } catch (Exception ex) {
                String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                if (client != null) client.execute(() -> complete(token, null, message));
            }
        });
        init();
    }

    private void complete(long token, ImportResult result, String failure) {
        if (token != request || minecraft == null || VersionCompat.currentScreen(minecraft) != this) return;
        pending = null;
        error = failure;
        preview = result;
        if (result != null) previewName = result.name();
        init();
    }

    private void buildPreview() {
        boolean naming = nameField != null && nameField.isFocused();
        int nameCursor = nameField == null ? previewName.length() : nameField.getCursorPosition();
        boolean searching = materialSearch != null && materialSearch.isFocused();
        int cursor = materialSearch == null ? previewView.query.length() : materialSearch.getCursorPosition();
        layout(108, Component.translatable("gui.resourcetracker.draft.title"));
        showPanel();
        EditBox name = new EditBox(font, left, 30, contentWidth, 20,
                Component.translatable("gui.resourcetracker.edit.list_name"));
        name.setMaxLength(120);
        name.setValue(previewName);
        addRenderableWidget(name);
        nameField = name;
        name.setCursorPosition(Math.min(nameCursor, previewName.length()));
        Component source = Component.translatable("gui.resourcetracker.draft.file_source",
                preview.source().getFileName(), preview.materials().size());
        Component backFiles = Component.translatable("gui.resourcetracker.import.back_files");
        int backWidth = font.width(backFiles) + 12;
        var sourceDetails = Component.literal(preview.source().toString()).append("\n").append(source);
        if (!preview.diagnostics().isEmpty()) sourceDetails.append("\n").append(DiagnosticText.of(preview.diagnostics().getFirst()));
        label(source, left, 58, contentWidth - backWidth - 6)
                .setTooltip(net.minecraft.client.gui.components.Tooltip.create(sourceDetails));
        button(backFiles, left + contentWidth - backWidth, 54, backWidth,
                b -> { preview = null; page = browserPage; init(); });
        int notesWidth = preview.diagnostics().isEmpty() ? 0 : 94;
        materialSearch = DraftMaterialsTable.search(this, previewView, left, 80,
                contentWidth - (notesWidth == 0 ? 0 : notesWidth + 6), () -> {
                    if (minecraft != null) minecraft.execute(() -> { init(); setFocused(materialSearch); });
                });
        materialSearch.setCursorPosition(Math.min(cursor, previewView.query.length()));
        if (searching) setFocused(materialSearch);
        if (notesWidth > 0) button(Component.translatable("gui.resourcetracker.draft.notes", preview.diagnostics().size()),
                left + contentWidth - notesWidth, 80, notesWidth,
                b -> { if (minecraft != null) VersionCompat.setScreen(minecraft, new NotesScreen(this,
                        Component.translatable("gui.resourcetracker.draft.notes", preview.diagnostics().size()),
                        preview.diagnostics().stream().map(DiagnosticText::of).toList())); });
        List<TrackerConfig.TrackedItem> materials = preview.materials().entrySet().stream()
                .map(entry -> new TrackerConfig.TrackedItem(entry.getKey(), entry.getValue())).toList();
        DraftMaterialsTable.rows(this, materials, previewView, contentWidth,
                DraftMaterialsTable.Capability.READ_ONLY,
                item -> Component.literal(item.getDisplayName() + " × " + item.targetCount), item -> {});
        int half = (contentWidth - 6) / 2;
        var accept = button(Component.translatable("gui.resourcetracker.import.accept"), left, height - 26, half, b -> accept());
        accept.active = preview.canImport() && TrackerConfig.hasActiveContext() && !TrackerConfig.isGlobalWriteBlocked() && !previewName.isBlank();
        name.setResponder(value -> {
            previewName = value;
            accept.active = preview.canImport() && TrackerConfig.hasActiveContext() && !TrackerConfig.isGlobalWriteBlocked() && !previewName.isBlank();
        });
        button(Component.translatable("gui.cancel"), left + half + 6, height - 26, contentWidth - half - 6, b -> onClose());
        if (naming) setFocused(nameField);
    }

    private void accept() {
        if (preview == null || !preview.canImport() || previewName.isBlank() || !TrackerConfig.hasActiveContext()
                || TrackerConfig.isGlobalWriteBlocked() || minecraft == null) return;
        TrackerConfig.TrackingList created = TrackerConfig.createList(previewName.trim());
        preview.materials().forEach((id, count) -> created.items.add(new TrackerConfig.TrackedItem(id, count)));
        created.isVisible = true;
        TrackerConfig.INSTANCE.lists.add(created);
        TrackerConfig.saveList(created);
        ResourceTrackerClient.invalidateTargetItemCache();
        VersionCompat.setScreen(minecraft, new EditScreen(parent, created));
    }

    private void cancelRead() {
        request++;
        if (pending != null) pending.cancel(true);
        pending = null;
    }

    @Override
    public void onClose() {
        cancelRead();
        super.onClose();
    }

    @Override
    public void removed() {
        cancelRead();
        super.removed();
    }
}
