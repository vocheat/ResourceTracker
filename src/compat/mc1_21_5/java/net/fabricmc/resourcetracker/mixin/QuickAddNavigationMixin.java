package net.fabricmc.resourcetracker.mixin;

import net.fabricmc.resourcetracker.client.QuickAddController;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep the underlying container alive only while opening our modal confirmation. */
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class QuickAddNavigationMixin {
    @Redirect(method = "setScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;removed()V"))
    private void resourcetracker$preserveQuickAddParent(Screen previous) {
        if (!QuickAddController.isPreservingParent(previous)) previous.removed();
    }
}
