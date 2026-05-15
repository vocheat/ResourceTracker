package net.fabricmc.resourcetracker.client.render;

import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class HudOverlay {
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        if (!TrackerConfig.INSTANCE.hudVisible) return;

        for (TrackerConfig.TrackingList list : TrackerConfig.INSTANCE.lists) {
            if (!list.isVisible) continue;

            VersionCompat.push(context);
            VersionCompat.translate(context, list.x, list.y);
            float scale = TrackerConfig.clampScale(list.scale);
            VersionCompat.scale(context, scale, scale);

            renderListContent(context, client, list);

            VersionCompat.pop(context);
        }
    }

    private void renderListContent(GuiGraphicsExtractor context, Minecraft client, TrackerConfig.TrackingList list) {
        HudRenderModel model = HudRenderCache.get(list, client.font, client.getWindow().getGuiScaledHeight());

        context.fill(
                model.backgroundMinX,
                model.backgroundMinY,
                model.backgroundMaxX,
                model.backgroundMaxY,
                model.backgroundColor
        );
        context.text(client.font, model.title, 0, 0, model.titleColor);

        for (HudRenderModel.Row row : model.rows) {
            if (model.showIcons && row.stack != null) {
                context.item(row.stack, row.itemX, row.itemY);
            }
            context.text(client.font, row.nameText, row.nameX, row.nameY, row.nameColor);
            context.text(client.font, row.countText, row.countX, row.countY, row.countColor);
        }
    }
}
