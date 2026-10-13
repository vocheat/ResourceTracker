package net.fabricmc.resourcetracker.client.render;

import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.client.gui.HudMoveScreen;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class HudOverlay {
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        if (!TrackerConfig.INSTANCE.hudVisible) return;
        if (client.screen instanceof HudMoveScreen) return;

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

        HudRenderer.render(context, client.font, model);
    }
}
