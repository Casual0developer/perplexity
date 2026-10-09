package dev.perplexity.client.module;

import dev.perplexity.client.module.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting> settings = new ArrayList<>();

    private boolean enabled;
    private int keybind;
    private boolean expanded;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    protected <T extends Setting> T add(T setting) {
        settings.add(setting);
        return setting;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public List<Setting> getSettings() {
        return Collections.unmodifiableList(settings);
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
