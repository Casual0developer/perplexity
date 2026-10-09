package dev.perplexity.client.mixin.rotation;

import dev.perplexity.client.utils.rotation.Rotation;
import dev.perplexity.client.utils.rotation.RotationManager;
import dev.perplexity.client.utils.rotation.RotationRaycast;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-runs the crosshair pick along the managed rotation so block/entity interactions use the same
 * ray the server will see. In 1.21.11 the picking logic lives in {@code GameRenderer.pick} rather
 * than {@code Minecraft.pick}, but it still writes Minecraft's public hit fields.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererPickRotationMixin {
    @Inject(method = "pick(F)V", at = @At("TAIL"))
    private void perplexity$pickWithManagedRotation(float partialTicks, CallbackInfo callbackInfo) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) {
            return;
        }

        Rotation rotation = RotationManager.packetRotation().orElse(null);
        if (rotation == null) {
            return;
        }

        HitResult result = RotationRaycast.trace(
                rotation,
                minecraft.player.blockInteractionRange(),
                minecraft.player.entityInteractionRange(),
                ClipContext.Fluid.NONE,
                entity -> !entity.isSpectator() && entity.isPickable());
        if (result == null) {
            return;
        }

        minecraft.hitResult = result;
        minecraft.crosshairPickEntity = result instanceof EntityHitResult entityHit
                ? entityHit.getEntity()
                : null;
    }
}
