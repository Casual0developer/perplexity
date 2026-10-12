package dev.perplexity.client.module.setting;

import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * A keyboard key, captured by clicking its row and pressing the new key. Escape cancels the
 * capture instead of binding. Only one setting listens at a time, tracked statically so rows
 * in different panels cannot fight over the next keypress.
 */
public final class KeybindSetting extends Setting {
    private static KeybindSetting listening;

    private int keyCode;

    public KeybindSetting(String name, int defaultCode) {
        super(name);
        this.keyCode = defaultCode;
    }

    public int get() {
        return keyCode;
    }

    public void set(int keyCode) {
        this.keyCode = keyCode;
    }

    public boolean isListening() {
        return listening == this;
    }

    public static void listen(KeybindSetting setting) {
        listening = setting;
    }

    /**
     * Offers a keypress to the listening setting. Returns true when something captured it.
     */
    public static boolean capture(int keyCode) {
        if (listening == null) {
            return false;
        }
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
            listening.keyCode = keyCode;
        }
        listening = null;
        return true;
    }

    public String displayName() {
        if (isListening()) {
            return "...";
        }
        return InputConstants.Type.KEYSYM.getOrCreate(keyCode)
                .getDisplayName().getString().toUpperCase(Locale.ROOT);
    }
}
