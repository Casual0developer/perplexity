package dev.perplexity.client.render.vg;

import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static dev.perplexity.client.render.vg.VG.vgCreateFontMem;
import static dev.perplexity.client.render.vg.VG.vgFindFont;

public final class VGFont {
    public static final String FACE = "inter";
    public static final String MEDIUM = "inter-medium";
    public static final String SEMIBOLD = "inter-semibold";

    private static final String REGULAR_PATH = "assets/perplexity/font/inter.ttf";
    private static final String MEDIUM_PATH = "assets/perplexity/font/inter-medium.ttf";
    private static final String SEMIBOLD_PATH = "assets/perplexity/font/inter-semibold.ttf";

    private static final List<ByteBuffer> BUFFERS = new ArrayList<>();
    private static boolean loaded;
    private static boolean mediumLoaded;
    private static boolean semiboldLoaded;

    private VGFont() {
    }

    public static void load() {
        if (loaded && available()) {
            return;
        }

        unload();

        if (!VGContext.isValid()) {
            return;
        }

        loaded = register(FACE, REGULAR_PATH);
        mediumLoaded = register(MEDIUM, MEDIUM_PATH);
        semiboldLoaded = register(SEMIBOLD, SEMIBOLD_PATH);
    }

    private static boolean register(String face, String path) {
        ByteBuffer buffer = read(path);

        if (buffer == null) {
            return false;
        }

        int result = vgCreateFontMem(VGContext.handle(), face, buffer, false);

        if (result != -1) {
            BUFFERS.add(buffer);
            return true;
        }

        MemoryUtil.memFree(buffer);
        return false;
    }

    public static void init() {
        load();
    }

    public static void ensureLoaded() {
        load();
    }

    public static boolean available() {
        return VGContext.isValid() && vgFindFont(VGContext.handle(), FACE) != -1;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static String face() {
        return FACE;
    }

    public static String getFace() {
        return FACE;
    }

    public static String mediumFace() {
        return mediumLoaded ? MEDIUM : FACE;
    }

    public static String semiboldFace() {
        return semiboldLoaded ? SEMIBOLD : FACE;
    }

    public static boolean hasMedium() {
        return mediumLoaded;
    }

    public static boolean hasSemibold() {
        return semiboldLoaded;
    }

    public static void unload() {
        loaded = false;
        mediumLoaded = false;
        semiboldLoaded = false;

        for (ByteBuffer buffer : BUFFERS) {
            try {
                MemoryUtil.memFree(buffer);
            } catch (Throwable ignored) {
            }
        }

        BUFFERS.clear();
    }

    private static ByteBuffer read(String path) {
        try (InputStream input = VGFont.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                return null;
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int length;

            while ((length = input.read(chunk)) != -1) {
                output.write(chunk, 0, length);
            }

            byte[] bytes = output.toByteArray();
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes);
            buffer.flip();

            return buffer;
        } catch (Exception ignored) {
            return null;
        }
    }
}
