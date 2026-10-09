package dev.perplexity.client.gui.clickgui;

import java.awt.Color;

/**
 * Palette and metrics copied from the reference client's glass UI constants so the ClickGUI reads
 * like the original: 120-wide panels, radius 12, 16-high module rows, tiny type, and a purple accent
 * shown only through text and the small controls.
 */
public final class GlassTheme {
    // --- Metrics -------------------------------------------------------------------------------

    public static final float PANEL_WIDTH = 120.0F;
    public static final float PANEL_HEADER_HEIGHT = 18.0F;
    public static final float PANEL_SPACING = 8.0F;
    public static final float INITIAL_Y = 15.0F;
    public static final float MAX_PANEL_HEIGHT = 400.0F;

    public static final float MODULE_HEIGHT = 16.0F;
    public static final float MODULE_SPACING = 2.0F;
    public static final float SETTING_HEIGHT = 14.0F;
    public static final float SLIDER_HEIGHT = 20.0F;
    public static final float SETTING_SPACING = 2.0F;
    public static final float SETTING_INDENT = 4.0F;
    public static final float PADDING = 5.0F;

    public static final float RADIUS_SM = 4.0F;
    public static final float RADIUS_MD = 7.0F;
    public static final float RADIUS_LG = 12.0F;

    /** Size of the round "check" control used by boolean settings. */
    public static final float CHECK_SIZE = 9.0F;
    public static final float TRACK_HEIGHT = 4.0F;
    public static final float KNOB_SIZE = 5.0F;

    public static final float SCROLL_MULTIPLIER = 14.0F;

    // --- Type ----------------------------------------------------------------------------------

    public static final float FONT_HEADER = 7.5F;
    public static final float FONT_MODULE = 8.2F;
    public static final float FONT_SETTING = 6.0F;
    public static final float FONT_HINT = 5.7F;

    /** Extra size a module name grows to on hover, as in the reference. */
    public static final float MODULE_HOVER_SCALE = 1.08F;

    public static final Color ACCENT = new Color(90, 82, 222);

    private static boolean dark = true;

    private GlassTheme() {
    }

    public static void setDark(boolean value) {
        dark = value;
    }

    public static boolean isDark() {
        return dark;
    }

    // --- Palette -------------------------------------------------------------------------------

    /** The single translucent panel surface. No border, no header fill — as in the reference. */
    public static Color surfacePanel() {
        return dark ? new Color(22, 22, 24, 235) : new Color(248, 248, 248, 225);
    }

    public static Color textPrimary() {
        return dark ? new Color(255, 255, 255) : new Color(0, 0, 0);
    }

    public static Color textSecondary() {
        return dark ? new Color(235, 235, 245, 230) : new Color(20, 20, 20, 230);
    }

    public static Color textMuted() {
        return dark ? new Color(200, 200, 215, 200) : new Color(60, 60, 60, 205);
    }

    public static Color moduleHover() {
        return dark ? new Color(255, 255, 255, 24) : new Color(0, 0, 0, 22);
    }

    public static Color interactiveMuted() {
        return dark ? new Color(85, 85, 95, 190) : new Color(190, 190, 200, 150);
    }

    public static Color knob() {
        return dark ? new Color(240, 240, 245, 245) : new Color(255, 255, 255, 250);
    }

    public static Color dockBackground() {
        return dark ? new Color(22, 22, 24, 220) : new Color(248, 248, 248, 215);
    }

    public static Color textOnAccent() {
        return new Color(255, 255, 255, 240);
    }

    /** Blur tint drawn over the background, matching the reference's BlurBackground tint. */
    public static Color scrim() {
        return dark ? new Color(0, 0, 0, 90) : new Color(0, 0, 0, 45);
    }

    public static Color accent() {
        return ACCENT;
    }

    public static Color accent(float alpha) {
        return withAlpha(ACCENT, Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F));
    }

    // --- Helpers -------------------------------------------------------------------------------

    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.max(0, Math.min(255, alpha)));
    }

    /** Multiplies an existing colour's alpha, mirroring the reference's {@code wa(argb, f)}. */
    public static Color scaleAlpha(Color color, float factor) {
        return withAlpha(color, Math.round(color.getAlpha() * factor));
    }

    public static Color lerp(Color from, Color to, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return new Color(
                (int) (from.getRed() + (to.getRed() - from.getRed()) * t),
                (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * t),
                (int) (from.getAlpha() + (to.getAlpha() - from.getAlpha()) * t));
    }

    public static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
