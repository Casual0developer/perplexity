package dev.perplexity.client.gui.clickgui;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.ModeSetting;
import dev.perplexity.client.module.setting.NumberSetting;
import dev.perplexity.client.module.setting.RangeSetting;
import dev.perplexity.client.module.setting.Setting;
import dev.perplexity.client.render.vg.VGFont;
import dev.perplexity.client.render.vg.VGRenderer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single glass panel for one {@link Category}: one translucent surface with a slightly rounded
 * corner, a semibold header in the category's own colour, module names as plain text that turn
 * accent-coloured when enabled, and indented settings rows underneath.
 */
public final class Panel {
    private static final int TYPE_MODULE = 0;
    private static final int TYPE_BOOL = 1;
    private static final int TYPE_NUMBER = 2;
    private static final int TYPE_MODE = 3;
    private static final int TYPE_RANGE = 4;

    /** Distance a settings row travels while it slides into place, in GUI units. */
    private static final float SETTINGS_SLIDE = 9.0F;
    /** Extra distance the whole content block drifts while a panel collapses. */
    private static final float COLLAPSE_SLIDE = 8.0F;

    // Per-frame easing factors: fraction of the remaining distance covered each frame.
    private static final float EXPAND_SPEED = 0.26F;
    private static final float HOVER_SPEED = 0.35F;
    private static final float ENABLE_SPEED = 0.30F;
    private static final float COLLAPSE_SPEED = 0.22F;
    private static final float SLIDER_SPEED = 0.45F;
    /** Tracking applied to the category name so it reads as a header without a rule under it. */
    private static final float HEADER_TRACKING = 0.45F;
    /** Extra delay before each successive settings row starts its slide, as a fraction of expand. */
    private static final float STAGGER = 0.12F;

    private final Category category;
    private final List<Module> modules;

    private float x;
    private float y;
    private float width = GlassTheme.PANEL_WIDTH;

    private boolean collapsed;
    private float scroll;

    private final List<Row> rows = new ArrayList<>();
    private final Map<Module, Float> expandAnimations = new IdentityHashMap<>();
    private final Map<Module, Float> hoverAnimations = new IdentityHashMap<>();
    private final Map<Module, Float> enabledAnimations = new IdentityHashMap<>();
    private final Map<NumberSetting, Float> sliderAnimations = new IdentityHashMap<>();
    private final Map<RangeSetting, Float> rangeLowAnimations = new IdentityHashMap<>();
    private final Map<RangeSetting, Float> rangeHighAnimations = new IdentityHashMap<>();

    private float collapse;

    private boolean draggingPanel;
    private float dragOffsetX;
    private float dragOffsetY;

    private NumberSetting draggingSlider;
    private RangeSetting draggingRange;
    private boolean draggingRangeHigh;

    public Panel(Category category, List<Module> modules, float x, float y) {
        this.category = category;
        this.modules = modules;
        this.x = x;
        this.y = y;
    }

    public Category getCategory() {
        return category;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public void setX(float x) {
        this.x = x;
    }

    public void setY(float y) {
        this.y = y;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    private float contentHeight() {
        float total = 0.0F;
        for (Module module : modules) {
            total += elementHeight(module) + GlassTheme.MODULE_SPACING;
        }
        return total;
    }

    /**
     * Current drawn height. The open height is eased back toward the header-only height by
     * {@code collapse}, so right-clicking a header folds the panel smoothly instead of snapping.
     */
    public float getHeight() {
        if (modules.isEmpty()) {
            return GlassTheme.PANEL_HEADER_HEIGHT;
        }
        float open = Math.min(GlassTheme.MAX_PANEL_HEIGHT,
                GlassTheme.PANEL_HEADER_HEIGHT + 2.0F + contentHeight());
        return GlassTheme.PANEL_HEADER_HEIGHT
                + (open - GlassTheme.PANEL_HEADER_HEIGHT) * (1.0F - collapse);
    }

    private float elementHeight(Module module) {
        return GlassTheme.MODULE_HEIGHT + expand(module) * settingsHeight(module);
    }

    private float settingsHeight(Module module) {
        if (!module.hasSettings()) {
            return 0.0F;
        }
        float total = GlassTheme.SETTING_SPACING;
        for (Setting setting : module.getSettings()) {
            total += rowHeight(setting) + GlassTheme.SETTING_SPACING;
        }
        return total;
    }

    // --- Rendering -----------------------------------------------------------------------------

    public void render(float mouseX, float mouseY) {
        // Advance the panel-level animation before anything reads its eased value.
        collapse = approach(collapse, collapsed ? 1.0F : 0.0F, COLLAPSE_SPEED);

        float height = getHeight();
        VGRenderer.verticalGradient(x, y, width, height, GlassTheme.RADIUS_LG,
                GlassTheme.surfacePanel(), GlassTheme.surfacePanelBottom());

        // Header: semibold and tracked so it still reads as a header now that the separator is gone.
        float headerSize = GlassTheme.FONT_HEADER;
        VGRenderer.textLeft(category.getDisplayName(), x + GlassTheme.PADDING,
                y + GlassTheme.PANEL_HEADER_HEIGHT / 2.0F,
                headerSize, VGFont.semiboldFace(), HEADER_TRACKING, GlassTheme.headerText());

        rows.clear();
        float contentAlpha = 1.0F - collapse;
        if (modules.isEmpty() || contentAlpha <= 0.01F) {
            return;
        }

        float contentTop = y + GlassTheme.PANEL_HEADER_HEIGHT + 2.0F;
        float visibleHeight = height - GlassTheme.PANEL_HEADER_HEIGHT - 2.0F;
        float maxScroll = Math.max(0.0F, contentHeight() * contentAlpha - visibleHeight);
        scroll = Math.max(0.0F, Math.min(maxScroll, scroll));

        if (visibleHeight <= 1.0F) {
            return;
        }

        VGRenderer.scissor(x, contentTop, width, visibleHeight);

        float cursor = contentTop - scroll + collapse * COLLAPSE_SLIDE;
        for (Module module : modules) {
            boolean expanded = module.isExpanded() && module.hasSettings();
            float expand = animate(expandAnimations, module, expanded ? 1.0F : 0.0F, EXPAND_SPEED);
            float elementHeight = GlassTheme.MODULE_HEIGHT + expand * settingsHeight(module);

            boolean hovered = GlassTheme.inside(mouseX, mouseY, x, cursor, width, GlassTheme.MODULE_HEIGHT);
            float hover = animate(hoverAnimations, module, hovered ? 1.0F : 0.0F, HOVER_SPEED);
            float enabled = animate(enabledAnimations, module, module.isEnabled() ? 1.0F : 0.0F, ENABLE_SPEED);

            if (hover > 0.01F) {
                VGRenderer.roundedRect(x + GlassTheme.PADDING * 0.5F, cursor,
                        width - GlassTheme.PADDING, GlassTheme.MODULE_HEIGHT,
                        GlassTheme.RADIUS_SM,
                        GlassTheme.scaleAlpha(GlassTheme.moduleHover(), hover * contentAlpha));
            }

            Color nameColor = GlassTheme.scaleAlpha(
                    GlassTheme.lerp(GlassTheme.textSecondary(), GlassTheme.accent(), enabled), contentAlpha);
            VGRenderer.textLeft(module.getName(), x + GlassTheme.PADDING + 1.0F,
                    cursor + GlassTheme.MODULE_HEIGHT / 2.0F,
                    GlassTheme.FONT_MODULE, VGFont.FACE, nameColor);

            if (module.hasSettings()) {
                drawChevron(x + width - GlassTheme.PADDING - 2.0F,
                        cursor + GlassTheme.MODULE_HEIGHT / 2.0F, expand,
                        GlassTheme.scaleAlpha(GlassTheme.textMuted(), contentAlpha * (0.45F + 0.55F * expand)));
            }

            rows.add(new Row(TYPE_MODULE, module, null, x, cursor, width, GlassTheme.MODULE_HEIGHT));

            if (expand > 0.01F && module.hasSettings()) {
                float settingY = cursor + GlassTheme.MODULE_HEIGHT + GlassTheme.SETTING_SPACING;
                int index = 0;
                for (Setting setting : module.getSettings()) {
                    float rowHeight = rowHeight(setting);
                    // Rows ease in one after another, sliding up into the space the panel is opening.
                    float start = Math.min(0.6F, index * STAGGER);
                    float row = clamp01((expand - start) / (1.0F - start));
                    float offsetY = (1.0F - row) * SETTINGS_SLIDE;
                    float settingX = x + GlassTheme.SETTING_INDENT;
                    float settingW = width - GlassTheme.SETTING_INDENT - 4.0F;
                    boolean settingHovered = GlassTheme.inside(mouseX, mouseY,
                            settingX, settingY + offsetY, settingW, rowHeight);
                    renderSetting(setting, settingX, settingY + offsetY, settingW, rowHeight,
                            settingHovered, contentAlpha * row);
                    rows.add(new Row(rowType(setting), module, setting, settingX,
                            settingY + offsetY, settingW, rowHeight));
                    settingY += rowHeight + GlassTheme.SETTING_SPACING;
                    index++;
                }
            }

            cursor += elementHeight + GlassTheme.MODULE_SPACING;
        }

        VGRenderer.resetScissor();
    }

    /** A small chevron that turns from a right arrow into a down arrow as settings open. */
    private void drawChevron(float cx, float cy, float expand, Color color) {
        VGRenderer.save();
        VGRenderer.translate(cx, cy);
        VGRenderer.rotate((float) Math.toRadians(90.0 * expand));
        VGRenderer.line(-1.6F, -3.0F, 1.8F, 0.0F, 1.4F, color);
        VGRenderer.line(1.8F, 0.0F, -1.6F, 3.0F, 1.4F, color);
        VGRenderer.restore();
    }

    private void renderSetting(Setting setting, float sx, float sy, float sw, float sh,
                               boolean hovered, float rowAlpha) {
        float alpha = clamp01(rowAlpha);

        float labelCenterY = sy + GlassTheme.SETTING_HEIGHT / 2.0F;

        if (setting instanceof BooleanSetting bool) {
            if (hovered) {
                VGRenderer.roundedRect(sx, sy, sw, sh, GlassTheme.RADIUS_SM,
                        GlassTheme.scaleAlpha(GlassTheme.moduleHover(), 0.45F * alpha));
            }
            VGRenderer.textLeft(setting.getName(), sx + GlassTheme.PADDING, labelCenterY,
                    GlassTheme.FONT_SETTING, VGFont.FACE,
                    GlassTheme.scaleAlpha(bool.get() ? GlassTheme.textPrimary() : GlassTheme.textSecondary(), alpha));

            float check = GlassTheme.CHECK_SIZE;
            float cx = sx + sw - GlassTheme.PADDING - check;
            float cy = sy + (GlassTheme.SETTING_HEIGHT - check) / 2.0F;
            if (bool.get()) {
                VGRenderer.roundedRect(cx, cy, check, check, GlassTheme.RADIUS_SM,
                        GlassTheme.scaleAlpha(GlassTheme.accent(), alpha));
                drawCheck(cx + check / 2.0F, cy + check / 2.0F, check,
                        GlassTheme.scaleAlpha(GlassTheme.textOnAccent(), alpha));
            } else {
                VGRenderer.roundedRect(cx, cy, check, check, GlassTheme.RADIUS_SM,
                        GlassTheme.scaleAlpha(GlassTheme.interactiveMuted(), alpha));
            }
            return;
        }

        if (setting instanceof NumberSetting number) {
            if (hovered) {
                VGRenderer.roundedRect(sx, sy, sw, sh, GlassTheme.RADIUS_SM,
                        GlassTheme.scaleAlpha(GlassTheme.moduleHover(), 0.5F * alpha));
            }
            VGRenderer.textLeft(setting.getName(), sx + GlassTheme.PADDING, labelCenterY,
                    GlassTheme.FONT_SETTING, VGFont.FACE,
                    GlassTheme.scaleAlpha(GlassTheme.textSecondary(), alpha));

            String value = number.display();
            float valueWidth = VGRenderer.textWidth(value, GlassTheme.FONT_SETTING, VGFont.FACE, 0.0F);
            VGRenderer.textLeft(value, (sx + sw - GlassTheme.PADDING) - valueWidth, labelCenterY,
                    GlassTheme.FONT_SETTING, VGFont.FACE,
                    GlassTheme.scaleAlpha(draggingSlider == number ? GlassTheme.textPrimary() : GlassTheme.textMuted(), alpha));

            float trackX = sx + GlassTheme.PADDING;
            float trackW = sw - GlassTheme.PADDING * 2.0F;
            float trackY = sy + GlassTheme.SETTING_HEIGHT + 2.0F;
            float trackH = GlassTheme.TRACK_HEIGHT;
            VGRenderer.roundedRect(trackX, trackY, trackW, trackH, trackH / 2.0F,
                    GlassTheme.scaleAlpha(GlassTheme.interactiveMuted(), alpha));

            float fraction = animate(sliderAnimations, number, number.normalized(), SLIDER_SPEED);
            float filled = trackW * fraction;
            if (filled > 0.5F) {
                VGRenderer.roundedRect(trackX, trackY, filled, trackH, trackH / 2.0F,
                        GlassTheme.scaleAlpha(GlassTheme.accent(), alpha));
            }
            float knob = GlassTheme.KNOB_SIZE;
            VGRenderer.circle(trackX + filled, trackY + trackH / 2.0F, knob * 0.5F,
                    GlassTheme.scaleAlpha(GlassTheme.knob(), alpha));
            return;
        }

        if (setting instanceof RangeSetting range) {
            if (hovered) {
                VGRenderer.roundedRect(sx, sy, sw, sh, GlassTheme.RADIUS_SM,
                        GlassTheme.scaleAlpha(GlassTheme.moduleHover(), 0.5F * alpha));
            }
            VGRenderer.textLeft(setting.getName(), sx + GlassTheme.PADDING, labelCenterY,
                    GlassTheme.FONT_SETTING, VGFont.FACE,
                    GlassTheme.scaleAlpha(GlassTheme.textSecondary(), alpha));

            String value = range.display();
            float valueWidth = VGRenderer.textWidth(value, GlassTheme.FONT_SETTING, VGFont.FACE, 0.0F);
            VGRenderer.textLeft(value, (sx + sw - GlassTheme.PADDING) - valueWidth, labelCenterY,
                    GlassTheme.FONT_SETTING, VGFont.FACE,
                    GlassTheme.scaleAlpha(draggingRange == range ? GlassTheme.textPrimary() : GlassTheme.textMuted(), alpha));

            float trackX = sx + GlassTheme.PADDING;
            float trackW = sw - GlassTheme.PADDING * 2.0F;
            float trackY = sy + GlassTheme.SETTING_HEIGHT + 2.0F;
            float trackH = GlassTheme.TRACK_HEIGHT;
            VGRenderer.roundedRect(trackX, trackY, trackW, trackH, trackH / 2.0F,
                    GlassTheme.scaleAlpha(GlassTheme.interactiveMuted(), alpha));

            float lowX = trackW * animate(rangeLowAnimations, range, range.normalizedLow(), SLIDER_SPEED);
            float highX = trackW * animate(rangeHighAnimations, range, range.normalizedHigh(), SLIDER_SPEED);
            if (highX - lowX > 0.5F) {
                VGRenderer.roundedRect(trackX + lowX, trackY, highX - lowX, trackH, trackH / 2.0F,
                        GlassTheme.scaleAlpha(GlassTheme.accent(), alpha));
            }
            float knob = GlassTheme.KNOB_SIZE;
            VGRenderer.circle(trackX + lowX, trackY + trackH / 2.0F, knob * 0.5F,
                    GlassTheme.scaleAlpha(GlassTheme.knob(), alpha));
            VGRenderer.circle(trackX + highX, trackY + trackH / 2.0F, knob * 0.5F,
                    GlassTheme.scaleAlpha(GlassTheme.knob(), alpha));
            return;
        }

        if (setting instanceof ModeSetting mode) {
            if (hovered) {
                VGRenderer.roundedRect(sx, sy, sw, sh, GlassTheme.RADIUS_SM,
                        GlassTheme.scaleAlpha(GlassTheme.moduleHover(), 0.45F * alpha));
            }
            VGRenderer.textLeft(setting.getName(), sx + GlassTheme.PADDING, labelCenterY,
                    GlassTheme.FONT_SETTING, VGFont.FACE,
                    GlassTheme.scaleAlpha(GlassTheme.textSecondary(), alpha));

            String label = mode.get();
            float pillPad = 6.0F;
            float pillWidth = VGRenderer.textWidth(label, GlassTheme.FONT_HINT, VGFont.FACE, 0.0F) + pillPad * 2.0F;
            float pillHeight = 13.0F;
            float pillX = (sx + sw - GlassTheme.PADDING) - pillWidth;
            float pillY = sy + (GlassTheme.SETTING_HEIGHT - pillHeight) / 2.0F;
            VGRenderer.roundedRect(pillX, pillY, pillWidth, pillHeight, GlassTheme.RADIUS_SM,
                    GlassTheme.scaleAlpha(GlassTheme.accent(), 0.9F * alpha));
            VGRenderer.textCentered(label, pillX + pillWidth / 2.0F, pillY + pillHeight / 2.0F,
                    GlassTheme.FONT_HINT, VGFont.FACE, GlassTheme.scaleAlpha(GlassTheme.textOnAccent(), alpha));
        }
    }

    /**
     * Check mark scaled to the checkbox it sits in, centred on {@code (cx, cy)}.
     *
     * <p>Drawn as one polyline rather than two strokes so the elbow joins cleanly, and
     * {@link VGRenderer#polyline} rounds both caps and joins — square ends plus a butt joint at the
     * bend are what made the old mark look broken and disconnected.</p>
     */
    private void drawCheck(float cx, float cy, float size, Color color) {
        float[] points = {
                cx - size * 0.33F, cy + size * 0.01F,
                cx - size * 0.09F, cy + size * 0.25F,
                cx + size * 0.33F, cy - size * 0.27F,
        };
        VGRenderer.polyline(points, 3, Math.max(1.0F, size * 0.15F), false, color);
    }

    private static float rowHeight(Setting setting) {
        return setting instanceof NumberSetting || setting instanceof RangeSetting
                ? GlassTheme.SLIDER_HEIGHT
                : GlassTheme.SETTING_HEIGHT;
    }

    private float expand(Module module) {
        if (!module.hasSettings()) {
            return 0.0F;
        }
        return expandAnimations.getOrDefault(module, module.isExpanded() ? 1.0F : 0.0F);
    }

    /**
     * Eases the value stored under {@code key} toward {@code target}. A fresh key starts at the
     * target so first paint is already settled, and the last half-percent snaps to avoid the value
     * creeping toward the target forever.
     */
    private static <K> float animate(Map<K, Float> map, K key, float target, float speed) {
        float current = map.getOrDefault(key, target);
        float next = current + (target - current) * speed;
        if (Math.abs(target - next) < 0.001F) {
            next = target;
        }
        map.put(key, next);
        return next;
    }

    /** Eases a plain scalar the same way {@link #animate} eases a mapped one. */
    private static float approach(float current, float target, float speed) {
        float next = current + (target - current) * speed;
        return Math.abs(target - next) < 0.001F ? target : next;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int rowType(Setting setting) {
        if (setting instanceof BooleanSetting) {
            return TYPE_BOOL;
        }
        if (setting instanceof NumberSetting) {
            return TYPE_NUMBER;
        }
        if (setting instanceof RangeSetting) {
            return TYPE_RANGE;
        }
        return TYPE_MODE;
    }

    // --- Input ---------------------------------------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (GlassTheme.inside(mouseX, mouseY, x, y, width, GlassTheme.PANEL_HEADER_HEIGHT)) {
            if (button == 0) {
                draggingPanel = true;
                dragOffsetX = (float) mouseX - x;
                dragOffsetY = (float) mouseY - y;
            } else if (button == 1) {
                collapsed = !collapsed;
            }
            return true;
        }

        for (Row row : rows) {
            if (!GlassTheme.inside(mouseX, mouseY, row.x, row.y, row.w, row.h)) {
                continue;
            }

            switch (row.type) {
                case TYPE_MODULE -> {
                    // Reference behaviour: left click toggles, right click opens the settings.
                    if (button == 0) {
                        row.module.toggle();
                    } else if (button == 1 && row.module.hasSettings()) {
                        row.module.setExpanded(!row.module.isExpanded());
                    }
                }
                case TYPE_BOOL -> {
                    if (button == 0 && row.setting instanceof BooleanSetting bool) {
                        bool.toggle();
                    }
                }
                case TYPE_NUMBER -> {
                    if (button == 0 && row.setting instanceof NumberSetting number) {
                        draggingSlider = number;
                        updateSlider(number, mouseX, row);
                    }
                }
                case TYPE_RANGE -> {
                    if (button == 0 && row.setting instanceof RangeSetting range) {
                        draggingRange = range;
                        draggingRangeHigh = nearestKnobIsHigh(range, mouseX, row);
                        updateRange(range, mouseX, row);
                    }
                }
                case TYPE_MODE -> {
                    if (row.setting instanceof ModeSetting mode && (button == 0 || button == 1)) {
                        mode.cycle();
                    }
                }
                default -> {
                }
            }
            return true;
        }

        return GlassTheme.inside(mouseX, mouseY, x, y, width, getHeight());
    }

    public void mouseDragged(double mouseX, double mouseY, int button) {
        if (draggingPanel) {
            x = (float) mouseX - dragOffsetX;
            y = (float) mouseY - dragOffsetY;
            return;
        }
        if (draggingSlider != null) {
            for (Row row : rows) {
                if (row.setting == draggingSlider) {
                    updateSlider(draggingSlider, mouseX, row);
                    break;
                }
            }
        }
        if (draggingRange != null) {
            for (Row row : rows) {
                if (row.setting == draggingRange) {
                    updateRange(draggingRange, mouseX, row);
                    break;
                }
            }
        }
    }

    public void mouseReleased() {
        draggingPanel = false;
        draggingSlider = null;
        draggingRange = null;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (collapsed || !GlassTheme.inside(mouseX, mouseY, x, y, width, getHeight())) {
            return false;
        }
        float visible = getHeight() - GlassTheme.PANEL_HEADER_HEIGHT - 2.0F;
        float maxScroll = Math.max(0.0F, contentHeight() - visible);
        if (maxScroll <= 0.0F) {
            return false;
        }
        float next = Math.max(0.0F, Math.min(maxScroll, scroll - (float) amount * GlassTheme.SCROLL_MULTIPLIER));
        if (next == scroll) {
            return false;
        }
        scroll = next;
        return true;
    }

    private void updateSlider(NumberSetting number, double mouseX, Row row) {
        float trackX = row.x + GlassTheme.PADDING;
        float trackW = row.w - GlassTheme.PADDING * 2.0F;
        if (trackW <= 0.0F) {
            return;
        }
        number.setNormalized((float) ((mouseX - trackX) / trackW));
    }

    /** Picks the knob closer to the cursor so a drag grabs the end the user aimed at. */
    private static boolean nearestKnobIsHigh(RangeSetting range, double mouseX, Row row) {
        float trackX = row.x + GlassTheme.PADDING;
        float trackW = row.w - GlassTheme.PADDING * 2.0F;
        float lowX = trackX + trackW * range.normalizedLow();
        float highX = trackX + trackW * range.normalizedHigh();
        return Math.abs(mouseX - highX) < Math.abs(mouseX - lowX);
    }

    private void updateRange(RangeSetting range, double mouseX, Row row) {
        float trackX = row.x + GlassTheme.PADDING;
        float trackW = row.w - GlassTheme.PADDING * 2.0F;
        if (trackW <= 0.0F) {
            return;
        }
        float fraction = (float) ((mouseX - trackX) / trackW);
        if (draggingRangeHigh) {
            range.setNormalizedHigh(fraction);
        } else {
            range.setNormalizedLow(fraction);
        }
    }

    private record Row(int type, Module module, Setting setting, float x, float y, float w, float h) {
    }
}
