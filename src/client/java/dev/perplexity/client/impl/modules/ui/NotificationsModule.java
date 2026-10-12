package dev.perplexity.client.impl.modules.ui;

import dev.perplexity.client.gui.Toasts;
import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;

/**
 * Master switch for toggle toasts. Turning it off clears whatever is on screen.
 */
public final class NotificationsModule extends Module {
    private static NotificationsModule instance;

    public NotificationsModule() {
        super("Notifications", "Toast on toggle", Category.UI);
        instance = this;
    }

    public static boolean enabled() {
        return instance != null && instance.isEnabled();
    }

    @Override
    protected void onDisable() {
        Toasts.clear();
    }
}
