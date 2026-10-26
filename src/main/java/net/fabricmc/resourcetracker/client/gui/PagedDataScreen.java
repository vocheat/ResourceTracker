package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.compat.VersionCompat;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Widget-only layout shared by all supported renderer API versions. */
abstract class PagedDataScreen extends Screen {
    protected final Screen parent;
    protected int left, contentWidth, page, pageSize, rowTop;

    protected PagedDataScreen(Screen parent, Component title) {
        super(title);
        this.parent = parent;
    }

    protected void layout(int top) {
        clearWidgets();
        contentWidth = Math.max(80, Math.min(620, width - 24));
        left = (width - contentWidth) / 2;
        rowTop = top;
        pageSize = Math.max(1, (height - top - 62) / 24);
        label(title, left, 10, contentWidth);
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

    protected void pagination(int count) {
        int pages = Math.max(1, (count + pageSize - 1) / pageSize);
        page = Math.max(0, Math.min(page, pages - 1));
        int y = height - 52;
        Button previous = button(Component.literal("<"), left, y, 30, b -> { page--; init(); });
        previous.active = page > 0;
        label(Component.literal((page + 1) + " / " + pages), left + 36, y + 4, 64);
        Button next = button(Component.literal(">"), left + 102, y, 30, b -> { page++; init(); });
        next.active = page + 1 < pages;
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
    public void onClose() {
        if (minecraft != null) VersionCompat.setScreen(minecraft, parent);
    }
}
