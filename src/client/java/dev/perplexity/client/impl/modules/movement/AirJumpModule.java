package dev.perplexity.client.impl.modules.movement;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.utils.Obf;

import net.minecraft.client.Minecraft;

/** Lets each new jump-key press start another normal jump while the player is airborne. */
public final class AirJumpModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    private boolean jumpDown;

    public AirJumpModule() {
        super(Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        jumpDown = mc.options.keyJump.isDown();
    }

    @Override
    protected void onDisable() {
        jumpDown = false;
    }

    @Override
    protected void onTick() {
        if (mc.player == null || mc.level == null) {
            jumpDown = false;
            return;
        }

        boolean pressed = mc.options.keyJump.isDown();
        if (pressed && !jumpDown && !mc.player.onGround()
                && !mc.player.isPassenger() && !mc.player.getAbilities().flying) {
            mc.player.jumpFromGround();
            mc.player.fallDistance = 0.0;
        }
        jumpDown = pressed;
    }

    @Override
    public String getDisplayName() {
        return Obf.s("H1MFMQd3Bkk=");
    }
}
