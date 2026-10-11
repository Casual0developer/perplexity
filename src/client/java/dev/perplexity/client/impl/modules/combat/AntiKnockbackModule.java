package dev.perplexity.client.impl.modules.combat;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.NumberSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Scales down server-sent knockback for the local player. Horizontal and Vertical control how
 * much of each component is taken (0 = none, 100 = full), while Chance rolls per hit whether
 * the reduction applies at all — missed rolls take full vanilla knockback.
 */
public final class AntiKnockbackModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();
    private static AntiKnockbackModule instance;

    private final NumberSetting horizontal = add(new NumberSetting("Horizontal",
            0.0, 0.0, 100.0, 1.0));
    private final NumberSetting vertical = add(new NumberSetting("Vertical",
            0.0, 0.0, 100.0, 1.0));
    private final NumberSetting chance = add(new NumberSetting("Chance",
            100.0, 0.0, 100.0, 1.0));

    public AntiKnockbackModule() {
        super("Anti Knockback", "Reduces incoming knockback", Category.COMBAT);
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
        double horizontalScale = instance.horizontal.get() / 100.0;
        double verticalScale = instance.vertical.get() / 100.0;
        Vec3 movement = packet.getMovement();
        mc.player.lerpMotion(new Vec3(
                movement.x * horizontalScale,
                movement.y * verticalScale,
                movement.z * horizontalScale));
        return true;
    }
}
