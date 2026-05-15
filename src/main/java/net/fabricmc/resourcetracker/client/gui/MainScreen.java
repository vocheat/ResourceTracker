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

import net.fabricmc.resourcetracker.client.ResourceTrackerClient;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.util.PngIcons;
import net.fabricmc.resourcetracker.util.RenderUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
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
    private final int itemHeight = 25;
    private boolean wasMouseDown = false;
    private boolean leftShiftDown = false;
    private boolean rightShiftDown = false;
    private int addIconX;
    private int addIconY;
    private int reloadIconX;
    private int reloadIconY;
    private int worldFolderIconX;
    private int worldFolderIconY;
    private int allFolderIconX;
    private int allFolderIconY;
    private Button addListButton;
    private Button reloadListsButton;
    private Button settingsButton;
    private Button openWorldFolderButton;

    public MainScreen(Screen parent) {
        super(Component.translatable("gui.resourcetracker.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.listTop = 82;
        this.listBottom = this.height - 50;

        int centerX = this.width / 2;
        int boxWidth = getBoxWidth();
        int boxX = getBoxX(boxWidth);
        int toolY = this.listTop - 21;
        int sideWidth = 150;
        int sideX = boxX + boxWidth + 12;
        int sideY = this.listTop;
        if (sideX + sideWidth > this.width - 8) {
            sideX = boxX;
            this.listBottom = Math.max(this.listTop + 50, this.height - 126);
            sideY = this.listBottom + 6;
        }

        this.addIconX = boxX;
        this.addIconY = toolY;
        this.reloadIconX = boxX + boxWidth - 21;
        this.reloadIconY = toolY;
        this.worldFolderIconX = sideX;
        this.worldFolderIconY = sideY;
        this.allFolderIconX = sideX;
        this.allFolderIconY = sideY + 25;

        this.addListButton = this.addRenderableWidget(
                Button.builder(
                                Component.literal(""),
                                button -> {
                                    TrackerConfig.TrackingList newList = TrackerConfig.createList("List " + (TrackerConfig.INSTANCE.lists.size() + 1));
                                    newList.isVisible = true;
                                    TrackerConfig.INSTANCE.lists.add(newList);
                                    TrackerConfig.saveList(newList);
                                    ResourceTrackerClient.invalidateTargetItemCache();
                                    this.scrollOffset = Double.MAX_VALUE;
                                    this.init();
                                }
                        )
                        .bounds(this.addIconX, this.addIconY, 21, 21)
                        .build()
        );
        this.addListButton.active = TrackerConfig.hasActiveContext();

        this.reloadListsButton = Button.builder(
                        Component.literal(""),
                        button -> {
                            TrackerConfig.reloadActiveContextLists();
                            ResourceTrackerClient.invalidateTargetItemCache();
                            this.scrollOffset = 0;
                            this.init();
                        }
                )
                .bounds(this.reloadIconX, this.reloadIconY, 21, 21)
                .build();
        this.reloadListsButton.active = TrackerConfig.hasActiveContext();
        this.addRenderableWidget(this.reloadListsButton);

        this.addRenderableWidget(
                Button.builder(
                                Component.translatable("gui.resourcetracker.move_hud"),
                                button -> {
                                    if (this.minecraft != null) {
                                        this.minecraft.setScreen(new HudMoveScreen(this));
                                    }
                                }
                        )
                        .bounds(boxX + 29, toolY, boxWidth - 58, 21)
                        .build()
        );

        this.openWorldFolderButton = createActionButton(sideX, sideY,
                Component.translatable("gui.resourcetracker.open_world_lists"),
                button -> TrackerConfig.openActiveListsFolder());
        this.openWorldFolderButton.active = TrackerConfig.hasActiveContext();
        this.addRenderableWidget(this.openWorldFolderButton);

        this.addRenderableWidget(createActionButton(sideX, sideY + 25,
                Component.translatable("gui.resourcetracker.open_all_lists"),
                button -> TrackerConfig.openListsRootFolder()));

        this.settingsButton = this.addRenderableWidget(createActionButton(sideX, sideY + 50,
                Component.translatable("gui.resourcetracker.settings"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new SettingsScreen(this));
                    }
                }));

        this.addRenderableWidget(
                Button.builder(
                                Component.translatable("gui.resourcetracker.close"),
                                button -> {
                                    if (this.minecraft != null) {
                                        this.minecraft.setScreen(this.parent);
                                    }
                                }
                        )
                        .bounds(centerX - 50, this.height - 30, 100, 20)
                        .build()
        );
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // No-op: we draw our own semi-transparent background in render()
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xA0000000);

        String titleText = this.title.getString();
        int titleWidth = this.font.width(titleText);
        int titleX = (this.width - titleWidth) / 2;
        context.fill(titleX - 2, 8, titleX + titleWidth + 2, 20, 0xFF222222);
        context.drawString(this.font, this.title, titleX, 10, 0xFFFFFFFF, true);

        if (!TrackerConfig.hasActiveContext()) {
            Component noWorld = Component.translatable("gui.resourcetracker.no_world");
            context.drawString(this.font, noWorld, (this.width - this.font.width(noWorld)) / 2, 72, 0xFFFFAA55, true);
        } else if (TrackerConfig.INSTANCE.lists.isEmpty()) {
            Component empty = Component.translatable("gui.resourcetracker.no_lists");
            context.drawString(this.font, empty, (this.width - this.font.width(empty)) / 2, 72, 0xFFAAAAAA, true);
        }

        handleMouseInput(mouseX, mouseY);
        renderScrollableList(context, mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);
        drawButtonIcon(context, Icon.PLUS, this.addIconX, this.addIconY, 21, 21, iconColor(this.addListButton, 0xFFFFFFFF));
        drawButtonIcon(context, Icon.RELOAD, this.reloadIconX, this.reloadIconY, 21, 21, iconColor(this.reloadListsButton, 0xFFFFFFFF));
        drawButtonIcon(context, Icon.FOLDER, this.worldFolderIconX, this.worldFolderIconY, 21, 21, iconColor(this.openWorldFolderButton, 0xFFFFFFFF));
        drawButtonIcon(context, Icon.FOLDER, this.allFolderIconX, this.allFolderIconY, 21, 21, 0xFFFFFFFF);
        drawButtonIcon(context, Icon.GEAR, this.allFolderIconX, this.allFolderIconY + 25, 21, 21, 0xFFFFFFFF);
        showButtonTooltip(context, mouseX, mouseY, this.addListButton, Component.translatable("gui.resourcetracker.add_list"));
        showButtonTooltip(context, mouseX, mouseY, this.reloadListsButton, Component.translatable("gui.resourcetracker.reload_lists"));
        showButtonTooltip(context, mouseX, mouseY, this.settingsButton, Component.translatable("gui.resourcetracker.settings"));
    }

    /**
     * Renders the custom scrollable list of tracking groups.
     * Handles clipping (scissor test) and item interactions.
     */
    private void renderScrollableList(GuiGraphics context, int mouseX, int mouseY) {
        List<TrackerConfig.TrackingList> lists = TrackerConfig.INSTANCE.lists;
        int contentHeight = lists.size() * itemHeight;

        int boxWidth = getBoxWidth();
        int boxHeight = this.listBottom - this.listTop;
        int boxX = getBoxX(boxWidth);

        int maxScroll = Math.max(0, contentHeight - boxHeight);
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll);

        RenderUtils.drawBox(context, boxX, listTop, boxWidth, boxHeight);

        // Enable scissor to clip content outside the list box
        context.enableScissor(boxX, listTop, boxX + boxWidth, listBottom);

        // Flag: is the mouse hovering over any trash icon?
        boolean isHoveringTrash = false;
        Component iconTooltip = null;

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
            boolean eyeHovered = (mouseX >= eyeX && mouseX < eyeX + 21 && mouseY >= eyeY && mouseY < eyeY + 21);
            drawPixelEye(context, eyeX, eyeY, list.isVisible, eyeHovered);
            if (eyeHovered) {
                iconTooltip = Component.translatable(list.isVisible ? "gui.resourcetracker.visibility_hide" : "gui.resourcetracker.visibility_show");
            }

            String nameText = RenderUtils.shortenText(this.font, list.name, boxWidth - 66);
            context.drawString(this.font, Component.literal(nameText), x + 31, y + 8, 0xFFFFFFFF, true);

            // Trash Icon (Delete)
            int trashX = x + boxWidth - 25;
            int trashY = y + 2;
            boolean trashHovered = (mouseX >= trashX && mouseX < trashX + 21 && mouseY >= trashY && mouseY < trashY + 21);

            if (trashHovered) {
                isHoveringTrash = true;
            }

            boolean canDelete = trashHovered && shiftPressed();
            drawPixelDeleteIcon(context, trashX, trashY, trashHovered, canDelete);
        }

        context.disableScissor();

        if (maxScroll > 0) {
            RenderUtils.drawStyledScrollbar(context, boxX + boxWidth - 6, listTop, boxHeight, contentHeight, scrollOffset);
        }

        // Render Tooltip ABOVE the scissor cut (after disableScissor)
        if (isHoveringTrash) {
            if (shiftPressed()) {
                // Red text "Delete"
                VersionCompat.setTooltip(context, font, Component.translatable("gui.resourcetracker.delete").withStyle(ChatFormatting.RED), mouseX, mouseY);
            } else {
                // Gray text "Hold Shift"
                VersionCompat.setTooltip(context, font, Component.translatable("gui.resourcetracker.hold_shift").withStyle(ChatFormatting.GRAY), mouseX, mouseY);
            }
        } else if (iconTooltip != null) {
            VersionCompat.setTooltip(context, font, iconTooltip, mouseX, mouseY);
        }
    }

    private void handleMouseInput(int mouseX, int mouseY) {
        long windowHandle = VersionCompat.getWindowHandle(this.minecraft.getWindow());
        boolean isMouseDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (isMouseDown && !wasMouseDown) {
            if (mouseY >= listTop && mouseY <= listBottom) {
                List<TrackerConfig.TrackingList> lists = TrackerConfig.INSTANCE.lists;
                int boxWidth = getBoxWidth();
                int boxX = getBoxX(boxWidth);

                double relativeY = mouseY - listTop + scrollOffset - 2;
                if (relativeY >= 0) {
                    int index = (int) (relativeY / itemHeight);

                    if (index >= 0 && index < lists.size()) {
                        TrackerConfig.TrackingList list = lists.get(index);

                        int eyeX = boxX + 4;
                        int trashX = boxX + boxWidth - 25;

                        if (mouseX >= eyeX && mouseX < eyeX + 25) {
                            list.isVisible = !list.isVisible;
                            TrackerConfig.saveList(list);
                            ResourceTrackerClient.invalidateTargetItemCache();
                            playClickSound();
                        } else if (mouseX >= trashX && mouseX < trashX + 21) {
                            if (shiftPressed()) {
                                TrackerConfig.deleteList(list);
                                ResourceTrackerClient.invalidateTargetItemCache();
                                playClickSound();
                            }
                        } else if (mouseX > boxX + 31 && mouseX < trashX - 4) {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(new EditScreen(this, list));
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
    public boolean keyPressed(KeyEvent event) {
        if (ResourceTrackerClient.openMenuKey != null && ResourceTrackerClient.openMenuKey.matches(event)) {
            onClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_LEFT_SHIFT) {
            leftShiftDown = true;
        } else if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            rightShiftDown = true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_LEFT_SHIFT) {
            leftShiftDown = false;
        } else if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            rightShiftDown = false;
        }
        return super.keyReleased(event);
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
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
        }
    }

    private int getBoxWidth() {
        return Math.min(280, Math.max(120, this.width - 16));
    }

    private int getBoxX(int boxWidth) {
        return (this.width - boxWidth) / 2;
    }

    private Button createActionButton(int x, int y, Component text, Button.OnPress onPress) {
        return Button.builder(Component.literal("   ").append(text), onPress)
                .bounds(x, y, 150, 21)
                .build();
    }


    private void showButtonTooltip(GuiGraphics context, int mouseX, int mouseY, Button button, Component text) {
        if (button != null && mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight()) {
            VersionCompat.setTooltip(context, font, text, mouseX, mouseY);
        }
    }

    private enum Icon {
        PLUS(PngIcons.Icon.PLUS),
        RELOAD(PngIcons.Icon.RELOAD),
        FOLDER(PngIcons.Icon.FOLDER),
        GEAR(PngIcons.Icon.SETTINGS);

        private final PngIcons.Icon texture;

        Icon(PngIcons.Icon texture) {
            this.texture = texture;
        }
    }

    private int iconColor(Button button, int enabledColor) {
        return button != null && !button.active ? 0xFF777777 : enabledColor;
    }

    private void drawButtonIcon(GuiGraphics context, Icon icon, int buttonX, int buttonY, int buttonW, int buttonH, int color) {
        RenderUtils.drawPngIconInBox(context, icon.texture, buttonX, buttonY, buttonW, buttonH, color);
    }

    private void drawPixelEye(GuiGraphics context, int x, int y, boolean isVisible, boolean isHovered) {
        int color = isHovered ? 0xFFCCCCCC : 0xFF888888;
        RenderUtils.drawPngIconInBox(context, isVisible ? PngIcons.Icon.EYE : PngIcons.Icon.EYE_CROSSED, x, y, 21, 21, isVisible ? color : 0xFFFF5555);
    }

    private void drawPixelDeleteIcon(GuiGraphics context, int x, int y, boolean isHovered, boolean isDanger) {
        int mainColor;
        if (isDanger) {
            mainColor = 0xFFFF5555;
        } else {
            mainColor = isHovered ? 0xFFCCCCCC : 0xFF888888;
        }
        RenderUtils.drawPngIconInBox(context, PngIcons.Icon.TRASH, x, y, 21, 21, mainColor);
    }


    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private boolean shiftPressed() {
        return leftShiftDown || rightShiftDown;
    }
}


