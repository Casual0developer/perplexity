package dev.perplexity.client.mixin.rotation;

import dev.perplexity.client.utils.rotation.RotationManager;
import dev.perplexity.client.utils.rotation.UseItemPacketRotations;

import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stamps the managed rotation onto item-use packets. */
@Mixin(ServerboundUseItemPacket.class)
public class ServerboundUseItemPacketRotationMixin {
    @Mutable
    @Shadow
    @Final
    private float yRot;

    @Mutable
    @Shadow
    @Final
    private float xRot;

    @Inject(method = "<init>(Lnet/minecraft/world/InteractionHand;IFF)V", at = @At("RETURN"))
    private void perplexity$applyManagedRotation(InteractionHand hand, int sequence, float yaw, float pitch,
                                                 CallbackInfo callbackInfo) {
        if (!UseItemPacketRotations.shouldOverride()) {
            return;
        }

        // Also aligns ordinary use after silent aim ends: the camera yaw can still differ from the
        // movement yaw by one or more complete turns.
        yRot = RotationManager.packetYaw(yaw);
        xRot = RotationManager.packetPitch(pitch);
    }
}
