package dev.perplexity.client.mixin.rotation;

import dev.perplexity.client.utils.rotation.MovementCorrection;
import dev.perplexity.client.utils.rotation.Rotation;
import dev.perplexity.client.utils.rotation.RotationManager;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Uses the managed movement rotation for the firework elytra boost. */
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketRotationMixin {
    @Shadow
    private LivingEntity attachedToEntity;

    @ModifyExpressionValue(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 perplexity$flightBoostDirection(Vec3 original) {
        if (attachedToEntity != Minecraft.getInstance().player
                || RotationManager.movementCorrection() == MovementCorrection.OFF) {
            return original;
        }
        Rotation rotation = RotationManager.currentRotation();
        return rotation == null ? original : rotation.direction();
    }
}
