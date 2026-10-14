package dev.perplexity.client.impl.modules.ui;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.ModuleManager;
import dev.perplexity.client.module.setting.KeybindSetting;

import org.lwjgl.glfw.GLFW;

/**
 * Master kill-switch. Pressing its key enables this module, which switches everything else
 * off and locks the menu opener until pressed again. Disarming leaves modules off for you
 * to re-enable by hand; the opener works again immediately.
 */
public final class PanicModule extends Module {
    private static PanicModule instance;

    private final KeybindSetting key = add(new KeybindSetting("Key", GLFW.GLFW_KEY_DELETE));

    public PanicModule() {
        super(Category.UI);
        instance = this;
    }

    @Override
    public String getDisplayName() {
        return "Panic";
    }

    public static int panicKey() {
        return instance == null ? GLFW.GLFW_KEY_DELETE : instance.key.get();
    }

    public static boolean panicked() {
        return instance != null && instance.isEnabled();
    }

    public static void togglePanic() {
        if (instance != null) {
            instance.toggle();
        }
    }

    @Override
    protected void onEnable() {
        for (Module module : ModuleManager.get().getModules()) {
            if (module != this && module.isEnabled()) {
                module.setEnabled(false);
            }
        }
    }
}
