package dev.perplexity.client.impl.modules.combat;
import dev.perplexity.client.utils.Obf;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.DropdownSetting;
import dev.perplexity.client.module.setting.NumberSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Scales down server-sent knockback for the local player. Horizontal and Vertical control how
 * much of each component is taken (0 = none, 100 = full), while Chance rolls per hit whether
 * the reduction applies at all — missed rolls take full vanilla knockback.
 *
 * <p>Karhu mode clamps to what Karhu's checks tolerate instead of the sliders: Velocity (A)
 * demands vertical within ~0.01% of full, so vertical is forced to 100%; Velocity (B) flags
 * sustained horizontal below ~80% for modern clients, so horizontal never drops under 85%.
 * Anything stronger flags within a hit or two — this is the check's own math, not caution.</p>
 */
public final class KnockbackModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();
    private static KnockbackModule instance;

    private final NumberSetting horizontal = add(new NumberSetting("Horizontal",
            0.0, 0.0, 100.0, 1.0));
    private final NumberSetting vertical = add(new NumberSetting("Vertical",
            0.0, 0.0, 100.0, 1.0));
    private final NumberSetting chance = add(new NumberSetting("Chance",
            100.0, 0.0, 100.0, 1.0));
    private final DropdownSetting mode = add(new DropdownSetting("Mode", "Normal",
            "Normal", "Karhu"));

    public KnockbackModule() {
        super(Category.COMBAT);
        instance = this;
    }

    /**
     * Applies a scaled version of an incoming velocity packet. Returns true when the packet
     * was handled here and vanilla must skip it.
     */
    public static boolean handle(ClientboundSetEntityMotionPacket packet) {
        if (instance == null || !instance.isEnabled()) {
            return false;
        }
        if (mc.player == null || mc.level == null || packet.getId() != mc.player.getId()) {
            return false;
        }
        if (ThreadLocalRandom.current().nextDouble() * 100.0 >= instance.chance.get()) {
            return false;
        }
        Vec3 movement = packet.getMovement();
        if (instance.mode.is("Karhu")) {
            double horizontalScale = Math.max(instance.horizontal.get(), 85.0) / 100.0;
            mc.player.lerpMotion(new Vec3(
                    movement.x * horizontalScale,
                    movement.y,
                    movement.z * horizontalScale));
            return true;
        }
        double horizontalScale = instance.horizontal.get() / 100.0;
        double verticalScale = instance.vertical.get() / 100.0;
        mc.player.lerpMotion(new Vec3(
                movement.x * horizontalScale,
                movement.y * verticalScale,
                movement.z * horizontalScale));
        return true;
    }
    @Override
    public String getDisplayName() {
        return Obf.s("H1QDeG1JBVZvPk0QeRY=");
    }

}
