package dev.perplexity.client.gui;

import dev.perplexity.client.gui.clickgui.GlassTheme;
import dev.perplexity.client.impl.modules.ui.MenuKeyModule;
import dev.perplexity.client.impl.modules.ui.PopupsModule;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.render.vg.VGFont;
import dev.perplexity.client.render.vg.VGRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Bottom-right toggle toasts. At most four show at once, newest at the bottom; each slides in
 * from the right edge and fades out at the end of its life. Rendered in the same reference
 * space as the ClickGUI so toasts match it at any GUI scale.
 */
public final class Toasts {
    private static final int MAX_VISIBLE = 4;
    private static final long LIFETIME_MS = 3500L;
    private static final long SLIDE_MS = 380L;
    private static final long FADE_MS = 600L;

    private static final float TOAST_WIDTH = 168.0F;
    private static final float TOAST_HEIGHT = 28.0F;
    private static final float MARGIN = 12.0F;
    private static final float STACK_GAP = 6.0F;
    private static final float PILL_HEIGHT = 16.0F;
    private static final float NAME_SIZE = 9.0F;
    private static final float PILL_SIZE = 8.0F;

    private static final SoundEvent CLICK =
            SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("perplexity", "click"));

    private record Toast(Module module, String notice, boolean on, long born) {
    }

    private static final List<Toast> queue = new ArrayList<>();

    private Toasts() {
    }

    public static void push(Module module, boolean on) {
        if (module == null || !PopupsModule.enabled()) {
            return;
        }
        queue.add(new Toast(module, null, on, System.currentTimeMillis()));
        while (queue.size() > MAX_VISIBLE) {
            queue.remove(0);
        }
        if (on) {
            playClick();
        }
    }

    /** Plain notice text; only ever innocent words, never module names. */
    public static void pushNotice(String notice) {
        if (notice == null || !PopupsModule.enabled()) {
            return;
        }
        queue.add(new Toast(null, notice, false, System.currentTimeMillis()));
        while (queue.size() > MAX_VISIBLE) {
            queue.remove(0);
        }
    }

    private static void playClick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null) {
            return;
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(CLICK, 1.0F));
    }

    public static void clear() {
        queue.clear();
    }

    public static void render() {
        long now = System.currentTimeMillis();
        queue.removeIf(toast -> now - toast.born() > LIFETIME_MS);
        if (queue.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() == null) {
            return;
        }
        float unit = (float) (minecraft.getWindow().getGuiScale() / 2.0F);
        float uiWidth = minecraft.getWindow().getGuiScaledWidth() * unit;
        float uiHeight = minecraft.getWindow().getGuiScaledHeight() * unit;
        if (!VGRenderer.beginFrame()) {
            return;
        }
        VGFont.setFamily(MenuKeyModule.fontId());
        try {
            VGRenderer.save();
            VGRenderer.scale(2.0F, 2.0F);

            int count = queue.size();
            for (int i = 0; i < count; i++) {
                Toast toast = queue.get(i);
                long age = now - toast.born();
                float slide = clamp01((float) age / SLIDE_MS);
                float ease = 1.0F - (1.0F - slide) * (1.0F - slide) * (1.0F - slide);
                float alpha = age > LIFETIME_MS - FADE_MS
                        ? (float) (LIFETIME_MS - age) / FADE_MS
                        : 1.0F;
                alpha = clamp01(alpha);

                float x = uiWidth - MARGIN - TOAST_WIDTH + (1.0F - ease) * TOAST_WIDTH;
                float y = uiHeight - MARGIN - (count - i) * (TOAST_HEIGHT + STACK_GAP) + STACK_GAP;
                String label = toast.module() != null
                        ? toast.module().getDisplayName()
                        : toast.notice();

                VGRenderer.roundedRect(x, y, TOAST_WIDTH, TOAST_HEIGHT, GlassTheme.RADIUS_SM,
                        GlassTheme.scaleAlpha(GlassTheme.surfacePanel(), alpha));
                if (toast.on()) {
                    VGRenderer.roundedRect(x, y, 2.5F, TOAST_HEIGHT, 1.0F,
                            GlassTheme.scaleAlpha(GlassTheme.accent(), alpha));
                }
                VGRenderer.textLeft(label, x + 10.0F, y + TOAST_HEIGHT / 2.0F,
                        NAME_SIZE, VGFont.face(),
                        GlassTheme.scaleAlpha(GlassTheme.textPrimary(), alpha));

                String state = toast.on() ? "ON" : "OFF";
                float pillPad = 8.0F;
                float pillWidth = VGRenderer.textWidth(state,
                        PILL_SIZE, VGFont.face(), 0.0F) + pillPad * 2.0F;
                float pillX = x + TOAST_WIDTH - 8.0F - pillWidth;
                float pillY = y + (TOAST_HEIGHT - PILL_HEIGHT) / 2.0F;
                VGRenderer.roundedRect(pillX, pillY, pillWidth, PILL_HEIGHT, GlassTheme.RADIUS_SM,
                        toast.on()
                                ? GlassTheme.scaleAlpha(GlassTheme.accent(), 0.95F * alpha)
                                : GlassTheme.scaleAlpha(GlassTheme.interactiveMuted(), 0.7F * alpha));
                VGRenderer.textCentered(state, pillX + pillWidth / 2.0F, pillY + PILL_HEIGHT / 2.0F,
                        PILL_SIZE, VGFont.face(),
                        GlassTheme.scaleAlpha(toast.on()
                                ? GlassTheme.textOnAccent() : GlassTheme.textMuted(), alpha));
            }

            VGRenderer.restore();
        } finally {
            VGRenderer.endFrame();
        }
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
