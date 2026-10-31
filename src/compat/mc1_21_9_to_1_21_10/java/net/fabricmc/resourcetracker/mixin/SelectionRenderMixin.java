package net.fabricmc.resourcetracker.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.resourcetracker.compat.SelectionCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Supply the rendering events absent from Fabric API for Minecraft 1.21.9. */
@Mixin(LevelRenderer.class)
public abstract class SelectionRenderMixin {
    @Inject(method = "extractBlockOutline", at = @At("RETURN"))
    private void resourcetracker$extractSelection(Camera camera, LevelRenderState state, CallbackInfo ci) {
        SelectionCompat.extract(state);
    }

    @Inject(method = "submitEntities", at = @At("RETURN"))
    private void resourcetracker$submitSelection(PoseStack matrices, LevelRenderState state,
                                               SubmitNodeCollector collector, CallbackInfo ci) {
        SelectionCompat.submit(matrices, state, collector);
    }
}
