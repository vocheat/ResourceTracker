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

package net.fabricmc.resourcetracker.client.render;

import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Handles the rendering of the Resource Tracker overlay on the in-game HUD.
 * <p>
 * This class is responsible for drawing the tracking lists, items, counts,
 * and handling layout logic (such as multi-column rendering if the list is too long).
 * </p>
 *
 * @author vocheat
 */
public class HudOverlay {



    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        if (!TrackerConfig.INSTANCE.hudVisible) return;

        for (TrackerConfig.TrackingList list : TrackerConfig.INSTANCE.lists) {
            if (!list.isVisible) continue;

            VersionCompat.push(context);
            VersionCompat.translate(context, (float) list.x, (float) list.y);
            float scale = TrackerConfig.clampScale(list.scale);
            VersionCompat.scale(context, scale, scale);

            renderListContent(context, client, list);

            VersionCompat.pop(context);
        }
    }

    private void renderListContent(GuiGraphics context, Minecraft client, TrackerConfig.TrackingList list) {
        HudRenderModel model = HudRenderCache.get(list, client.font, client.getWindow().getGuiScaledHeight());

        context.fill(
                model.backgroundMinX,
                model.backgroundMinY,
                model.backgroundMaxX,
                model.backgroundMaxY,
                model.backgroundColor
        );
        context.drawString(client.font, model.title, 0, 0, model.titleColor);

        for (HudRenderModel.Row row : model.rows) {
            if (model.showIcons && row.stack != null) {
                context.renderItem(row.stack, row.itemX, row.itemY);
            }
            context.drawString(client.font, row.nameText, row.nameX, row.nameY, row.nameColor);
            context.drawString(client.font, row.countText, row.countX, row.countY, row.countColor);
        }
    }
}
