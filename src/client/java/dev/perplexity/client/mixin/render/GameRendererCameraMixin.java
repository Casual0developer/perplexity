package dev.perplexity.client.mixin.render;

import dev.perplexity.client.impl.modules.render.CameraModule;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips the hurt/death camera tilt while No Hurt Cam is on.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererCameraMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void perplexity$steadyCam(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (CameraModule.enabled()) {
            ci.cancel();
        }
    }
}
