package dev.perplexity.client.gui.clickgui;

import dev.perplexity.client.render.vg.VGRenderer;

import java.awt.Color;
import java.util.Random;

/**
 * Animated backdrop for the ClickGUI: two soft violet washes plus a slow drift of purple flakes
 * falling behind the panels.
 *
 * <p>Everything is drawn immediate-mode with the ported NanoVG renderer, so this runs once per
 * rendered frame while the screen is open. The flake field is allocated once per resize and the
 * per-flake loop allocates nothing; shade colours are picked from a cached array rather than built
 * per particle. Motion is driven by wall-clock deltas rather than frames, so the fall speed does not
 * change with framerate, and the delta is clamped so a stalled or paused frame cannot teleport the
 * field.</p>
 */
final class Backdrop {
    private static final int MIN_FLAKES = 50;
    private static final int MAX_FLAKES = 110;
    /** Roughly one flake per this many square GUI units, clamped to the range above. */
    private static final float AREA_PER_FLAKE = 9000.0F;
    private static final float MAX_DELTA_SECONDS = 0.05F;
    private static final float SPAWN_MARGIN = 24.0F;

    private final Random random = new Random(0x5EEDF00DL);

    private Flake[] flakes = new Flake[0];
    private float width = -1.0F;
    private float height = -1.0F;
    private long lastFrameNanos;

    void render(float width, float height) {
        if (width != this.width || height != this.height) {
            this.width = width;
            this.height = height;
            seed();
        }

        float delta = deltaSeconds();
        drawWashes();
        drawFlakes(delta);
    }

    private void drawWashes() {
        float span = Math.max(width, height);
        VGRenderer.radialGradient(width * 0.20F, -span * 0.28F, 0.0F, span * 0.60F,
                GlassTheme.glow(), GlassTheme.glowFade());
        VGRenderer.radialGradient(width * 0.84F, height + span * 0.22F, 0.0F, span * 0.55F,
                GlassTheme.glowSoft(), GlassTheme.glowFade());
    }

    private void drawFlakes(float delta) {
        Color[] shades = GlassTheme.snowShades();
        for (Flake flake : flakes) {
            flake.y += flake.fallSpeed * delta;
            flake.phase += flake.swaySpeed * delta;

            if (flake.y - flake.radius > height) {
                flake.y = -flake.radius - random.nextFloat() * SPAWN_MARGIN;
                flake.baseX = random.nextFloat() * (width + SPAWN_MARGIN * 2.0F) - SPAWN_MARGIN;
            }

            float x = flake.baseX + (float) Math.sin(flake.phase) * flake.sway;
            float twinkle = 0.5F + 0.5F * (float) Math.sin(flake.phase * flake.twinkleSpeed);
            int shade = Math.min(shades.length - 1, (int) (twinkle * shades.length));
            VGRenderer.circle(x, flake.y, flake.radius, shades[shade]);
        }
    }

    /** Rebuilds the field, spreading the first generation over the whole screen so it starts full. */
    private void seed() {
        int count = Math.min(MAX_FLAKES, Math.max(MIN_FLAKES,
                (int) (width * height / AREA_PER_FLAKE)));
        flakes = new Flake[count];
        for (int i = 0; i < count; i++) {
            flakes[i] = spawn(random.nextFloat() * height);
        }
    }

    private Flake spawn(float y) {
        Flake flake = new Flake();
        flake.baseX = random.nextFloat() * (width + SPAWN_MARGIN * 2.0F) - SPAWN_MARGIN;
        flake.y = y;
        flake.radius = 0.7F + random.nextFloat() * 1.9F;
        flake.fallSpeed = 18.0F + random.nextFloat() * 42.0F;
        flake.sway = 3.0F + random.nextFloat() * 8.0F;
        flake.swaySpeed = 0.4F + random.nextFloat() * 0.8F;
        flake.phase = random.nextFloat() * (float) Math.PI * 2.0F;
        flake.twinkleSpeed = 0.8F + random.nextFloat() * 1.6F;
        return flake;
    }

    private float deltaSeconds() {
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 0.0F : (now - lastFrameNanos) / 1.0E9F;
        lastFrameNanos = now;
        return Math.max(0.0F, Math.min(MAX_DELTA_SECONDS, delta));
    }

    /** One drifting flake. Positions are in GUI units, matching the rest of the ClickGUI. */
    private static final class Flake {
        private float baseX;
        private float y;
        private float radius;
        private float fallSpeed;
        private float sway;
        private float swaySpeed;
        private float phase;
        private float twinkleSpeed;
    }
}
