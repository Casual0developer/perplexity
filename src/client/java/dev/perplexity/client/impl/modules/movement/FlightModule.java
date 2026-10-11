package dev.perplexity.client.impl.modules.movement;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.DropdownSetting;
import dev.perplexity.client.module.setting.NumberSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.phys.Vec3;

/**
 * Flight with three drive modes.
 *
 * <ul>
 *   <li>Vanilla: full motion control, velocity is hard-set from input every tick.</li>
 *   <li>Creative: spoofs the flying ability and lets vanilla's own creative flight move the
 *   player. Untouched when genuinely in creative, so nothing fights the real ability.</li>
 *   <li>Velocity: momentum-preserving. Input steers, otherwise the current velocity decays
 *   instead of snapping to zero.</li>
 *   <li>Packet: vanilla-style motion client-side, plus a spoofed move packet every tick that
 *   reports the post-motion position as on ground, so the server never sees air time.</li>
 * </ul>
 */
public final class FlightModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    private static final double DIAGONAL = 0.7071;
    /** Fraction of velocity kept per tick with no input in Velocity mode. */
    private static final double MOMENTUM = 0.94;

    private final DropdownSetting mode = add(new DropdownSetting("Mode", "Vanilla",
            "Vanilla", "Creative", "Velocity", "Packet"));
    private final NumberSetting speed = add(new NumberSetting("Speed",
            0.8, 0.1, 3.0, 0.1));
    private final BooleanSetting noGravity = add(new BooleanSetting("No Gravity",
            true));
    private final BooleanSetting antiKick = add(new BooleanSetting("Anti Kick",
            false));

    private int ticks;
    private boolean spoofedAbilities;

    public FlightModule() {
        super("Flight", "Fly in survival", Category.MOVEMENT);
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
        mc.player.setNoGravity(noGravity.get());
        if (mode.is("Velocity")) {
            tickVelocity();
        } else if (mode.is("Packet")) {
            tickPacket();
        } else {
            tickVanilla();
        }
    }

    private void tickVanilla() {
        double[] input = readInput();
        double forward = input[0];
        double strafe = input[1];
        double vertical = input[2];

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

    /**
     * Vanilla-style motion on the client, plus a spoofed move packet every tick reporting the
     * post-motion position as on ground. Vanilla's own packet goes out during the player tick;
     * this runs after it (end of tick), so the server's last word on our position is grounded
     * and it never accumulates the air time its flight checks look for.
     */
    private void tickPacket() {
        double[] input = readInput();
        double forward = input[0];
        double strafe = input[1];
        double vertical = input[2];

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

        if (mc.getConnection() != null) {
            Vec3 pos = mc.player.position();
            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                    pos.x + motionX, pos.y + motionY, pos.z + motionZ,
                    mc.player.getYRot(), mc.player.getXRot(), true, false));
        }
    }

    private void tickVelocity() {
        double[] input = readInput();
        double forward = input[0];
        double strafe = input[1];
        double vertical = input[2];

        Vec3 current = mc.player.getDeltaMovement();
        double motionX;
        double motionZ;
        if (forward != 0.0 || strafe != 0.0) {
            double speedValue = speed.get();
            double yawRad = Math.toRadians(mc.player.getYRot());
            motionX = (forward * -Math.sin(yawRad) + strafe * -Math.cos(yawRad)) * speedValue;
            motionZ = (forward * Math.cos(yawRad) + strafe * -Math.sin(yawRad)) * speedValue;
        } else {
            motionX = current.x * MOMENTUM;
            motionZ = current.z * MOMENTUM;
        }

        double motionY;
        if (vertical != 0.0) {
            motionY = vertical * speed.get();
        } else {
            motionY = current.y * MOMENTUM;
        }
        if (antiKick.get() && vertical == 0.0 && ticks % 30 == 0) {
            motionY = -0.04;
        }

        mc.player.setDeltaMovement(motionX, motionY, motionZ);
        mc.player.fallDistance = 0.0;
    }

    /**
     * Movement input as {@code [forward, strafe, vertical]}, each in {@code [-1, 1]}.
     * Diagonals are normalized so corner movement is not faster than straight movement.
     */
    private static double[] readInput() {
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
            forward *= DIAGONAL;
            strafe *= DIAGONAL;
        }
        return new double[] { forward, strafe, vertical };
    }
}
