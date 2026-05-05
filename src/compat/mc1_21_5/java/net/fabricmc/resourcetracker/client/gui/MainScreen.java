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
import net.fabricmc.resourcetracker.util.PixelIcons;
import net.fabricmc.resourcetracker.util.RenderUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
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
    private final int itemHeight = 28;
    private boolean wasMouseDown = false;
    private boolean leftShiftDown = false;
    private boolean rightShiftDown = false;
    private int addIconX;
    private int addIconY;
    private int reloadIconX;
    private int reloadIconY;
    private int gearIconX;
    private int gearIconY;
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
        this.listTop = 78;
        this.listBottom = this.height - 50;

        int centerX = this.width / 2;
        int boxWidth = 280;
        int boxX = centerX - (boxWidth / 2);
        int toolY = this.listTop - 24;
        int sideX = boxX + boxWidth + 12;
        if (sideX + 150 > this.width - 8) {
            sideX = Math.max(8, boxX - 162);
        }

        this.gearIconX = boxX - 28;
        this.gearIconY = 25;
        this.addIconX = boxX;
        this.addIconY = toolY;
        this.reloadIconX = boxX + boxWidth - 24;
        this.reloadIconY = toolY;
        this.worldFolderIconX = sideX;
        this.worldFolderIconY = this.listTop;
        this.allFolderIconX = sideX;
        this.allFolderIconY = this.listTop + 28;

        this.settingsButton = this.addRenderableWidget(
                Button.builder(
                                Component.literal(""),
                                button -> {
                                    if (this.minecraft != null) {
                                        this.minecraft.setScreen(new SettingsScreen(this));
                                    }
                                }
                        )
                        .bounds(this.gearIconX, this.gearIconY, 24, 24)
                        .build()
        );

        this.addListButton = this.addRenderableWidget(
                Button.builder(
                                Component.literal(""),
                                button -> {
                                    TrackerConfig.TrackingList newList = TrackerConfig.createList("List " + (TrackerConfig.INSTANCE.lists.size() + 1));
                                    newList.isVisible = true;
                                    TrackerConfig.INSTANCE.lists.add(newList);
                                    TrackerConfig.save();
                                    this.scrollOffset = Double.MAX_VALUE;
                                    this.init();
                                }
                        )
                        .bounds(this.addIconX, this.addIconY, 24, 24)
                        .build()
        );
        this.addListButton.active = TrackerConfig.hasActiveContext();

        this.reloadListsButton = Button.builder(
                        Component.literal(""),
                        button -> {
                            TrackerConfig.reloadActiveContextLists();
                            this.scrollOffset = 0;
                            this.init();
                        }
                )
                .bounds(this.reloadIconX, this.reloadIconY, 24, 24)
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
                        .bounds(boxX + 32, toolY, boxWidth - 64, 24)
                        .build()
        );

        this.openWorldFolderButton = Button.builder(
                        Component.literal("   ").append(Component.translatable("gui.resourcetracker.open_world_lists")),
                        button -> TrackerConfig.openActiveListsFolder()
                )
                .bounds(sideX, this.listTop, 150, 24)
                .build();
        this.openWorldFolderButton.active = TrackerConfig.hasActiveContext();
        this.addRenderableWidget(this.openWorldFolderButton);

        this.addRenderableWidget(
                Button.builder(
                                Component.literal("   ").append(Component.translatable("gui.resourcetracker.open_all_lists")),
                                button -> TrackerConfig.openListsRootFolder()
                        )
                        .bounds(sideX, this.listTop + 28, 150, 24)
                        .build()
        );

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
        drawButtonIcon(context, Icon.PLUS, this.addIconX, this.addIconY, 24, 24, iconColor(this.addListButton, 0xFFFFFFFF));
        drawButtonIcon(context, Icon.RELOAD, this.reloadIconX, this.reloadIconY, 24, 24, iconColor(this.reloadListsButton, 0xFFDDDDDD));
        drawButtonIcon(context, Icon.GEAR, this.gearIconX, this.gearIconY, 24, 24, iconColor(this.settingsButton, 0xFFDDDDDD));
        drawButtonIcon(context, Icon.FOLDER, this.worldFolderIconX, this.worldFolderIconY, 24, 24, iconColor(this.openWorldFolderButton, 0xFFFFFFFF));
        drawButtonIcon(context, Icon.FOLDER, this.allFolderIconX, this.allFolderIconY, 24, 24, 0xFFFFFFFF);
    }

    /**
     * Renders the custom scrollable list of tracking groups.
     * Handles clipping (scissor test) and item interactions.
     */
    private void renderScrollableList(GuiGraphics context, int mouseX, int mouseY) {
        List<TrackerConfig.TrackingList> lists = TrackerConfig.INSTANCE.lists;
        int contentHeight = lists.size() * itemHeight;

        int centerX = this.width / 2;
        int boxWidth = 280;
        int boxHeight = this.listBottom - this.listTop;
        int boxX = centerX - (boxWidth / 2);

        int maxScroll = Math.max(0, contentHeight - boxHeight);
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll);

        RenderUtils.drawBox(context, boxX, listTop, boxWidth, boxHeight);

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
            boolean eyeHovered = (mouseX >= eyeX && mouseX < eyeX + 24 && mouseY >= eyeY && mouseY < eyeY + 24);
            drawPixelEye(context, eyeX, eyeY, list.isVisible, eyeHovered);

            String nameText = RenderUtils.shortenText(this.font, list.name, boxWidth - 72);
            context.drawString(this.font, Component.literal(nameText), x + 34, y + 10, 0xFFFFFFFF, true);

            // Trash Icon (Delete)
            int trashX = x + boxWidth - 28;
            int trashY = y + 2;
            boolean trashHovered = (mouseX >= trashX && mouseX < trashX + 24 && mouseY >= trashY && mouseY < trashY + 24);

            if (trashHovered) {
                isHoveringTrash = true;
            }

            boolean canDelete = trashHovered && shiftPressed();
            drawPixelTrash(context, trashX, trashY, trashHovered, canDelete);
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
        }
    }

    private void handleMouseInput(int mouseX, int mouseY) {
        long windowHandle = VersionCompat.getWindowHandle(this.minecraft.getWindow());
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
                        int trashX = boxX + boxWidth - 28;

                        if (mouseX >= eyeX && mouseX < eyeX + 28) {
                            list.isVisible = !list.isVisible;
                            TrackerConfig.save();
                            playClickSound();
                        } else if (mouseX >= trashX && mouseX < trashX + 24) {
                            if (shiftPressed()) {
                                TrackerConfig.deleteList(list);
                                playClickSound();
                            }
                        } else if (mouseX > boxX + 34 && mouseX < trashX - 4) {
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ResourceTrackerClient.openMenuKey != null && ResourceTrackerClient.openMenuKey.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT) {
            leftShiftDown = true;
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            rightShiftDown = true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT) {
            leftShiftDown = false;
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            rightShiftDown = false;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
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

    private enum Icon {
        PLUS(PixelIcons.PLUS),
        RELOAD(PixelIcons.RELOAD),
        FOLDER(PixelIcons.FOLDER),
        GEAR(PixelIcons.GEAR);

        private final int[] pixels;

        Icon(int[] pixels) {
            this.pixels = pixels;
        }
    }

    private int iconColor(Button button, int enabledColor) {
        return button != null && !button.active ? 0xFF777777 : enabledColor;
    }

    private void drawButtonIcon(GuiGraphics context, Icon icon, int buttonX, int buttonY, int buttonW, int buttonH, int color) {
        RenderUtils.drawPixelIcon24InBox(context, icon.pixels, buttonX, buttonY, buttonW, buttonH, color);
    }

    private void drawPixelEye(GuiGraphics context, int x, int y, boolean isVisible, boolean isHovered) {
        int color = isHovered ? 0xFFCCCCCC : 0xFF888888;
        RenderUtils.drawPixelIcon24(context, x, y, isVisible ? color : 0xFFFF5555, isVisible ? PixelIcons.EYE_OPEN : PixelIcons.EYE_CLOSED);
    }

    private void drawPixelTrash(GuiGraphics context, int x, int y, boolean isHovered, boolean isDanger) {
        int mainColor;
        if (isDanger) {
            mainColor = 0xFFFF5555;
        } else {
            mainColor = isHovered ? 0xFFCCCCCC : 0xFF888888;
        }
        RenderUtils.drawPixelIcon24(context, x, y, mainColor, PixelIcons.TRASH);
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
