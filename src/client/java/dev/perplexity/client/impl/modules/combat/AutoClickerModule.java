package dev.perplexity.client.impl.modules.combat;

import dev.perplexity.client.mixin.combat.MinecraftAttackInvoker;
import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.DropdownSetting;
import dev.perplexity.client.module.setting.RangeSetting;

import net.minecraft.client.Minecraft;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Clicks for you on a humanized schedule: every click re-rolls the rate inside CPS, so
 * consecutive intervals never match exactly. Left goes through the real attack path, right
 * through the real use path.
 *
 * <p>Grim mode clicks at the start of the tick instead of the end. Grim's Post check flags
 * action packets that arrive after the tick's movement packet, which is exactly where
 * end-of-tick clicks land; pre-tick clicks arrive before movement, the way vanilla's own
 * clicks do. Pre-tick also keeps swings clear of Karhu's Scaffold (B) swing window.</p>
 *
 * <p>Karhu mode is Grim timing plus irregular intervals: gaussian jitter, rare outlier
 * pauses and a hard floor keep Karhu's AutoClicker (D) statistics (deviation, kurtosis,
 * outlier count) out of flag range, where uniform re-rolls would look mechanical after a
 * couple thousand swings.</p>
 */
public final class AutoClickerModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();
    private static AutoClickerModule instance;

    private final DropdownSetting mode = add(new DropdownSetting("Mode", "Normal",
            "Normal", "Grim", "Karhu"));
    private final RangeSetting cps = add(new RangeSetting("CPS",
            8.0, 12.0, 1.0, 20.0, 0.5));
    private final BooleanSetting leftClick = add(new BooleanSetting("Left Click",
            true));
    private final BooleanSetting rightClick = add(new BooleanSetting("Right Click",
            false));

    private int ticksSinceClick;

    public AutoClickerModule() {
        super("Auto Clicker", "Clicks automatically", Category.COMBAT);
        instance = this;
    }

    @Override
    protected void onEnable() {
        ticksSinceClick = Integer.MAX_VALUE / 2;
    }

    @Override
    protected void onTick() {
        if (!mode.is("Normal")) {
            return;
        }
        doClicks(false);
    }

    /** Pre-tick entry point, called at the start of the tick for Grim and Karhu modes. */
    public static void preTick() {
        if (instance == null || !instance.isEnabled()) {
            return;
        }
        if (instance.mode.is("Karhu")) {
            instance.doClicks(true);
        } else if (instance.mode.is("Grim")) {
            instance.doClicks(false);
        }
    }

    private void doClicks(boolean humanized) {
        if (!canRun()) {
            return;
        }
        if (!leftClick.get() && !rightClick.get()) {
            return;
        }
        double intervalTicks;
        if (humanized) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            double base = 20.0 / Math.max(0.5, cps.random());
            double jittered = base + random.nextGaussian() * 0.9;
            if (random.nextDouble() < 0.015) {
                jittered += 3 + random.nextInt(3);
            }
            intervalTicks = Math.min(9.0, Math.max(1.0, jittered));
        } else {
            double rate = cps.random();
            if (rate <= 0.0) {
                return;
            }
            intervalTicks = Math.max(1.0, 20.0 / rate);
        }
        if (++ticksSinceClick < Math.round((float) intervalTicks)) {
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
