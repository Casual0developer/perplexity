package dev.perplexity.client.render.vg;

import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.stb.STBTruetype.stbtt_FindGlyphIndex;
import static org.lwjgl.stb.STBTruetype.stbtt_GetFontOffsetForIndex;
import static org.lwjgl.stb.STBTruetype.stbtt_GetFontVMetrics;
import static org.lwjgl.stb.STBTruetype.stbtt_GetGlyphBitmapBox;
import static org.lwjgl.stb.STBTruetype.stbtt_GetGlyphHMetrics;
import static org.lwjgl.stb.STBTruetype.stbtt_GetGlyphKernAdvance;
import static org.lwjgl.stb.STBTruetype.stbtt_InitFont;
import static org.lwjgl.stb.STBTruetype.stbtt_MakeGlyphBitmap;
import static org.lwjgl.stb.STBTruetype.stbtt_ScaleForMappingEmToPixels;

final class VGFonts {
    static final int INVALID = -1;

    static final int ATLAS_SIZE = 2048;

    private static final int APREC = 16;
    private static final int ZPREC = 7;

    static final class Font {
        final String name;
        final ByteBuffer data;
        final boolean freeData;
        final STBTTFontinfo info;
        final float ascender;
        final float descender;
        final float lineh;
        final Map<Long, Glyph> glyphs = new HashMap<>();

        Font(String name, ByteBuffer data, boolean freeData, STBTTFontinfo info, float ascender, float descender, float lineh) {
            this.name = name;
            this.data = data;
            this.freeData = freeData;
            this.info = info;
            this.ascender = ascender;
            this.descender = descender;
            this.lineh = lineh;
        }
    }

    static final class Glyph {
        int index;
        Font renderFont;
        float scale;
        int x0;
        int y0;
        int x1;
        int y1;
        int xadv;
        int xoff;
        int yoff;
        boolean hasBitmap;
    }

    static final class Quad {
        float x0;
        float y0;
        float s0;
        float t0;
        float x1;
        float y1;
        float s1;
        float t1;
    }

    static final class TextIter {
        Font font;
        int isize;
        int iblur;
        float scale;
        float x;
        float y;
        float nextx;
        float nexty;
        float spacing;
        String text;
        int next;
        int prevGlyphIndex;
        Font prevFont;
        boolean bitmapRequired;
    }

    private final List<Font> fonts = new ArrayList<>();

    int font;
    float size;
    float spacing;
    float blur;
    int align;

    final ByteBuffer atlas;
    final float itw;
    final float ith;
    private int[] nodeX = new int[256];
    private int[] nodeY = new int[256];
    private int[] nodeW = new int[256];
    private int nnodes;
    final int[] dirty = new int[4];
    int[] dirtyRects = new int[4 * 64];
    int ndirtyRects;

    private ByteBuffer scratch = MemoryUtil.memAlloc(64 * 64);
    private final int[] i0 = new int[1];
    private final int[] i1 = new int[1];
    private final int[] i2 = new int[1];
    private final int[] i3 = new int[1];
    private final int[] position = new int[2];

    VGFonts() {
        atlas = MemoryUtil.memCalloc(ATLAS_SIZE * ATLAS_SIZE);
        itw = 1.0F / ATLAS_SIZE;
        ith = 1.0F / ATLAS_SIZE;
        resetDirty();
        resetPacker();
    }

    void delete() {
        for (Font entry : fonts) {
            entry.info.free();
            if (entry.freeData) {
                MemoryUtil.memFree(entry.data);
            }
        }
        fonts.clear();
        for (Font fallback : fallbacks) {
            fallback.info.free();
        }
        fallbacks.clear();
        MemoryUtil.memFree(atlas);
        MemoryUtil.memFree(scratch);
    }

    private static final String[] FALLBACK_FONTS = fallbackCandidates();
    private final List<Font> fallbacks = new ArrayList<>();
    private int nextFallback;

    private static String[] fallbackCandidates() {
        List<String> paths = new ArrayList<>();
        String windir = System.getenv("WINDIR");
        String windows = (windir == null ? "C:\\Windows" : windir) + "\\Fonts\\";
        for (String name : new String[]{
                "segoeui.ttf", "YuGothM.ttc", "msyh.ttc", "msjh.ttc", "malgun.ttf", "meiryo.ttc",
                "msgothic.ttc", "simsun.ttc", "Nirmala.ttf", "LeelawUI.ttf", "ebrima.ttf", "gadugi.ttf",
                "seguisym.ttf", "arialuni.ttf", "tahoma.ttf"}) {
            paths.add(windows + name);
        }
        for (String path : new String[]{
                "/System/Library/Fonts/Hiragino Sans GB.ttc", "/System/Library/Fonts/PingFang.ttc",
                "/System/Library/Fonts/AppleSDGothicNeo.ttc", "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
                "/Library/Fonts/Arial Unicode.ttf",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/google-noto-cjk/NotoSansCJK-Regular.ttc", "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
                "/usr/share/fonts/noto/NotoSans-Regular.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/TTF/DejaVuSans.ttf"}) {
            paths.add(path);
        }
        return paths.toArray(new String[0]);
    }

    private Font findFallback(int codepoint, int[] out) {
        for (Font fallback : fallbacks) {
            int index = stbtt_FindGlyphIndex(fallback.info, codepoint);
            if (index != 0) {
                out[0] = index;
                return fallback;
            }
        }
        while (nextFallback < FALLBACK_FONTS.length) {
            Font loaded = loadFallback(FALLBACK_FONTS[nextFallback++]);
            if (loaded == null) {
                continue;
            }
            fallbacks.add(loaded);
            int index = stbtt_FindGlyphIndex(loaded.info, codepoint);
            if (index != 0) {
                out[0] = index;
                return loaded;
            }
        }
        return null;
    }

    private static Font loadFallback(String path) {
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(java.nio.file.Path.of(path))) {
            ByteBuffer data = channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, channel.size());
            int offset = stbtt_GetFontOffsetForIndex(data, 0);
            STBTTFontinfo info = STBTTFontinfo.malloc();
            if (offset < 0 || !stbtt_InitFont(info, data, offset)) {
                info.free();
                return null;
            }
            return new Font(path, data, false, info, 0.8F, -0.2F, 1.0F);
        } catch (Throwable throwable) {
            return null;
        }
    }

    int addFont(String name, ByteBuffer data, boolean freeData) {
        STBTTFontinfo info = STBTTFontinfo.malloc();
        if (!stbtt_InitFont(info, data)) {
            info.free();
            if (freeData) {
                MemoryUtil.memFree(data);
            }
            return INVALID;
        }
        int[] ascent = new int[1];
        int[] descent = new int[1];
        int[] lineGap = new int[1];
        stbtt_GetFontVMetrics(info, ascent, descent, lineGap);
        int a = ascent[0] + lineGap[0];
        int fh = a - descent[0];
        float ascender = (float) a / (float) fh;
        float descender = (float) descent[0] / (float) fh;
        fonts.add(new Font(name, data, freeData, info, ascender, descender, ascender - descender));
        return fonts.size() - 1;
    }

    int findFont(String name) {
        if (name == null) {
            return INVALID;
        }
        for (int i = 0; i < fonts.size(); i++) {
            if (fonts.get(i).name.equals(name)) {
                return i;
            }
        }
        return INVALID;
    }

    private Font current() {
        return font < 0 || font >= fonts.size() ? null : fonts.get(font);
    }

    private static float pixelHeightScale(Font font, float size) {
        return stbtt_ScaleForMappingEmToPixels(font.info, size);
    }

    private void resetDirty() {
        dirty[0] = ATLAS_SIZE;
        dirty[1] = ATLAS_SIZE;
        dirty[2] = 0;
        dirty[3] = 0;
        ndirtyRects = 0;
    }

    boolean isDirty() {
        return dirty[0] < dirty[2] && dirty[1] < dirty[3];
    }

    void clearDirty() {
        resetDirty();
    }

    void resetAtlas() {
        for (Font entry : fonts) {
            entry.glyphs.clear();
        }
        resetPacker();
        resetDirty();
    }

    private void resetPacker() {
        nnodes = 1;
        nodeX[0] = 0;
        nodeY[0] = 0;
        nodeW[0] = ATLAS_SIZE;
    }

    private void insertNode(int idx, int x, int y, int w) {
        if (nnodes + 1 > nodeX.length) {
            int size = nodeX.length * 2;
            nodeX = java.util.Arrays.copyOf(nodeX, size);
            nodeY = java.util.Arrays.copyOf(nodeY, size);
            nodeW = java.util.Arrays.copyOf(nodeW, size);
        }
        System.arraycopy(nodeX, idx, nodeX, idx + 1, nnodes - idx);
        System.arraycopy(nodeY, idx, nodeY, idx + 1, nnodes - idx);
        System.arraycopy(nodeW, idx, nodeW, idx + 1, nnodes - idx);
        nodeX[idx] = x;
        nodeY[idx] = y;
        nodeW[idx] = w;
        nnodes++;
    }

    private void removeNode(int idx) {
        System.arraycopy(nodeX, idx + 1, nodeX, idx, nnodes - idx - 1);
        System.arraycopy(nodeY, idx + 1, nodeY, idx, nnodes - idx - 1);
        System.arraycopy(nodeW, idx + 1, nodeW, idx, nnodes - idx - 1);
        nnodes--;
    }

    private int rectFits(int i, int w, int h) {
        int x = nodeX[i];
        int y = nodeY[i];
        if (x + w > ATLAS_SIZE) {
            return -1;
        }
        int spaceLeft = w;
        while (spaceLeft > 0) {
            if (i == nnodes) {
                return -1;
            }
            y = Math.max(y, nodeY[i]);
            if (y + h > ATLAS_SIZE) {
                return -1;
            }
            spaceLeft -= nodeW[i];
            ++i;
        }
        return y;
    }

    private boolean allocRect(int w, int h, int[] out) {
        int besth = ATLAS_SIZE;
        int bestw = ATLAS_SIZE;
        int besti = -1;
        int bestx = -1;
        int besty = -1;
        for (int i = 0; i < nnodes; i++) {
            int y = rectFits(i, w, h);
            if (y != -1 && (y + h < besth || (y + h == besth && nodeW[i] < bestw))) {
                besti = i;
                bestw = nodeW[i];
                besth = y + h;
                bestx = nodeX[i];
                besty = y;
            }
        }
        if (besti == -1) {
            return false;
        }

        insertNode(besti, bestx, besty + h, w);
        for (int i = besti + 1; i < nnodes; i++) {
            int prevEnd = nodeX[i - 1] + nodeW[i - 1];
            if (nodeX[i] >= prevEnd) {
                break;
            }
            int shrink = prevEnd - nodeX[i];
            nodeX[i] += shrink;
            nodeW[i] -= shrink;
            if (nodeW[i] > 0) {
                break;
            }
            removeNode(i);
            i--;
        }
        for (int i = 0; i < nnodes - 1; i++) {
            if (nodeY[i] == nodeY[i + 1]) {
                nodeW[i] += nodeW[i + 1];
                removeNode(i + 1);
                i--;
            }
        }
        out[0] = bestx;
        out[1] = besty;
        return true;
    }

    private Glyph getGlyph(Font font, int codepoint, int isize, int iblur, boolean bitmapRequired) {
        if (isize < 2) {
            return null;
        }
        if (iblur > 20) {
            iblur = 20;
        }
        int pad = iblur + 2;

        long key = ((long) codepoint << 32) | ((long) (isize & 0xFFFF) << 16) | (iblur & 0xFFFF);
        Glyph glyph = font.glyphs.get(key);
        if (glyph != null && (!bitmapRequired || glyph.hasBitmap)) {
            return glyph;
        }

        float glyphSize = isize / 10.0F;
        Font renderFont = font;
        int index = stbtt_FindGlyphIndex(font.info, codepoint);
        if (index == 0 && codepoint > 0x20 && !Character.isISOControl(codepoint)) {
            Font fallback = findFallback(codepoint, i0);
            if (fallback != null) {
                renderFont = fallback;
                index = i0[0];
            }
        }
        float scale = pixelHeightScale(renderFont, glyphSize);
        stbtt_GetGlyphHMetrics(renderFont.info, index, i0, i1);
        int advance = i0[0];
        stbtt_GetGlyphBitmapBox(renderFont.info, index, scale, scale, i0, i1, i2, i3);
        int bx0 = i0[0];
        int by0 = i1[0];
        int gw = i2[0] - bx0 + pad * 2;
        int gh = i3[0] - by0 + pad * 2;

        int gx;
        int gy;
        if (bitmapRequired) {
            if (!allocRect(gw, gh, position)) {
                return null;
            }
            gx = position[0];
            gy = position[1];
        } else {
            gx = -1;
            gy = -1;
        }

        if (glyph == null) {
            glyph = new Glyph();
            font.glyphs.put(key, glyph);
        }
        glyph.index = index;
        glyph.renderFont = renderFont;
        glyph.scale = scale;
        glyph.x0 = gx;
        glyph.y0 = gy;
        glyph.x1 = gx + gw;
        glyph.y1 = gy + gh;
        glyph.xadv = (short) (scale * advance * 10.0F);
        glyph.xoff = bx0 - pad;
        glyph.yoff = by0 - pad;
        glyph.hasBitmap = bitmapRequired;

        if (!bitmapRequired) {
            return glyph;
        }

        int area = gw * gh;
        if (scratch.capacity() < area) {
            MemoryUtil.memFree(scratch);
            scratch = MemoryUtil.memAlloc(Math.max(area, scratch.capacity() * 2));
        }
        long base = MemoryUtil.memAddress(scratch);
        MemoryUtil.memSet(base, 0, area);
        int innerWidth = gw - pad * 2;
        int innerHeight = gh - pad * 2;
        if (innerWidth > 0 && innerHeight > 0) {
            ByteBuffer target = MemoryUtil.memByteBuffer(base + (long) pad * gw + pad, area - pad * gw - pad);
            stbtt_MakeGlyphBitmap(renderFont.info, target, innerWidth, innerHeight, gw, scale, scale, index);
        }

        if (iblur > 0) {
            byte[] pixels = new byte[area];
            MemoryUtil.memByteBuffer(base, area).get(0, pixels, 0, area);
            blur(pixels, gw, gh, iblur);
            MemoryUtil.memByteBuffer(base, area).put(0, pixels, 0, area);
        }

        long atlasBase = MemoryUtil.memAddress(atlas);
        for (int y = 0; y < gh; y++) {
            MemoryUtil.memCopy(base + (long) y * gw, atlasBase + (long) (gy + y) * ATLAS_SIZE + gx, gw);
        }

        dirty[0] = Math.min(dirty[0], glyph.x0);
        dirty[1] = Math.min(dirty[1], glyph.y0);
        dirty[2] = Math.max(dirty[2], glyph.x1);
        dirty[3] = Math.max(dirty[3], glyph.y1);
        if ((ndirtyRects + 1) * 4 > dirtyRects.length) {
            dirtyRects = java.util.Arrays.copyOf(dirtyRects, dirtyRects.length * 2);
        }
        int r = ndirtyRects++ * 4;
        dirtyRects[r] = glyph.x0;
        dirtyRects[r + 1] = glyph.y0;
        dirtyRects[r + 2] = glyph.x1 - glyph.x0;
        dirtyRects[r + 3] = glyph.y1 - glyph.y0;
        return glyph;
    }

    private static void blurCols(byte[] dst, int w, int h, int alpha) {
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int z = 0;
            for (int x = 1; x < w; x++) {
                z += (alpha * (((dst[row + x] & 0xFF) << ZPREC) - z)) >> APREC;
                dst[row + x] = (byte) (z >> ZPREC);
            }
            dst[row + w - 1] = 0;
            z = 0;
            for (int x = w - 2; x >= 0; x--) {
                z += (alpha * (((dst[row + x] & 0xFF) << ZPREC) - z)) >> APREC;
                dst[row + x] = (byte) (z >> ZPREC);
            }
            dst[row] = 0;
        }
    }

    private static void blurRows(byte[] dst, int w, int h, int alpha) {
        for (int x = 0; x < w; x++) {
            int z = 0;
            for (int y = w; y < h * w; y += w) {
                z += (alpha * (((dst[x + y] & 0xFF) << ZPREC) - z)) >> APREC;
                dst[x + y] = (byte) (z >> ZPREC);
            }
            dst[x + (h - 1) * w] = 0;
            z = 0;
            for (int y = (h - 2) * w; y >= 0; y -= w) {
                z += (alpha * (((dst[x + y] & 0xFF) << ZPREC) - z)) >> APREC;
                dst[x + y] = (byte) (z >> ZPREC);
            }
            dst[x] = 0;
        }
    }

    private static void blur(byte[] dst, int w, int h, int blur) {
        if (blur < 1) {
            return;
        }
        float sigma = blur * 0.57735F;
        int alpha = (int) ((1 << APREC) * (1.0F - (float) Math.exp(-2.3F / (sigma + 1.0F))));
        blurRows(dst, w, h, alpha);
        blurCols(dst, w, h, alpha);
        blurRows(dst, w, h, alpha);
        blurCols(dst, w, h, alpha);
    }

    private void getQuad(int prevGlyphIndex, Font prevFont, Glyph glyph, float spacing, float[] pos, Quad q) {
        if (prevGlyphIndex != -1) {
            float adv = prevFont == glyph.renderFont
                    ? stbtt_GetGlyphKernAdvance(glyph.renderFont.info, prevGlyphIndex, glyph.index) * glyph.scale
                    : 0.0F;
            pos[0] += (int) (adv + spacing + 0.5F);
        }

        float xoff = (short) (glyph.xoff + 1);
        float yoff = (short) (glyph.yoff + 1);
        float x0 = glyph.x0 + 1;
        float y0 = glyph.y0 + 1;
        float x1 = glyph.x1 - 1;
        float y1 = glyph.y1 - 1;

        float rx = (float) Math.floor(pos[0] + xoff);
        float ry = (float) Math.floor(pos[1] + yoff);

        q.x0 = rx;
        q.y0 = ry;
        q.x1 = rx + x1 - x0;
        q.y1 = ry + y1 - y0;

        q.s0 = x0 * itw;
        q.t0 = y0 * ith;
        q.s1 = x1 * itw;
        q.t1 = y1 * ith;

        pos[0] += (int) (glyph.xadv / 10.0F + 0.5F);
    }

    private float vertAlign(Font font, int alignment, int isize) {
        if ((alignment & VG.VG_ALIGN_TOP) != 0) {
            return font.ascender * (float) isize / 10.0F;
        } else if ((alignment & VG.VG_ALIGN_MIDDLE) != 0) {
            return (font.ascender + font.descender) / 2.0F * (float) isize / 10.0F;
        } else if ((alignment & VG.VG_ALIGN_BASELINE) != 0) {
            return 0.0F;
        } else if ((alignment & VG.VG_ALIGN_BOTTOM) != 0) {
            return font.descender * (float) isize / 10.0F;
        }
        return 0.0F;
    }

    float textBounds(float x, float y, String text, float[] bounds) {
        Font font = current();
        if (font == null) {
            return 0.0F;
        }
        int isize = (short) (size * 10.0F);
        int iblur = (short) blur;
        float scale = pixelHeightScale(font, isize / 10.0F);

        y += vertAlign(font, align, isize);

        float minx = x;
        float maxx = x;
        float miny = y;
        float maxy = y;
        float startx = x;
        float[] pos = {x, y};
        Quad q = new Quad();
        int prevGlyphIndex = -1;
        Font prevFont = null;

        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            i += Character.charCount(codepoint);
            Glyph glyph = getGlyph(font, codepoint, isize, iblur, false);
            if (glyph != null) {
                getQuad(prevGlyphIndex, prevFont, glyph, spacing, pos, q);
                if (q.x0 < minx) minx = q.x0;
                if (q.x1 > maxx) maxx = q.x1;
                if (q.y0 < miny) miny = q.y0;
                if (q.y1 > maxy) maxy = q.y1;
            }
            prevGlyphIndex = glyph != null ? glyph.index : -1;
            prevFont = glyph != null ? glyph.renderFont : null;
        }

        float advance = pos[0] - startx;

        if ((align & VG.VG_ALIGN_LEFT) != 0) {
        } else if ((align & VG.VG_ALIGN_RIGHT) != 0) {
            minx -= advance;
            maxx -= advance;
        } else if ((align & VG.VG_ALIGN_CENTER) != 0) {
            minx -= advance * 0.5F;
            maxx -= advance * 0.5F;
        }

        if (bounds != null) {
            bounds[0] = minx;
            bounds[1] = miny;
            bounds[2] = maxx;
            bounds[3] = maxy;
        }
        return advance;
    }

    void lineBounds(float y, float[] bounds) {
        Font font = current();
        if (font == null) {
            return;
        }
        int isize = (short) (size * 10.0F);
        y += vertAlign(font, align, isize);
        bounds[1] = y - font.ascender * (float) isize / 10.0F;
        bounds[3] = bounds[1] + font.lineh * isize / 10.0F;
    }

    boolean iterInit(TextIter iter, float x, float y, String text, boolean bitmapRequired) {
        Font font = current();
        if (font == null) {
            return false;
        }
        iter.font = font;
        iter.isize = (short) (size * 10.0F);
        iter.iblur = (short) blur;
        iter.scale = pixelHeightScale(font, iter.isize / 10.0F);

        if ((align & VG.VG_ALIGN_LEFT) != 0) {
        } else if ((align & VG.VG_ALIGN_RIGHT) != 0) {
            x -= textBounds(x, y, text, null);
        } else if ((align & VG.VG_ALIGN_CENTER) != 0) {
            x -= textBounds(x, y, text, null) * 0.5F;
        }
        y += vertAlign(font, align, iter.isize);

        iter.x = iter.nextx = x;
        iter.y = iter.nexty = y;
        iter.spacing = spacing;
        iter.text = text;
        iter.next = 0;
        iter.prevGlyphIndex = -1;
        iter.prevFont = null;
        iter.bitmapRequired = bitmapRequired;
        return true;
    }

    static boolean isRenderable(TextIter iter) {
        return iter.isize >= 2;
    }

    boolean iterNext(TextIter iter, Quad quad) {
        if (iter.next >= iter.text.length()) {
            return false;
        }
        int codepoint = iter.text.codePointAt(iter.next);
        iter.next += Character.charCount(codepoint);
        iter.x = iter.nextx;
        iter.y = iter.nexty;
        Glyph glyph = getGlyph(iter.font, codepoint, iter.isize, iter.iblur, iter.bitmapRequired);
        if (glyph != null) {
            float[] pos = {iter.nextx, iter.nexty};
            getQuad(iter.prevGlyphIndex, iter.prevFont, glyph, iter.spacing, pos, quad);
            iter.nextx = pos[0];
            iter.nexty = pos[1];
        }
        iter.prevGlyphIndex = glyph != null ? glyph.index : -1;
        iter.prevFont = glyph != null ? glyph.renderFont : null;
        return true;
    }

    static void copyIter(TextIter dst, TextIter src) {
        dst.font = src.font;
        dst.isize = src.isize;
        dst.iblur = src.iblur;
        dst.scale = src.scale;
        dst.x = src.x;
        dst.y = src.y;
        dst.nextx = src.nextx;
        dst.nexty = src.nexty;
        dst.spacing = src.spacing;
        dst.text = src.text;
        dst.next = src.next;
        dst.prevGlyphIndex = src.prevGlyphIndex;
        dst.prevFont = src.prevFont;
        dst.bitmapRequired = src.bitmapRequired;
    }
}
