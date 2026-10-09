package dev.perplexity.client.mixin.rotation;

import dev.perplexity.client.utils.rotation.RotationManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the manager's idea of the server-side rotation in sync after corrections. */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerRotationMixin {
    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void perplexity$acceptPositionCorrection(ClientboundPlayerPositionPacket packet,
                                                     CallbackInfo callbackInfo) {
        perplexity$acceptAppliedRotation();
    }

    @Inject(method = "handleRotatePlayer", at = @At("TAIL"))
    private void perplexity$acceptRotationCorrection(ClientboundPlayerRotationPacket packet,
                                                     CallbackInfo callbackInfo) {
        perplexity$acceptAppliedRotation();
    }

    private void perplexity$acceptAppliedRotation() {
        // TAIL runs after client-thread dispatch and vanilla's relative-angle resolution.
        var player = Minecraft.getInstance().player;
        if (player != null) {
            RotationManager.acceptServerRotation(player.getYRot(), player.getXRot(), false, false);
        }
    }
}
