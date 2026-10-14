package dev.perplexity.client.mixin.anchor;

import dev.perplexity.client.impl.modules.combat.AnchorHelperModule;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Runs the anchor macro inside vanilla's keybind handling — after hotbar/menu input but before the
 * tick's attacks and item use — and suppresses the manual interactions it has taken over, so a
 * sequence never double-fires a use packet.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftAnchorInputMixin {
    @Inject(method = "handleKeybinds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z", ordinal = 0))
    private void perplexity$runAnchorHelper(CallbackInfo callbackInfo) {
        AnchorHelperModule.onHandleInputEarly();
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void perplexity$blockAnchorAttack(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (AnchorHelperModule.ownsCombatInput()) {
            callbackInfo.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "continueAttack", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean perplexity$blockHeldAttack(boolean held) {
        return held && !AnchorHelperModule.ownsCombatInput();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void perplexity$blockAnchorUse(CallbackInfo callbackInfo) {
        if (AnchorHelperModule.ownsCombatInput()) {
            callbackInfo.cancel();
        }
    }
}
