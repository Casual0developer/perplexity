package dev.perplexity.client.impl.modules.render;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;

/**
 * Disables the camera tilt on hurt (and the death tilt, which runs through the same hook).
 * Damage itself is untouched.
 */
public final class NoHurtCamModule extends Module {
    private static NoHurtCamModule instance;

    public NoHurtCamModule() {
        super("No Hurt Cam", "No camera tilt on damage", Category.RENDER);
        instance = this;
    }

    public static boolean enabled() {
        return instance != null && instance.isEnabled();
    }
}
