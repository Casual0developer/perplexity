package dev.perplexity.client.impl.modules.combat;

import dev.perplexity.client.mixin.combat.MinecraftAttackInvoker;
import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.RangeSetting;

import net.minecraft.client.Minecraft;

/**
 * Clicks for you on a humanized schedule: every click re-rolls the rate inside CPS, so
 * consecutive intervals never match exactly. Left goes through the real attack path, right
 * through the real use path.
 */
public final class AutoClickerModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    private final RangeSetting cps = add(new RangeSetting("CPS",
            8.0, 12.0, 1.0, 20.0, 0.5));
    private final BooleanSetting leftClick = add(new BooleanSetting("Left Click",
            true));
    private final BooleanSetting rightClick = add(new BooleanSetting("Right Click",
            false));

    private int ticksSinceClick;

    public AutoClickerModule() {
        super("Auto Clicker", "Clicks automatically", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        ticksSinceClick = Integer.MAX_VALUE / 2;
    }

    @Override
    protected void onTick() {
        if (!canRun()) {
            return;
        }
        if (!leftClick.get() && !rightClick.get()) {
            return;
        }
        double rate = cps.random();
        if (rate <= 0.0) {
            return;
        }
        if (++ticksSinceClick < Math.max(1, Math.round(20.0F / (float) rate))) {
            return;
        }
        ticksSinceClick = 0;
        MinecraftAttackInvoker invoker = (MinecraftAttackInvoker) mc;
        if (leftClick.get()) {
            invoker.perplexity$invokeStartAttack();
        }
        if (rightClick.get()) {
            invoker.perplexity$invokeStartUseItem();
        }
    }

    private static boolean canRun() {
        return mc.player != null && mc.level != null && mc.gameMode != null
                && mc.screen == null && mc.isWindowActive()
                && !mc.player.isDeadOrDying() && !mc.player.isSpectator();
    }
}
