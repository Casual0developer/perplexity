package dev.perplexity.client.mixin.protect;

import dev.perplexity.client.impl.modules.misc.ProtectModule;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Anonymizes overhead nametags while Protect is enabled. Every entity renderer funnels through
 * {@code getNameTag}, so one injection covers players without touching other entities.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityNametagProtectMixin {
    @Inject(method = "getNameTag", at = @At("HEAD"), cancellable = true)
    private void perplexity$anonymizeNametag(Entity entity, CallbackInfoReturnable<Component> cir) {
        Component spoofed = ProtectModule.nametagFor(entity);
        if (spoofed != null) {
            cir.setReturnValue(spoofed);
        }
    }
}
