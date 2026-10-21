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
import net.fabricmc.resourcetracker.config.TrackerHistory;
import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.util.PngIcons;
import net.fabricmc.resourcetracker.util.RenderUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** A compact, keyboard-accessible overview of the current world's lists. */
public class MainScreen extends Screen {
    private static final int ROW_HEIGHT = 32;
    private final Screen parent;
    private final List<Button> editButtons = new ArrayList<>();
    private final List<Button> visibilityButtons = new ArrayList<>();
    private final List<Button> menuButtons = new ArrayList<>();
    private final List<Action> actions = new ArrayList<>();
    private double scrollOffset;
    private boolean filesExpanded;
    private TrackerConfig.TrackingList menuList;
    private Button lastRowFocus;
    private Button filesButton;
    private int panelX, panelY, panelWidth, panelHeight;
    private int listX, listWidth, listTop, listBottom;

    private record Action(Button button, boolean primary, boolean danger) {}

    public MainScreen(Screen parent) {
        super(Component.translatable("gui.resourcetracker.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        setFocused(null);
        actions.clear();
        editButtons.clear();
        visibilityButtons.clear();
        menuButtons.clear();
        lastRowFocus = null;
        if (!TrackerConfig.INSTANCE.lists.contains(menuList)) menuList = null;
        panelWidth = Math.min(460, Math.max(1, width - 16));
        int innerWidth = Math.max(1, panelWidth - 20);
        boolean narrow = innerWidth < 340;
        int listTopOffset = (narrow ? 136 : 110) + (filesExpanded ? 26 : 0);
        int menuSpace = menuList == null ? 0 : 28;
        int desiredViewport = TrackerConfig.INSTANCE.lists.isEmpty()
                ? 72 : TrackerConfig.INSTANCE.lists.size() * ROW_HEIGHT;
        int desiredPanelHeight = listTopOffset + desiredViewport + 36 + menuSpace;
        panelHeight = Math.min(Math.min(350, Math.max(1, height - 16)), desiredPanelHeight);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        listX = panelX + 10;
        int toolbarY = panelY + 43;
        int createWidth = narrow ? (innerWidth - 6) / 2 : 112;
        Button create = action(listX, toolbarY, createWidth, "gui.resourcetracker.main.create", b -> createList(), true, false);
        create.active = TrackerConfig.hasActiveContext();
        int secondaryWidth = narrow ? innerWidth - createWidth - 6 : (innerWidth - createWidth - 18) / 3;
        Button move = action(listX + createWidth + 6, toolbarY, secondaryWidth, "gui.resourcetracker.main.place_hud", b -> {
            if (minecraft != null) minecraft.gui.setScreen(new HudMoveScreen(this));
        }, false, false);
        move.active = TrackerConfig.hasActiveContext();
        int secondY = narrow ? toolbarY + 26 : toolbarY;
        int settingsX = narrow ? listX : listX + createWidth + secondaryWidth + 12;
        int settingsWidth = narrow ? createWidth : secondaryWidth;
        action(settingsX, secondY, settingsWidth, "gui.resourcetracker.settings", b -> {
            if (minecraft != null) minecraft.gui.setScreen(new SettingsScreen(this));
        }, false, false);
        int filesX = narrow ? listX + createWidth + 6 : settingsX + secondaryWidth + 6;
        filesButton = action(filesX, secondY, listX + innerWidth - filesX, filesExpanded ? "gui.resourcetracker.main.files_close" : "gui.resourcetracker.main.files", b -> {
            filesExpanded = !filesExpanded;
            menuList = null;
            init();
            setFocused(filesButton);
        }, false, false);
        int afterToolbar = secondY + 26;
        Button importButton = action(listX, afterToolbar, innerWidth, "gui.resourcetracker.import.title", b -> {
            if (minecraft != null) VersionCompat.setScreen(minecraft, new ImportScreen(this));
        }, false, false);
        importButton.active = TrackerConfig.hasActiveContext();
        afterToolbar += 26;
        if (filesExpanded) {
            int fileWidth = (innerWidth - 12) / 3;
            Button world = action(listX, afterToolbar, fileWidth, "gui.resourcetracker.main.world_folder", b -> TrackerConfig.openActiveListsFolder(), false, false);
            world.active = TrackerConfig.hasActiveContext();
            action(listX + fileWidth + 6, afterToolbar, fileWidth, "gui.resourcetracker.main.root_folder", b -> TrackerConfig.openListsRootFolder(), false, false);
            Button reload = action(listX + 2 * (fileWidth + 6), afterToolbar, innerWidth - 2 * (fileWidth + 6), "gui.resourcetracker.main.reload", b -> {
                TrackerConfig.reloadActiveContextLists();
                ResourceTrackerClient.invalidateTargetItemCache();
                menuList = null;
                init();
            }, false, false);
            reload.active = TrackerConfig.hasActiveContext();
            afterToolbar += 26;
        }
        listTop = afterToolbar + 15;
        listBottom = Math.max(listTop + 1, panelY + panelHeight - 36 - (menuList == null ? 0 : 28));
        listWidth = Math.max(1, innerWidth - 9); // The scrollbar has its own gutter.
        createRowButtons();
        if (TrackerConfig.hasActiveContext() && TrackerConfig.INSTANCE.lists.isEmpty() && listBottom - listTop >= 54) {
            int ctaWidth = Math.max(1, Math.min(160, listWidth - 12));
            action(listX + (listWidth - ctaWidth) / 2, listTop + Math.max(27, (listBottom - listTop) / 2), ctaWidth,
                    "gui.resourcetracker.main.create", b -> createList(), true, false);
        }
        if (menuList != null) {
            int menuY = panelY + panelHeight - 61;
            int cancelWidth = Math.min(64, innerWidth / 3);
            int deleteWidth = Math.min(96, innerWidth - cancelWidth - 6);
            action(listX + innerWidth - cancelWidth - deleteWidth - 6, menuY, deleteWidth, "gui.resourcetracker.delete",
                    b -> confirmDelete(menuList), false, true);
            action(listX + innerWidth - cancelWidth, menuY, cancelWidth, "gui.cancel", b -> {
                menuList = null;
                init();
            }, false, false);
        }
        int footerWidth = Math.max(1, (innerWidth - 6) / 2);
        Button undo = action(listX, panelY + panelHeight - 27, footerWidth, "gui.resourcetracker.undo_delete", b -> {
            if (TrackerHistory.undoDeletion()) {
                menuList = null;
                init();
            }
        }, false, false);
        undo.active = TrackerHistory.canUndoDeletion();
        action(listX + footerWidth + 6, panelY + panelHeight - 27, footerWidth, "gui.resourcetracker.close", b -> onClose(), false, false);
        updateRows();
    }

    private Button action(int x, int y, int w, String key, Button.OnPress press, boolean primary, boolean danger) {
        Button button = Button.builder(Component.translatable(key), press).bounds(x, y, Math.max(1, w), 21).build();
        button.setAlpha(0);
        addRenderableWidget(button);
        actions.add(new Action(button, primary, danger));
        return button;
    }

    private void createList() {
        if (!TrackerConfig.hasActiveContext() || minecraft == null) return;
        TrackerConfig.TrackingList list = TrackerConfig.createList(Component.translatable("gui.resourcetracker.default_list", TrackerConfig.INSTANCE.lists.size() + 1).getString());
        list.isVisible = true;
        TrackerConfig.INSTANCE.lists.add(list);
        TrackerConfig.saveList(list);
        ResourceTrackerClient.invalidateTargetItemCache();
        scrollOffset = Double.MAX_VALUE;
        menuList = null;
        minecraft.gui.setScreen(new EditScreen(this, list));
    }

    private void createRowButtons() {
        for (TrackerConfig.TrackingList list : TrackerConfig.INSTANCE.lists) {
            Button edit = Button.builder(Component.translatable("gui.resourcetracker.edit_row", list.name), b -> {
                if (minecraft != null) minecraft.gui.setScreen(new EditScreen(this, list));
            }).bounds(listX + 4, listTop, Math.max(1, listWidth - 54), ROW_HEIGHT - 2).build();
            Button eye = Button.builder(Component.translatable(list.isVisible ? "gui.resourcetracker.visibility_hide" : "gui.resourcetracker.visibility_show"), b -> {
                list.isVisible = !list.isVisible;
                TrackerConfig.saveList(list);
                ResourceTrackerClient.invalidateTargetItemCache();
                b.setMessage(Component.translatable(list.isVisible ? "gui.resourcetracker.visibility_hide" : "gui.resourcetracker.visibility_show"));
            }).bounds(listX + listWidth - 48, listTop, 22, ROW_HEIGHT - 2).build();
            Button menu = Button.builder(Component.translatable("gui.resourcetracker.main.list_actions", list.name), b -> {
                menuList = menuList == list ? null : list;
                filesExpanded = false;
                init();
                if (menuList != null) {
                    for (Action action : actions) {
                        if (action.danger()) { setFocused(action.button()); break; }
                    }
                }
            }).bounds(listX + listWidth - 24, listTop, 22, ROW_HEIGHT - 2).build();
            for (Button button : List.of(edit, eye, menu)) {
                button.setAlpha(0);
                addRenderableWidget(button);
            }
            editButtons.add(edit);
            visibilityButtons.add(eye);
            menuButtons.add(menu);
        }
    }

    /** Focus-driven scrolling runs only when focus changes, so wheel scrolling stays put. */
    private void updateRows() {
        int viewport = Math.max(1, listBottom - listTop);
        int maxScroll = Math.max(0, editButtons.size() * ROW_HEIGHT - viewport);
        Button focus = getFocused() instanceof Button button ? button : null;
        if (focus != lastRowFocus) {
            int index = editButtons.indexOf(focus);
            if (index < 0) index = visibilityButtons.indexOf(focus);
            if (index < 0) index = menuButtons.indexOf(focus);
            if (index >= 0) {
                int top = index * ROW_HEIGHT;
                if (top < scrollOffset) scrollOffset = top;
                else if (top + ROW_HEIGHT > scrollOffset + viewport) scrollOffset = top + ROW_HEIGHT - viewport;
            }
            lastRowFocus = focus;
        }
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
        for (int i = 0; i < editButtons.size(); i++) {
            int y = listTop + i * ROW_HEIGHT - (int) scrollOffset;
            editButtons.get(i).setY(y);
            visibilityButtons.get(i).setY(y);
            menuButtons.get(i).setY(y);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        updateRows();
        context.fill(0, 0, width, height, 0x98000000);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, UiTheme.WINDOW);
        border(context, panelX, panelY, panelWidth, panelHeight, UiTheme.BORDER);
        context.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + 34, UiTheme.HEADER);
        text(context, title.getString(), listX, panelY + 8, panelWidth - 20, UiTheme.TEXT);
        text(context, Component.translatable("gui.resourcetracker.main.subtitle").getString(), listX, panelY + 21, panelWidth - 20, UiTheme.MUTED);
        text(context, Component.translatable("gui.resourcetracker.main.lists", TrackerConfig.INSTANCE.lists.size()).getString(), listX, listTop - 12, listWidth, UiTheme.MUTED);
        context.fill(listX, listTop, listX + listWidth, listBottom, UiTheme.PANEL);
        context.enableScissor(listX, listTop, listX + listWidth, listBottom);
        renderRows(context, mouseX, mouseY);
        if (!TrackerConfig.hasActiveContext() || TrackerConfig.INSTANCE.lists.isEmpty()) {
            String key = TrackerConfig.hasActiveContext() ? "gui.resourcetracker.main.empty" : "gui.resourcetracker.no_world";
            String message = Component.translatable(key).getString();
            String fitted = RenderUtils.shortenText(font, message, Math.max(0, listWidth - 16));
            int y = listTop + Math.max(8, (listBottom - listTop) / 2 - 12);
            context.text(font, fitted, listX + (listWidth - font.width(fitted)) / 2, y, UiTheme.MUTED, false);
        }
        context.disableScissor();
        int viewport = listBottom - listTop;
        int contentHeight = editButtons.size() * ROW_HEIGHT;
        RenderUtils.drawStyledScrollbar(context, listX + listWidth + 3, listTop, viewport, contentHeight, scrollOffset);
        if (menuList != null) {
            int available = panelWidth - 20 - Math.min(64, (panelWidth - 20) / 3) - Math.min(96, (panelWidth - 20) * 2 / 3 - 6) - 14;
            if (available > 30) text(context, menuList.name, listX, panelY + panelHeight - 55, available, UiTheme.MUTED);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
        for (Action action : actions) renderAction(context, action, mouseX, mouseY);
        if (mouseX >= listX && mouseX < listX + listWidth && mouseY >= listTop && mouseY < listBottom) {
            for (int i = 0; i < editButtons.size(); i++) {
                if (hovered(visibilityButtons.get(i), mouseX, mouseY)) {
                    VersionCompat.setTooltip(context, font, visibilityButtons.get(i).getMessage(), mouseX, mouseY);
                } else if (hovered(menuButtons.get(i), mouseX, mouseY)) {
                    VersionCompat.setTooltip(context, font, menuButtons.get(i).getMessage(), mouseX, mouseY);
                } else if (hovered(editButtons.get(i), mouseX, mouseY) && font.width(TrackerConfig.INSTANCE.lists.get(i).name) > listWidth - 64) {
                    VersionCompat.setTooltip(context, font, Component.literal(TrackerConfig.INSTANCE.lists.get(i).name), mouseX, mouseY);
                }
            }
        }
        for (Action action : actions) {
            Button button = action.button();
            if (hovered(button, mouseX, mouseY) && font.width(button.getMessage()) > button.getWidth() - 8) {
                VersionCompat.setTooltip(context, font, button.getMessage(), mouseX, mouseY);
            }
        }
    }

    private void renderRows(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        for (int i = 0; i < editButtons.size(); i++) {
            Button edit = editButtons.get(i);
            int y = edit.getY();
            if (y + ROW_HEIGHT <= listTop || y >= listBottom) continue;
            TrackerConfig.TrackingList list = TrackerConfig.INSTANCE.lists.get(i);
            if (hovered(edit, mouseX, mouseY)) context.fill(listX, y, listX + listWidth, y + ROW_HEIGHT - 1, UiTheme.HOVER);
            if (menuList == list) context.fill(listX, y, listX + 2, y + ROW_HEIGHT - 1, UiTheme.ACCENT);
            context.fill(listX, y + ROW_HEIGHT - 1, listX + listWidth, y + ROW_HEIGHT, UiTheme.WINDOW);
            text(context, list.name, listX + 8, y + 5, listWidth - 64, UiTheme.TEXT);
            TrackerConfig.TrackingList.Progress progress = list.progress(); // Uses tick-cached item counts.
            String status = Component.translatable(list.isVisible ? "gui.resourcetracker.main.visible" : "gui.resourcetracker.main.hidden").getString();
            String goals = Component.translatable("gui.resourcetracker.main.goals", progress.completed(), progress.total()).getString();
            boolean complete = progress.total() > 0 && progress.completed() == progress.total();
            String statusPrefix = status + " · ";
            int statusWidth = Math.min(font.width(statusPrefix), Math.max(0, listWidth - 64));
            text(context, statusPrefix, listX + 8, y + 18, statusWidth, UiTheme.MUTED);
            int goalsX = listX + 8 + statusWidth;
            int goalsWidth = Math.max(0, listWidth - 64 - statusWidth);
            boolean showCompleteIcon = complete && goalsWidth >= PngIcons.SMALL_CHECK_ADVANCE;
            if (showCompleteIcon) {
                RenderUtils.drawPngIconInBox(context, PngIcons.Icon.CHECK_SMALL,
                        goalsX, y + 17, PngIcons.SMALL_CHECK_ADVANCE, PngIcons.SMALL_CHECK_ADVANCE, UiTheme.ACCENT);
            }
            text(context, goals, goalsX + (showCompleteIcon ? PngIcons.SMALL_CHECK_ADVANCE : 0), y + 18,
                    goalsWidth - (showCompleteIcon ? PngIcons.SMALL_CHECK_ADVANCE : 0), complete ? UiTheme.ACCENT : UiTheme.MUTED);
            Button eye = visibilityButtons.get(i);
            if (hovered(eye, mouseX, mouseY)) context.fill(eye.getX(), y + 3, eye.getX() + eye.getWidth(), y + ROW_HEIGHT - 4, UiTheme.HOVER);
            RenderUtils.drawPngIconInBox(context, list.isVisible ? PngIcons.Icon.EYE : PngIcons.Icon.EYE_CROSSED,
                    eye.getX(), y + 3, eye.getWidth(), ROW_HEIGHT - 6, list.isVisible ? UiTheme.TEXT : UiTheme.MUTED);
            Button menu = menuButtons.get(i);
            if (hovered(menu, mouseX, mouseY)) context.fill(menu.getX(), y + 3, menu.getX() + menu.getWidth(), y + ROW_HEIGHT - 4, UiTheme.HOVER);
            RenderUtils.drawPngIconInBox(context, PngIcons.Icon.MORE,
                    menu.getX(), y + 3, menu.getWidth(), ROW_HEIGHT - 6, UiTheme.MUTED);
            for (Button button : List.of(edit, eye, menu)) {
                if (button.isFocused()) border(context, button.getX(), y, button.getWidth(), button.getHeight(), UiTheme.ACCENT);
            }
        }
    }

    private void renderAction(GuiGraphicsExtractor context, Action action, int mouseX, int mouseY) {
        Button button = action.button();
        int x = button.getX(), y = button.getY(), w = button.getWidth(), h = button.getHeight();
        boolean hover = button.active && hovered(button, mouseX, mouseY);
        boolean quietFiles = button == filesButton && !filesExpanded;
        if (!quietFiles || hover) {
            context.fill(x, y, x + w, y + h, hover ? UiTheme.HOVER : action.primary() ? UiTheme.HEADER : UiTheme.PANEL);
        }
        if (!quietFiles || hover || button.isFocused()) {
            border(context, x, y, w, h, button.isFocused() ? UiTheme.ACCENT : action.primary() && button.active ? UiTheme.ACCENT : UiTheme.BORDER);
        }
        int color = !button.active || quietFiles ? UiTheme.MUTED : action.danger() ? UiTheme.DANGER : UiTheme.TEXT;
        String label = RenderUtils.shortenText(font, button.getMessage().getString(), Math.max(0, w - 8));
        context.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, color, false);
    }

    private void text(GuiGraphicsExtractor context, String value, int x, int y, int maxWidth, int color) {
        context.text(font, RenderUtils.shortenText(font, value, Math.max(0, maxWidth)), x, y, color, false);
    }

    private void border(GuiGraphicsExtractor context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    private boolean hovered(Button button, double mouseX, double mouseY) {
        return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        updateRows();
        List<Button> rows = new ArrayList<>(editButtons);
        rows.addAll(visibilityButtons);
        rows.addAll(menuButtons);
        // Hidden row hit areas must never intercept toolbar/footer clicks.
        boolean inside = event.x() >= listX && event.x() < listX + listWidth && event.y() >= listTop && event.y() < listBottom;
        if (!inside) rows.forEach(rowButton -> rowButton.visible = false);
        try {
            return super.mouseClicked(event, doubleClick);
        } finally {
            rows.forEach(rowButton -> rowButton.visible = true);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= listX && mouseX < listX + listWidth + 9 && mouseY >= listTop && mouseY < listBottom) {
            scrollOffset -= verticalAmount * ROW_HEIGHT;
            updateRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (ResourceTrackerClient.openMenuKey != null && ResourceTrackerClient.openMenuKey.matches(event)) {
            onClose();
            ResourceTrackerClient.discardHandledOpenMenuKeyClicks();
            return true;
        }
        boolean handled = super.keyPressed(event);
        updateRows();
        return handled;
    }

    private void confirmDelete(TrackerConfig.TrackingList list) {
        if (minecraft == null || list == null) return;
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                TrackerHistory.recordDeletion(list, TrackerConfig.INSTANCE.lists.indexOf(list));
                TrackerConfig.deleteList(list);
                ResourceTrackerClient.invalidateTargetItemCache();
            }
            menuList = null;
            if (minecraft != null) minecraft.gui.setScreen(this);
        }, Component.translatable("gui.resourcetracker.delete_confirm_title"), Component.translatable("gui.resourcetracker.delete_confirm", list.name)));
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }
}
