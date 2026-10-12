package dev.perplexity.client.impl.modules.ui;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.KeybindSetting;

import org.lwjgl.glfw.GLFW;

/**
 * Holds the menu opener key. The row itself is the feature; the toggle carries no behaviour.
 */
public final class ClickGuiModule extends Module {
    private static ClickGuiModule instance;

    private final KeybindSetting key = add(new KeybindSetting("Key", GLFW.GLFW_KEY_RIGHT_SHIFT));
    private final BooleanSetting blur = add(new BooleanSetting("Blur", true));

    public ClickGuiModule() {
        super("Click GUI", "Menu opener key", Category.UI);
        instance = this;
    }

    public static int openKey() {
        return instance == null ? GLFW.GLFW_KEY_RIGHT_SHIFT : instance.key.get();
    }

    public static boolean blurOn() {
        return instance == null || instance.blur.get();
    }
}
