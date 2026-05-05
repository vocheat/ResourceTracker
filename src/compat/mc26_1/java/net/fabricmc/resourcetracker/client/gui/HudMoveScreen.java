package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.util.RenderUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class HudMoveScreen extends Screen {
    private final Screen parent;

    private TrackerConfig.TrackingList draggingList = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private boolean wasMouseDown = false;

    public HudMoveScreen(Screen parent) {
        super(Component.translatable("gui.resourcetracker.move.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.translatable("gui.resourcetracker.done"), b -> {
            TrackerConfig.save();
            minecraft.setScreen(parent);
        }).bounds(width / 2 - 50, height - 30, 100, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xA0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.centeredText(font, Component.translatable("gui.resourcetracker.move.drag_hint"), width / 2, 10, 0xFFFFFFFF);

        handleMouseInput(mouseX, mouseY);

        for (int index = TrackerConfig.INSTANCE.lists.size() - 1; index >= 0; index--) {
            TrackerConfig.TrackingList list = TrackerConfig.INSTANCE.lists.get(index);
            if (!list.isVisible) continue;
            renderScaledList(context, list);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void renderScaledList(GuiGraphicsExtractor context, TrackerConfig.TrackingList list) {
        VersionCompat.push(context);
        VersionCompat.translate(context, list.x, list.y);
        VersionCompat.scale(context, list.scale, list.scale);

        BoxSize baseSize = calculateBaseSize(list);
        int borderColor = (list == draggingList) ? 0xFF00FF00 : 0xFFFFFFFF;
        int padding = 4;

        renderBorder(context, -padding - 1, -padding - 1, baseSize.width + 2, baseSize.height + 2, borderColor);
        context.fill(-padding, -padding, baseSize.width - padding, baseSize.height - padding, list.backgroundColor);
        context.text(font, list.name, 0, 0, list.nameColor);

        int headerHeight = 14;
        int itemRowHeight = list.showIcons ? 24 : 12;
        int currentY = headerHeight;
        int iconOffset = list.showIcons ? 20 : 2;

        int validItems = 0;
        for (TrackerConfig.TrackedItem ti : list.items) {
            if (ti.isValid()) validItems++;
        }

        int[] layout = RenderUtils.calculateColumnLayout(list, validItems,
                minecraft.getWindow().getGuiScaledHeight(), headerHeight, padding, itemRowHeight);
        int numColumns = layout[0];
        int itemsPerColumn = layout[1];

        int colWidth = (baseSize.width - (padding * (numColumns - 1))) / numColumns;
        if (numColumns == 1) colWidth = baseSize.width;

        int currentColumn = 0;
        int columnOffsetX = 0;
        int drawn = 0;

        for (TrackerConfig.TrackedItem trackedItem : list.items) {
            if (!trackedItem.isValid()) continue;

            if (drawn > 0 && drawn % itemsPerColumn == 0) {
                currentColumn++;
                columnOffsetX = currentColumn * (colWidth + padding);
                currentY = headerHeight;
            }

            if (list.showIcons) {
                context.item(trackedItem.getStack(), columnOffsetX, currentY + 1);
            }

            int currentCount = trackedItem.cachedCount;
            boolean isDone = currentCount >= trackedItem.targetCount;

            int itemColor = list.textColor;
            int countColor = isDone ? 0xFF55FF55 : (itemColor & 0xAAFFFFFF);

            if (list.showIcons) {
                int availableWidth = colWidth - iconOffset;
                String itemName = RenderUtils.shortenText(font, trackedItem.getDisplayName(), availableWidth);
                context.text(font, itemName, columnOffsetX + iconOffset, currentY, itemColor);

                String countLine = RenderUtils.getCountText(currentCount, trackedItem.targetCount, list.showRemaining);
                context.text(font, countLine, columnOffsetX + iconOffset, currentY + 10, countColor);
            } else {
                String namePart = trackedItem.getDisplayName() + ": ";
                int nw = font.width(namePart);
                String countLine = RenderUtils.getCountText(currentCount, trackedItem.targetCount, list.showRemaining);

                context.text(font, namePart, columnOffsetX + iconOffset, currentY + 2, itemColor);
                context.text(font, countLine, columnOffsetX + iconOffset + nw, currentY + 2, countColor);
            }

            currentY += itemRowHeight;
            drawn++;
        }

        VersionCompat.pop(context);
    }

    private BoxSize calculateBaseSize(TrackerConfig.TrackingList list) {
        int padding = 4;
        int headerHeight = 14;
        int itemRowHeight = list.showIcons ? 24 : 12;
        int maxTextWidth = font.width(list.name);
        int iconOffset = list.showIcons ? 20 : 2;

        int validItems = 0;

        for (TrackerConfig.TrackedItem trackedItem : list.items) {
            if (!trackedItem.isValid()) continue;
            validItems++;

            String countText = RenderUtils.getCountText(trackedItem.cachedCount, trackedItem.targetCount, list.showRemaining);

            int entryWidth;
            if (list.showIcons) {
                int nameWidth = font.width(trackedItem.getDisplayName());
                int countWidth = font.width(countText);
                entryWidth = iconOffset + Math.max(nameWidth, countWidth);
            } else {
                String combined = trackedItem.getDisplayName() + ": " + countText;
                entryWidth = iconOffset + font.width(combined);
            }

            if (entryWidth > maxTextWidth) {
                maxTextWidth = entryWidth;
            }
        }

        int columnWidth = maxTextWidth + (padding * 2);

        int[] layout = RenderUtils.calculateColumnLayout(list, validItems,
                minecraft.getWindow().getGuiScaledHeight(), headerHeight, padding, itemRowHeight);
        int numColumns = layout[0];
        int itemsPerColumn = layout[1];

        int width = (columnWidth * numColumns) + (padding * (numColumns - 1));
        int height = headerHeight + (itemsPerColumn * itemRowHeight) + padding;

        return new BoxSize(width, height);
    }

    private void renderBorder(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private void handleMouseInput(int mouseX, int mouseY) {
        long windowHandle = VersionCompat.getWindowHandle(minecraft.getWindow());
        boolean isMouseDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (isMouseDown && !wasMouseDown) {
            if (mouseY >= this.height - 34 && mouseX >= this.width / 2 - 55 && mouseX <= this.width / 2 + 55) {
                this.wasMouseDown = true;
                return;
            }

            for (int index = TrackerConfig.INSTANCE.lists.size() - 1; index >= 0; index--) {
                TrackerConfig.TrackingList list = TrackerConfig.INSTANCE.lists.get(index);
                if (!list.isVisible) continue;

                BoxSize baseSize = calculateBaseSize(list);
                VisualBounds bounds = calculateVisualBounds(baseSize, list.scale);
                double hitX = list.x + bounds.left;
                double hitY = list.y + bounds.top;
                double hitRight = list.x + bounds.right;
                double hitBottom = list.y + bounds.bottom;

                if (mouseX >= hitX && mouseX <= hitRight && mouseY >= hitY && mouseY <= hitBottom) {
                    this.draggingList = list;
                    this.dragOffsetX = mouseX - list.x;
                    this.dragOffsetY = mouseY - list.y;
                    break;
                }
            }
        } else if (isMouseDown && wasMouseDown) {
            if (draggingList != null) {
                int newX = mouseX - dragOffsetX;
                int newY = mouseY - dragOffsetY;

                BoxSize baseSize = calculateBaseSize(draggingList);
                VisualBounds bounds = calculateVisualBounds(baseSize, draggingList.scale);

                int minX = (int) Math.ceil(-bounds.left);
                int maxX = (int) Math.floor(this.width - bounds.right);
                int minY = (int) Math.ceil(-bounds.top);
                int maxY = (int) Math.floor(this.height - bounds.bottom);

                draggingList.x = clamp(newX, minX, maxX);
                draggingList.y = clamp(newY, minY, maxY);
            }
        } else if (!isMouseDown && wasMouseDown) {
            if (draggingList != null) {
                draggingList = null;
                TrackerConfig.save();
            }
        }
        this.wasMouseDown = isMouseDown;
    }

    private VisualBounds calculateVisualBounds(BoxSize baseSize, float scale) {
        int padding = 4;
        double left = (-padding - 1) * scale;
        double top = (-padding - 1) * scale;
        double right = (baseSize.width - padding + 1) * scale;
        double bottom = (baseSize.height - padding + 1) * scale;
        return new VisualBounds(left, top, right, bottom);
    }

    private int clamp(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(value, max));
    }

    @Override
    public void onClose() {
        TrackerConfig.save();
        this.minecraft.setScreen(parent);
    }

    private record BoxSize(int width, int height) {}
    private record VisualBounds(double left, double top, double right, double bottom) {}
}
