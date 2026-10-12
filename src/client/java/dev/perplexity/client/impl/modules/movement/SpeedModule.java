package dev.perplexity.client.impl.modules.movement;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.DropdownSetting;
import dev.perplexity.client.module.setting.NumberSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Strafe speed. Drives horizontal velocity from input every tick while leaving vertical
 * motion (jumping, gravity) to vanilla.
 *
 * <p>Karhu mode is legit-only: Karhu's Speed (A/B/C) checks replay vanilla physics from
 * your inputs, so forced superhuman velocity flags within ticks no matter the shaping.
 * Instead this mode perfects vanilla movement itself — sprint held and a jump exactly on
 * every landing — which is the fastest legal motion and matches prediction exactly.</p>
 */
public final class SpeedModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    private static final double DIAGONAL = 0.7071;

    private final DropdownSetting mode = add(new DropdownSetting("Mode", "Normal",
            "Normal", "Karhu"));
    private final NumberSetting speed = add(new NumberSetting("Speed",
            1.0, 0.1, 5.0, 0.1));
    private final BooleanSetting onlyGround = add(new BooleanSetting("Only Ground",
            false));
    private final BooleanSetting jump = add(new BooleanSetting("Jump",
            true));

    public SpeedModule() {
        super("Speed", "Run faster", Category.MOVEMENT);
    }

    @Override
    protected void onTick() {
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mode.is("Karhu")) {
            tickKarhu();
            return;
        }

        if (onlyGround.get() && !mc.player.onGround()) {
            return;
        }

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
        if (forward == 0.0 && strafe == 0.0) {
            return;
        }
        if (forward != 0.0 && strafe != 0.0) {
            forward *= DIAGONAL;
            strafe *= DIAGONAL;
        }

        double speedValue = speed.get();
        double yawRad = Math.toRadians(mc.player.getYRot());
        double motionX = (forward * -Math.sin(yawRad) + strafe * -Math.cos(yawRad)) * speedValue;
        double motionZ = (forward * Math.cos(yawRad) + strafe * -Math.sin(yawRad)) * speedValue;

        Vec3 velocity = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(motionX, velocity.y, motionZ);
    }

    /** Perfect bunnyhop: sprint held, jump exactly on landing, physics otherwise vanilla. */
    private void tickKarhu() {
        boolean moving = mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
        if (!moving || mc.options.keyShift.isDown()) {
            return;
        }
        if (mc.player.getFoodData().getFoodLevel() <= 6) {
            return;
        }
        mc.player.setSprinting(true);
        if (jump.get() && mc.player.onGround()) {
            mc.player.jumpFromGround();
        }
    }
}
