package dev.perplexity.client.impl.modules.movement;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.NumberSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Vanilla-style flight. Drives the player's velocity directly every tick so it
 * works in survival without touching abilities packets.
 */
public final class FlightModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    private final NumberSetting speed = add(new NumberSetting("Speed",
            0.8, 0.1, 3.0, 0.1));
    private final BooleanSetting noGravity = add(new BooleanSetting("No Gravity",
            true));
    private final BooleanSetting antiKick = add(new BooleanSetting("Anti Kick",
            false));

    private int ticks;

    public FlightModule() {
        super("Flight", "Fly in survival", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        ticks = 0;
        if (mc.player != null && noGravity.get()) {
            mc.player.setNoGravity(true);
        }
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) {
            mc.player.setNoGravity(false);
            Vec3 velocity = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(velocity.x, Math.min(velocity.y, 0.0), velocity.z);
        }
    }

    @Override
    protected void onTick() {
        if (mc.player == null || mc.level == null) {
            return;
        }
        ticks++;
        mc.player.setNoGravity(noGravity.get());

        double forward = 0.0;
        if (mc.options.keyUp.isDown()) {
            forward += 1.0;
        }
        if (mc.options.keyDown.isDown()) {
            forward -= 1.0;
        }
        double strafe = 0.0;
        if (mc.options.keyRight.isDown()) {
            strafe += 1.0;
        }
        if (mc.options.keyLeft.isDown()) {
            strafe -= 1.0;
        }
        double vertical = 0.0;
        if (mc.options.keyJump.isDown()) {
            vertical += 1.0;
        }
        if (mc.options.keyShift.isDown()) {
            vertical -= 1.0;
        }

        if (forward != 0.0 && strafe != 0.0) {
            forward *= 0.7071;
            strafe *= 0.7071;
        }

        double speedValue = speed.get();
        double yawRad = Math.toRadians(mc.player.getYRot());
        double motionX = (forward * -Math.sin(yawRad) + strafe * -Math.cos(yawRad)) * speedValue;
        double motionZ = (forward * Math.cos(yawRad) + strafe * -Math.sin(yawRad)) * speedValue;
        double motionY = vertical * speedValue;

        if (antiKick.get() && vertical == 0.0 && ticks % 30 == 0) {
            motionY = -0.04;
        }

        mc.player.setDeltaMovement(motionX, motionY, motionZ);
        mc.player.fallDistance = 0.0F;
    }
}
