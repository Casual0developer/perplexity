package dev.perplexity.client.impl.modules.ui;
import dev.perplexity.client.utils.Obf;

import dev.perplexity.client.gui.Toasts;
import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;

/**
 * Master switch for toggle toasts. Turning it off clears whatever is on screen.
 */
public final class PopupsModule extends Module {
    private static PopupsModule instance;

    public PopupsModule() {
        super(Category.UI);
        instance = this;
    }

    public static boolean enabled() {
        return instance != null && instance.isEnabled();
    }

    @Override
    protected void onDisable() {
        Toasts.clear();
    }
    @Override
    public String getDisplayName() {
        return Obf.s("EFUDeCtrCFh4PEAfaQ==");
    }

}
