package dev.perplexity.client.mixin.rotation;

import dev.perplexity.client.utils.rotation.RotationManager;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Moves the player along the managed movement yaw instead of the camera yaw. */
@Mixin(Entity.class)
public abstract class EntityMovementMixin {
    @ModifyExpressionValue(
            method = "moveRelative",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getInputVector(Lnet/minecraft/world/phys/Vec3;FF)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 perplexity$movementVelocity(Vec3 original,
                                             @Local(argsOnly = true, ordinal = 0) float speed,
                                             @Local(argsOnly = true, ordinal = 0) Vec3 input) {
        if ((Object) this != Minecraft.getInstance().player) {
            return original;
        }
        Entity entity = (Entity) (Object) this;
        float playerYaw = entity.getYRot();
        float movementYaw = RotationManager.movementYaw(playerYaw);
        return movementYaw == playerYaw ? original : inputVector(input, speed, movementYaw);
    }

    private static Vec3 inputVector(Vec3 input, float speed, float yaw) {
        double lengthSqr = input.lengthSqr();
        if (lengthSqr < 1.0E-7D) {
            return Vec3.ZERO;
        }

        Vec3 scaled = (lengthSqr > 1.0D ? input.normalize() : input).scale(speed);
        float sin = Mth.sin(yaw * Mth.DEG_TO_RAD);
        float cos = Mth.cos(yaw * Mth.DEG_TO_RAD);
        return new Vec3(
                scaled.x * cos - scaled.z * sin,
                scaled.y,
                scaled.z * cos + scaled.x * sin);
    }
}
