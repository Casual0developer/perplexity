package dev.perplexity.client.module.setting;

import java.util.Arrays;

/**
 * An expandable checklist: every option carries its own on/off state and tapping one flips
 * just it, leaving the list open for the next. Everything starts on.
 */
public final class ToggleListSetting extends Setting {
    private final String[] options;
    private final boolean[] states;
    private boolean open;

    public ToggleListSetting(String name, String... options) {
        super(name);
        if (options == null || options.length == 0) {
            throw new IllegalArgumentException("ToggleListSetting needs at least one option");
        }
        this.options = options.clone();
        this.states = new boolean[options.length];
        Arrays.fill(this.states, true);
    }

    public String[] getOptions() {
        return options.clone();
    }

    /** Allocation-free access for per-frame loops; the option list never changes. */
    public int optionCount() {
        return options.length;
    }

    /** Allocation-free access for per-frame loops; the option list never changes. */
    public String option(int index) {
        return options[index];
    }

    public boolean isOn(int index) {
        return index >= 0 && index < states.length && states[index];
    }

    public void toggle(int index) {
        if (index >= 0 && index < states.length) {
            states[index] = !states[index];
        }
    }

    public void set(int index, boolean value) {
        if (index >= 0 && index < states.length) {
            states[index] = value;
        }
    }

    public int onCount() {
        int count = 0;
        for (boolean state : states) {
            if (state) {
                count++;
            }
        }
        return count;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }
}
