package dev.perplexity.client.mixin.protect;

import dev.perplexity.client.impl.modules.misc.ProtectionModule;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Anonymizes tab-list names while Protect is enabled. Runs at the head of the display-name
 * lookup so the fake name replaces the real one before team formatting is applied.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayProtectMixin {
    @Inject(method = "getNameForDisplay", at = @At("HEAD"), cancellable = true)
    private void perplexity$anonymizeTabName(PlayerInfo entry, CallbackInfoReturnable<Component> cir) {
        Component alias = ProtectionModule.tabNameFor(entry);
        if (alias != null) {
            cir.setReturnValue(alias);
        }
    }
}
