package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.client.render.HudRenderCache;
import net.fabricmc.resourcetracker.client.render.HudRenderModel;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.model.HudPlacement;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/** Keeps placement and hit testing in the same coordinates as the in-game HUD. */
final class HudMoveLayout {
    static final int PANEL_HEIGHT = 30;
    static final int DONE_WIDTH = 58;
    private static final int MARGIN = 2;

    private final List<Card> cards = new ArrayList<>();
    private int width;
    private int height;
    private Panel panel;
    private TrackerConfig.TrackingList draggingList;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean wasMouseDown;

    void resize(int width, int height) {
        this.width = width;
        this.height = height;
        panel = null;
        draggingList = null;
        wasMouseDown = false;
    }

    void update(Font font, int mouseX, int mouseY, boolean down) {
        if (!down && wasMouseDown && draggingList != null) {
            draggingList = null;
            TrackerConfig.saveAllActiveContextLists();
        }

        cards.clear();
        for (TrackerConfig.TrackingList list : TrackerConfig.INSTANCE.lists) {
            if (!list.isVisible) continue;
            HudRenderModel model = HudRenderCache.preview(list, font, height);
            HudPlacement placement = place(list, model, list.x, list.y);
            if (down && wasMouseDown && list == draggingList) {
                placement = place(list, model, mouseX - dragOffsetX, mouseY - dragOffsetY);
                list.x = Math.round(placement.x());
                list.y = Math.round(placement.y());
                placement = place(list, model, list.x, list.y);
            }
            cards.add(new Card(list, model, placement));
        }

        if (down && !wasMouseDown) {
            Card hovered = hoveredCard(mouseX, mouseY);
            if (hovered != null) {
                draggingList = hovered.list;
                dragOffsetX = mouseX - hovered.placement.x();
                dragOffsetY = mouseY - hovered.placement.y();
            }
        }
        wasMouseDown = down;
    }

    private HudPlacement place(TrackerConfig.TrackingList list, HudRenderModel model, double x, double y) {
        return model.placement(x, y, TrackerConfig.clampScale(list.scale), width, height);
    }

    Card hoveredCard(int mouseX, int mouseY) {
        if (panel != null && panel.contains(mouseX, mouseY)) return null;
        for (int i = cards.size() - 1; i >= 0; i--) {
            Card card = cards.get(i);
            if (card.placement.contains(mouseX, mouseY)) return card;
        }
        return null;
    }

    Card draggedCard() {
        if (draggingList != null) {
            for (Card card : cards) if (card.list == draggingList) return card;
        }
        return null;
    }

    /** At rest, identify a card on top of an intersection so it can be grabbed there. */
    Card overlappingCard(Card dragged) {
        if (dragged != null) {
            for (Card card : cards) if (card != dragged && overlaps(card.placement, dragged.placement)) return dragged;
            return null;
        }
        for (int front = cards.size() - 1; front > 0; front--) {
            Card card = cards.get(front);
            for (int back = 0; back < front; back++) {
                if (overlaps(card.placement, cards.get(back).placement)) return card;
            }
        }
        return null;
    }

    private static boolean overlaps(HudPlacement a, HudPlacement b) {
        return a.left() < b.right() && b.left() < a.right() && a.top() < b.bottom() && b.top() < a.bottom();
    }

    /** Prefer an empty corner, then use space above or below an existing card. */
    Panel fitPanel(int desiredWidth, Card dragged) {
        int panelWidth = Math.min(desiredWidth, width - 2 * MARGIN);
        int rightX = width - MARGIN - panelWidth;
        int bottomY = height - MARGIN - PANEL_HEIGHT;
        Panel best = new Panel(rightX, bottomY, panelWidth);
        double bestCost = panelCost(best, dragged);
        if (panel != null) {
            Panel previous = new Panel(Math.min(panel.left, rightX), panel.top, panelWidth);
            double cost = panelCost(previous, dragged);
            if (cost <= bestCost) {
                best = previous;
                bestCost = cost;
            }
        }
        if (bestCost == 0) return panel = best;

        List<Integer> rows = new ArrayList<>();
        rows.add(bottomY);
        rows.add(height - 48 - PANEL_HEIGHT);
        rows.add(MARGIN);
        for (Card card : cards) {
            rows.add((int) Math.floor(card.placement.top()) - PANEL_HEIGHT - MARGIN);
            rows.add((int) Math.ceil(card.placement.bottom()) + MARGIN);
        }
        for (int y : rows) {
            if (y < MARGIN || y > bottomY) continue;
            for (int x : new int[]{rightX, MARGIN}) {
                Panel candidate = new Panel(x, y, panelWidth);
                double cost = panelCost(candidate, dragged);
                if (cost == 0) return panel = candidate;
                if (cost < bestCost) {
                    best = candidate;
                    bestCost = cost;
                }
            }
        }
        return panel = best;
    }

    private double panelCost(Panel candidate, Card dragged) {
        double cost = 10 * candidate.intersection(width / 2 - 97, height - 48, width / 2 + 97, height);
        for (Card card : cards) {
            HudPlacement bounds = card.placement;
            double headerBottom = Math.min(bounds.bottom(), bounds.top() + 28 * bounds.scale());
            cost += 10 * candidate.intersection(bounds.left(), bounds.top(), bounds.right(), headerBottom);
            cost += (card == dragged ? 10 : 1)
                    * candidate.intersection(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        }
        return cost;
    }

    List<Card> cards() { return cards; }
    boolean isDragging() { return draggingList != null; }

    record Card(TrackerConfig.TrackingList list, HudRenderModel model, HudPlacement placement) {}

    record Panel(int left, int top, int width) {
        int right() { return left + width; }
        int bottom() { return top + PANEL_HEIGHT; }

        boolean contains(int x, int y) {
            return x >= left && x < right() && y >= top && y < bottom();
        }

        boolean covers(Card card) {
            HudPlacement bounds = card.placement;
            return intersection(bounds.left(), bounds.top(), bounds.right(), bounds.bottom()) > 0;
        }

        double intersection(double x1, double y1, double x2, double y2) {
            return Math.max(0, Math.min(right(), x2) - Math.max(left, x1))
                    * Math.max(0, Math.min(bottom(), y2) - Math.max(top, y1));
        }
    }
}
