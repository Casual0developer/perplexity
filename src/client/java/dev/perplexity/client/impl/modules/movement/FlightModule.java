package dev.perplexity.client.impl.modules.movement;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.DropdownSetting;
import dev.perplexity.client.module.setting.NumberSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.phys.Vec3;

/**
 * Flight with three drives:
 *
 * <ul>
 *   <li>Normal: full motion control, velocity hard-set from input every tick. For vanilla
 *   servers and no-anticheat play; any motion check will see superhuman velocity.</li>
 *   <li>Creative: spoofs the flying ability and lets vanilla's own creative flight move the
 *   player. Untouched when genuinely in creative, so nothing fights the real ability.</li>
 *   <li>Karhu: Karhu's Fly (A) handler is empty and its Fly (F) check only polices downward
 *   gravity, so this ascends (never evaluated), holds an exact-zero hover (reads as
 *   stationary) and hands vertical control back to gravity on sneak (a natural fall matches
 *   prediction exactly). Horizontal drift stays under every speed gate and nothing extra
 *   is sent. Constant descents and hover dips flag within ticks, so there are none.</li>
 * </ul>
 */
public final class FlightModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    private static final double DIAGONAL = 0.7071;
    /** Horizontal drift cap in Karhu mode: under every speed gate. */
    private static final double MAX_DRIFT = 0.08;

    private final DropdownSetting mode = add(new DropdownSetting("Mode", "Karhu",
            "Normal", "Creative", "Karhu"));
    private final NumberSetting speed = add(new NumberSetting("Speed",
            0.8, 0.1, 3.0, 0.1));
    private final NumberSetting climb = add(new NumberSetting("Climb",
            0.35, 0.1, 1.0, 0.05));
    private final BooleanSetting noGravity = add(new BooleanSetting("No Gravity",
            true));
    private final BooleanSetting antiKick = add(new BooleanSetting("Anti Kick",
            false));

    private int ticks;
    private boolean spoofedAbilities;

    public FlightModule() {
        super("Flight", "Fly your way", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        ticks = 0;
        spoofedAbilities = false;
        if (mc.player != null && noGravity.get()) {
            mc.player.setNoGravity(true);
        }
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) {
            mc.player.setNoGravity(false);
            if (spoofedAbilities && !mc.player.hasInfiniteMaterials()) {
                mc.player.getAbilities().flying = false;
            }
            Vec3 velocity = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(velocity.x, Math.min(velocity.y, 0.0), velocity.z);
        }
        spoofedAbilities = false;
    }

    @Override
    protected void onTick() {
        if (mc.player == null || mc.level == null) {
            return;
        }
        ticks++;
        if (mode.is("Creative")) {
            tickCreative();
            return;
        }
        if (mode.is("Karhu")) {
            tickKarhu();
            return;
        }
        tickNormal();
    }

    private void tickNormal() {
        mc.player.setNoGravity(noGravity.get());

        double forward = inputForward();
        double strafe = inputStrafe();
        double vertical = inputVertical();
        if (forward != 0.0 && strafe != 0.0) {
            forward *= DIAGONAL;
            strafe *= DIAGONAL;
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
        mc.player.fallDistance = 0.0;
    }

    private void tickCreative() {
        if (mc.player.hasInfiniteMaterials()) {
            spoofedAbilities = false;
            return;
        }
        Abilities abilities = mc.player.getAbilities();
        abilities.flying = true;
        abilities.setFlyingSpeed((float) (speed.get() * 0.05));
        spoofedAbilities = true;
        mc.player.fallDistance = 0.0;
    }

    private void tickKarhu() {
        double forward = inputForward();
        double strafe = inputStrafe();
        double vertical = inputVertical();

        double length = Math.hypot(forward, strafe);
        double motionX = 0.0;
        double motionZ = 0.0;
        if (length > 0.0) {
            double yawRad = Math.toRadians(mc.player.getYRot());
            motionX = (forward / length * -Math.sin(yawRad)
                    + strafe / length * -Math.cos(yawRad)) * MAX_DRIFT;
            motionZ = (forward / length * Math.cos(yawRad)
                    + strafe / length * -Math.sin(yawRad)) * MAX_DRIFT;
        }

        if (vertical > 0.0) {
            mc.player.setNoGravity(noGravity.get());
            mc.player.setDeltaMovement(motionX, vertical * climb.get(), motionZ);
        } else if (vertical == 0.0) {
            mc.player.setNoGravity(noGravity.get());
            mc.player.setDeltaMovement(motionX, 0.0, motionZ);
        } else {
            // Sneak hands vertical control back to gravity: a natural fall matches
            // prediction exactly, while any driven descent flags within ticks.
            mc.player.setNoGravity(false);
            Vec3 velocity = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(motionX, velocity.y, motionZ);
        }
        mc.player.fallDistance = 0.0;
    }

    private static double inputForward() {
        double forward = 0.0;
        if (mc.options.keyUp.isDown()) {
            forward += 1.0;
        }
        if (mc.options.keyDown.isDown()) {
            forward -= 1.0;
        }
        return forward;
    }

    private static double inputStrafe() {
        double strafe = 0.0;
        if (mc.options.keyRight.isDown()) {
            strafe += 1.0;
        }
        if (mc.options.keyLeft.isDown()) {
            strafe -= 1.0;
        }
        return strafe;
    }

    private static double inputVertical() {
        double vertical = 0.0;
        if (mc.options.keyJump.isDown()) {
            vertical += 1.0;
        }
        if (mc.options.keyShift.isDown()) {
            vertical -= 1.0;
        }
        return vertical;
    }
}
