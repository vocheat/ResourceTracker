package net.fabricmc.resourcetracker.model;

/** Dependency-free checks for the transform shared by HUD rendering and dragging. */
public final class HudPlacementRegression {
    public static void main(String[] args) {
        leftPositionExpandsRight();
        growthMovesWholeRightCardInward();
        iconLayoutChanges();
        fractionalScaleAndDrag();
        viewportResize();
        oversizedCardsFit();
        degenerateViewport();
        System.out.println("HUD placement regression checks passed");
    }

    private static void leftPositionExpandsRight() {
        HudPlacement before = card(20, 30, 1, 209, 91, 640, 360);
        HudPlacement after = card(20, 30, 1, 245, 91, 640, 360);
        close(20, before.x(), "ordinary saved X");
        close(before.x(), after.x(), "left card keeps its origin when counts grow");
        close(30, after.y(), "ordinary saved Y");
        close(1, after.scale(), "ordinary configured scale");
        close(36, after.right() - before.right(), "left card expands to the right");
    }

    private static void growthMovesWholeRightCardInward() {
        // A completed row adds a check and can change its count text width.
        HudPlacement before = card(430, 20, 1, 209, 91, 640, 360);
        HudPlacement after = card(430, 20, 1, 245, 91, 640, 360);
        close(430, before.x(), "right card initially fits");
        close(395, after.x(), "completed card origin shifts inward");
        close(640, after.right(), "completed card right edge");
        close(after.x() - before.x(), after.left() - before.left(), "title and card move together");
        check(after.contains(after.left(), after.top()), "displayed top-left is draggable");
        check(!after.contains(after.left() - 1, after.top()), "old detached left edge is not draggable");
        inside(after, 640, 360);
    }

    private static void iconLayoutChanges() {
        // Icons use stacked name/count rows; disabling them uses a wider horizontal row.
        HudPlacement icons = card(405, 20, 1, 220, 119, 640, 360);
        HudPlacement text = card(405, 20, 1, 310, 79, 640, 360);
        close(405, icons.x(), "icon layout fits saved origin");
        close(330, text.x(), "text-only layout moves inward");
        close(640, text.right(), "text-only card right edge");
        inside(text, 640, 360);
        close(405, card(405, 20, 1, 220, 119, 640, 360).x(),
                "restoring icons restores the saved origin without persisted anchoring");
    }

    private static void fractionalScaleAndDrag() {
        HudPlacement placed = card(300, -40, 1.375f, 177, 89, 320, 200);
        close(1.375, placed.scale(), "fractional configured scale is preserved");
        inside(placed, 320, 200);
        check(placed.contains((placed.left() + placed.right()) / 2,
                (placed.top() + placed.bottom()) / 2), "fitted card hit testing");
        double mouseX = placed.x() + 35, mouseY = placed.y() + 20;
        double offsetX = mouseX - placed.x(), offsetY = mouseY - placed.y();
        HudPlacement noMove = card(mouseX - offsetX, mouseY - offsetY, 1.375f, 177, 89, 320, 200);
        close(placed.x(), noMove.x(), "clicking a clamped card does not jump X");
        close(placed.y(), noMove.y(), "clicking a clamped card does not jump Y");
        HudPlacement dragged = card(10_000, 10_000, 1.375f, 177, 89, 320, 200);
        HudPlacement saved = card(Math.round(dragged.x()), Math.round(dragged.y()),
                1.375f, 177, 89, 320, 200);
        inside(saved, 320, 200);
        check(Math.abs(saved.x() - dragged.x()) <= 0.5, "integer saved X stays at the displayed edge");
        check(Math.abs(saved.y() - dragged.y()) <= 0.5, "integer saved Y stays at the displayed edge");
    }

    private static void viewportResize() {
        HudPlacement small = card(430, 250, 1, 209, 91, 320, 180);
        inside(small, 320, 180);
        HudPlacement restored = card(430, 250, 1, 209, 91, 640, 360);
        close(430, restored.x(), "wider viewport restores saved X");
        close(250, restored.y(), "taller viewport restores saved Y");
    }

    private static void oversizedCardsFit() {
        HudPlacement wide = card(430, 250, 2, 1993, 493, 320, 180);
        inside(wide, 320, 180);
        check(wide.scale() <= 0.16f, "wide card fits uniformly");
        close(320, wide.right() - wide.left(), "wide card uses viewport width", 0.0001);
        HudPlacement tall = card(-100, -100, 2, 193, 1993, 320, 180);
        inside(tall, 320, 180);
        close(180, tall.bottom() - tall.top(), "tall card uses viewport height", 0.0001);
        for (float scale : new float[]{0.3f, 0.75f, 1, 1.375f, 2, 5}) {
            for (int width : new int[]{1, 17, 319, 640}) {
                HudPlacement p = card(Integer.MAX_VALUE, Integer.MIN_VALUE, scale, 2003, 997, width, 173);
                inside(p, width, 173);
            }
        }
    }

    private static void degenerateViewport() {
        for (int[] viewport : new int[][]{{0, 180}, {320, 0}, {-1, 180}, {320, -1}, {0, 0}}) {
            HudPlacement p = card(430, 250, 1, 209, 91, viewport[0], viewport[1]);
            close(0, p.scale(), "no transform for an empty viewport");
            check(!p.contains(0, 0), "empty viewport has no drag hit target");
            check(Float.isFinite(p.x()) && Float.isFinite(p.y()), "empty viewport placement stays finite");
        }
        HudPlacement point = HudPlacement.resolve(10, 20, 1, 0, 0, 0, 0, 320, 180);
        close(10, point.x(), "zero-width bounds keep X");
        close(20, point.y(), "zero-height bounds keep Y");
    }

    private static HudPlacement card(double x, double y, float scale, double right, double bottom,
                                     int viewportWidth, int viewportHeight) {
        return HudPlacement.resolve(x, y, scale, -7, -7, right, bottom, viewportWidth, viewportHeight);
    }

    private static void inside(HudPlacement p, int width, int height) {
        check(p.left() >= 0 && p.top() >= 0 && p.right() <= width && p.bottom() <= height,
                "all rendered edges fit: " + p + " in " + width + "x" + height);
    }

    private static void close(double expected, double actual, String message) {
        close(expected, actual, message, 0.00001);
    }

    private static void close(double expected, double actual, String message, double tolerance) {
        check(Math.abs(expected - actual) <= tolerance, message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
