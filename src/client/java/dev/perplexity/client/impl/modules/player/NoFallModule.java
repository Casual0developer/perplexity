package dev.perplexity.client.impl.modules.player;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;

/**
 * Prevents fall damage. The client's accumulated fall distance is zeroed every tick,
 * which on its own stops vanilla (client-authoritative) fall damage; the optional
 * ground spoof additionally tells the server we are on ground while falling fast,
 * covering servers that compute the damage themselves.
 */
public final class NoFallModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    /** Downward speed that counts as actually falling rather than stepping down a block. */
    private static final double SPOOF_SPEED_THRESHOLD = -0.1;
    /**
     * Descent in blocks before spoofing is even considered. Jump arcs total ~1.25, so
     * ordinary jumping never spoofs.
     */
    private static final double SPOOF_DISTANCE_THRESHOLD = 2.5;
    /** Start spoofing once the ground is this close: the server resets its fall tracker on
     * the first spoof, so one or two grounded packets before impact is all it takes. */
    private static final double NEAR_GROUND_DISTANCE = 6.0;
    /** How far down the ground scan reaches. */
    private static final int SCAN_DEPTH = 16;
    /**
     * Fallback for when the scan finds no ground (void, unstreamed chunks): long falls spoof
     * regardless, since the alternative is certain damage.
     */
    private static final double LONG_FALL_DISTANCE = 12.0;

    private final BooleanSetting spoofGround = add(new BooleanSetting("Spoof Ground",
            true));

    private double airFallDistance;

    public NoFallModule() {
        super("No Fall", "Prevents fall damage", Category.PLAYER);
    }

    @Override
    protected void onDisable() {
        airFallDistance = 0.0;
    }

    @Override
    protected void onTick() {
        if (mc.player == null || mc.level == null) {
            return;
        }
        mc.player.fallDistance = 0.0;

        if (mc.player.onGround()) {
            airFallDistance = 0.0;
        } else if (mc.player.getDeltaMovement().y < 0.0) {
            airFallDistance -= mc.player.getDeltaMovement().y;
        }

        // Spoofing mid-air all the way down reads as teleport/clip weirdness to vertical
        // checks, so packets only go out in the landing zone (or on blind long falls).
        if (!spoofGround.get() || mc.getConnection() == null
                || mc.player.getVehicle() != null
                || mc.player.onGround()
                || mc.player.isFallFlying()
                || airFallDistance <= SPOOF_DISTANCE_THRESHOLD
                || mc.player.getDeltaMovement().y >= SPOOF_SPEED_THRESHOLD
                || (!nearGround() && airFallDistance <= LONG_FALL_DISTANCE)) {
            return;
        }
        mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(true, false));
    }

    /** True when solid ground is within landing range below the feet. */
    private boolean nearGround() {
        int feetX = Mth.floor(mc.player.getX());
        int feetY = Mth.floor(mc.player.getY());
        int feetZ = Mth.floor(mc.player.getZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i <= SCAN_DEPTH; i++) {
            pos.set(feetX, feetY - i, feetZ);
            if (!mc.level.getBlockState(pos).isAir()) {
                return mc.player.getY() - (feetY - i + 1) <= NEAR_GROUND_DISTANCE;
            }
        }
        return false;
    }
}
