package dev.perplexity.client.module.setting;

public final class ModeSetting extends Setting {
    private final String[] modes;
    private int index;

    public ModeSetting(String name, String defaultValue, String... modes) {
        super(name);
        this.modes = modes;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equalsIgnoreCase(defaultValue)) {
                this.index = i;
                break;
            }
        }
    }

    public String get() {
        return modes[index];
    }

    public int getIndex() {
        return index;
    }

    public boolean is(String value) {
        return value != null && get().equalsIgnoreCase(value);
    }

    public void select(int index) {
        if (index >= 0 && index < modes.length) {
            this.index = index;
        }
    }

    public String[] getModes() {
        return modes;
    }

    public void cycle() {
        index = (index + 1) % modes.length;
    }
}
