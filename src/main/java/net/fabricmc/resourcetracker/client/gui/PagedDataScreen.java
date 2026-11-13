package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.compat.VersionCompat;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Widget-only layout shared by all supported renderer API versions. */
abstract class PagedDataScreen extends EditorInputScreen {
    protected final Screen parent;
    protected int left, contentWidth, page, pageSize, rowTop;
    private boolean undoShortcutDown;
    private int panelTop, panelBottom;

    protected PagedDataScreen(Screen parent, Component title) {
        super(title);
        this.parent = parent;
    }

    protected void layout(int top) {
        layout(top, title);
    }

    protected void layout(int top, Component heading) {
        layout(top, heading, 620, 10);
    }

    protected void layout(int top, Component heading, int maxWidth, int headingY) {
        undoShortcutDown = minecraft != null && VersionCompat.isUndoPressed(minecraft);
        setFocused(null);
        clearWidgets();
        panelTop = panelBottom = 0;
        contentWidth = Math.max(80, Math.min(maxWidth, width - 24));
        left = (width - contentWidth) / 2;
        rowTop = top;
        pageSize = Math.max(1, (height - top - 62) / 24);
        label(heading, left, headingY, contentWidth);
    }

    protected void showPanel() { showPanel(4, height - 4); }

    protected void showPanel(int top, int bottom) {
        panelTop = top;
        panelBottom = bottom;
    }

    @Override
    protected boolean drawEditorBackground(BackgroundFill fill) {
        if (panelBottom <= panelTop) return false;
        fill.draw(0, 0, width, height, 0xA0000000);
        fill.draw(left - 8, panelTop, left + contentWidth + 8, panelBottom, UiTheme.WINDOW);
        return true;
    }

    protected EditBox label(Component text, int x, int y, int w) {
        EditBox label = new EditBox(font, x, y, Math.max(1, w), 16, text);
        label.setMaxLength(32767);
        label.setValue(text.getString());
        label.setCursorPosition(0);
        label.setHighlightPos(0);
        label.setTooltip(net.minecraft.client.gui.components.Tooltip.create(text));
        label.setBordered(false);
        label.setEditable(false);
        label.active = false;
        label.setTextColor(UiTheme.TEXT);
        label.setTextColorUneditable(UiTheme.TEXT);
        addRenderableWidget(label);
        return label;
    }

    protected Button button(Component text, int x, int y, int w, Button.OnPress action) {
        return addRenderableWidget(TrackerButton.builder(text, action).bounds(x, y, Math.max(1, w), 20).build());
    }

    protected net.minecraft.client.gui.Font textFont() { return font; }

    @Override
    public void setFocused(net.minecraft.client.gui.components.events.GuiEventListener focused) {
        // Mouse dispatch may finish after an action rebuilt the children.
        if (focused != null && !children().contains(focused)) return;
        super.setFocused(focused);
    }

    protected EditBox input(EditBox field) { return addRenderableWidget(field); }

    protected void pagination(int count) {
        pagination(count, height - 52);
    }

    protected void pagination(int count, int y) {
        int pages = Math.max(1, (count + pageSize - 1) / pageSize);
        page = Math.max(0, Math.min(page, pages - 1));
        Button previous = button(Component.literal("<"), left, y, 30, b -> { page--; init(); });
        previous.active = page > 0;
        label(Component.literal((page + 1) + " / " + pages), left + 36, y + 4, 64);
        Button next = button(Component.literal(">"), left + 102, y, 30, b -> { page++; init(); });
        next.active = page + 1 < pages;
    }

    /** Render wrapped readonly text using only widgets shared by every supported UI API. */
    protected int wrappedLabel(Component text, int x, int y, int w, int color) {
        for (String paragraph : text.getString().split("\\n", -1)) {
            String remaining = paragraph;
            if (remaining.isEmpty()) { y += 12; continue; }
            while (!remaining.isEmpty()) {
                String line = font.plainSubstrByWidth(remaining, Math.max(1, w));
                if (line.isEmpty()) line = remaining.substring(0, 1);
                if (line.length() < remaining.length()) {
                    int space = line.lastIndexOf(' ');
                    if (space > 0) line = line.substring(0, space);
                }
                EditBox field = label(Component.literal(line), x, y, w);
                field.setTextColorUneditable(color);
                y += 12;
                remaining = remaining.substring(line.length()).stripLeading();
            }
        }
        return y;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0 && y >= rowTop && y < height - 56) {
            page = Math.max(0, page + (vertical < 0 ? 1 : -1));
            init();
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public void tick() {
        super.tick();
        var list = undoList();
        boolean down = minecraft != null && VersionCompat.isUndoPressed(minecraft);
        if (list != null && down && !undoShortcutDown
                && net.fabricmc.resourcetracker.config.TrackerHistory.undo(list)) init();
        undoShortcutDown = down;
    }

    protected net.fabricmc.resourcetracker.config.TrackerConfig.TrackingList undoList() { return null; }

    @Override
    public void onClose() {
        if (minecraft != null) VersionCompat.setScreen(minecraft, parent);
    }
}
