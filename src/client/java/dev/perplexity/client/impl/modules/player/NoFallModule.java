package dev.perplexity.client.impl.modules.player;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.DropdownSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;

/**
 * Prevents fall damage. The client's accumulated fall distance is zeroed every tick, which on
 * its own stops vanilla (client-authoritative) fall damage; the mode picks how the server is
 * convinced:
 *
 * <ul>
 *   <li>Normal: plain ground spoof once a real fall (over 2.5 blocks) is underway. Jumps never
 *   qualify, so jumping with it on stays quiet.</li>
 *   <li>Vulcan: spoofs through the whole fall once underway, like Normal. Minimal last-moment
 *   spoofing kept taking damage (one packet can't survive ping delay), so this keeps the
 *   tracker reset continuously instead.</li>
 * </ul>
 */
public final class NoFallModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    /** Downward speed that counts as actually falling rather than stepping down a block. */
    private static final double SPOOF_SPEED_THRESHOLD = -0.1;
    /** Descent in blocks before any spoofing is considered. Jump arcs total ~1.25. */
    private static final double SPOOF_DISTANCE_THRESHOLD = 2.5;
    /** How far down the ground scan reaches. */
    private static final int SCAN_DEPTH = 16;
    /** Vulcan spoofs once the ground is this close, plus blind long falls as a fallback. */
    private static final double NEAR_GROUND_DISTANCE = 6.0;
    /** Blind long falls spoof regardless, since the alternative is certain damage. */
    private static final double LONG_FALL_DISTANCE = 12.0;

    private final BooleanSetting spoofGround = add(new BooleanSetting("Spoof Ground",
            true));
    private final DropdownSetting mode = add(new DropdownSetting("Mode", "Vulcan",
            "Normal", "Vulcan"));

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
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        mc.player.fallDistance = 0.0;

        if (mc.player.onGround()) {
            airFallDistance = 0.0;
        } else if (mc.player.getDeltaMovement().y < 0.0) {
            airFallDistance -= mc.player.getDeltaMovement().y;
        }

        if (!spoofGround.get() || mc.getConnection() == null
                || mc.player.getVehicle() != null
                || mc.player.onGround()
                || mc.player.isFallFlying()) {
            return;
        }
        if (mode.is("Normal")) {
            tickNormal();
        } else {
            tickVulcan();
        }
    }

    /** Plain ground spoof for real falls; jumps never reach the arming distance. */
    private void tickNormal() {
        if (airFallDistance > SPOOF_DISTANCE_THRESHOLD
                && mc.player.getDeltaMovement().y < SPOOF_SPEED_THRESHOLD) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(true, false));
        }
    }

    /** Sustained spoofing through the fall; continuous resets survive ping delay. */
    private void tickVulcan() {
        double motionY = mc.player.getDeltaMovement().y;
        if (airFallDistance <= SPOOF_DISTANCE_THRESHOLD || motionY >= SPOOF_SPEED_THRESHOLD) {
            return;
        }
        if (!nearGround() && airFallDistance <= LONG_FALL_DISTANCE) {
            return;
        }
        mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(true, false));
    }

    /** True when solid ground is within spoofing range below the feet. */
    private boolean nearGround() {
        return distanceToGround() <= NEAR_GROUND_DISTANCE;
    }

    /** Feet-to-ground distance, or {@link Double#MAX_VALUE} when no ground is in range. */
    private double distanceToGround() {
        int feetX = Mth.floor(mc.player.getX());
        int feetY = Mth.floor(mc.player.getY());
        int feetZ = Mth.floor(mc.player.getZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i <= SCAN_DEPTH; i++) {
            pos.set(feetX, feetY - i, feetZ);
            if (!mc.level.getBlockState(pos).isAir()) {
                return mc.player.getY() - (feetY - i + 1);
            }
        }
        return Double.MAX_VALUE;
    }
}
