package dev.perplexity.client.utils.rotation;

import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;

public final class UseItemPacketRotations {
    private static final ThreadLocal<Boolean> EXPLICIT_ROTATION = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static boolean shouldOverride() {
        return !EXPLICIT_ROTATION.get();
    }

    public static ServerboundUseItemPacket explicit(InteractionHand hand, int sequence, Rotation rotation) {
        Rotation packetRotation = rotation.nearestTo(RotationManager.serverRotation());
        EXPLICIT_ROTATION.set(Boolean.TRUE);
        try {
            return new ServerboundUseItemPacket(hand, sequence, packetRotation.yaw(), packetRotation.pitch());
        } finally {
            EXPLICIT_ROTATION.set(Boolean.FALSE);
        }
    }

    private UseItemPacketRotations() {
    }
}
