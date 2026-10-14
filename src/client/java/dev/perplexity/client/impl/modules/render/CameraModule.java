package dev.perplexity.client.impl.modules.render;
import dev.perplexity.client.utils.Obf;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;

/**
 * Disables the camera tilt on hurt (and the death tilt, which runs through the same hook).
 * Damage itself is untouched.
 */
public final class CameraModule extends Module {
    private static CameraModule instance;

    public CameraModule() {
        super(Category.RENDER);
        instance = this;
    }

    public static boolean enabled() {
        return instance != null && instance.isEnabled();
    }
    @Override
    public String getDisplayName() {
        return Obf.s("EFVXWThwHxlPNEI=");
    }

}
