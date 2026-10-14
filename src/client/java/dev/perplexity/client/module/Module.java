package dev.perplexity.client.module;

import dev.perplexity.client.gui.Toasts;
import dev.perplexity.client.module.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
    private final Category category;
    private final List<Setting> settings = new ArrayList<>();
    /** Cached unmodifiable view: settings are all added during construction, so this never goes stale. */
    private List<Setting> settingsView;

    private boolean enabled;
    private int keybind;
    private boolean expanded;

    protected Module(Category category) {
        this.category = category;
    }

    protected <T extends Setting> T add(T setting) {
        settings.add(setting);
        return setting;
    }

    /**
     * Original display name, decoded fresh on every call and never stored: the plaintext
     * exists only transiently while drawn. Implementations return an Obf blob decode.
     */
    public abstract String getDisplayName();

    public Category getCategory() {
        return category;
    }

    public List<Setting> getSettings() {
        if (settingsView == null) {
            settingsView = Collections.unmodifiableList(settings);
        }
        return settingsView;
    }

    public boolean hasSettings() {
        return !settings.isEmpty();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        if (this.enabled == value) {
            return;
        }
        this.enabled = value;
        Toasts.push(this, value);
        if (value) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public int getKeybind() {
        return keybind;
    }

    public void setKeybind(int keybind) {
        this.keybind = keybind;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected void onTick() {
    }

    /** Runs the module's per-tick hook. Called by {@link ModuleManager} for enabled modules only. */
    public void tick() {
        if (!enabled) {
            return;
        }
        onTick();
    }
}
