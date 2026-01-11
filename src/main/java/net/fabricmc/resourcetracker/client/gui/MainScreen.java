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

package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The main menu screen for the Resource Tracker.
 * <p>
 * Allows users to:
 * <ul>
 * <li>Create new tracking lists.</li>
 * <li>Toggle visibility of lists (Eye icon).</li>
 * <li>Delete lists (Trash icon + Shift).</li>
 * <li>Navigate to the HUD editing screen.</li>
 * </ul>
 * This screen implements a custom scrollable list container.
 * </p>
 *
 * @author vocheat
 */
public class MainScreen extends Screen {
    private final Screen parent;

    private double scrollOffset = 0;
    private int listTop = 60;
    private int listBottom;
    private final int itemHeight = 20; // Height of a single list entry

    private boolean wasMouseDown = false;

    public MainScreen(Screen parent) {
        super(Text.translatable("gui.resourcetracker.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.clearChildren();
        this.listBottom = this.height - 50;

        int topBtnY = 25;

        // "Move HUD Elements" Button
        this.addDrawableChild(
                ButtonWidget.builder(
                                Text.translatable("gui.resourcetracker.move_hud"),
                                button -> {
                                    if (this.client != null) {
                                        this.client.setScreen(new HudMoveScreen(this));
                                    }
                                }
                        )
                        .dimensions(this.width / 2 - 155, topBtnY, 150, 20)
                        .build()
        );

        // "Add New List" Button
        this.addDrawableChild(
                ButtonWidget.builder(
                                Text.translatable("gui.resourcetracker.add_list").formatted(Formatting.YELLOW),
                                button -> {
                                    TrackerConfig.TrackingList newList = new TrackerConfig.TrackingList();
                                    newList.name = "List " + (TrackerConfig.INSTANCE.lists.size() + 1);
                                    newList.isVisible = true;
                                    TrackerConfig.INSTANCE.lists.add(newList);
                                    TrackerConfig.save();
                                    // Scroll to bottom to show the new list
                                    this.scrollOffset = Double.MAX_VALUE;
                                    if (this.client != null) {
                                        this.client.setScreen(new MainScreen(this.parent));
                                    }
                                }
                        )
                        .dimensions(this.width / 2 + 5, topBtnY, 150, 20)
                        .build()
        );

        // "Close" Button
        this.addDrawableChild(
                ButtonWidget.builder(
                                Text.translatable("gui.resourcetracker.close"),
                                button -> {
                                    if (this.client != null) {
                                        this.client.setScreen(this.parent);
                                    }
                                }
                        )
                        .dimensions(this.width / 2 - 50, this.height - 30, 100, 20)
                        .build()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Darkened background (similar to standard Minecraft Edit screens)
        context.fill(0, 0, this.width, this.height, 0xA0000000);

        // Title with background box
        String titleText = this.title.getString();
        int titleWidth = this.textRenderer.getWidth(titleText);
        int titleX = (this.width - titleWidth) / 2;
        context.fill(titleX - 2, 8, titleX + titleWidth + 2, 20, 0xFF222222);
        context.drawText(this.textRenderer, this.title, titleX, 10, 0xFFFFFFFF, true);

        handleMouseInput(mouseX, mouseY);
        renderScrollableList(context, mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);
    }

    /**
     * Renders the custom scrollable list of tracking groups.
     * Handles clipping (scissor test) and item interactions.
     */
    private void renderScrollableList(DrawContext context, int mouseX, int mouseY) {
        List<TrackerConfig.TrackingList> lists = TrackerConfig.INSTANCE.lists;
        int contentHeight = lists.size() * itemHeight;

        int centerX = this.width / 2;
        int boxWidth = 280;
        int boxHeight = this.listBottom - this.listTop;
        int boxX = centerX - (boxWidth / 2);

        int maxScroll = Math.max(0, contentHeight - boxHeight);
        this.scrollOffset = MathHelper.clamp(this.scrollOffset, 0, maxScroll);

        drawBox(context, boxX, listTop, boxWidth, boxHeight);

        // Enable scissor to clip content outside the list box
        context.enableScissor(boxX, listTop, boxX + boxWidth, listBottom);

        // Flag: is the mouse hovering over any trash icon?
        boolean isHoveringTrash = false;

        for (int i = 0; i < lists.size(); i++) {
            TrackerConfig.TrackingList list = lists.get(i);

            int y = (int) (listTop + (i * itemHeight) - scrollOffset + 2);
            int x = boxX;

            // Optimization: Skip rendering if item is outside the viewable area
            if (y + itemHeight < listTop || y > listBottom) {
                continue;
            }

            boolean isRowHovered = (mouseX >= x + 1 && mouseX < x + boxWidth - 1 && mouseY >= y && mouseY < y + itemHeight);

            // Highlight row on hover
            if (isRowHovered) {
                context.fill(x + 1, y, x + boxWidth - 1, y + itemHeight, 0x30FFFFFF);
            }

            // Separator line
            if (i > 0) {
                context.fill(x + 1, y - 1, x + boxWidth - 1, y, 0xFF333333);
            }

            // Eye Icon (Visibility Toggle)
            int eyeX = x + 4;
            int eyeY = y + 2;
            boolean eyeHovered = (mouseX >= eyeX && mouseX < eyeX + 16 && mouseY >= eyeY && mouseY < eyeY + 16);
            drawPixelEye(context, eyeX, eyeY, list.isVisible, eyeHovered);

            // List Name
            String nameText = list.name;
            int maxNameWidth = boxWidth - 60;
            if (this.textRenderer.getWidth(nameText) > maxNameWidth) {
                nameText = this.textRenderer.trimToWidth(nameText, maxNameWidth) + "..";
            }
            context.drawText(this.textRenderer, Text.translatable(nameText), x + 24, y + 6, 0xFFFFFFFF, true);

            // Trash Icon (Delete)
            int trashX = x + boxWidth - 20;
            int trashY = y + 2;
            boolean trashHovered = (mouseX >= trashX && mouseX < trashX + 16 && mouseY >= trashY && mouseY < trashY + 16);

            if (trashHovered) {
                isHoveringTrash = true;
            }

            boolean canDelete = trashHovered && isShiftDown();
            drawPixelTrash(context, trashX, trashY, trashHovered, canDelete);
        }

        context.disableScissor();

        if (maxScroll > 0) {
            drawStyledScrollbar(context, boxX + boxWidth - 6, listTop, boxHeight, contentHeight, scrollOffset);
        }

        // Render Tooltip ABOVE the scissor cut (after disableScissor)
        if (isHoveringTrash) {
            if (isShiftDown()) {
                // Red text "Delete"
                context.drawTooltip(textRenderer, Text.translatable("gui.resourcetracker.delete").formatted(Formatting.RED), mouseX, mouseY);
            } else {
                // Gray text "Hold Shift"
                context.drawTooltip(textRenderer, Text.translatable("gui.resourcetracker.hold_shift").formatted(Formatting.GRAY), mouseX, mouseY);
            }
        }
    }

    /**
     * Draws the background box for the list.
     */
    private void drawBox(DrawContext context, int x, int y, int w, int h) {
        // 1. Semi-transparent black background
        context.fill(x, y, x + w, y + h, 0x80000000);

        // 2. Border color
        // Changed from dark gray to White (0xFFFFFFFF) for better visibility on dark backgrounds
        int color = 0xFFFFFFFF;

        // 3. Draw border lines manually
        context.fill(x, y, x + w, y + 1, color);       // Top
        context.fill(x, y + h - 1, x + w, y + h, color); // Bottom
        context.fill(x, y, x + 1, y + h, color);       // Left
        context.fill(x + w - 1, y, x + w, y + h, color); // Right
    }

    private void drawStyledScrollbar(DrawContext context, int x, int y, int h, int contentH, double scroll) {
        if (contentH <= h) return;
        context.fill(x, y, x + 5, y + h, 0xFF000000);
        int barH = Math.max(20, (int) ((float) h / contentH * h));
        int maxScroll = contentH - h;
        int barY = y + (int) ((scroll / maxScroll) * (h - barH));
        context.fill(x, barY, x + 5, barY + barH, 0xFF888888);
    }

    /**
     * Manually handles mouse input for the custom list, as the items are not standard widgets.
     */
    private void handleMouseInput(int mouseX, int mouseY) {
        long windowHandle = this.client.getWindow().getHandle();
        boolean isMouseDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (isMouseDown && !wasMouseDown) {
            if (mouseY >= listTop && mouseY <= listBottom) {
                List<TrackerConfig.TrackingList> lists = TrackerConfig.INSTANCE.lists;
                int centerX = this.width / 2;
                int boxWidth = 280;
                int boxX = centerX - (boxWidth / 2);

                double relativeY = mouseY - listTop + scrollOffset - 2;
                if (relativeY >= 0) {
                    int index = (int) (relativeY / itemHeight);

                    if (index >= 0 && index < lists.size()) {
                        TrackerConfig.TrackingList list = lists.get(index);

                        int eyeX = boxX + 4;
                        int trashX = boxX + boxWidth - 20;

                        // Click on Eye (Toggle Visibility)
                        if (mouseX >= eyeX && mouseX < eyeX + 16) {
                            list.isVisible = !list.isVisible;
                            TrackerConfig.save();
                            playClickSound();
                        }
                        // Click on Trash (Delete)
                        else if (mouseX >= trashX && mouseX < trashX + 16) {
                            if (isShiftDown()) {
                                lists.remove(list);
                                TrackerConfig.save();
                                playClickSound();
                            }
                        }
                        // Click on Center (Edit)
                        else if (mouseX > boxX + 24 && mouseX < trashX - 4) {
                            if (this.client != null) {
                                this.client.setScreen(new EditScreen(this, list));
                            }
                            playClickSound();
                        }
                    }
                }
            }
        }

        wasMouseDown = isMouseDown;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY >= listTop && mouseY <= listBottom) {
            this.scrollOffset -= (verticalAmount * 20);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void playClickSound() {
        if (this.client != null && this.client.player != null) {
            this.client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
        }
    }

    /**
     * Programmatically draws a 16x16 pixel-art Eye icon.
     */
    private void drawPixelEye(DrawContext context, int x, int y, boolean isVisible, boolean isHovered) {
        int color = isHovered ? 0xFFCCCCCC : 0xFF888888;

        // Row 3
        context.fill(x + 5, y + 3, x + 11, y + 4, color);

        // Row 4
        context.fill(x + 3, y + 4, x + 6, y + 5, color);
        context.fill(x + 10, y + 4, x + 13, y + 5, color);

        // Row 5
        context.fill(x + 1, y + 5, x + 4, y + 6, color);
        context.fill(x + 6, y + 5, x + 10, y + 6, color);
        context.fill(x + 12, y + 5, x + 15, y + 6, color);

        // Row 6
        context.fill(x + 1, y + 6, x + 2, y + 7, color);
        context.fill(x + 5, y + 6, x + 6, y + 7, color);
        context.fill(x + 10, y + 6, x + 11, y + 7, color);
        context.fill(x + 14, y + 6, x + 15, y + 7, color);

        // Row 7
        context.fill(x + 0, y + 7, x + 2, y + 8, color);
        context.fill(x + 5, y + 7, x + 9, y + 8, color);
        context.fill(x + 10, y + 7, x + 11, y + 8, color);
        context.fill(x + 14, y + 7, x + 16, y + 8, color);

        // Row 8
        context.fill(x + 0, y + 8, x + 1, y + 9, color);
        context.fill(x + 5, y + 8, x + 7, y + 9, color);
        context.fill(x + 7, y + 8, x + 9, y + 9, color);
        context.fill(x + 10, y + 8, x + 11, y + 9, color);
        context.fill(x + 15, y + 8, x + 16, y + 9, color);

        // Row 9
        context.fill(x + 0, y + 9, x + 2, y + 10, color);
        context.fill(x + 5, y + 9, x + 9, y + 10, color);
        context.fill(x + 10, y + 9, x + 11, y + 10, color);
        context.fill(x + 14, y + 9, x + 16, y + 10, color);

        // Row 10
        context.fill(x + 1, y + 10, x + 4, y + 11, color);
        context.fill(x + 6, y + 10, x + 10, y + 11, color);
        context.fill(x + 12, y + 10, x + 15, y + 11, color);

        // Row 11
        context.fill(x + 3, y + 11, x + 6, y + 12, color);
        context.fill(x + 10, y + 11, x + 13, y + 12, color);

        // Row 12
        context.fill(x + 5, y + 12, x + 11, y + 13, color);

        // Draw Red Diagonal Line if not visible
        if (!isVisible) {
            int redColor = 0xFFFF5555;
            context.fill(x + 1, y + 1, x + 2, y + 2, redColor);
            context.fill(x + 2, y + 2, x + 3, y + 3, redColor);
            context.fill(x + 3, y + 3, x + 4, y + 4, redColor);
            context.fill(x + 4, y + 4, x + 5, y + 5, redColor);
            context.fill(x + 5, y + 5, x + 6, y + 6, redColor);
            context.fill(x + 6, y + 6, x + 7, y + 7, redColor);
            context.fill(x + 7, y + 7, x + 8, y + 8, redColor);
            context.fill(x + 8, y + 8, x + 9, y + 9, redColor);
            context.fill(x + 9, y + 9, x + 10, y + 10, redColor);
            context.fill(x + 10, y + 10, x + 11, y + 11, redColor);
            context.fill(x + 11, y + 11, x + 12, y + 12, redColor);
            context.fill(x + 12, y + 12, x + 13, y + 13, redColor);
            context.fill(x + 13, y + 13, x + 14, y + 14, redColor);
            context.fill(x + 14, y + 14, x + 15, y + 15, redColor);
        }
    }

    /**
     * Programmatically draws a 16x16 pixel-art Trash Can icon.
     */
    private void drawPixelTrash(DrawContext context, int x, int y, boolean isHovered, boolean isDanger) {
        int mainColor;
        if (isDanger) {
            mainColor = 0xFFFF5555; // Red (Ready to delete)
        } else {
            mainColor = isHovered ? 0xFFCCCCCC : 0xFF888888; // Standard Gray
        }

        // Handle (Small rectangle on top)
        context.fill(x + 6, y + 2, x + 10, y + 3, mainColor);
        context.fill(x + 6, y + 3, x + 7, y + 4, mainColor);
        context.fill(x + 9, y + 3, x + 10, y + 4, mainColor);

        // Lid
        context.fill(x + 3, y + 4, x + 13, y + 5, mainColor);
        context.fill(x + 3, y + 5, x + 4, y + 6, mainColor);
        context.fill(x + 12, y + 5, x + 13, y + 6, mainColor);
        context.fill(x + 3, y + 6, x + 13, y + 7, mainColor);

        // Body
        context.fill(x + 4, y + 7, x + 5, y + 13, mainColor);
        context.fill(x + 11, y + 7, x + 12, y + 13, mainColor);
        context.fill(x + 5, y + 13, x + 11, y + 14, mainColor);

        // Stripes
        context.fill(x + 6, y + 8, x + 7, y + 12, mainColor);
        context.fill(x + 9, y + 8, x + 10, y + 12, mainColor);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    private boolean isShiftDown() {
        if (this.client == null) return false;
        long handle = this.client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }
}