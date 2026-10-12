package dev.perplexity.client.impl.modules.ui;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.ToggleListSetting;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows or hides whole menu tabs. The UI tab itself can never hide, or the switch to bring
 * anything back would vanish with it. While this module is off, every tab shows.
 */
public final class TabsModule extends Module {
    private static TabsModule instance;

    private final ToggleListSetting visible;
    private final Category[] listed;

    public TabsModule() {
        super("Tabs", "Show or hide menu tabs", Category.UI);
        List<Category> categories = new ArrayList<>();
        for (Category category : Category.values()) {
            // The UI tab holds this very switch, so it is never listed: hiding it would
            // strand the user with no way back.
            if (category != Category.UI) {
                categories.add(category);
            }
        }
        listed = categories.toArray(new Category[0]);
        String[] names = new String[listed.length];
        for (int i = 0; i < listed.length; i++) {
            names[i] = listed[i].getDisplayName();
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
        for (int i = 0; i < instance.listed.length; i++) {
            if (instance.listed[i] == category) {
                return instance.visible.isOn(i);
            }
        }
        return true;
    }
}
