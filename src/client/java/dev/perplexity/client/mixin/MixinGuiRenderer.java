package dev.perplexity.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;

import dev.perplexity.client.gui.clickgui.ClickGuiOverlay;
import dev.perplexity.client.gui.Toasts;

import net.minecraft.client.gui.render.GuiRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the NanoVG ClickGUI right after Minecraft has submitted the collected GUI to the main render
 * target, so that immediate-mode GL drawn by the ported renderer ends up in the presented frame.
 */
@Mixin(GuiRenderer.class)
public class MixinGuiRenderer {
    @Inject(method = "render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("TAIL"))
    private void perplexity$renderClickGui(GpuBufferSlice buffer, CallbackInfo callbackInfo) {
        ClickGuiOverlay.render();
        Toasts.render();
    }
}
