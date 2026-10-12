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
    /** Distance from a panel's edge, or a row's own rect, to the text or control inside it. */
    public static final float PADDING = 8.0F;

    /** Thickness of the panel edge. Drawn as two fills, never as a stroke. */
    public static final float PANEL_BORDER = 1.0F;
    /** Inset of a settings group container from the panel's sides. */
    public static final float GROUP_INSET = 4.0F;

    // Derived metrics, written as expressions rather than numbers so the setting rows cannot drift off
    // the column the module names sit on when one of the paddings above changes.

    /**
     * Left edge of the column a module's name and its settings' names share. The unit over
     * {@link #PADDING} is optical: it squares the regular-face rows with the semibold header above
     * them, rather than pushing them out of line with it.
     */
    public static final float TEXT_INSET = PADDING + 1.0F;
    /**
     * Inset of a setting row inside its group container. Flush with the container's inner edge —
     * {@link #GROUP_INSET} plus the container's {@link #PANEL_BORDER} edge — so a hovered row fills
     * the well it belongs to instead of floating inside it.
     */
    public static final float SETTING_INDENT = GROUP_INSET + PANEL_BORDER;
    /**
     * Inset of a setting row's own content (name, slider track, value, pill) from the row's rect.
     * {@code SETTING_INDENT + SETTING_PADDING} is exactly {@link #TEXT_INSET}, so a setting's name
     * lands on the same column as the module name above it and its track and controls line up with
     * the row's own edges.
     */
    public static final float SETTING_PADDING = TEXT_INSET - SETTING_INDENT;

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

    public static final Color ACCENT = new Color(250, 72, 140);

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
        return dark ? new Color(26, 18, 42, 208) : new Color(250, 248, 255, 200);
    }

    /** Bottom of the panel surface gradient, a shade deeper so panels gain a little depth. */
    public static Color surfacePanelBottom() {
        return dark ? new Color(12, 8, 24, 222) : new Color(236, 232, 250, 216);
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
        return dark ? new Color(88, 72, 104, 150) : new Color(212, 200, 230, 175);
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

    /** Category header text. Plain near-white, like the reference. */
    public static Color headerText() {
        return dark ? new Color(240, 238, 250) : new Color(88, 66, 186);
    }

    public static Color moduleHover() {
        return dark ? new Color(255, 110, 170, 30) : new Color(250, 72, 140, 26);
    }

    public static Color interactiveMuted() {
        return dark ? new Color(96, 80, 106, 190) : new Color(210, 198, 228, 165);
    }

    public static Color knob() {
        return dark ? new Color(228, 219, 255, 245) : new Color(255, 255, 255, 250);
    }

    public static Color textOnAccent() {
        return new Color(255, 255, 255, 240);
    }

    /** Blur tint drawn over the background: kept faint so the game reads through the menu. */
    public static Color scrim() {
        return dark ? new Color(9, 5, 18, 38) : new Color(28, 16, 52, 18);
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

    /** Bright end of the enabled-row gradient. */
    public static Color accentBright() {
        return new Color(255, 105, 168);
    }

    /** Deep end of the enabled-row gradient. */
    public static Color accentDeep() {
        return new Color(238, 52, 118);
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
