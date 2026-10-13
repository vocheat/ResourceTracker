package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
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
    private int tab, left, top, windowWidth, windowHeight, contentTop, contentBottom, contentHeight, scrollY;
    private Component recoveryMessage;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("gui.resourcetracker.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        setFocused(null);
        invalidFields.clear();
        contentWidgets.clear();
        labels.clear();
        swatches.clear();
        windowWidth = Math.min(440, width - 20);
        windowHeight = Math.min(340, height - 16);
        left = (width - windowWidth) / 2;
        top = (height - windowHeight) / 2;
        contentTop = top + 62;
        contentBottom = top + windowHeight - 38;
        int tabWidth = (windowWidth - 32) / 3;
        String[] tabs = {"new_lists", "appearance", "files"};
        for (int i = 0; i < tabs.length; i++) {
            int selectedTab = i;
            Button button = TrackerButton.builder(Component.translatable("gui.resourcetracker.settings." + tabs[i]), b -> {
                if (tab == selectedTab) return;
                tab = selectedTab;
                scrollY = 0;
                init();
            }).bounds(left + 12 + i * (tabWidth + 4), top + 32, tabWidth, 20).build();

            addRenderableWidget(button);
        }
        int y = 4;
        if (TrackerConfig.isGlobalWriteBlocked()) {
            y = wrapLabel(y, Component.translatable("gui.resourcetracker.config_recovery_warning",
                    String.valueOf(TrackerConfig.getRecoveryBackupPath())), UiTheme.DANGER);
            addContent(TrackerButton.builder(Component.translatable("gui.resourcetracker.config_recovery"), b -> recover())
                    .bounds(left + 16, 0, windowWidth - 32, 20).build(), y + 4);
            y += 32;
        }
        if (recoveryMessage != null) y = wrapLabel(y, recoveryMessage, UiTheme.ACCENT) + 8;
        if (tab == 0) y = defaults(y);
        else if (tab == 1) y = appearance(y);
        else y = files(y);
        contentHeight = y + 8;
        repositionContent();
        addRenderableWidget(TrackerButton.builder(Component.translatable("gui.resourcetracker.done"), b -> onClose())
                .bounds(width / 2 - 60, top + windowHeight - 28, 120, 20).primary().build());
    }

    private int defaults(int y) {
        y = wrapLabel(y, Component.translatable("gui.resourcetracker.settings.new_lists_note"), UiTheme.MUTED) + 10;
        y = intField(y, "gui.resourcetracker.settings.x", TrackerConfig.INSTANCE.defaultX, null, null,
                "gui.resourcetracker.validation.integer", value -> TrackerConfig.INSTANCE.defaultX = value);
        y = intField(y, "gui.resourcetracker.settings.y", TrackerConfig.INSTANCE.defaultY, null, null,
                "gui.resourcetracker.validation.integer", value -> TrackerConfig.INSTANCE.defaultY = value);
        addLabel(y + 5, Component.translatable("gui.resourcetracker.settings.scale"), UiTheme.TEXT);
        EditBox scale = field(y, "gui.resourcetracker.settings.scale", String.valueOf(TrackerConfig.INSTANCE.defaultScale));
        scale.setResponder(text -> {
            Float value = parseFloat(text);
            boolean valid = value != null && Float.isFinite(value) && value >= TrackerConfig.MIN_SCALE && value <= TrackerConfig.MAX_SCALE;
            markValidity(scale, valid, "gui.resourcetracker.validation.scale");
            if (valid) TrackerConfig.INSTANCE.defaultScale = value;
        });
        y += 28;
        y = intField(y, "gui.resourcetracker.settings.columns", TrackerConfig.INSTANCE.defaultColumns, 0, TrackerConfig.MAX_COLUMNS,
                "gui.resourcetracker.validation.columns", value -> TrackerConfig.INSTANCE.defaultColumns = value);
        y = wrapLabel(y, Component.translatable("gui.resourcetracker.settings.columns_note"), UiTheme.MUTED) + 8;
        int buttonWidth = (windowWidth - 40) / 2;
        addContent(TrackerButton.builder(modeLabel(), b -> {
            TrackerConfig.INSTANCE.defaultShowRemaining = !TrackerConfig.INSTANCE.defaultShowRemaining;
            b.setMessage(modeLabel());
        }).bounds(left + 16, 0, buttonWidth, 20).build(), y);
        addContent(TrackerButton.builder(iconsLabel(), b -> {
            TrackerConfig.INSTANCE.defaultShowIcons = !TrackerConfig.INSTANCE.defaultShowIcons;
            b.setMessage(iconsLabel());
        }).bounds(left + 24 + buttonWidth, 0, buttonWidth, 20).build(), y);
        y += 32;
        addContent(TrackerButton.builder(Component.translatable("gui.resourcetracker.settings.reset_defaults"), b -> {
            TrackerConfig.resetDefaultListSettings();
            init();
        }).bounds(left + 16, 0, windowWidth - 32, 20).build(), y);
        return y + 28;
    }

    private int appearance(int y) {
        y = wrapLabel(y, Component.translatable("gui.resourcetracker.settings.new_lists_note"), UiTheme.MUTED) + 14;
        String[] keys = {"text_color", "title_color", "background_color"};
        for (int i = 0; i < keys.length; i++) {
            int index = i;
            Component label = Component.translatable("gui.resourcetracker.settings." + keys[i]);
            addLabel(y + 5, label, UiTheme.TEXT);
            int buttonX = left + windowWidth - 124;
            Button button = TrackerButton.builder(Component.literal(String.format("#%06X", color(index) & 0xFFFFFF)), b -> {
                if (minecraft != null) minecraft.setScreen(new ColorPickerScreen(this, label, color(index), value -> setColor(index, value)));
            }).bounds(buttonX - 42, 0, 150, 20).build();
            addContent(button, y);
            swatches.add(new Swatch(buttonX - 38, y + 2, index));
            y += 34;
        }
        return wrapLabel(y + 6, Component.translatable("gui.resourcetracker.settings.color_note"), UiTheme.MUTED);
    }

    private int files(int y) {
        y = wrapLabel(y, Component.translatable("gui.resourcetracker.settings.files_note"), UiTheme.MUTED) + 12;
        Button world = TrackerButton.builder(Component.translatable("gui.resourcetracker.open_world_lists"), b -> TrackerConfig.openActiveListsFolder())
                .bounds(left + 16, 0, windowWidth - 32, 20).build();
        world.active = TrackerConfig.hasActiveContext();
        addContent(world, y);
        y += 28;
        addContent(TrackerButton.builder(Component.translatable("gui.resourcetracker.open_all_lists"), b -> TrackerConfig.openListsRootFolder())
                .bounds(left + 16, 0, windowWidth - 32, 20).build(), y);
        y += 28;
        Button reload = TrackerButton.builder(Component.translatable("gui.resourcetracker.reload_lists"), b -> {
                    TrackerConfig.reloadActiveContextLists();
                    net.fabricmc.resourcetracker.client.ResourceTrackerClient.invalidateTargetItemCache();
                })
                .bounds(left + 16, 0, windowWidth - 32, 20).build();
        reload.active = TrackerConfig.hasActiveContext();
        addContent(reload, y);
        return y + 28;
    }

    private void recover() {
        if (minecraft == null) return;
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) recoveryMessage = Component.translatable(TrackerConfig.recoverConfig().name().equals("SUCCESS")
                    ? "gui.resourcetracker.config_recovery_success" : "gui.resourcetracker.config_recovery_failed");
            if (minecraft != null) minecraft.setScreen(this);
        }, Component.translatable("gui.resourcetracker.config_recovery"), Component.translatable("gui.resourcetracker.config_recovery_confirm")));
    }

    private int intField(int y, String key, int value, Integer min, Integer max, String validation, IntConsumer setter) {
        addLabel(y + 5, Component.translatable(key), UiTheme.TEXT);
        EditBox field = field(y, key, String.valueOf(value));
        field.setResponder(text -> {
            Integer parsed = parseInteger(text);
            boolean valid = parsed != null && (min == null || parsed >= min) && (max == null || parsed <= max);
            markValidity(field, valid, validation);
            if (valid) setter.accept(parsed);
        });
        return y + 28;
    }

    private EditBox field(int y, String key, String value) {
        EditBox field = new EditBox(font, left + windowWidth - 124, 0, 108, 20, Component.translatable(key));
        field.setMaxLength(16);
        field.setValue(value);
        field.setTextColor(UiTheme.TEXT);
        addContent(field, y);
        return field;
    }

    private void addContent(AbstractWidget widget, int y) {
        contentWidgets.put(widget, y);
        addRenderableWidget(widget);
        addWidget(new ScrollFocusTarget(widget));
    }
    private void addLabel(int y, Component text, int color) { labels.add(new Label(y, text.getVisualOrderText(), color)); }
    private int wrapLabel(int y, Component text, int color) {
        for (FormattedCharSequence line : font.split(text, windowWidth - 36)) {
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
        if (mouseX >= left && mouseX <= left + windowWidth && mouseY >= contentTop && mouseY <= contentBottom) {
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
        int tabWidth = (windowWidth - 32) / 3;
        int activeX = left + 12 + tab * (tabWidth + 4);
        context.fill(activeX, top + 53, activeX + tabWidth, top + 55, UiTheme.ACCENT);
        context.fill(left + 10, contentTop, left + windowWidth - 10, contentBottom, UiTheme.PANEL);
        context.enableScissor(left + 12, contentTop, left + windowWidth - 12, contentBottom);
        for (Label label : labels) context.drawString(font, label.text(), left + 16, contentTop + label.y() - scrollY, label.color(), false);
        for (Swatch swatch : swatches) ColorPickerScreen.drawSwatch(context, swatch.x() + 7, contentTop + swatch.y() - scrollY, 16, 16, color(swatch.index()));
        context.disableScissor();
        super.render(context, mouseX, mouseY, delta);
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
        if (minecraft != null) minecraft.setScreen(parent);
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
    private record Swatch(int x, int y, int index) {}
}
