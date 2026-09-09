package net.fabricmc.resourcetracker.compat;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.resourcetracker.client.render.HudOverlay;

public class HudCompat {
    public static void register(HudOverlay overlay) {
        HudRenderCallback.EVENT.register(overlay::render);
    }
}
