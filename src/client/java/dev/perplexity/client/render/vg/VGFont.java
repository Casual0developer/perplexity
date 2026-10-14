package dev.perplexity.client.render.vg;

import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static dev.perplexity.client.render.vg.VG.vgCreateFontMem;
import static dev.perplexity.client.render.vg.VG.vgFindFont;

/**
 * Menu typefaces. Inter ships regular/medium/semibold; the other families ship regular only
 * and fall back to it for the heavier weights. The active family switches live through
 * {@link #setFamily(String)} — every text call resolves through here, so nothing else
 * needs to know which font is on.
 */
public final class VGFont {
    public static final String FACE = "inter";
    public static final String MEDIUM = "inter-medium";
    public static final String SEMIBOLD = "inter-semibold";

    public static final String MONTSERRAT = "montserrat";
    public static final String NUNITO = "nunito";
    public static final String MANROPE = "manrope";
    public static final String JETBRAINS_MONO = "jetbrainsmono";

    private static final String REGULAR_PATH = "assets/perplexity/font/inter.ttf";
    private static final String MEDIUM_PATH = "assets/perplexity/font/inter-medium.ttf";
    private static final String SEMIBOLD_PATH = "assets/perplexity/font/inter-semibold.ttf";
    private static final String MONTSERRAT_PATH = "assets/perplexity/font/montserrat.ttf";
    private static final String NUNITO_PATH = "assets/perplexity/font/nunito.ttf";
    private static final String MANROPE_PATH = "assets/perplexity/font/manrope.ttf";
    private static final String JETBRAINS_MONO_PATH = "assets/perplexity/font/jetbrainsmono.ttf";

    private static final List<ByteBuffer> BUFFERS = new ArrayList<>();
    private static boolean loaded;
    private static boolean mediumLoaded;
    private static boolean semiboldLoaded;
    private static boolean montserratLoaded;
    private static boolean nunitoLoaded;
    private static boolean manropeLoaded;
    private static boolean jetbrainsMonoLoaded;

    private static String family = FACE;

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
        montserratLoaded = register(MONTSERRAT, MONTSERRAT_PATH);
        nunitoLoaded = register(NUNITO, NUNITO_PATH);
        manropeLoaded = register(MANROPE, MANROPE_PATH);
        jetbrainsMonoLoaded = register(JETBRAINS_MONO, JETBRAINS_MONO_PATH);
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

    /** Switches the active family; unknown ids keep the current one. */
    public static void setFamily(String id) {
        if (id == null) {
            return;
        }
        switch (id) {
            case MONTSERRAT -> {
                if (montserratLoaded) {
                    family = MONTSERRAT;
                }
            }
            case NUNITO -> {
                if (nunitoLoaded) {
                    family = NUNITO;
                }
            }
            case MANROPE -> {
                if (manropeLoaded) {
                    family = MANROPE;
                }
            }
            case JETBRAINS_MONO -> {
                if (jetbrainsMonoLoaded) {
                    family = JETBRAINS_MONO;
                }
            }
            default -> family = FACE;
        }
    }

    public static String face() {
        return family;
    }

    public static String getFace() {
        return family;
    }

    public static String mediumFace() {
        if (FACE.equals(family)) {
            return mediumLoaded ? MEDIUM : FACE;
        }
        return family;
    }

    public static String semiboldFace() {
        if (FACE.equals(family)) {
            return semiboldLoaded ? SEMIBOLD : FACE;
        }
        return family;
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
        montserratLoaded = false;
        nunitoLoaded = false;
        manropeLoaded = false;
        jetbrainsMonoLoaded = false;
        family = FACE;

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
