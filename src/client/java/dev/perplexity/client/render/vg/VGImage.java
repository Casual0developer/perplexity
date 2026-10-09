package dev.perplexity.client.render.vg;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.awt.Color;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.LinkedHashMap;
import java.util.Map;

import static dev.perplexity.client.render.vg.VG.VG_IMAGE_GENERATE_MIPMAPS;
import static dev.perplexity.client.render.vg.VG.vgBeginPath;
import static dev.perplexity.client.render.vg.VG.vgCreateImageMem;
import static dev.perplexity.client.render.vg.VG.vgDeleteImage;
import static dev.perplexity.client.render.vg.VG.vgFill;
import static dev.perplexity.client.render.vg.VG.vgFillPaint;
import static dev.perplexity.client.render.vg.VG.vgImagePattern;
import static dev.perplexity.client.render.vg.VG.vgRect;
import static dev.perplexity.client.render.vg.VG.vgRoundedRect;

public final class VGImage {
    private static final int MAX_IMAGES = 48;
    private static final Map<String, Integer> IMAGES = new LinkedHashMap<>(32, 0.75F, true);

    private VGImage() {
    }

    private static InputStream openAssetStream(String path) {
        return VGImage.class.getClassLoader().getResourceAsStream(path);
    }

    public static synchronized int load(String path) {
        if (!VGContext.isValid()) {
            return -1;
        }
        Integer cached = IMAGES.get(path);
        if (cached != null) {
            return cached;
        }
        ByteBuffer buffer = null;
        try (InputStream input = openAssetStream(path)) {
            if (input == null) {
                return -1;
            }
            buffer = MemoryUtil.memAlloc(8192);
            ReadableByteChannel channel = Channels.newChannel(input);
            while (channel.read(buffer) != -1) {
                if (buffer.remaining() == 0) {
                    ByteBuffer bigger = MemoryUtil.memAlloc(buffer.capacity() * 2);
                    buffer.flip();
                    bigger.put(buffer);
                    MemoryUtil.memFree(buffer);
                    buffer = bigger;
                }
            }
            buffer.flip();
            return cacheImage(path, vgCreateImageMem(VGContext.handle(), VG_IMAGE_GENERATE_MIPMAPS, buffer));
        } catch (Exception ignored) {
            return -1;
        } finally {
            if (buffer != null) {
                MemoryUtil.memFree(buffer);
            }
        }
    }

    public static synchronized int loadBytes(String key, byte[] bytes) {
        if (!VGContext.isValid() || key == null || key.isBlank() || bytes == null || bytes.length == 0) {
            return -1;
        }
        Integer cached = IMAGES.get(key);
        if (cached != null) {
            return cached;
        }
        ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
        try {
            buffer.put(bytes).flip();
            return cacheImage(key, vgCreateImageMem(VGContext.handle(), VG_IMAGE_GENERATE_MIPMAPS, buffer));
        } catch (Throwable ignored) {
            return -1;
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    public static synchronized int wrapGlTexture(int glTextureId, int width, int height) {
        if (!VGContext.isValid() || glTextureId <= 0) {
            return -1;
        }
        String key = "gltex:" + glTextureId;
        Integer cached = IMAGES.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            int image = VG.vgCreateImageFromHandle(
                    VGContext.handle(), glTextureId, width, height, VG.VG_IMAGE_NODELETE);
            return cacheImage(key, image);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    public static boolean drawCropped(
            int image,
            float destX,
            float destY,
            float destSize,
            float srcX,
            float srcY,
            float srcRegion,
            float srcFullSize,
            Color tint
    ) {
        if (!VGRenderer.isInFrame() || image == -1 || image == 0 || destSize <= 0.0F) {
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            float scale = destSize / srcRegion;
            float ex = destX - srcX * scale;
            float ey = destY - srcY * scale;
            float extent = srcFullSize * scale;
            float alpha = tint.getAlpha() / 255.0F;
            VGPaint paint = vgImagePattern(VGContext.handle(), ex, ey, extent, extent, 0.0F, image, alpha, new VGPaint());
            vgBeginPath(VGContext.handle());
            vgRect(VGContext.handle(), destX, destY, destSize, destSize);
            vgFillPaint(VGContext.handle(), paint);
            vgFill(VGContext.handle());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int cacheImage(String key, int image) {
        if (image == -1 || image == 0) {
            return -1;
        }
        IMAGES.put(key, image);
        trimCache();
        return image;
    }

    private static void trimCache() {
        while (IMAGES.size() > MAX_IMAGES) {
            Map.Entry<String, Integer> eldest = IMAGES.entrySet().iterator().next();
            IMAGES.remove(eldest.getKey());
            delete(eldest.getValue());
        }
    }

    public static boolean draw(String path, float x, float y, float width, float height, Color tint) {
        int image = loadSized(path, () -> readAsset(path), width, height);
        return drawImage(image > 0 ? image : load(path), x, y, width, height, 0.0F, tint);
    }

    public static boolean drawBytesRounded(String key, byte[] bytes, float x, float y, float width, float height, float radius, Color tint) {
        int image = loadSized(key, () -> bytes, width, height);
        return drawImage(image > 0 ? image : loadBytes(key, bytes), x, y, width, height, radius, tint);
    }

    private static final Map<String, byte[]> ASSET_BYTES = new LinkedHashMap<>(16, 0.75F, true);
    private static final Map<String, int[]> SOURCE_SIZES = new java.util.HashMap<>();

    private static byte[] readAsset(String path) {
        byte[] cached = ASSET_BYTES.get(path);
        if (cached != null) {
            return cached;
        }
        try (InputStream input = openAssetStream(path)) {
            if (input == null) {
                return null;
            }
            byte[] bytes = input.readAllBytes();
            ASSET_BYTES.put(path, bytes);
            while (ASSET_BYTES.size() > 32) {
                ASSET_BYTES.remove(ASSET_BYTES.keySet().iterator().next());
            }
            return bytes;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static synchronized int loadSized(String key, java.util.function.Supplier<byte[]> source, float width, float height) {
        if (!VGContext.isValid() || key == null || !VGRenderer.isInFrame()) {
            return -1;
        }
        float[] xform = new float[6];
        VG.vgCurrentTransform(VGContext.handle(), xform);
        float sx = bucket((float) Math.hypot(xform[0], xform[1]));
        float sy = bucket((float) Math.hypot(xform[2], xform[3]));
        int targetWidth = Math.max(1, (int) Math.ceil(width * sx));
        int targetHeight = Math.max(1, (int) Math.ceil(height * sy));
        String sizedKey = key + "@" + targetWidth + "x" + targetHeight;
        Integer cached = IMAGES.get(sizedKey);
        if (cached != null) {
            return cached;
        }
        int[] known = SOURCE_SIZES.get(key);
        if (known != null && targetWidth * 1.15F >= known[0] && targetHeight * 1.15F >= known[1]) {
            return -1;
        }

        byte[] bytes = source.get();
        if (bytes == null || bytes.length == 0) {
            return -1;
        }
        ByteBuffer encoded = MemoryUtil.memAlloc(bytes.length);
        ByteBuffer decoded = null;
        ByteBuffer resized = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            encoded.put(bytes).flip();
            java.nio.IntBuffer w = stack.mallocInt(1);
            java.nio.IntBuffer h = stack.mallocInt(1);
            java.nio.IntBuffer n = stack.mallocInt(1);
            decoded = org.lwjgl.stb.STBImage.stbi_load_from_memory(encoded, w, h, n, 4);
            if (decoded == null) {
                return -1;
            }
            int sourceWidth = w.get(0);
            int sourceHeight = h.get(0);
            SOURCE_SIZES.put(key, new int[]{sourceWidth, sourceHeight});
            if (targetWidth * 1.15F >= sourceWidth && targetHeight * 1.15F >= sourceHeight) {
                return -1;
            }
            resized = MemoryUtil.memAlloc(targetWidth * targetHeight * 4);
            boolean ok = org.lwjgl.stb.STBImageResize.stbir_resize_uint8_generic(
                    decoded, sourceWidth, sourceHeight, 0,
                    resized, targetWidth, targetHeight, 0,
                    4, 3, 0,
                    org.lwjgl.stb.STBImageResize.STBIR_EDGE_CLAMP,
                    org.lwjgl.stb.STBImageResize.STBIR_FILTER_CATMULLROM,
                    org.lwjgl.stb.STBImageResize.STBIR_COLORSPACE_SRGB);
            if (!ok) {
                return -1;
            }
            return cacheImage(sizedKey, VG.vgCreateImageRGBA(VGContext.handle(), targetWidth, targetHeight, 0, resized));
        } catch (Throwable ignored) {
            return -1;
        } finally {
            MemoryUtil.memFree(encoded);
            if (decoded != null) {
                org.lwjgl.stb.STBImage.stbi_image_free(decoded);
            }
            if (resized != null) {
                MemoryUtil.memFree(resized);
            }
        }
    }

    private static float bucket(float scale) {
        return Math.max(0.25F, (float) Math.ceil(scale * 4.0F - 0.05F) / 4.0F);
    }

    private static boolean drawImage(int image, float x, float y, float width, float height, float radius, Color tint) {
        if (!VGRenderer.isInFrame() || image == -1 || image == 0 || width <= 0.0F || height <= 0.0F) {
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            float alpha = tint.getAlpha() / 255.0F;
            VGPaint paint = vgImagePattern(VGContext.handle(), x, y, width, height, 0.0F, image, alpha, new VGPaint());
            vgBeginPath(VGContext.handle());
            if (radius > 0.0F) {
                vgRoundedRect(VGContext.handle(), x, y, width, height, radius);
            } else {
                vgRect(VGContext.handle(), x, y, width, height);
            }
            vgFillPaint(VGContext.handle(), paint);
            vgFill(VGContext.handle());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static synchronized void remove(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        delete(IMAGES.remove(key));
        SOURCE_SIZES.remove(key);
        String prefix = key + "@";
        java.util.Iterator<Map.Entry<String, Integer>> iterator = IMAGES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            if (entry.getKey().startsWith(prefix)) {
                delete(entry.getValue());
                iterator.remove();
            }
        }
    }

    public static synchronized void clear() {
        for (Integer image : IMAGES.values()) {
            delete(image);
        }
        IMAGES.clear();
        SOURCE_SIZES.clear();
    }

    private static void delete(Integer image) {
        if (VGContext.isValid() && image != null && image > 0) {
            try {
                vgDeleteImage(VGContext.handle(), image);
            } catch (Throwable ignored) {
            }
        }
    }
}