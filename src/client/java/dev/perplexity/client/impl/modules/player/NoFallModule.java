package dev.perplexity.client.impl.modules.player;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/**
 * Prevents fall damage. The client's accumulated fall distance is zeroed every tick,
 * which on its own stops vanilla (client-authoritative) fall damage; the optional
 * ground spoof additionally tells the server we are on ground while falling fast,
 * covering servers that compute the damage themselves.
 */
public final class NoFallModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    /** Fall distance above which the spoof packet starts being sent. */
    private static final double SPOOF_FALL_THRESHOLD = 2.5;
    /** Downward speed below which falling is fast enough to bother spoofing. */
    private static final double SPOOF_SPEED_THRESHOLD = -0.1;

    private final BooleanSetting spoofGround = add(new BooleanSetting("Spoof Ground",
            true));

    public NoFallModule() {
        super("No Fall", "Prevents fall damage", Category.PLAYER);
    }

    @Override
    protected void onTick() {
        if (mc.player == null || mc.level == null) {
            return;
        }
        double fallen = mc.player.fallDistance;
        mc.player.fallDistance = 0.0;

        if (spoofGround.get() && mc.getConnection() != null
                && !mc.player.onGround()
                && fallen > SPOOF_FALL_THRESHOLD
                && mc.player.getDeltaMovement().y < SPOOF_SPEED_THRESHOLD) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(true, false));
        }
    }
}
