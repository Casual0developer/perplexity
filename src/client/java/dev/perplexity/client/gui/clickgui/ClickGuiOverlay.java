package dev.perplexity.client.gui.clickgui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;

import dev.perplexity.Perplexity;
import dev.perplexity.client.gui.auth.AuthScreen;

import net.minecraft.client.Minecraft;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Drives the NanoVG ClickGUI outside of Minecraft's collected GUI pass.
 *
 * <p>In 1.21.9+ {@code Screen.render} no longer issues GPU work: it only records draw state that
 * {@code GuiRenderer.render} submits later against {@code Minecraft.getMainRenderTarget()}. NanoVG
 * issues immediate-mode OpenGL, so drawing from inside {@code Screen.render} writes to whatever
 * framebuffer happens to be bound and never reaches the presented image. Instead we open our own
 * render pass on the main render target once Minecraft has finished its GUI submission, which binds
 * that framebuffer, and draw the menu into it. The pass keeps the target bound for the duration of
 * drawing and is closed immediately after.</p>
 */
public final class ClickGuiOverlay {
    private ClickGuiOverlay() {
    }

    private static boolean reportedFailure;

    public static void render() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof ClickGuiScreen)
                && !(minecraft.screen instanceof AuthScreen)) {
            return;
        }

        RenderTarget target = minecraft.getMainRenderTarget();
        RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "perplexity-clickgui",
                        target.getColorTextureView(),
                        OptionalInt.empty(),
                        null,
                        OptionalDouble.empty());
        try {
            if (minecraft.screen instanceof ClickGuiScreen clickGui) {
                clickGui.drawVg();
            } else if (minecraft.screen instanceof AuthScreen auth) {
                auth.drawVg();
            }
        } catch (Throwable throwable) {
            // A broken menu must not take the game down; surface the first failure once so it is
            // diagnosable in the log instead of silently rendering nothing.
            if (!reportedFailure) {
                reportedFailure = true;
                Perplexity.LOGGER.error("ClickGUI render failed", throwable);
            }
        } finally {
            pass.close();
        }
    }
}
