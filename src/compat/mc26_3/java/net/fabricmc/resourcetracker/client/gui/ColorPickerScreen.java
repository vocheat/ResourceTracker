package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/** Edits RGB and alpha separately without discarding any ARGB channel. */
public class ColorPickerScreen extends Screen {
    private static final int[] PRESETS = {
            0xFFFFFF, 0xD8DBD2, 0x555B50, 0x111510, 0xF06A64, 0xEAB85C,
            0xF0E781, 0xA7D779, 0x67CBB5, 0x6EAAE7, 0xB798E6, 0xE39CC4
    };
    private final Screen parent;
    private final IntConsumer onChange;
    private final int originalColor;
    private final Map<AbstractWidget, Integer> contentWidgets = new IdentityHashMap<>();
    private final Map<EditBox, Component> invalidFields = new IdentityHashMap<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<Preset> presets = new ArrayList<>();
    private final EditBox[] channels = new EditBox[4];
    private EditBox hexField, alphaField;
    private int color, left, top, windowWidth, windowHeight, contentTop, contentBottom, contentHeight, scrollY, previewY, alphaY;
    private boolean advanced, updatingFields;

    public ColorPickerScreen(Screen parent, Component title, int initialColor, IntConsumer onChange) {
        super(title);
        this.parent = parent;
        this.color = initialColor;
        this.originalColor = initialColor;
        this.onChange = onChange;
    }

    @Override
    protected void init() {
        clearWidgets();
        setFocused(null);
        contentWidgets.clear();
        invalidFields.clear();
        labels.clear();
        presets.clear();
        windowWidth = Math.min(380, width - 20);
        windowHeight = Math.min(340, height - 16);
        left = (width - windowWidth) / 2;
        top = (height - windowHeight) / 2;
        contentTop = top + 34;
        contentBottom = top + windowHeight - 38;
        int y = 4;
        label(y, "gui.resourcetracker.color.preview", UiTheme.MUTED);
        previewY = y + 15;
        y += 76;
        label(y, "gui.resourcetracker.color.presets", UiTheme.MUTED);
        y += 16;
        int columns = Math.max(4, Math.min(6, (windowWidth - 32) / 48));
        int cellWidth = (windowWidth - 32 - (columns - 1) * 4) / columns;
        for (int i = 0; i < PRESETS.length; i++) {
            int rgb = PRESETS[i];
            int x = left + 16 + (i % columns) * (cellWidth + 4);
            int rowY = y + (i / columns) * 26;
            Button button = TrackerButton.builder(Component.literal(String.format("#%06X", rgb)), b -> applyColor((color & 0xFF000000) | rgb, null))
                    .bounds(x, 0, cellWidth, 20).build();
            button.setTooltip(Tooltip.create(Component.literal(String.format("#%06X", rgb))));
            addContent(button, rowY);
            presets.add(new Preset(button, rgb));
        }
        y += ((PRESETS.length + columns - 1) / columns) * 26 + 6;
        label(y, "gui.resourcetracker.color.hex", UiTheme.TEXT);
        hexField = field(left + 16, y + 14, windowWidth - 32, 7, Component.translatable("gui.resourcetracker.color.hex"), String.format("#%06X", color & 0xFFFFFF));
        hexField.setResponder(text -> {
            if (updatingFields) return;
            String value = text.startsWith("#") ? text.substring(1) : text;
            boolean valid = value.matches("[0-9a-fA-F]{6}");
            markValidity(hexField, valid, "gui.resourcetracker.validation.hex");
            if (valid) applyColor((color & 0xFF000000) | Integer.parseInt(value, 16), hexField);
        });
        y += 44;
        label(y, "gui.resourcetracker.color.alpha", UiTheme.TEXT);
        alphaY = y + 20;
        alphaField = field(left + 16, y + 14, 80, 3, Component.translatable("gui.resourcetracker.color.alpha"), String.valueOf(color >>> 24));
        alphaField.setResponder(text -> {
            if (updatingFields) return;
            Integer value = parseByte(text);
            markValidity(alphaField, value != null, "gui.resourcetracker.validation.rgba");
            if (value != null) applyColor((value << 24) | (color & 0xFFFFFF), alphaField);
        });
        y += 44;
        addContent(TrackerButton.builder(Component.translatable(advanced ? "gui.resourcetracker.color.advanced_hide" : "gui.resourcetracker.color.advanced"), b -> {
            // Only this explicit section toggle rebuilds widgets; wheel scrolling retains input and focus.
            advanced = !advanced;
            init();
        }).bounds(left + 16, 0, windowWidth - 32, 20).build(), y);
        y += 30;
        if (advanced) {
            int channelWidth = (windowWidth - 44) / 4;
            String[] names = {"R", "G", "B", "A"};
            for (int i = 0; i < channels.length; i++) {
                int index = i;
                int x = left + 16 + i * (channelWidth + 4);
                labels.add(new Label(x, y, Component.literal(names[i]), UiTheme.MUTED));
                channels[i] = field(x, y + 14, channelWidth, 3, Component.literal(names[i] + " (0–255)"), String.valueOf(channel(i)));
                channels[i].setResponder(text -> {
                    if (!updatingFields) applyChannels(index);
                });
            }
            y += 44;
        }
        contentHeight = y + 6;
        repositionContent();
        int footerWidth = (windowWidth - 40) / 2;
        addRenderableWidget(TrackerButton.builder(Component.translatable("gui.resourcetracker.color.original"), b -> applyColor(originalColor, null))
                .bounds(left + 16, top + windowHeight - 28, footerWidth, 20).build());
        addRenderableWidget(TrackerButton.builder(Component.translatable("gui.resourcetracker.done"), b -> onClose())
                .bounds(left + 24 + footerWidth, top + windowHeight - 28, footerWidth, 20).primary().build());
    }

    private int channel(int index) { return (color >>> (index == 3 ? 24 : 16 - index * 8)) & 255; }

    private void applyChannels(int editedIndex) {
        int[] values = new int[4];
        boolean valid = true;
        for (int i = 0; i < channels.length; i++) {
            Integer value = parseByte(channels[i].getValue());
            markValidity(channels[i], value != null, "gui.resourcetracker.validation.rgba");
            if (value == null) valid = false;
            else values[i] = value;
        }
        if (valid) applyColor((values[3] << 24) | (values[0] << 16) | (values[1] << 8) | values[2], channels[editedIndex]);
    }

    private void applyColor(int value, EditBox source) {
        color = value;
        onChange.accept(value);
        updatingFields = true;
        try {
            syncField(hexField, source, String.format("#%06X", color & 0xFFFFFF));
            syncField(alphaField, source, String.valueOf(color >>> 24));
            if (advanced) for (int i = 0; i < channels.length; i++) syncField(channels[i], source, String.valueOf(channel(i)));
        } finally {
            updatingFields = false;
        }
    }

    private void syncField(EditBox field, EditBox source, String value) {
        if (field != null && field != source) {
            field.setValue(value);
            field.setTextColor(UiTheme.TEXT);
            invalidFields.remove(field);
        }
    }

    private EditBox field(int x, int y, int width, int maxLength, Component label, String value) {
        EditBox field = new EditBox(font, x, 0, width, 20, label);
        field.setMaxLength(maxLength);
        field.setValue(value);
        field.setTextColor(UiTheme.TEXT);
        addContent(field, y);
        return field;
    }
    private void label(int y, String key, int color) { labels.add(new Label(left + 16, y, Component.translatable(key), color)); }
    private void addContent(AbstractWidget widget, int y) {
        contentWidgets.put(widget, y);
        addRenderableWidget(widget);
        addWidget(new ScrollFocusTarget(widget));
    }
    private Integer parseByte(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= 0 && parsed <= 255 ? parsed : null;
        } catch (NumberFormatException ignored) { return null; }
    }
    private void markValidity(EditBox field, boolean valid, String key) {
        field.setTextColor(valid ? UiTheme.TEXT : UiTheme.DANGER);
        if (valid) invalidFields.remove(field);
        else invalidFields.put(field, Component.translatable(key));
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
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {}
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x90000000);
        context.fill(left - 1, top - 1, left + windowWidth + 1, top + windowHeight + 1, UiTheme.BORDER);
        context.fill(left, top, left + windowWidth, top + windowHeight, UiTheme.WINDOW);
        context.fill(left, top, left + windowWidth, top + 26, UiTheme.HEADER);
        context.centeredText(font, title, width / 2, top + 9, UiTheme.TEXT);
        context.fill(left + 10, contentTop, left + windowWidth - 10, contentBottom, UiTheme.PANEL);
        context.enableScissor(left + 12, contentTop, left + windowWidth - 12, contentBottom);
        for (Label label : labels) context.text(font, label.text(), label.x(), contentTop + label.y() - scrollY, label.color(), false);
        drawSwatch(context, left + 16, contentTop + previewY - scrollY, 48, 48, color);
        context.text(font, Component.literal(Math.round((color >>> 24) * 100f / 255f) + "%"), left + 108,
                contentTop + alphaY - scrollY, UiTheme.MUTED, false);
        context.disableScissor();
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.enableScissor(left + 12, contentTop, left + windowWidth - 12, contentBottom);
        for (Preset preset : presets) {
            if (preset.button().visible) drawSwatch(context, preset.button().getX() + 3, preset.button().getY() + 3,
                    preset.button().getWidth() - 6, 14, 0xFF000000 | preset.rgb());
        }
        context.disableScissor();
        if (contentHeight > contentBottom - contentTop) {
            int track = contentBottom - contentTop;
            int thumb = Math.max(12, track * track / contentHeight);
            int thumbY = contentTop + scrollY * (track - thumb) / Math.max(1, contentHeight - track);
            context.fill(left + windowWidth - 6, contentTop, left + windowWidth - 4, contentBottom, UiTheme.BORDER);
            context.fill(left + windowWidth - 6, thumbY, left + windowWidth - 4, thumbY + thumb, UiTheme.ACCENT);
        }
        for (Map.Entry<EditBox, Component> entry : invalidFields.entrySet()) {
            if (entry.getKey().visible && (entry.getKey().isFocused() || entry.getKey().isMouseOver(mouseX, mouseY))) {
                VersionCompat.setTooltip(context, font, entry.getValue(), mouseX, mouseY);
                break;
            }
        }
    }

    /** Checkerboard compositing makes even completely transparent colors visible. */
    public static void drawSwatch(GuiGraphicsExtractor context, int x, int y, int width, int height, int argb) {
        context.fill(x - 1, y - 1, x + width + 1, y + height + 1, UiTheme.BORDER);
        int alpha = argb >>> 24;
        for (int row = 0; row < height; row += 6) {
            for (int column = 0; column < width; column += 6) {
                int gray = ((row / 6 + column / 6) & 1) == 0 ? 174 : 116;
                int r = (((argb >>> 16) & 255) * alpha + gray * (255 - alpha) + 127) / 255;
                int g = (((argb >>> 8) & 255) * alpha + gray * (255 - alpha) + 127) / 255;
                int b = ((argb & 255) * alpha + gray * (255 - alpha) + 127) / 255;
                context.fill(x + column, y + row, x + Math.min(width, column + 6), y + Math.min(height, row + 6),
                        0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
    }
    @Override
    public void onClose() { if (minecraft != null) minecraft.gui.setScreen(parent); }

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

    private record Label(int x, int y, Component text, int color) {}
    private record Preset(Button button, int rgb) {}
}
