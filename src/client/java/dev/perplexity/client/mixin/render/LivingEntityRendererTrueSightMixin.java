package dev.perplexity.client.mixin.render;

import dev.perplexity.client.impl.modules.render.TrueSightModule;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reveals invisible players while True Sight is enabled. The state flags drive body rendering,
 * and forcing {@code shouldShowName} lets vanilla bake the nametag (and its attachment) itself.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererTrueSightMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void perplexity$revealBody(LivingEntity entity, LivingEntityRenderState state,
                                       float partialTick, CallbackInfo ci) {
        if (TrueSightModule.reveal(entity)) {
            state.isInvisible = false;
            state.isInvisibleToPlayer = false;
        }
    }

    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void perplexity$revealNametag(LivingEntity entity, double distanceToCameraSq,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (entity != null && TrueSightModule.reveal(entity)) {
            cir.setReturnValue(true);
        }
    }
}
