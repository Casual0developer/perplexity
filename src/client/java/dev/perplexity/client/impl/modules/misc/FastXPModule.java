package dev.perplexity.client.impl.modules.misc;

import dev.perplexity.client.mixin.misc.MinecraftAccessor;
import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.NumberSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/**
 * Throw-rate control for XP bottles. Nothing is automatic: you hold a bottle and hold use,
 * and throws go out at exactly Throws Per Second. Vanilla's own repeat-use is held off while
 * throwing so the configured rate is the actual rate, and released when you stop.
 */
public final class FastXPModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    private final NumberSetting throwsPerSecond = add(new NumberSetting("Throws Per Second",
            8.0, 1.0, 20.0, 1.0));

    private int ticksSinceThrow;
    private boolean suppressing;

    public FastXPModule() {
        super("Fast XP", "Throw bottles faster while holding use", Category.MISC);
    }

    @Override
    protected void onDisable() {
        releaseSuppression();
    }

    @Override
    protected void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            releaseSuppression();
            return;
        }
        if (mc.screen != null
                || !mc.player.getMainHandItem().is(Items.EXPERIENCE_BOTTLE)
                || !mc.options.keyUse.isDown()) {
            releaseSuppression();
            return;
        }
        int interval = Math.max(1, Math.round(20.0F / (float) throwsPerSecond.get()));
        if (++ticksSinceThrow < interval) {
            return;
        }
        ticksSinceThrow = 0;
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        mc.player.swing(InteractionHand.MAIN_HAND);
        ((MinecraftAccessor) mc).perplexity$setRightClickDelay(interval + 2);
        suppressing = true;
    }

    /** Lets vanilla's repeat-use run again after we held it off. */
    private void releaseSuppression() {
        if (suppressing) {
            suppressing = false;
            if (mc != null) {
                ((MinecraftAccessor) mc).perplexity$setRightClickDelay(0);
            }
        }
    }
}
