package dev.perplexity.client.mixin.rotation;

import dev.perplexity.client.utils.rotation.RotationManager;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Lets a Change Look rotation absorb the raw mouse delta before the camera is turned. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerRotationMixin {
    @WrapOperation(
            method = "turnPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void perplexity$handleManagedMouseTurn(LocalPlayer player, double cursorDeltaX, double cursorDeltaY,
                                                   Operation<Void> original) {
        RotationManager.handleMouseTurn(cursorDeltaX, cursorDeltaY);
        original.call(player, cursorDeltaX, cursorDeltaY);
    }
}
