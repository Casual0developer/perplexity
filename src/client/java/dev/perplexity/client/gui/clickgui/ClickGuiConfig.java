package dev.perplexity.client.gui.clickgui;

import org.lwjgl.glfw.GLFW;

/** Options for the ClickGUI itself. Kept out of the module list so the registry can stay empty. */
public final class ClickGuiConfig {
    public static boolean dark = true;
    public static boolean blur = true;
    public static boolean dock = true;
    public static int openKey = GLFW.GLFW_KEY_RIGHT_SHIFT;

    private ClickGuiConfig() {
    }
}
