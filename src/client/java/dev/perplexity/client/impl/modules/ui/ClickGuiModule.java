package dev.perplexity.client.impl.modules.ui;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.KeybindSetting;
import dev.perplexity.client.module.setting.NumberSetting;

import org.lwjgl.glfw.GLFW;

/**
 * Holds the menu opener key. The row itself is the feature; the toggle carries no behaviour.
 */
public final class ClickGuiModule extends Module {
    private static ClickGuiModule instance;

    private final KeybindSetting key = add(new KeybindSetting("Key", GLFW.GLFW_KEY_RIGHT_SHIFT));
    private final NumberSetting blur = add(new NumberSetting("Blur",
            50.0, 0.0, 100.0, 1.0));

    public ClickGuiModule() {
        super("Click GUI", "Menu opener key", Category.UI);
        instance = this;
    }

    public static int openKey() {
        return instance == null ? GLFW.GLFW_KEY_RIGHT_SHIFT : instance.key.get();
    }

    /**
     * Fractional blur passes for this frame. Vanilla exposes exactly one blur strength, so a
     * smooth slider is faked temporally: the fractional part carries across frames and flips
     * an extra pass on and off, averaging out to the slider position. Exact integers (0, 50,
     * 100) hold steady with no flicker; midpoints alternate. Must be called exactly once per
     * frame — every call advances the fraction.
     */
    private static final int MAX_BLUR_PASSES = 2;
    private static double blurFraction;

    public static int blurPasses() {
        if (instance == null) {
            return 1;
        }
        blurFraction += instance.blur.get() / 100.0 * MAX_BLUR_PASSES;
        int passes = (int) blurFraction;
        if (passes > MAX_BLUR_PASSES) {
            passes = MAX_BLUR_PASSES;
        }
        blurFraction -= passes;
        return passes;
    }
}
