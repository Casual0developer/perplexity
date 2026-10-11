package dev.perplexity.client.impl.modules.movement;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;

import net.minecraft.client.Minecraft;

/**
 * Sprints whenever movement input is held, in any direction. Backs off while sneaking or
 * starving so it never fights vanilla's own sprint rules.
 */
public final class AutoSprintModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    public AutoSprintModule() {
        super("Auto Sprint", "Always sprint while moving", Category.MOVEMENT);
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
    }

    @Override
    protected void onTick() {
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mc.options.keyShift.isDown() || mc.player.isSprinting()) {
            return;
        }
        if (mc.player.getFoodData().getFoodLevel() <= 6) {
            return;
        }
        if (mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown()) {
            mc.player.setSprinting(true);
        }
    }
}
