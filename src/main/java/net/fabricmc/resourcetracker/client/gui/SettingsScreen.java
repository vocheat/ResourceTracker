package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.config.TrackerHistory;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

public class SettingsScreen extends Screen {
    private final Screen parent;
    private final Map<EditBox, Component> invalidFields = new IdentityHashMap<>();
    private final Map<AbstractWidget, Integer> contentWidgets = new IdentityHashMap<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<Swatch> swatches = new ArrayList<>();
    private static final List<String> SECTIONS = List.of("new_lists", "notifications", "keys", "files");
    private final Map<String, String> fieldDrafts = new java.util.HashMap<>();
    private final Map<String, Integer> fieldCursors = new java.util.HashMap<>();
    private final Map<AbstractWidget, String> focusKeys = new IdentityHashMap<>();
    private String section, focusKey;
    private int left, top, windowWidth, windowHeight, contentLeft, contentWidth, contentTop, contentBottom, contentHeight, scrollY;
    private boolean wide;
    private List<FormattedCharSequence> entryWarning = List.of();
    private Component recoveryMessage;

    public SettingsScreen(Screen parent) { this(parent, "new_lists"); }

    public SettingsScreen(Screen parent, String section) {
        super(Component.translatable("gui.resourcetracker.settings.title"));
        this.parent = parent;
        this.section = SECTIONS.contains(section) ? section : "new_lists";
    }

    @Override
    protected void init() {
        if (getFocused() instanceof AbstractWidget focused) focusKey = focusKeys.get(focused);
        for (Map.Entry<AbstractWidget, String> entry : focusKeys.entrySet()) {
            if (entry.getKey() instanceof EditBox field) fieldCursors.put(entry.getValue(), field.getCursorPosition());
        }
        clearWidgets();
        setFocused(null);
        invalidFields.clear();
        contentWidgets.clear();
        focusKeys.clear();
        labels.clear();
        swatches.clear();
        windowWidth = Math.min(650, width - 20);
        windowHeight = Math.min(390, height - 16);
        left = (width - windowWidth) / 2;
        top = (height - windowHeight) / 2;
        wide = windowWidth >= 570 && windowHeight >= 260;
        entryWarning = TrackerConfig.isGlobalWriteBlocked()
                ? font.split(Component.translatable("gui.resourcetracker.settings.write_blocked"), windowWidth - 32) : List.of();
        int navigationTop = top + 32 + entryWarning.size() * 12;
        contentTop = wide ? navigationTop : navigationTop + 28;
        contentBottom = top + windowHeight - 38;
        contentLeft = wide ? left + 184 : left + 16;
        contentWidth = left + windowWidth - 16 - contentLeft;
        if (wide) {
            for (int i = 0; i < SECTIONS.size(); i++) {
                String value = SECTIONS.get(i);
                Button button = TrackerButton.builder(sectionName(value), b -> selectSection(value))
                        .bounds(left + 12, navigationTop + i * 28, 160, 20).build();
                addRenderableWidget(button);
            }
        } else {
            addRenderableWidget(TrackerButton.builder(Component.translatable("gui.resourcetracker.settings.section", sectionName(section)), b -> {
                if (minecraft != null) VersionCompat.setScreen(minecraft, new ChoiceScreen<>(this,
                        Component.translatable("gui.resourcetracker.settings.section_title"), SECTIONS, section,
                        this::sectionName, this::selectSection));
            }).bounds(left + 16, navigationTop, windowWidth - 32, 20).primary().build());
        }
        int y = 4;
        if (recoveryMessage != null) y = wrapLabel(y, recoveryMessage, UiTheme.ACCENT) + 8;
        y = switch (section) {
            case "notifications" -> notifications(y);
            case "keys" -> keys(y);
            case "files" -> files(y);
            default -> defaults(y);
        };
        contentHeight = y + 8;
        repositionContent();
        addRenderableWidget(TrackerButton.builder(Component.translatable("gui.resourcetracker.done"), b -> onClose())
                .bounds(width / 2 - 60, top + windowHeight - 28, 120, 20).primary().build());
        for (Map.Entry<AbstractWidget, String> entry : focusKeys.entrySet()) {
            if (entry.getValue().equals(focusKey) && entry.getKey().active) { setFocused(entry.getKey()); break; }
        }
    }

    private Component sectionName(String value) { return Component.translatable("gui.resourcetracker.settings." + value); }
    private void selectSection(String value) {
        if (!section.equals(value)) {
            section = value;
            scrollY = 0;
            focusKey = null;
            setFocused(null);
        }
        init();
    }

    private int defaults(int y) {
        y = wrapLabel(y, Component.translatable("gui.resourcetracker.settings.new_lists_note"), UiTheme.MUTED) + 10;
        y = intField(y, "gui.resourcetracker.settings.x", TrackerConfig.INSTANCE.defaultX, null, null,
                "gui.resourcetracker.validation.integer", value -> TrackerConfig.INSTANCE.defaultX = value);
        y = intField(y, "gui.resourcetracker.settings.y", TrackerConfig.INSTANCE.defaultY, null, null,
                "gui.resourcetracker.validation.integer", value -> TrackerConfig.INSTANCE.defaultY = value);
        addLabel(y, Component.translatable("gui.resourcetracker.settings.scale"), UiTheme.TEXT);
        EditBox scale = field(y + 14, "gui.resourcetracker.settings.scale", String.valueOf(TrackerConfig.INSTANCE.defaultScale));
        scale.setResponder(text -> {
            fieldDrafts.put("gui.resourcetracker.settings.scale", text);
            Float value = parseFloat(text);
            boolean valid = value != null && Float.isFinite(value) && value >= TrackerConfig.MIN_SCALE && value <= TrackerConfig.MAX_SCALE;
            markValidity(scale, valid, "gui.resourcetracker.validation.scale");
            if (valid) TrackerConfig.INSTANCE.defaultScale = value;
        });
        Float scaleValue = parseFloat(scale.getValue());
        markValidity(scale, scaleValue != null && Float.isFinite(scaleValue) && scaleValue >= TrackerConfig.MIN_SCALE && scaleValue <= TrackerConfig.MAX_SCALE, "gui.resourcetracker.validation.scale");
        y += 42;
        addContent(TrackerButton.builder(columnsLabel(TrackerConfig.INSTANCE.defaultColumns), b -> {
            if (minecraft != null) VersionCompat.setScreen(minecraft, new ChoiceScreen<>(this,
                    Component.translatable("gui.resourcetracker.settings.columns"), List.of(0, 1, 2, 3, 4, 5),
                    TrackerConfig.INSTANCE.defaultColumns, this::columnsLabel, value -> TrackerConfig.INSTANCE.defaultColumns = value));
        }).bounds(contentLeft, 0, contentWidth, 20).build(), y);
        y += 28;
        int buttonWidth = contentWidth;
        addContent(TrackerButton.builder(modeLabel(), b -> {
            TrackerConfig.INSTANCE.defaultShowRemaining = !TrackerConfig.INSTANCE.defaultShowRemaining;
            b.setMessage(modeLabel());
        }).bounds(contentLeft, 0, buttonWidth, 20).build(), y);
        y += 26;
        addContent(TrackerButton.builder(iconsLabel(), b -> {
            TrackerConfig.INSTANCE.defaultShowIcons = !TrackerConfig.INSTANCE.defaultShowIcons;
            b.setMessage(iconsLabel());
        }).bounds(contentLeft, 0, buttonWidth, 20).build(), y);
        y = appearance(y + 32) + 12;
        addContent(TrackerButton.builder(Component.translatable("gui.resourcetracker.settings.reset_defaults"), b -> {
            TrackerConfig.resetDefaultListSettings();
            fieldDrafts.clear();
            init();
        }).bounds(contentLeft, 0, contentWidth, 20).build(), y);
        return y + 28;
    }

    private int appearance(int y) {
        y = wrapLabel(y, Component.translatable("gui.resourcetracker.edit.group_colors"), UiTheme.ACCENT) + 8;
        String[] keys = {"text_color", "title_color", "background_color"};
        for (int i = 0; i < keys.length; i++) {
            int index = i;
            Component label = Component.translatable("gui.resourcetracker.settings." + keys[i]);
            addLabel(y, label, UiTheme.TEXT);
            y += 14;
            int buttonX = contentLeft;
            Button button = TrackerButton.builder(Component.literal(String.format("#%06X", color(index) & 0xFFFFFF)), b -> {
                if (minecraft != null) VersionCompat.setScreen(minecraft, new ColorPickerScreen(this, label, color(index), value -> setColor(index, value)));
            }).bounds(buttonX, 0, contentWidth, 20).build();
            addContent(button, y);
            swatches.add(new Swatch(button, index));
            y += 28;
        }
        return wrapLabel(y + 6, Component.translatable("gui.resourcetracker.settings.color_note"), UiTheme.MUTED);
    }


    private int notifications(int y) {
        y = wrapLabel(y, Component.translatable("gui.resourcetracker.settings.notifications_note"), UiTheme.MUTED) + 10;
        addContent(TrackerButton.builder(notificationToggle("resource_setting", TrackerConfig.INSTANCE.notifyResources), b -> {
            TrackerConfig.INSTANCE.notifyResources = !TrackerConfig.INSTANCE.notifyResources;
            b.setMessage(notificationToggle("resource_setting", TrackerConfig.INSTANCE.notifyResources));
        }).bounds(contentLeft, 0, contentWidth, 20).build(), y);
        y += 26;
        addContent(TrackerButton.builder(notificationToggle("list_setting", TrackerConfig.INSTANCE.notifyLists), b -> {
            TrackerConfig.INSTANCE.notifyLists = !TrackerConfig.INSTANCE.notifyLists;
            b.setMessage(notificationToggle("list_setting", TrackerConfig.INSTANCE.notifyLists));
        }).bounds(contentLeft, 0, contentWidth, 20).build(), y);
        y += 26;
        addContent(TrackerButton.builder(notificationSoundLabel(), b -> {
            if (minecraft != null) VersionCompat.setScreen(minecraft, new ChoiceScreen<>(this,
                    Component.translatable("gui.resourcetracker.settings.sound_title"), List.of(0, 1, 2),
                    TrackerConfig.INSTANCE.completionSound, value -> Component.translatable("gui.resourcetracker.notifications.sound." + value),
                    value -> TrackerConfig.INSTANCE.completionSound = value));
        }).bounds(contentLeft, 0, contentWidth, 20).build(), y);
        y = intField(y + 28, "gui.resourcetracker.notifications.volume", TrackerConfig.INSTANCE.completionVolume,
                0, 100, "gui.resourcetracker.notifications.volume_validation", value -> TrackerConfig.INSTANCE.completionVolume = value);
        return TrackerConfig.INSTANCE.completionSound == 0
                ? wrapLabel(y, Component.translatable("gui.resourcetracker.settings.muted_volume_note"), UiTheme.MUTED) + 8 : y;
    }

    private Component notificationToggle(String key, boolean enabled) {
        return Component.translatable("gui.resourcetracker.notifications." + key,
                Component.translatable(enabled ? "options.on" : "options.off"));
    }

    private Component notificationSoundLabel() {
        return Component.translatable("gui.resourcetracker.notifications.sound_setting",
                Component.translatable("gui.resourcetracker.notifications.sound." + TrackerConfig.INSTANCE.completionSound));
    }

    private Component columnsLabel(int value) {
        return value == 0 ? Component.translatable("gui.resourcetracker.edit.columns_auto")
                : Component.translatable("gui.resourcetracker.edit.columns", value);
    }

    private int keys(int y) {
        String[] names = {"open", "toggle_hud", "quick_add", "select_area"};
        for (String name : names) {
            String id = "key.resourcetracker." + name;
            Component assigned = Component.translatable("key.keyboard.unknown");
            if (minecraft != null) for (KeyMapping mapping : minecraft.options.keyMappings) {
                if (mapping.getName().equals(id)) { assigned = mapping.getTranslatedKeyMessage(); break; }
            }
            y = wrapLabel(y, Component.translatable("gui.resourcetracker.settings.key_assignment", Component.translatable(id), assigned), UiTheme.TEXT) + 12;
        }
        addContent(TrackerButton.builder(Component.translatable("gui.resourcetracker.settings.assign_keys"), b -> {
            if (minecraft != null) VersionCompat.setScreen(minecraft, new KeyBindsScreen(this, minecraft.options));
        }).bounds(contentLeft, 0, contentWidth, 20).build(), y);
        return y + 28;
    }

    private int files(int y) {
        y = wrapLabel(y, Component.translatable("gui.resourcetracker.settings.files_note"), UiTheme.MUTED) + 12;
        if (!TrackerConfig.hasActiveContext()) y = wrapLabel(y, Component.translatable("gui.resourcetracker.settings.no_world"), UiTheme.MUTED) + 8;
        if (TrackerConfig.isGlobalWriteBlocked()) {
            y = wrapLabel(y, Component.translatable("gui.resourcetracker.config_recovery_warning",
                    String.valueOf(TrackerConfig.getRecoveryBackupPath())), UiTheme.DANGER) + 8;
            addContent(TrackerButton.builder(Component.translatable("gui.resourcetracker.config_recovery"), b -> recover())
                    .bounds(contentLeft, 0, contentWidth, 20).build(), y);
            y += 28;
        }
        Button world = TrackerButton.builder(Component.translatable("gui.resourcetracker.open_world_lists"), b -> TrackerConfig.openActiveListsFolder())
                .bounds(contentLeft, 0, contentWidth, 20).build();
        world.active = TrackerConfig.hasActiveContext();
        addContent(world, y);
        y += 28;
        addContent(TrackerButton.builder(Component.translatable("gui.resourcetracker.open_all_lists"), b -> TrackerConfig.openListsRootFolder())
                .bounds(contentLeft, 0, contentWidth, 20).build(), y);
        y += 28;
        Button reload = TrackerButton.builder(Component.translatable("gui.resourcetracker.reload_lists"), b -> {
                    TrackerConfig.reloadActiveContextLists();
                    net.fabricmc.resourcetracker.client.ResourceTrackerClient.invalidateTargetItemCache();
                    init();
                })
                .bounds(contentLeft, 0, contentWidth, 20).build();
        reload.active = TrackerConfig.hasActiveContext();
        addContent(reload, y);
        y += 28;
        Button restore = TrackerButton.builder(Component.translatable("gui.resourcetracker.undo_delete"), b -> {
            TrackerHistory.undoDeletion();
            init();
        }).bounds(contentLeft, 0, contentWidth, 20).build();
        restore.active = TrackerHistory.canUndoDeletion();
        addContent(restore, y);
        y += 28;
        if (!restore.active) y = wrapLabel(y, Component.translatable("gui.resourcetracker.settings.no_deleted"), UiTheme.MUTED) + 8;
        return y;
    }

    private void recover() {
        if (minecraft == null) return;
        VersionCompat.setScreen(minecraft, new ConfirmScreen(confirmed -> {
            if (confirmed) recoveryMessage = Component.translatable(TrackerConfig.recoverConfig().name().equals("SUCCESS")
                    ? "gui.resourcetracker.config_recovery_success" : "gui.resourcetracker.config_recovery_failed");
            if (minecraft != null) VersionCompat.setScreen(minecraft, this);
        }, Component.translatable("gui.resourcetracker.config_recovery"), Component.translatable("gui.resourcetracker.config_recovery_confirm")));
    }

    private int intField(int y, String key, int value, Integer min, Integer max, String validation, IntConsumer setter) {
        addLabel(y, Component.translatable(key), UiTheme.TEXT);
        EditBox field = field(y + 14, key, String.valueOf(value));
        field.setResponder(fieldResponder(field, key, min, max, validation, setter));
        fieldResponder(field, key, min, max, validation, setter).accept(field.getValue());
        return y + 42;
    }

    private java.util.function.Consumer<String> fieldResponder(EditBox field, String key, Integer min, Integer max, String validation, IntConsumer setter) {
        return text -> {
            fieldDrafts.put(key, text);
            Integer parsed = parseInteger(text);
            boolean valid = parsed != null && (min == null || parsed >= min) && (max == null || parsed <= max);
            markValidity(field, valid, validation);
            if (valid) setter.accept(parsed);
        };
    }

    private EditBox field(int y, String key, String value) {
        EditBox field = new EditBox(font, contentLeft, 0, contentWidth, 20, Component.translatable(key));
        field.setMaxLength(16);
        field.setValue(fieldDrafts.getOrDefault(key, value));
        field.setCursorPosition(Math.min(field.getValue().length(), fieldCursors.getOrDefault(key, field.getValue().length())));
        focusKeys.put(field, key);
        field.setTextColor(UiTheme.TEXT);
        addContent(field, y);
        return field;
    }

    private void addContent(AbstractWidget widget, int y) {
        contentWidgets.put(widget, y);
        focusKeys.putIfAbsent(widget, "control_" + contentWidgets.size());
        if (widget instanceof Button) widget.setTooltip(net.minecraft.client.gui.components.Tooltip.create(widget.getMessage()));
        addRenderableWidget(widget);
        addWidget(new ScrollFocusTarget(widget));
    }
    private void addLabel(int y, Component text, int color) { labels.add(new Label(y, text.getVisualOrderText(), color)); }
    private int wrapLabel(int y, Component text, int color) {
        for (FormattedCharSequence line : font.split(text, contentWidth)) {
            labels.add(new Label(y, line, color));
            y += 12;
        }
        return y;
    }
    private void repositionContent() {
        scrollY = Math.max(0, Math.min(scrollY, Math.max(0, contentHeight - (contentBottom - contentTop))));
        contentWidgets.forEach((widget, y) -> {
            widget.setY(contentTop + y - scrollY);
            widget.visible = widget.getY() >= contentTop && widget.getY() + widget.getHeight() <= contentBottom;
        });
        if (getFocused() instanceof AbstractWidget focused && !focused.visible) setFocused(null);
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= contentLeft && mouseX <= contentLeft + contentWidth && mouseY >= contentTop && mouseY <= contentBottom) {
            scrollY -= (int) (verticalAmount * 20);
            repositionContent();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {}
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x90000000);
        context.fill(left - 1, top - 1, left + windowWidth + 1, top + windowHeight + 1, UiTheme.BORDER);
        context.fill(left, top, left + windowWidth, top + windowHeight, UiTheme.WINDOW);
        context.fill(left, top, left + windowWidth, top + 26, UiTheme.HEADER);
        context.drawCenteredString(font, title, width / 2, top + 9, UiTheme.TEXT);
        for (int i = 0; i < entryWarning.size(); i++) context.drawString(font, entryWarning.get(i), left + 16, top + 31 + i * 12, UiTheme.DANGER, false);
        context.fill(contentLeft - 6, contentTop, contentLeft + contentWidth + 6, contentBottom, UiTheme.PANEL);
        context.enableScissor(contentLeft - 2, contentTop, contentLeft + contentWidth + 2, contentBottom);
        for (Label label : labels) context.drawString(font, label.text(), contentLeft, contentTop + label.y() - scrollY, label.color(), false);
        context.disableScissor();
        super.render(context, mouseX, mouseY, delta);
        if (wide) {
            int y = contentTop + SECTIONS.indexOf(section) * 28 + 21;
            context.fill(left + 12, y, left + 172, y + 2, UiTheme.ACCENT);
        }
        context.enableScissor(contentLeft - 2, contentTop, contentLeft + contentWidth + 2, contentBottom);
        for (Swatch swatch : swatches) if (swatch.button().visible) ColorPickerScreen.drawSwatch(context, swatch.button().getX() + 7, swatch.button().getY() + 2, 16, 16, color(swatch.index()));
        context.disableScissor();
        if (contentHeight > contentBottom - contentTop) {
            int trackHeight = contentBottom - contentTop;
            int thumbHeight = Math.max(12, trackHeight * trackHeight / contentHeight);
            int thumbY = contentTop + scrollY * (trackHeight - thumbHeight) / Math.max(1, contentHeight - trackHeight);
            context.fill(left + windowWidth - 6, contentTop, left + windowWidth - 4, contentBottom, UiTheme.BORDER);
            context.fill(left + windowWidth - 6, thumbY, left + windowWidth - 4, thumbY + thumbHeight, UiTheme.ACCENT);
        }
        for (Map.Entry<EditBox, Component> entry : invalidFields.entrySet()) {
            if (entry.getKey().visible && (entry.getKey().isFocused() || entry.getKey().isMouseOver(mouseX, mouseY))) {
                VersionCompat.setTooltip(context, font, entry.getValue(), mouseX, mouseY);
                break;
            }
        }
    }
    private void markValidity(EditBox field, boolean valid, String key) {
        field.setTextColor(valid ? UiTheme.TEXT : UiTheme.DANGER);
        if (valid) invalidFields.remove(field);
        else invalidFields.put(field, Component.translatable(key));
    }
    private Integer parseInteger(String value) {
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException ignored) { return null; }
    }
    private Float parseFloat(String value) {
        try { return Float.parseFloat(value.trim()); } catch (NumberFormatException ignored) { return null; }
    }
    private Component modeLabel() {
        return Component.translatable(TrackerConfig.INSTANCE.defaultShowRemaining ? "gui.resourcetracker.edit.mode_need" : "gui.resourcetracker.edit.mode_count");
    }
    private Component iconsLabel() {
        return Component.translatable(TrackerConfig.INSTANCE.defaultShowIcons ? "gui.resourcetracker.edit.icons_on" : "gui.resourcetracker.edit.icons_off");
    }
    private int color(int index) {
        return switch (index) {
            case 0 -> TrackerConfig.INSTANCE.defaultTextColor;
            case 1 -> TrackerConfig.INSTANCE.defaultNameColor;
            default -> TrackerConfig.INSTANCE.defaultBackgroundColor;
        };
    }
    private void setColor(int index, int value) {
        switch (index) {
            case 0 -> TrackerConfig.INSTANCE.defaultTextColor = value;
            case 1 -> TrackerConfig.INSTANCE.defaultNameColor = value;
            default -> TrackerConfig.INSTANCE.defaultBackgroundColor = value;
        }
    }
    @Override
    public void onClose() {
        TrackerConfig.saveGlobalSettingsOnly();
        if (minecraft != null) VersionCompat.setScreen(minecraft, parent);
    }

    @Override
    public void setFocused(GuiEventListener focused) {
        super.setFocused(focused);
        if (focused instanceof AbstractWidget widget && contentWidgets.containsKey(widget)) {
            int y = contentWidgets.get(widget);
            int viewportHeight = contentBottom - contentTop;
            if (y < scrollY) scrollY = y;
            else if (y + widget.getHeight() > scrollY + viewportHeight) scrollY = y + widget.getHeight() - viewportHeight;
            repositionContent();
        }
    }

    /** Allows native Tab navigation to reveal clipped controls without exposing them to mouse input. */
    private static final class ScrollFocusTarget implements GuiEventListener, NarratableEntry {
        private final AbstractWidget widget;
        private ScrollFocusTarget(AbstractWidget widget) { this.widget = widget; }
        @Override
        public ComponentPath nextFocusPath(FocusNavigationEvent event) {
            return event instanceof FocusNavigationEvent.TabNavigation && widget.active && !widget.visible && !widget.isFocused()
                    ? ComponentPath.leaf(widget) : null;
        }
        @Override
        public void setFocused(boolean focused) {}
        @Override
        public boolean isFocused() { return false; }
        @Override
        public boolean isActive() { return false; }
        @Override
        public NarrationPriority narrationPriority() { return NarrationPriority.NONE; }
        @Override
        public void updateNarration(NarrationElementOutput output) {}
    }

    private record Label(int y, FormattedCharSequence text, int color) {}
    private record Swatch(Button button, int index) {}
}
