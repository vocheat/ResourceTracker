package net.fabricmc.resourcetracker.client.render;

import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.client.gui.HudMoveScreen;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.model.HudPlacement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class HudOverlay {
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.hud.isHidden()) return;
        if (!TrackerConfig.INSTANCE.hudVisible) return;
        if (client.gui.screen() instanceof HudMoveScreen) return;

        for (TrackerConfig.TrackingList list : TrackerConfig.INSTANCE.lists) {
            if (!list.isVisible) continue;

            HudRenderModel model = HudRenderCache.get(list, client.font, client.getWindow().getGuiScaledHeight());
            HudPlacement placement = model.placement(list.x, list.y, TrackerConfig.clampScale(list.scale),
                    client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
            VersionCompat.push(context);
            VersionCompat.translate(context, placement.x(), placement.y());
            VersionCompat.scale(context, placement.scale(), placement.scale());

            HudRenderer.render(context, client.font, model);

            VersionCompat.pop(context);
        }
    }

}
