package dev.perplexity.client.mixin.rotation;

import dev.perplexity.client.utils.rotation.MovementCorrection;
import dev.perplexity.client.utils.rotation.Rotation;
import dev.perplexity.client.utils.rotation.RotationManager;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Applies the movement yaw to jump velocity and elytra flight. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMovementRotationMixin {
    @ModifyExpressionValue(
            method = "jumpFromGround",
            at = @At(value = "NEW", target = "(DDD)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 perplexity$fixJumpVelocity(Vec3 original) {
        Rotation rotation = movementRotation();
        if (rotation == null) {
            return original;
        }

        float yaw = rotation.yaw() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw) * 0.2F, 0.0, Mth.cos(yaw) * 0.2F);
    }

    @ModifyExpressionValue(
            method = "updateFallFlyingMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F"))
    private float perplexity$fixFallFlyingPitch(float original) {
        Rotation rotation = movementRotation();
        return rotation == null ? original : rotation.pitch();
    }

    @ModifyExpressionValue(
            method = "updateFallFlyingMovement",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 perplexity$fixFallFlyingLook(Vec3 original) {
        Rotation rotation = movementRotation();
        return rotation == null ? original : rotation.direction();
    }

    private Rotation movementRotation() {
        if ((Object) this != Minecraft.getInstance().player) {
            return null;
        }
        if (RotationManager.movementCorrection() == MovementCorrection.OFF) {
            return null;
        }
        return RotationManager.currentRotation();
    }
}
