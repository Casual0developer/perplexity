package dev.perplexity.client.mixin.protect;

import dev.perplexity.client.impl.modules.misc.ProtectModule;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swaps the local player's skin for a random default one while Protect has Randomize Skin on.
 * Only the local player is ever affected, and it is purely visual on this client.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class LocalPlayerSkinProtectMixin {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void perplexity$randomizeSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin spoofed = ProtectModule.skinFor((AbstractClientPlayer) (Object) this);
        if (spoofed != null) {
            cir.setReturnValue(spoofed);
        }
    }
}
