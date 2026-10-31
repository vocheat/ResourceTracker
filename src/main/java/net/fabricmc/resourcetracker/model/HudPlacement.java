package net.fabricmc.resourcetracker.model;

/** Transient, viewport-safe transform and hit bounds for a complete HUD card. */
public record HudPlacement(float x, float y, float scale, double left, double top, double right, double bottom) {
    /**
     * Keep the requested origin until an edge would leave the viewport. Oversized cards
     * are uniformly fitted without changing their configured scale or layout.
     */
    public static HudPlacement resolve(double x, double y, float configuredScale,
                                       double minX, double minY, double maxX, double maxY,
                                       int viewportWidth, int viewportHeight) {
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(maxX)
                || !Double.isFinite(maxY) || maxX < minX || maxY < minY) {
            throw new IllegalArgumentException("HUD bounds must be finite and ordered");
        }
        if (viewportWidth <= 0 || viewportHeight <= 0) return new HudPlacement(0, 0, 0, 0, 0, 0, 0);
        double requestedScale = Float.isFinite(configuredScale) && configuredScale > 0 ? configuredScale : 1;
        double fittedScale = requestedScale;
        if (maxX > minX) fittedScale = Math.min(fittedScale, viewportWidth / (maxX - minX));
        if (maxY > minY) fittedScale = Math.min(fittedScale, viewportHeight / (maxY - minY));
        float scale = floorFloat(fittedScale);
        // A fully fitted card may need a fractional origin. Leave enough room for
        // the float transform used by every Minecraft GUI compatibility profile.
        while (scale > 0 && (ceilFloat(-minX * scale) > floorFloat(viewportWidth - maxX * scale)
                || ceilFloat(-minY * scale) > floorFloat(viewportHeight - maxY * scale))) {
            scale = Math.nextDown(scale);
        }
        float placedX = clampOrigin(x, -minX * scale, viewportWidth - maxX * scale);
        float placedY = clampOrigin(y, -minY * scale, viewportHeight - maxY * scale);
        return new HudPlacement(placedX, placedY, scale, placedX + minX * scale, placedY + minY * scale,
                placedX + maxX * scale, placedY + maxY * scale);
    }

    public boolean contains(double mouseX, double mouseY) {
        return scale > 0 && mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }

    private static float clampOrigin(double value, double min, double max) {
        if (!Double.isFinite(value)) value = 0;
        return Math.max(ceilFloat(min), Math.min((float) value, floorFloat(max)));
    }

    private static float floorFloat(double value) {
        float result = (float) value;
        return result > value ? Math.nextDown(result) : result;
    }

    private static float ceilFloat(double value) {
        float result = (float) value;
        return result < value ? Math.nextUp(result) : result;
    }
}
