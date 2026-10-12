package dev.perplexity.client.impl.modules.ui;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.ToggleListSetting;

/**
 * Shows or hides whole menu tabs. The UI tab itself can never hide, or the switch to bring
 * anything back would vanish with it. While this module is off, every tab shows.
 */
public final class TabsModule extends Module {
    private static TabsModule instance;

    private final ToggleListSetting visible;

    public TabsModule() {
        super("Tabs", "Show or hide menu tabs", Category.UI);
        Category[] categories = Category.values();
        String[] names = new String[categories.length];
        for (int i = 0; i < categories.length; i++) {
            names[i] = categories[i].getDisplayName();
        }
        visible = add(new ToggleListSetting("Visible", names));
        instance = this;
    }

    public static boolean isShown(Category category) {
        if (category == Category.UI) {
            return true;
        }
        if (instance == null || !instance.isEnabled()) {
            return true;
        }
        return instance.visible.isOn(category.ordinal());
    }
}
