package dev.perplexity.client.gui.clickgui;

import java.awt.Color;

/**
 * Palette and metrics for the ClickGUI: black-violet surfaces, one purple accent, and tight corner
 * radii so panels read as squared-off cards instead of pills.
 */
public final class GlassTheme {
    // --- Metrics -------------------------------------------------------------------------------

    public static final float PANEL_WIDTH = 128.0F;
    public static final float PANEL_HEADER_HEIGHT = 22.0F;
    /** Gap between the header baseline area and the first module row. */
    public static final float PANEL_CONTENT_GAP = 4.0F;
    public static final float PANEL_SPACING = 10.0F;
    public static final float INITIAL_Y = 24.0F;
    public static final float MAX_PANEL_HEIGHT = 460.0F;

    public static final float MODULE_HEIGHT = 20.0F;
    public static final float MODULE_SPACING = 3.0F;
    public static final float SETTING_HEIGHT = 18.0F;
    /** Two-line slider row: label on top, track underneath. Deliberately tighter than the old 28. */
    public static final float SLIDER_HEIGHT = 24.0F;
    /** Vertical centre of a slider row's label, measured from the top of the row. */
    public static final float SLIDER_LABEL_CENTER = 7.5F;
    /** Top of a slider row's track, measured from the top of the row. */
    public static final float SLIDER_TRACK_TOP = 15.0F;
    public static final float SETTING_SPACING = 3.0F;
    /** Inset of a setting row inside its group container; the container is {@link #GROUP_INSET} in. */
    public static final float SETTING_INDENT = 8.0F;
    public static final float PADDING = 8.0F;

    /** Thickness of the panel edge. Drawn as two fills, never as a stroke. */
    public static final float PANEL_BORDER = 1.0F;
    /** Inset of a settings group container from the panel's sides. */
    public static final float GROUP_INSET = 4.0F;

    // Deliberately small: the UI reads as flat panels, not soap bars.
    public static final float RADIUS_SM = 2.0F;
    public static final float RADIUS_MD = 3.0F;
    public static final float RADIUS_LG = 5.0F;

    /** Boolean settings are a switch: pill track plus a knob that slides between the ends. */
    public static final float TOGGLE_WIDTH = 20.0F;
    public static final float TOGGLE_HEIGHT = 12.0F;
    public static final float TOGGLE_INSET = 2.0F;
    public static final float TRACK_HEIGHT = 4.0F;
    public static final float KNOB_SIZE = 6.0F;

    public static final float SCROLL_MULTIPLIER = 20.0F;

    // --- Type ----------------------------------------------------------------------------------

    /** The header outweighs the module rows so a panel reads top-down despite having no rule. */
    public static final float FONT_HEADER = 9.5F;
    public static final float FONT_MODULE = 9.0F;
    public static final float FONT_SETTING = 7.5F;
    public static final float FONT_HINT = 7.0F;

    public static final Color ACCENT = new Color(124, 92, 255);

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

    /**
     * Top of the panel surface gradient: near-black with a violet cast. Left a little translucent so
     * the backdrop glows and flakes read faintly through the panels instead of being hidden by them.
     */
    public static Color surfacePanel() {
        return dark ? new Color(26, 18, 42, 226) : new Color(250, 248, 255, 216);
    }

    /** Bottom of the panel surface gradient, a shade deeper so panels gain a little depth. */
    public static Color surfacePanelBottom() {
        return dark ? new Color(12, 8, 24, 238) : new Color(236, 232, 250, 232);
    }

    /**
     * Hairline edge around a panel. Without it a near-black panel dissolves into a dark scene, so
     * this is what keeps the card readable over any background. Kept faint on purpose: any stronger
     * and the panels start reading as outlined form fields instead of cards.
     */
    public static Color panelBorder() {
        return dark ? new Color(124, 92, 255, 42) : new Color(124, 92, 255, 62);
    }

    /** Recessed well behind an expanded module's settings, so the rows read as one grouped block. */
    public static Color groupFill() {
        return dark ? new Color(0, 0, 0, 52) : new Color(0, 0, 0, 16);
    }

    /** Unfilled part of a slider track. Dimmer than {@link #interactiveMuted()} so tracks recede. */
    public static Color sliderTrack() {
        return dark ? new Color(70, 62, 102, 150) : new Color(206, 200, 228, 175);
    }

    public static Color textPrimary() {
        return dark ? new Color(246, 243, 255) : new Color(16, 12, 28);
    }

    public static Color textSecondary() {
        return dark ? new Color(227, 221, 246, 232) : new Color(42, 34, 62, 232);
    }

    public static Color textMuted() {
        return dark ? new Color(176, 168, 208, 205) : new Color(100, 90, 128, 210);
    }

    /** Category header text. One colour for every category, softened lavender. */
    public static Color headerText() {
        return dark ? new Color(214, 205, 255) : new Color(88, 66, 186);
    }

    public static Color moduleHover() {
        return dark ? new Color(150, 118, 255, 30) : new Color(124, 92, 255, 26);
    }

    public static Color interactiveMuted() {
        return dark ? new Color(84, 74, 124, 190) : new Color(198, 190, 226, 165);
    }

    public static Color knob() {
        return dark ? new Color(228, 219, 255, 245) : new Color(255, 255, 255, 250);
    }

    public static Color textOnAccent() {
        return new Color(255, 255, 255, 240);
    }

    /** Blur tint drawn over the background: a deep violet-black rather than flat grey. */
    public static Color scrim() {
        return dark ? new Color(9, 5, 18, 130) : new Color(28, 16, 52, 60);
    }

    // --- Backdrop ------------------------------------------------------------------------------

    /** Brighter of the two soft radial washes painted behind the panels. */
    public static Color glow() {
        return withAlpha(ACCENT, dark ? 96 : 44);
    }

    /** Dimmer wash for the opposite corner, so the backdrop is not lit evenly. */
    public static Color glowSoft() {
        return withAlpha(ACCENT, dark ? 68 : 30);
    }

    /** Transparent edge of both washes. */
    public static Color glowFade() {
        return withAlpha(ACCENT, 0);
    }

    /**
     * Falling-flake shades, dimmest first. The backdrop picks one per flake each frame to fake a
     * twinkle without allocating a colour per particle.
     */
    public static Color[] snowShades() {
        return dark ? DARK_SNOW : LIGHT_SNOW;
    }

    private static final Color[] DARK_SNOW = {
            new Color(122, 96, 206, 55),
            new Color(156, 132, 232, 95),
            new Color(190, 172, 250, 140),
            new Color(228, 218, 255, 195),
    };

    private static final Color[] LIGHT_SNOW = {
            new Color(122, 96, 206, 60),
            new Color(124, 92, 255, 105),
            new Color(124, 92, 255, 150),
            new Color(104, 74, 220, 190),
    };

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
