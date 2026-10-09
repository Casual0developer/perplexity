package dev.perplexity.client.gui.clickgui;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.ModeSetting;
import dev.perplexity.client.module.setting.NumberSetting;
import dev.perplexity.client.module.setting.Setting;
import dev.perplexity.client.render.vg.VGFont;
import dev.perplexity.client.render.vg.VGRenderer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single glass panel for one {@link Category}, styled after the reference client: one translucent
 * rounded surface (no border, no header fill, no accent bar), module names as plain text that turn
 * accent-coloured when enabled, and indented, minimal settings rows underneath.
 */
public final class Panel {
    private static final int TYPE_MODULE = 0;
    private static final int TYPE_BOOL = 1;
    private static final int TYPE_NUMBER = 2;
    private static final int TYPE_MODE = 3;

    private static final float SETTINGS_SLIDE = 22.0F;

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

    private boolean draggingPanel;
    private float dragOffsetX;
    private float dragOffsetY;

    private NumberSetting draggingSlider;

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

    public float getHeight() {
        if (collapsed || modules.isEmpty()) {
            return GlassTheme.PANEL_HEADER_HEIGHT;
        }
        return Math.min(GlassTheme.MAX_PANEL_HEIGHT,
                GlassTheme.PANEL_HEADER_HEIGHT + 2.0F + contentHeight());
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
        float height = getHeight();
        VGRenderer.roundedRect(x, y, width, height, GlassTheme.RADIUS_LG, GlassTheme.surfacePanel());

        // Header: just the category name, tiny and muted. No bar, no fill.
        float headerSize = GlassTheme.FONT_HEADER;
        VGRenderer.text(category.getDisplayName(), x + GlassTheme.PADDING + 0.8F,
                y + GlassTheme.PANEL_HEADER_HEIGHT / 2.0F - headerSize * 0.52F,
                headerSize, VGFont.mediumFace(), 0.0F, 0.0F, GlassTheme.textSecondary());

        rows.clear();
        if (collapsed || modules.isEmpty()) {
            return;
        }

        float contentTop = y + GlassTheme.PANEL_HEADER_HEIGHT + 2.0F;
        float visibleHeight = height - GlassTheme.PANEL_HEADER_HEIGHT - 2.0F;
        float maxScroll = Math.max(0.0F, contentHeight() - visibleHeight);
        scroll = Math.max(0.0F, Math.min(maxScroll, scroll));

        if (visibleHeight <= 0.0F) {
            return;
        }

        VGRenderer.scissor(x, contentTop, width, visibleHeight);

        float cursor = contentTop - scroll;
        for (Module module : modules) {
            boolean expanded = module.isExpanded() && module.hasSettings();
            float expand = animate(expandAnimations, module, expanded ? 1.0F : 0.0F);
            float elementHeight = GlassTheme.MODULE_HEIGHT + expand * settingsHeight(module);

            boolean hovered = GlassTheme.inside(mouseX, mouseY, x, cursor, width, GlassTheme.MODULE_HEIGHT);
            float hover = animate(hoverAnimations, module, hovered ? 1.0F : 0.0F);

            Color nameColor = module.isEnabled() ? GlassTheme.accent() : GlassTheme.textSecondary();
            float size = GlassTheme.FONT_MODULE * (1.0F + (GlassTheme.MODULE_HOVER_SCALE - 1.0F) * hover);
            VGRenderer.text(module.getName(), x + 6.0F,
                    cursor + GlassTheme.MODULE_HEIGHT / 2.0F - size * 0.52F,
                    size, VGFont.FACE, 0.0F, 0.0F, nameColor);

            rows.add(new Row(TYPE_MODULE, module, null, x, cursor, width, GlassTheme.MODULE_HEIGHT));

            if (expand > 0.01F && module.hasSettings()) {
                float settingY = cursor + GlassTheme.MODULE_HEIGHT + GlassTheme.SETTING_SPACING;
                for (Setting setting : module.getSettings()) {
                    float rowHeight = rowHeight(setting);
                    float offsetX = (1.0F - expand) * SETTINGS_SLIDE;
                    float settingX = x + GlassTheme.SETTING_INDENT + offsetX;
                    float settingW = width - GlassTheme.SETTING_INDENT - 4.0F;
                    boolean settingHovered = GlassTheme.inside(mouseX, mouseY,
                            settingX, settingY, settingW, rowHeight);
                    renderSetting(setting, settingX, settingY, settingW, rowHeight,
                            settingHovered, GlassTheme.scaleAlpha(GlassTheme.textPrimary(), expand));
                    rows.add(new Row(rowType(setting), module, setting, settingX, settingY, settingW, rowHeight));
                    settingY += rowHeight + GlassTheme.SETTING_SPACING;
                }
            }

            cursor += elementHeight + GlassTheme.MODULE_SPACING;
        }

        VGRenderer.resetScissor();
    }

    private void renderSetting(Setting setting, float sx, float sy, float sw, float sh,
                               boolean hovered, Color fade) {
        float alpha = fade.getAlpha() / 255.0F;

        if (setting instanceof BooleanSetting bool) {
            if (hovered) {
                VGRenderer.roundedRect(sx, sy, sw, sh, GlassTheme.RADIUS_SM,
                        GlassTheme.scaleAlpha(GlassTheme.moduleHover(), 0.45F * alpha));
            }
            VGRenderer.text(setting.getName(), sx + GlassTheme.PADDING,
                    sy + GlassTheme.SETTING_HEIGHT / 2.0F - 3.5F,
                    GlassTheme.FONT_SETTING, VGFont.FACE, 0.0F, 0.0F,
                    GlassTheme.scaleAlpha(bool.get() ? GlassTheme.textPrimary() : GlassTheme.textSecondary(), alpha));

            float check = GlassTheme.CHECK_SIZE;
            float cx = sx + sw - GlassTheme.PADDING - check;
            float cy = sy + (GlassTheme.SETTING_HEIGHT - check) / 2.0F;
            if (bool.get()) {
                VGRenderer.circle(cx + check / 2.0F, cy + check / 2.0F, check / 2.0F,
                        GlassTheme.scaleAlpha(GlassTheme.accent(), alpha));
                drawCheck(cx + check / 2.0F, cy + check / 2.0F, GlassTheme.scaleAlpha(GlassTheme.textOnAccent(), alpha), alpha);
            } else {
                VGRenderer.circle(cx + check / 2.0F, cy + check / 2.0F, check / 2.0F,
                        GlassTheme.scaleAlpha(GlassTheme.interactiveMuted(), alpha));
            }
            return;
        }

        if (setting instanceof NumberSetting number) {
            if (hovered) {
                VGRenderer.roundedRect(sx, sy, sw, sh, GlassTheme.RADIUS_SM,
                        GlassTheme.scaleAlpha(GlassTheme.moduleHover(), 0.5F * alpha));
            }
            VGRenderer.text(setting.getName(), sx + GlassTheme.PADDING, sy + 3.0F,
                    GlassTheme.FONT_SETTING, VGFont.FACE, 0.0F, 0.0F,
                    GlassTheme.scaleAlpha(GlassTheme.textSecondary(), alpha));

            String value = number.display();
            float valueWidth = VGRenderer.textWidth(value, GlassTheme.FONT_SETTING, VGFont.FACE, 0.0F);
            VGRenderer.text(value, (sx + sw - GlassTheme.PADDING) - valueWidth, sy + 3.0F,
                    GlassTheme.FONT_SETTING, VGFont.FACE, 0.0F, 0.0F,
                    GlassTheme.scaleAlpha(draggingSlider == number ? GlassTheme.textPrimary() : GlassTheme.textMuted(), alpha));

            float trackX = sx + GlassTheme.PADDING;
            float trackW = sw - GlassTheme.PADDING * 2.0F;
            float trackY = sy + GlassTheme.SETTING_HEIGHT + 1.0F;
            float trackH = GlassTheme.TRACK_HEIGHT;
            VGRenderer.roundedRect(trackX, trackY, trackW, trackH, trackH / 2.0F,
                    GlassTheme.scaleAlpha(GlassTheme.interactiveMuted(), alpha));

            float fraction = number.normalized();
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

        if (setting instanceof ModeSetting mode) {
            if (hovered) {
                VGRenderer.roundedRect(sx, sy, sw, sh, GlassTheme.RADIUS_SM,
                        GlassTheme.scaleAlpha(GlassTheme.moduleHover(), 0.45F * alpha));
            }
            VGRenderer.text(setting.getName(), sx + GlassTheme.PADDING,
                    sy + GlassTheme.SETTING_HEIGHT / 2.0F - 3.5F,
                    GlassTheme.FONT_SETTING, VGFont.FACE, 0.0F, 0.0F,
                    GlassTheme.scaleAlpha(GlassTheme.textSecondary(), alpha));

            String label = mode.get();
            float pillPad = 4.0F;
            float pillWidth = VGRenderer.textWidth(label, GlassTheme.FONT_HINT, VGFont.FACE, 0.0F) + pillPad * 2.0F;
            float pillHeight = 10.0F;
            float pillX = (sx + sw - GlassTheme.PADDING) - pillWidth;
            float pillY = sy + (GlassTheme.SETTING_HEIGHT - pillHeight) / 2.0F;
            VGRenderer.roundedRect(pillX, pillY, pillWidth, pillHeight, pillHeight / 2.0F,
                    GlassTheme.scaleAlpha(GlassTheme.accent(), 0.9F * alpha));
            VGRenderer.textCentered(label, pillX + pillWidth / 2.0F, pillY + pillHeight / 2.0F,
                    GlassTheme.FONT_HINT, VGFont.FACE, GlassTheme.scaleAlpha(GlassTheme.textOnAccent(), alpha));
        }
    }

    private void drawCheck(float cx, float cy, Color color, float alpha) {
        VGRenderer.line(cx - 2.0F, cy, cx - 0.6F, cy + 1.5F, 1.1F, color);
        VGRenderer.line(cx - 0.6F, cy + 1.5F, cx + 2.0F, cy - 1.6F, 1.1F, color);
    }

    private static float rowHeight(Setting setting) {
        return setting instanceof NumberSetting ? GlassTheme.SLIDER_HEIGHT : GlassTheme.SETTING_HEIGHT;
    }

    private float expand(Module module) {
        if (!module.hasSettings()) {
            return 0.0F;
        }
        return expandAnimations.getOrDefault(module, module.isExpanded() ? 1.0F : 0.0F);
    }

    private static float animate(Map<Module, Float> map, Module module, float target) {
        float current = map.getOrDefault(module, target);
        float next = current + (target - current) * 0.3F;
        if (Math.abs(target - next) < 0.01F) {
            next = target;
        }
        map.put(module, next);
        return next;
    }

    private static int rowType(Setting setting) {
        if (setting instanceof BooleanSetting) {
            return TYPE_BOOL;
        }
        if (setting instanceof NumberSetting) {
            return TYPE_NUMBER;
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
    }

    public void mouseReleased() {
        draggingPanel = false;
        draggingSlider = null;
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

    private record Row(int type, Module module, Setting setting, float x, float y, float w, float h) {
    }
}
