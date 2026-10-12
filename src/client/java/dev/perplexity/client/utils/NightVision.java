package dev.perplexity.client.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Full brightness via an invisible, iconless Night Vision effect. Each owner tracks whether it
 * introduced the effect: pre-existing Night Vision is never stripped, and two owners (XRay and
 * Full Bright) converge without talking to each other — whoever is still on re-adds it within
 * a tick of the other letting go.
 */
public final class NightVision {
    private static final Minecraft mc = Minecraft.getInstance();

    private boolean owned;

    public void enable() {
        owned = mc.player != null && !mc.player.hasEffect(MobEffects.NIGHT_VISION);
        apply();
    }

    public void disable() {
        if (owned) {
            owned = false;
            if (mc.player != null) {
                mc.player.removeEffect(MobEffects.NIGHT_VISION);
            }
        }
    }

    public void tick() {
        if (owned && mc.player != null && !mc.player.hasEffect(MobEffects.NIGHT_VISION)) {
            apply();
        }
    }

    private static void apply() {
        if (mc.player != null) {
            mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                    999999, 0, false, false, false));
        }
    }
}
