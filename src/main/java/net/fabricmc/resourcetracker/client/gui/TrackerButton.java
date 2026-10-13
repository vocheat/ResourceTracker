package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.util.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Native button behavior and narration with the tracker's shared panel style. */
public class TrackerButton extends Button {
    private boolean primary;
    private TrackerButton(int x, int y, int width, int height, Component message, OnPress onPress, CreateNarration narration) {
        super(x, y, width, height, message, onPress, narration);
    }

    public static Builder builder(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    @Override
    protected void renderContents(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (alpha <= 0 || width <= 0 || height <= 0) return;
        int x = getX(), y = getY();
        int fill = active && isHoveredOrFocused() ? UiTheme.HOVER : primary && active ? UiTheme.HEADER : UiTheme.PANEL;
        int edge = (isFocused() || primary && active) ? UiTheme.ACCENT : UiTheme.BORDER;
        context.fill(x, y, x + width, y + height, faded(fill));
        if (height > 2 && width > 2) context.fill(x + 1, y + 1, x + width - 1, y + 2, faded(UiTheme.HEADER));
        context.fill(x, y, x + width, y + 1, faded(edge));
        context.fill(x, y + height - 1, x + width, y + height, faded(edge));
        context.fill(x, y, x + 1, y + height, faded(edge));
        context.fill(x + width - 1, y, x + width, y + height, faded(edge));
        Font font = Minecraft.getInstance().font;
        String label = RenderUtils.shortenText(font, getMessage().getString(), Math.max(0, width - 8));
        context.drawString(font, label, x + (width - font.width(label)) / 2,
                y + (height - font.lineHeight) / 2, faded(active ? UiTheme.TEXT : UiTheme.MUTED), false);
    }

    private int faded(int color) {
        int opacity = Math.round((color >>> 24) * Math.max(0, Math.min(1, alpha)));
        return (color & 0x00FFFFFF) | (opacity << 24);
    }

    /** Covariant builder keeps ordinary Button builder call sites unchanged. */
    public static class Builder extends Button.Builder {
        private final Component message;
        private final OnPress onPress;
        private int x, y, width = DEFAULT_WIDTH, height = DEFAULT_HEIGHT;
        private Tooltip tooltip;
        private boolean primary;
        private CreateNarration narration = DEFAULT_NARRATION;

        public Builder(Component message, OnPress onPress) {
            super(message, onPress);
            this.message = message;
            this.onPress = onPress;
        }

        public Builder primary() {
            this.primary = true;
            return this;
        }

        @Override
        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        @Override
        public Builder width(int width) {
            this.width = width;
            return this;
        }

        @Override
        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        @Override
        public Builder bounds(int x, int y, int width, int height) {
            return pos(x, y).size(width, height);
        }

        @Override
        public Builder tooltip(Tooltip tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        @Override
        public Builder createNarration(CreateNarration narration) {
            this.narration = narration;
            return this;
        }

        @Override
        public TrackerButton build() {
            TrackerButton button = new TrackerButton(x, y, width, height, message, onPress, narration);
            button.primary = primary;
            button.setTooltip(tooltip);
            return button;
        }
    }
}
