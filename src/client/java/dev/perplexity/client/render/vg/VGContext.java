package dev.perplexity.client.render.vg;

import com.mojang.blaze3d.systems.RenderSystem;

import static dev.perplexity.client.render.vg.VG.VG_ANTIALIAS;
import static dev.perplexity.client.render.vg.VG.VG_STENCIL_STROKES;
import static dev.perplexity.client.render.vg.VG.vgCreate;
import static dev.perplexity.client.render.vg.VG.vgDelete;

public final class VGContext {
    private static long handle;
    private static boolean initialized;

    private VGContext() {
    }

    public static void init() {
        if (initialized && isValid()) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        if (handle != 0L && handle != -1L) {
            cleanup();
        }
        try {
            handle = vgCreate(VG_ANTIALIAS | VG_STENCIL_STROKES);
            if (!isValid()) {
                handle = vgCreate(VG_ANTIALIAS);
            }
        } catch (Throwable ignored) {
            handle = 0L;
        }
        if (!isValid()) {
            return;
        }
        initialized = true;
        VGFont.load();
        try {
            VG.vgWarmup(handle, VGFont.face(), VGFont.mediumFace(), VGFont.semiboldFace());
        } catch (Throwable ignored) {
        }
    }

    public static void cleanup() {
        long oldHandle = handle;
        handle = 0L;
        initialized = false;
        VGFont.unload();
        VGImage.clear();
        if (oldHandle != 0L && oldHandle != -1L) {
            try {
                vgDelete(oldHandle);
            } catch (Throwable ignored) {
            }
        }
    }

    public static long handle() {
        return handle;
    }

    public static boolean isValid() {
        return handle != 0L && handle != -1L;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
