package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.client.ResourceTrackerClient;
import net.fabricmc.resourcetracker.client.render.HudRenderModel;
import net.fabricmc.resourcetracker.client.render.HudRenderer;
import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.model.HudPlacement;
import net.fabricmc.resourcetracker.util.RenderUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Screen for repositioning HUD lists by dragging their rendered cards. */
public class HudMoveScreen extends Screen {
    private static final int CARD_EDGE = (UiTheme.BORDER & 0x00FFFFFF) | 0x90000000;

    private final Screen parent;
    private final HudMoveLayout layout = new HudMoveLayout();
    private TrackerButton done;

    public HudMoveScreen(Screen parent) {
        super(Component.translatable("gui.resourcetracker.move.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        layout.resize(width, height);
        done = addRenderableWidget(TrackerButton.builder(Component.translatable("gui.resourcetracker.done"), b -> onClose())
                .bounds(0, 0, HudMoveLayout.DONE_WIDTH, 20).primary().build());
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Keep the game world visible while the player positions cards.
        context.fill(0, 0, width, height, 0x38000000);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        handleMouseInput(mouseX, mouseY);

        HudMoveLayout.Card dragged = layout.draggedCard();
        HudMoveLayout.Card overlap = layout.overlappingCard(dragged);
        HudMoveLayout.Card hovered = dragged == null ? layout.hoveredCard(mouseX, mouseY) : null;

        for (HudMoveLayout.Card card : layout.cards()) {
            if (card != dragged) drawCard(context, card, card == hovered ? UiTheme.TEXT : CARD_EDGE, false);
        }
        if (dragged != null) {
            VersionCompat.nextGuiLayer(context);
            drawCard(context, dragged, UiTheme.ACCENT, overlap != null);
        }

        VersionCompat.nextGuiLayer(context);
        drawControls(context, dragged, overlap);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawCard(GuiGraphics context, HudMoveLayout.Card card, int edge, boolean opaqueBacking) {
        HudPlacement placement = card.placement();
        HudRenderModel model = card.model();
        VersionCompat.push(context);
        VersionCompat.translate(context, placement.x(), placement.y());
        VersionCompat.scale(context, placement.scale(), placement.scale());
        if (opaqueBacking) {
            context.fill(model.backgroundMinX, model.backgroundMinY, model.backgroundMaxX, model.backgroundMaxY,
                    UiTheme.WINDOW);
            VersionCompat.nextGuiLayer(context);
        }
        HudRenderer.render(context, font, model);
        outline(context, model.backgroundMinX - 1, model.backgroundMinY - 1,
                model.backgroundMaxX + 1, model.backgroundMaxY + 1, edge);
        VersionCompat.pop(context);
    }

    private void drawControls(GuiGraphics context, HudMoveLayout.Card dragged, HudMoveLayout.Card overlap) {
        String hint = Component.translatable(overlap == null
                ? "gui.resourcetracker.move.drag_hint" : "gui.resourcetracker.move.overlap").getString();
        String detail = "";
        if (dragged != null) {
            TrackerConfig.TrackingList list = dragged.list();
            detail = list.name + " · " + list.x + ", " + list.y;
        } else if (overlap != null) {
            detail = Component.translatable("gui.resourcetracker.move.overlap_hint", overlap.list().name).getString();
        }

        int padding = HudMoveLayout.DONE_WIDTH + 18;
        int desiredWidth = Math.min(Math.max(font.width(hint), font.width(detail)), 220) + padding;
        HudMoveLayout.Panel panel = layout.fitPanel(desiredWidth, dragged);
        done.setX(panel.right() - HudMoveLayout.DONE_WIDTH - 5);
        done.setY(panel.top() + 5);

        // A large card can fill the viewport. Its drag preview takes priority in that case.
        done.visible = dragged == null || !panel.covers(dragged);
        if (!done.visible) return;

        context.fill(panel.left(), panel.top(), panel.right(), panel.bottom(), UiTheme.PANEL);
        outline(context, panel.left(), panel.top(), panel.right(), panel.bottom(), UiTheme.BORDER);
        int textWidth = Math.max(1, panel.width() - padding);
        context.drawString(font, RenderUtils.shortenText(font, hint, textWidth), panel.left() + 6,
                panel.top() + (detail.isEmpty() ? 11 : 5), overlap == null ? UiTheme.TEXT : UiTheme.DANGER);
        if (!detail.isEmpty()) {
            context.drawString(font, RenderUtils.shortenText(font, detail, textWidth),
                    panel.left() + 6, panel.top() + 16, UiTheme.MUTED);
        }
    }

    private void handleMouseInput(int mouseX, int mouseY) {
        long handle = VersionCompat.getWindowHandle(minecraft.getWindow());
        boolean down = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        layout.update(font, mouseX, mouseY, down);
    }

    private static void outline(GuiGraphics context, int left, int top, int right, int bottom, int color) {
        context.fill(left, top, right, top + 1, color);
        context.fill(left, bottom - 1, right, bottom, color);
        context.fill(left, top + 1, left + 1, bottom - 1, color);
        context.fill(right - 1, top + 1, right, bottom - 1, color);
    }

    @Override
    public void onClose() {
        TrackerConfig.saveAllActiveContextLists();
        ResourceTrackerClient.invalidateTargetItemCache();
        if (minecraft != null) minecraft.setScreen(parent);
    }

}
