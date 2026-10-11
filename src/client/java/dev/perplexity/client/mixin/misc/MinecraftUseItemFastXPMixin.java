package dev.perplexity.client.mixin.misc;

import dev.perplexity.client.impl.modules.misc.FastXPModule;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds vanilla's repeat-use off while Fast XP drives the throw rate itself. Without this the
 * two cadences interleave and throws randomly double up or drop out.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftUseItemFastXPMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void perplexity$fastXPgate(CallbackInfo ci) {
        if (FastXPModule.suppressVanillaUse()) {
            ci.cancel();
        }
    }
}
