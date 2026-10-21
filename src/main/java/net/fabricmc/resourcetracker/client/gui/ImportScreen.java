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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
    private long request;
    private boolean searchFocused;

    public ImportScreen(net.minecraft.client.gui.screens.Screen parent) {
        super(parent, Component.translatable("gui.resourcetracker.import.title"));
    }

    @Override
    protected void init() {
        layout(138);
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
        } else if (preview != null) {
            buildPreview();
        } else {
            buildFiles();
        }
        button(Component.translatable("gui.cancel"), left + contentWidth - 90, height - 26, 90, b -> onClose());
    }

    // Rebuilding through the screen's normal initialization keeps filtering and paging identical.
    private void refreshRows() {
        if (minecraft != null) minecraft.execute(this::init);
    }

    private void buildFiles() {
        List<Path> entries = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            String filter = query.toLowerCase(Locale.ROOT);
            paths.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).contains(filter))
                    .filter(path -> Files.isDirectory(path) || supported(path))
                    .sorted(Comparator.<Path, Boolean>comparing(path -> !Files.isDirectory(path))
                            .thenComparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(entries::add);
        } catch (IOException | SecurityException ex) { error = ex.getMessage(); }
        label(Component.literal(error == null ? directory.toString() : error), left, 110, contentWidth);
        pagination(entries.size());
        for (int i = page * pageSize; i < Math.min(entries.size(), (page + 1) * pageSize); i++) {
            Path entry = entries.get(i);
            boolean folder = Files.isDirectory(entry);
            String name = (folder ? "[+] " : "") + entry.getFileName();
            button(Component.literal(name), left, rowTop + (i % pageSize) * 24, contentWidth,
                    b -> { if (folder) navigate(entry); else read(entry); });
        }
        if (entries.isEmpty() && error == null) label(Component.translatable("gui.resourcetracker.edit.no_results"), left, rowTop, contentWidth);
    }

    private boolean supported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".litematic") || name.endsWith(".json") || name.endsWith(".txt");
    }

    private void navigate(Path path) {
        if (!Files.isDirectory(path)) { if (Files.isRegularFile(path)) read(path); else { error = Component.translatable("gui.resourcetracker.import.missing_folder").getString(); init(); } return; }
        cancelRead();
        directory = path.toAbsolutePath().normalize();
        preview = null;
        error = null;
        query = "";
        page = 0;
        init();
    }

    private void read(Path path) {
        cancelRead();
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
        init();
    }

    private void buildPreview() {
        label(Component.translatable("gui.resourcetracker.import.preview", preview.name(), preview.materials().size()), left, 110, contentWidth);
        List<Component> rows = new ArrayList<>();
        for (String diagnostic : preview.diagnostics()) rows.add(DiagnosticText.of(diagnostic));
        for (Map.Entry<String, Integer> entry : preview.materials().entrySet()) {
            var item = new TrackerConfig.TrackedItem(entry.getKey(), entry.getValue());
            rows.add(Component.literal(item.getDisplayName() + " × " + entry.getValue()));
        }
        pagination(rows.size());
        for (int i = page * pageSize; i < Math.min(rows.size(), (page + 1) * pageSize); i++) {
            label(rows.get(i), left, rowTop + (i % pageSize) * 24 + 4, contentWidth);
        }
        var accept = button(Component.translatable("gui.resourcetracker.import.accept"), left, height - 26, contentWidth - 96, b -> accept());
        accept.active = preview.canImport() && TrackerConfig.hasActiveContext();
        button(Component.translatable("gui.resourcetracker.import.back_files"), left + 138, height - 52, contentWidth - 138,
                b -> { preview = null; page = 0; init(); });
    }

    private void accept() {
        if (preview == null || !preview.canImport() || !TrackerConfig.hasActiveContext() || minecraft == null) return;
        TrackerConfig.TrackingList created = TrackerConfig.createList(preview.name());
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
