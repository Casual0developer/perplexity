package dev.perplexity.client.mixin.rotation;

import dev.perplexity.client.utils.rotation.RotationManager;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Remaps the pressed keys onto the movement yaw so the player walks the direction the server sees,
 * rather than the direction the (silent) camera points.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputRotationMixin {
    @ModifyExpressionValue(
            method = "tick",
            at = @At(value = "NEW", target = "(ZZZZZZZ)Lnet/minecraft/world/entity/player/Input;"))
    private Input perplexity$correctMovementInput(Input input) {
        return RotationManager.correctKeyboardInput(input);
    }
}
