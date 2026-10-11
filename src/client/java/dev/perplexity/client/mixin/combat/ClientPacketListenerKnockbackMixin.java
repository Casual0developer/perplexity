package dev.perplexity.client.mixin.combat;

import dev.perplexity.client.impl.modules.combat.AntiKnockbackModule;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes server velocity packets through the Anti Knockback module so scaled motion replaces
 * the vanilla one instead of stacking with it.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerKnockbackMixin {
    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"), cancellable = true)
    private void perplexity$reduceKnockback(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        if (AntiKnockbackModule.handle(packet)) {
            ci.cancel();
        }
    }
}
