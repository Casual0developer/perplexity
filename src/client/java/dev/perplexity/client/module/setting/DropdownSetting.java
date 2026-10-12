package dev.perplexity.client.module.setting;

/**
 * Single-select option list. Rendered as an expandable dropdown in the ClickGUI:
 * the header shows the current choice, opening it reveals every option to pick from.
 */
public final class DropdownSetting extends Setting {
    private final String[] options;
    private int selected;
    private boolean open;

    public DropdownSetting(String name, String defaultValue, String... options) {
        super(name);
        if (options == null || options.length == 0) {
            throw new IllegalArgumentException("DropdownSetting needs at least one option");
        }
        this.options = options.clone();
        select(defaultValue);
    }

    public String get() {
        return options[selected];
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

    public int getSelected() {
        return selected;
    }

    public boolean is(String value) {
        return value != null && get().equalsIgnoreCase(value);
    }

    public void select(int index) {
        if (index >= 0 && index < options.length) {
            selected = index;
        }
    }

    public void select(String value) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(value)) {
                selected = i;
                return;
            }
        }
    }

    public void cycle() {
        selected = (selected + 1) % options.length;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }
}
