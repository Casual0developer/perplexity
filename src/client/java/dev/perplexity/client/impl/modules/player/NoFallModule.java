package dev.perplexity.client.impl.modules.player;

import dev.perplexity.client.gui.Toasts;
import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.DropdownSetting;
import dev.perplexity.client.utils.rotation.MovementCorrection;
import dev.perplexity.client.utils.rotation.Rotation;
import dev.perplexity.client.utils.rotation.RotationManager;
import dev.perplexity.client.utils.rotation.RotationTarget;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

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
 *   <li>Grim: MLG water placement, no spoofed flags at all. Grim's GroundSpoof check compares
 *   the claimed flag against its own physics simulation, so any mid-air grounded claim
 *   mismatches no matter the packet type, and its Timer check counts movement packets, so
 *   the old double-packet spoof flagged there too. Landing in placed water is legit
 *   mechanics it accepts. Needs a water bucket in the hotbar and does nothing in the
 *   Nether. There is deliberately no NCP mode: NCP rebuilds fall distance purely from Y
 *   positions, so spoofing changes nothing and constant spoofing even deals damage
 *   mid-fall.</li>
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
    /** MLG arms once the ground is this close. */
    private static final double MLG_ARM_DISTANCE = 6.0;
    /** MLG only fires for falls that could actually hurt. */
    private static final double MLG_MIN_FALL = 3.0;
    /** MLG only fires when falling decisively, not drifting down. */
    private static final double MLG_MIN_SPEED = -0.5;

    private final BooleanSetting spoofGround = add(new BooleanSetting("Spoof Ground",
            true));
    private final DropdownSetting mode = add(new DropdownSetting("Mode", "Vulcan",
            "Normal", "Vulcan", "Grim"));

    private double airFallDistance;
    private int prevSlot = -1;
    private boolean mlgAimed;
    private boolean warnedNoBucket;
    private int aimTick = -1;

    public NoFallModule() {
        super("No Fall", "Prevents fall damage", Category.PLAYER);
    }

    @Override
    protected void onDisable() {
        airFallDistance = 0.0;
        restoreMlg();
    }

    @Override
    protected void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        mc.player.fallDistance = 0.0;

        if (mc.player.onGround()) {
            airFallDistance = 0.0;
            restoreMlg();
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
        } else if (mode.is("Grim")) {
            tickMlg();
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

    /**
     * MLG water clutch for Grim: aim straight down through the silent rotation channel a
     * tick early so the server's own raycast finds ground, then use the bucket. The slot
     * is restored on landing. Sends no movement packets, which is what Grim's Timer counts.
     */
    private void tickMlg() {
        if (mc.player.isInWater() || mc.player.isInLava()
                || mc.level.dimension() == Level.NETHER
                || airFallDistance <= MLG_MIN_FALL
                || mc.player.getDeltaMovement().y >= MLG_MIN_SPEED) {
            return;
        }
        double distance = distanceToGround();
        if (distance == Double.MAX_VALUE || distance > MLG_ARM_DISTANCE) {
            return;
        }
        int slot = findWaterBucket();
        if (slot == -1) {
            if (!warnedNoBucket) {
                warnedNoBucket = true;
                Toasts.push("No Bucket", false);
            }
            return;
        }
        if (!mlgAimed) {
            prevSlot = mc.player.getInventory().getSelectedSlot();
            mlgAimed = true;
            aimTick = mc.player.tickCount;
        }
        mc.player.getInventory().setSelectedSlot(slot);
        RotationManager.request(this, RotationTarget.to(
                new Rotation(mc.player.getYRot(), 90.0F))
                .movementCorrection(MovementCorrection.OFF)
                .ticksUntilReset(10)
                .build());
        if (mc.player.tickCount <= aimTick) {
            return;
        }
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void restoreMlg() {
        mlgAimed = false;
        warnedNoBucket = false;
        aimTick = -1;
        RotationManager.clear(this);
        if (prevSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(prevSlot);
            prevSlot = -1;
        }
    }

    private static int findWaterBucket() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.WATER_BUCKET)) {
                return i;
            }
        }
        return -1;
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
