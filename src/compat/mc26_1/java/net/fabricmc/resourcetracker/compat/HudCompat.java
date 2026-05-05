package net.fabricmc.resourcetracker.compat;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.resourcetracker.client.render.HudOverlay;
import net.minecraft.resources.Identifier;

public class HudCompat {
    public static void register(HudOverlay overlay) {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.parse("resourcetracker:hud"),
                overlay::render
        );
    }
}
