package dev.perplexity.client.module;

import java.awt.Color;

public enum Category {
    COMBAT("Combat", new Color(0xE8, 0x5A, 0x6B)),
    RENDER("Render", new Color(0x5A, 0x7C, 0xE8)),
    MOVEMENT("Movement", new Color(0x4C, 0xC9, 0x8A)),
    PLAYER("Player", new Color(0xE8, 0xB8, 0x5A)),
    MISC("Misc", new Color(0xB0, 0x7C, 0xE8)),
    UI("UI", new Color(0xC8, 0xC8, 0xD2));

    private final String displayName;
    private final Color color;

    Category(String displayName, Color color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Color getColor() {
        return color;
    }
}
