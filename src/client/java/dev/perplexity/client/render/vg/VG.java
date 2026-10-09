package dev.perplexity.client.render.vg;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public final class VG {
    public static final int VG_ANTIALIAS = 1;
    public static final int VG_STENCIL_STROKES = 1 << 1;

    public static final int VG_CCW = 1;
    public static final int VG_CW = 2;
    public static final int VG_SOLID = 1;
    public static final int VG_HOLE = 2;

    public static final int VG_BUTT = 0;
    public static final int VG_ROUND = 1;
    public static final int VG_SQUARE = 2;
    public static final int VG_BEVEL = 3;
    public static final int VG_MITER = 4;

    public static final int VG_ALIGN_LEFT = 1;
    public static final int VG_ALIGN_CENTER = 1 << 1;
    public static final int VG_ALIGN_RIGHT = 1 << 2;
    public static final int VG_ALIGN_TOP = 1 << 3;
    public static final int VG_ALIGN_MIDDLE = 1 << 4;
    public static final int VG_ALIGN_BOTTOM = 1 << 5;
    public static final int VG_ALIGN_BASELINE = 1 << 6;

    public static final int VG_IMAGE_GENERATE_MIPMAPS = 1;
    public static final int VG_IMAGE_REPEATX = 1 << 1;
    public static final int VG_IMAGE_REPEATY = 1 << 2;
    public static final int VG_IMAGE_FLIPY = 1 << 3;
    public static final int VG_IMAGE_PREMULTIPLIED = 1 << 4;
    public static final int VG_IMAGE_NEAREST = 1 << 5;
    public static final int VG_IMAGE_NODELETE = 1 << 16;

    private static final float PI = 3.14159265358979323846264338327F;
    private static final float KAPPA90 = 0.5522847493F;

    private static final int CMD_MOVETO = 0;
    private static final int CMD_LINETO = 1;
    private static final int CMD_BEZIERTO = 2;
    private static final int CMD_CLOSE = 3;
    private static final int CMD_WINDING = 4;

    private static final int PT_CORNER = 0x01;
    private static final int PT_LEFT = 0x02;
    private static final int PT_BEVEL = 0x04;
    private static final int PT_INNERBEVEL = 0x08;

    private static final int MAX_STATES = 32;
    private static final int MAX_FONT_IMAGES = 4;

    private static final List<Context> CONTEXTS = new ArrayList<>();
    private static long lastHandle;
    private static Context lastContext;

    private VG() {
    }

    private static final class State {
        final VGPaint fill = new VGPaint();
        final VGPaint stroke = new VGPaint();
        boolean shapeAntiAlias;
        float strokeWidth;
        float miterLimit;
        int lineJoin;
        int lineCap;
        float alpha;
        final float[] xform = new float[6];
        final float[] scissorXform = new float[6];
        final float[] scissorExtent = new float[2];
        float fontSize;
        float letterSpacing;
        float lineHeight;
        float fontBlur;
        int textAlign;
        int fontId;

        void set(State o) {
            fill.set(o.fill);
            stroke.set(o.stroke);
            shapeAntiAlias = o.shapeAntiAlias;
            strokeWidth = o.strokeWidth;
            miterLimit = o.miterLimit;
            lineJoin = o.lineJoin;
            lineCap = o.lineCap;
            alpha = o.alpha;
            System.arraycopy(o.xform, 0, xform, 0, 6);
            System.arraycopy(o.scissorXform, 0, scissorXform, 0, 6);
            scissorExtent[0] = o.scissorExtent[0];
            scissorExtent[1] = o.scissorExtent[1];
            fontSize = o.fontSize;
            letterSpacing = o.letterSpacing;
            lineHeight = o.lineHeight;
            fontBlur = o.fontBlur;
            textAlign = o.textAlign;
            fontId = o.fontId;
        }
    }

    static final class Path {
        int first;
        int count;
        boolean closed;
        int nbevel;
        int fill;
        int nfill;
        int stroke;
        int nstroke;
        int winding;
        boolean convex;

        void reset() {
            first = 0;
            count = 0;
            closed = false;
            nbevel = 0;
            fill = 0;
            nfill = 0;
            stroke = 0;
            nstroke = 0;
            winding = 0;
            convex = false;
        }
    }

    static final class PathCache {
        float[] px = new float[128];
        float[] py = new float[128];
        float[] pdx = new float[128];
        float[] pdy = new float[128];
        float[] plen = new float[128];
        float[] pdmx = new float[128];
        float[] pdmy = new float[128];
        int[] pflags = new int[128];
        int npoints;

        Path[] paths = new Path[16];
        int npaths;

        float[] verts = new float[4 * 256];

        final float[] bounds = new float[4];

        void clear() {
            npoints = 0;
            npaths = 0;
        }

        void ensurePoints(int count) {
            if (count <= px.length) {
                return;
            }
            int size = Math.max(count, px.length * 2);
            px = java.util.Arrays.copyOf(px, size);
            py = java.util.Arrays.copyOf(py, size);
            pdx = java.util.Arrays.copyOf(pdx, size);
            pdy = java.util.Arrays.copyOf(pdy, size);
            plen = java.util.Arrays.copyOf(plen, size);
            pdmx = java.util.Arrays.copyOf(pdmx, size);
            pdmy = java.util.Arrays.copyOf(pdmy, size);
            pflags = java.util.Arrays.copyOf(pflags, size);
        }

        void ensureVerts(int count) {
            if (count * 4 > verts.length) {
                verts = new float[Math.max(count * 4, verts.length * 2)];
            }
        }
    }

    private static final class Context {
        final long handle;
        final int flags;
        float[] commands = new float[256];
        int ncommands;
        float commandx;
        float commandy;
        final State[] states = new State[MAX_STATES];
        int nstates;
        final PathCache cache = new PathCache();
        float tessTol;
        float distTol;
        float fringeWidth;
        float devicePxRatio;
        final VGGL gl;
        final VGFonts fonts;
        int fontImage;
        final List<Integer> retiredFontImages = new ArrayList<>();
        final List<Integer> spareFontImages = new ArrayList<>();
        float[] textVerts = new float[4 * 6 * 64];

        Context(long handle, int flags, VGGL gl, VGFonts fonts) {
            this.handle = handle;
            this.flags = flags;
            this.gl = gl;
            this.fonts = fonts;
            for (int i = 0; i < MAX_STATES; i++) {
                states[i] = new State();
            }
        }

        State state() {
            return states[nstates - 1];
        }
    }

    private static Context ctx(long handle) {
        if (handle == lastHandle && lastContext != null) {
            return lastContext;
        }
        for (Context context : CONTEXTS) {
            if (context.handle == handle) {
                lastHandle = handle;
                lastContext = context;
                return context;
            }
        }
        throw new IllegalArgumentException("invalid vector context " + handle);
    }

    public static long vgCreate(int flags) {
        VGGL gl = new VGGL(flags);
        try {
            if (!gl.create()) {
                gl.delete();
                return 0L;
            }
        } catch (Throwable throwable) {
            try {
                gl.delete();
            } catch (Throwable ignored) {
            }
            return 0L;
        }
        long handle = 1L;
        for (Context context : CONTEXTS) {
            handle = Math.max(handle, context.handle + 1L);
        }
        Context context = new Context(handle, flags, gl, new VGFonts());
        CONTEXTS.add(context);
        vgSave(handle);
        vgReset(handle);
        setDevicePixelRatio(context, 1.0F);
        return handle;
    }

    public static void vgDelete(long handle) {
        Context context = ctx(handle);
        CONTEXTS.remove(context);
        if (lastContext == context) {
            lastContext = null;
            lastHandle = 0L;
        }
        context.gl.delete();
        context.fonts.delete();
    }

    private static void setDevicePixelRatio(Context context, float ratio) {
        context.tessTol = 0.25F / ratio;
        context.distTol = 0.01F / ratio;
        context.fringeWidth = 1.0F / ratio;
        context.devicePxRatio = ratio;
    }

    public static void vgBeginFrame(long handle, float windowWidth, float windowHeight, float devicePixelRatio) {
        Context context = ctx(handle);
        context.nstates = 0;
        vgSave(handle);
        vgReset(handle);
        setDevicePixelRatio(context, devicePixelRatio);
        context.gl.viewport(windowWidth, windowHeight);
    }

    public static void vgCancelFrame(long handle) {
        ctx(handle).gl.cancel();
    }

    public static void vgWarmup(long handle, String... faces) {
        Context context = ctx(handle);
        VGColor white = new VGColor(1.0F, 1.0F, 1.0F, 0.8F);
        VGColor clear = new VGColor(0.0F, 0.0F, 0.0F, 0.0F);
        VGPaint paint = new VGPaint();
        float[] bounds = new float[4];
        String sample = "Atlas 12:45 Saturday, September";
        vgBeginFrame(handle, 1920.0F, 1080.0F, 1.0F);
        try {
            for (int i = 0; i < 120; i++) {
                float o = i % 7;
                vgSave(handle);
                vgTranslate(handle, 960.0F, 540.0F);
                vgScale(handle, 0.9F + o * 0.01F, 0.9F + o * 0.01F);
                vgTranslate(handle, -960.0F, -540.0F);
                vgScissor(handle, 10.0F, 10.0F, 1500.0F, 900.0F);
                vgIntersectScissor(handle, 20.0F, 20.0F, 1400.0F, 800.0F);
                vgGlobalAlpha(handle, 0.9F);

                vgBeginPath(handle);
                vgRect(handle, 0.0F, 0.0F, 400.0F + o, 300.0F);
                vgFillColor(handle, white);
                vgFill(handle);

                vgBeginPath(handle);
                vgRoundedRect(handle, 50.0F + o, 60.0F, 300.0F, 70.0F, 12.0F + o);
                vgFillPaint(handle, vgLinearGradient(handle, 0.0F, 60.0F, 0.0F, 130.0F, white, clear, paint));
                vgFill(handle);
                vgStrokeWidth(handle, 1.0F + o * 0.3F);
                vgStrokeColor(handle, white);
                vgStroke(handle);

                vgBeginPath(handle);
                vgRect(handle, 20.0F, 20.0F, 500.0F, 200.0F);
                vgRoundedRectVarying(handle, 40.0F, 40.0F, 300.0F, 100.0F, 20.0F, 6.0F, 20.0F, 6.0F);
                vgPathWinding(handle, VG_HOLE);
                vgFillPaint(handle, vgBoxGradient(handle, 40.0F, 40.0F, 300.0F, 100.0F, 20.0F, 18.0F, white, clear, paint));
                vgFill(handle);

                vgBeginPath(handle);
                vgCircle(handle, 600.0F, 300.0F, 20.0F + o);
                vgFillPaint(handle, vgRadialGradient(handle, 600.0F, 300.0F, 5.0F, 30.0F, white, clear, paint));
                vgFill(handle);

                vgBeginPath(handle);
                for (int k = 0; k < 12; k++) {
                    double a = k * Math.PI / 6.0;
                    float r = (k & 1) == 0 ? 60.0F : 25.0F;
                    float x = 800.0F + (float) Math.cos(a) * r;
                    float y = 300.0F + (float) Math.sin(a) * r;
                    if (k == 0) {
                        vgMoveTo(handle, x, y);
                    } else {
                        vgLineTo(handle, x, y);
                    }
                }
                vgClosePath(handle);
                vgCircle(handle, 800.0F, 300.0F, 10.0F);
                vgPathWinding(handle, VG_HOLE);
                vgFillColor(handle, white);
                vgFill(handle);

                vgLineCap(handle, (i & 1) == 0 ? VG_ROUND : VG_BUTT);
                vgLineJoin(handle, (i & 1) == 0 ? VG_ROUND : VG_MITER);
                vgBeginPath(handle);
                vgMoveTo(handle, 100.0F, 400.0F);
                vgLineTo(handle, 140.0F + o, 440.0F);
                vgLineTo(handle, 100.0F, 480.0F);
                vgArc(handle, 300.0F, 450.0F, 30.0F, -1.0F, 4.0F, VG_CW);
                vgStrokeWidth(handle, 2.5F);
                vgStroke(handle);
                vgLineCap(handle, VG_BUTT);
                vgLineJoin(handle, VG_MITER);

                if (faces != null) {
                    for (String face : faces) {
                        if (face == null) {
                            continue;
                        }
                        vgFontFace(handle, face);
                        vgTextAlign(handle, (i & 1) == 0 ? VG_ALIGN_CENTER | VG_ALIGN_MIDDLE : VG_ALIGN_LEFT | VG_ALIGN_TOP);
                        vgTextLetterSpacing(handle, (i & 2) == 0 ? 0.0F : -0.5F);
                        vgFontSize(handle, 12.0F + (i % 6) * 9.0F);
                        vgFontBlur(handle, (i % 3) == 2 ? 6.0F : 0.0F);
                        vgFillColor(handle, white);
                        vgText(handle, 500.0F, 600.0F, sample);
                        vgFontBlur(handle, 0.0F);
                        vgTextBounds(handle, 0.0F, 0.0F, sample, bounds);
                    }
                }
                vgRestore(handle);
            }
        } finally {
            context.gl.cancel();
            context.fonts.resetAtlas();
            for (int image : context.retiredFontImages) {
                context.spareFontImages.add(image);
            }
            context.retiredFontImages.clear();
            context.nstates = 0;
            vgSave(handle);
            vgReset(handle);
        }
    }

    public static void vgEndFrame(long handle) {
        Context context = ctx(handle);
        uploadFontAtlas(context);
        context.gl.flush();
        for (int image : context.retiredFontImages) {
            if (context.spareFontImages.size() < MAX_FONT_IMAGES - 1) {
                context.spareFontImages.add(image);
            } else {
                context.gl.deleteTexture(image);
            }
        }
        context.retiredFontImages.clear();
    }

    public static VGColor vgRGBA(int r, int g, int b, int a, VGColor result) {
        return result.r(r / 255.0F).g(g / 255.0F).b(b / 255.0F).a(a / 255.0F);
    }

    public static VGColor vgRGBAf(float r, float g, float b, float a, VGColor result) {
        return result.r(r).g(g).b(b).a(a);
    }

    static void transformIdentity(float[] t) {
        t[0] = 1.0F;
        t[1] = 0.0F;
        t[2] = 0.0F;
        t[3] = 1.0F;
        t[4] = 0.0F;
        t[5] = 0.0F;
    }

    static void transformTranslate(float[] t, float tx, float ty) {
        t[0] = 1.0F;
        t[1] = 0.0F;
        t[2] = 0.0F;
        t[3] = 1.0F;
        t[4] = tx;
        t[5] = ty;
    }

    static void transformScale(float[] t, float sx, float sy) {
        t[0] = sx;
        t[1] = 0.0F;
        t[2] = 0.0F;
        t[3] = sy;
        t[4] = 0.0F;
        t[5] = 0.0F;
    }

    static void transformRotate(float[] t, float a) {
        float cs = (float) Math.cos(a);
        float sn = (float) Math.sin(a);
        t[0] = cs;
        t[1] = sn;
        t[2] = -sn;
        t[3] = cs;
        t[4] = 0.0F;
        t[5] = 0.0F;
    }

    static void transformMultiply(float[] t, float[] s) {
        float t0 = t[0] * s[0] + t[1] * s[2];
        float t2 = t[2] * s[0] + t[3] * s[2];
        float t4 = t[4] * s[0] + t[5] * s[2] + s[4];
        t[1] = t[0] * s[1] + t[1] * s[3];
        t[3] = t[2] * s[1] + t[3] * s[3];
        t[5] = t[4] * s[1] + t[5] * s[3] + s[5];
        t[0] = t0;
        t[2] = t2;
        t[4] = t4;
    }

    static void transformPremultiply(float[] t, float[] s) {
        float[] s2 = s.clone();
        transformMultiply(s2, t);
        System.arraycopy(s2, 0, t, 0, 6);
    }

    static boolean transformInverse(float[] inv, float[] t) {
        double det = (double) t[0] * t[3] - (double) t[2] * t[1];
        if (det > -1e-6 && det < 1e-6) {
            transformIdentity(inv);
            return false;
        }
        double invdet = 1.0 / det;
        inv[0] = (float) (t[3] * invdet);
        inv[2] = (float) (-t[2] * invdet);
        inv[4] = (float) (((double) t[2] * t[5] - (double) t[3] * t[4]) * invdet);
        inv[1] = (float) (-t[1] * invdet);
        inv[3] = (float) (t[0] * invdet);
        inv[5] = (float) (((double) t[1] * t[4] - (double) t[0] * t[5]) * invdet);
        return true;
    }

    private static float[] transformPoint(float[] t, float sx, float sy, float[] out, int offset) {
        out[offset] = sx * t[0] + sy * t[2] + t[4];
        out[offset + 1] = sx * t[1] + sy * t[3] + t[5];
        return out;
    }

    public static void vgSave(long handle) {
        Context context = ctx(handle);
        if (context.nstates >= MAX_STATES) {
            return;
        }
        if (context.nstates > 0) {
            context.states[context.nstates].set(context.states[context.nstates - 1]);
        }
        context.nstates++;
    }

    public static void vgRestore(long handle) {
        Context context = ctx(handle);
        if (context.nstates <= 1) {
            return;
        }
        context.nstates--;
    }

    public static void vgReset(long handle) {
        State state = ctx(handle).state();
        state.fill.setColor(new VGColor(1.0F, 1.0F, 1.0F, 1.0F));
        state.stroke.setColor(new VGColor(0.0F, 0.0F, 0.0F, 1.0F));
        state.shapeAntiAlias = true;
        state.strokeWidth = 1.0F;
        state.miterLimit = 10.0F;
        state.lineCap = VG_BUTT;
        state.lineJoin = VG_MITER;
        state.alpha = 1.0F;
        transformIdentity(state.xform);

        java.util.Arrays.fill(state.scissorXform, 0.0F);
        state.scissorExtent[0] = -1.0F;
        state.scissorExtent[1] = -1.0F;

        state.fontSize = 16.0F;
        state.letterSpacing = 0.0F;
        state.lineHeight = 1.0F;
        state.fontBlur = 0.0F;
        state.textAlign = VG_ALIGN_LEFT | VG_ALIGN_BASELINE;
        state.fontId = 0;
    }

    public static void vgShapeAntiAlias(long handle, boolean enabled) {
        ctx(handle).state().shapeAntiAlias = enabled;
    }

    public static void vgStrokeWidth(long handle, float width) {
        ctx(handle).state().strokeWidth = width;
    }

    public static void vgMiterLimit(long handle, float limit) {
        ctx(handle).state().miterLimit = limit;
    }

    public static void vgLineCap(long handle, int cap) {
        ctx(handle).state().lineCap = cap;
    }

    public static void vgLineJoin(long handle, int join) {
        ctx(handle).state().lineJoin = join;
    }

    public static void vgGlobalAlpha(long handle, float alpha) {
        ctx(handle).state().alpha = alpha;
    }

    public static void vgResetTransform(long handle) {
        transformIdentity(ctx(handle).state().xform);
    }

    public static void vgTranslate(long handle, float x, float y) {
        float[] t = new float[6];
        transformTranslate(t, x, y);
        transformPremultiply(ctx(handle).state().xform, t);
    }

    public static void vgRotate(long handle, float angle) {
        float[] t = new float[6];
        transformRotate(t, angle);
        transformPremultiply(ctx(handle).state().xform, t);
    }

    public static void vgScale(long handle, float x, float y) {
        float[] t = new float[6];
        transformScale(t, x, y);
        transformPremultiply(ctx(handle).state().xform, t);
    }

    public static void vgCurrentTransform(long handle, float[] xform) {
        System.arraycopy(ctx(handle).state().xform, 0, xform, 0, 6);
    }

    public static void vgCurrentTransform(long handle, FloatBuffer xform) {
        float[] t = ctx(handle).state().xform;
        for (int i = 0; i < 6; i++) {
            xform.put(xform.position() + i, t[i]);
        }
    }

    public static void vgStrokeColor(long handle, VGColor color) {
        ctx(handle).state().stroke.setColor(color);
    }

    public static void vgStrokePaint(long handle, VGPaint paint) {
        State state = ctx(handle).state();
        state.stroke.set(paint);
        transformMultiply(state.stroke.xform, state.xform);
    }

    public static void vgFillColor(long handle, VGColor color) {
        ctx(handle).state().fill.setColor(color);
    }

    public static void vgFillPaint(long handle, VGPaint paint) {
        State state = ctx(handle).state();
        state.fill.set(paint);
        transformMultiply(state.fill.xform, state.xform);
    }

    public static VGPaint vgLinearGradient(long handle, float sx, float sy, float ex, float ey,
                                           VGColor icol, VGColor ocol, VGPaint result) {
        final float large = 1e5F;
        result.clear();

        float dx = ex - sx;
        float dy = ey - sy;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d > 0.0001F) {
            dx /= d;
            dy /= d;
        } else {
            dx = 0.0F;
            dy = 1.0F;
        }

        result.xform[0] = dy;
        result.xform[1] = -dx;
        result.xform[2] = dx;
        result.xform[3] = dy;
        result.xform[4] = sx - dx * large;
        result.xform[5] = sy - dy * large;

        result.extent[0] = large;
        result.extent[1] = large + d * 0.5F;
        result.radius = 0.0F;
        result.feather = Math.max(1.0F, d);
        result.innerColor.set(icol);
        result.outerColor.set(ocol);
        return result;
    }

    public static VGPaint vgRadialGradient(long handle, float cx, float cy, float inr, float outr,
                                           VGColor icol, VGColor ocol, VGPaint result) {
        float r = (inr + outr) * 0.5F;
        float f = outr - inr;
        result.clear();
        transformIdentity(result.xform);
        result.xform[4] = cx;
        result.xform[5] = cy;
        result.extent[0] = r;
        result.extent[1] = r;
        result.radius = r;
        result.feather = Math.max(1.0F, f);
        result.innerColor.set(icol);
        result.outerColor.set(ocol);
        return result;
    }

    public static VGPaint vgBoxGradient(long handle, float x, float y, float w, float h, float r, float f,
                                        VGColor icol, VGColor ocol, VGPaint result) {
        result.clear();
        transformIdentity(result.xform);
        result.xform[4] = x + w * 0.5F;
        result.xform[5] = y + h * 0.5F;
        result.extent[0] = w * 0.5F;
        result.extent[1] = h * 0.5F;
        result.radius = r;
        result.feather = Math.max(1.0F, f);
        result.innerColor.set(icol);
        result.outerColor.set(ocol);
        return result;
    }

    public static VGPaint vgImagePattern(long handle, float cx, float cy, float w, float h, float angle,
                                         int image, float alpha, VGPaint result) {
        result.clear();
        transformRotate(result.xform, angle);
        result.xform[4] = cx;
        result.xform[5] = cy;
        result.extent[0] = w;
        result.extent[1] = h;
        result.image = image;
        result.innerColor.r(1.0F).g(1.0F).b(1.0F).a(alpha);
        result.outerColor.r(1.0F).g(1.0F).b(1.0F).a(alpha);
        return result;
    }

    public static void vgScissor(long handle, float x, float y, float w, float h) {
        State state = ctx(handle).state();
        w = Math.max(0.0F, w);
        h = Math.max(0.0F, h);
        transformIdentity(state.scissorXform);
        state.scissorXform[4] = x + w * 0.5F;
        state.scissorXform[5] = y + h * 0.5F;
        transformMultiply(state.scissorXform, state.xform);
        state.scissorExtent[0] = w * 0.5F;
        state.scissorExtent[1] = h * 0.5F;
    }

    public static void vgIntersectScissor(long handle, float x, float y, float w, float h) {
        State state = ctx(handle).state();
        if (state.scissorExtent[0] < 0) {
            vgScissor(handle, x, y, w, h);
            return;
        }

        float[] pxform = state.scissorXform.clone();
        float ex = state.scissorExtent[0];
        float ey = state.scissorExtent[1];
        float[] invxform = new float[6];
        transformInverse(invxform, state.xform);
        transformMultiply(pxform, invxform);
        float tex = ex * Math.abs(pxform[0]) + ey * Math.abs(pxform[2]);
        float tey = ex * Math.abs(pxform[1]) + ey * Math.abs(pxform[3]);

        float ax = pxform[4] - tex;
        float ay = pxform[5] - tey;
        float aw = tex * 2;
        float ah = tey * 2;
        float minx = Math.max(ax, x);
        float miny = Math.max(ay, y);
        float maxx = Math.min(ax + aw, x + w);
        float maxy = Math.min(ay + ah, y + h);
        vgScissor(handle, minx, miny, Math.max(0.0F, maxx - minx), Math.max(0.0F, maxy - miny));
    }

    public static void vgResetScissor(long handle) {
        State state = ctx(handle).state();
        java.util.Arrays.fill(state.scissorXform, 0.0F);
        state.scissorExtent[0] = -1.0F;
        state.scissorExtent[1] = -1.0F;
    }

    public static int vgCreateImageRGBA(long handle, int w, int h, int imageFlags, ByteBuffer data) {
        return ctx(handle).gl.createTexture(VGGL.TEXTURE_RGBA, w, h, imageFlags, data);
    }

    public static int vgCreateImageMem(long handle, int imageFlags, ByteBuffer data) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer n = stack.mallocInt(1);
            ByteBuffer image = STBImage.stbi_load_from_memory(data, w, h, n, 4);
            if (image == null) {
                return 0;
            }
            try {
                return vgCreateImageRGBA(handle, w.get(0), h.get(0), imageFlags, image);
            } finally {
                STBImage.stbi_image_free(image);
            }
        }
    }

    public static int vgCreateImageMem(long handle, int imageFlags, byte[] data) {
        ByteBuffer buffer = MemoryUtil.memAlloc(data.length);
        try {
            buffer.put(data).flip();
            return vgCreateImageMem(handle, imageFlags, buffer);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    public static int vgCreateImageFromHandle(long handle, int textureId, int w, int h, int imageFlags) {
        return ctx(handle).gl.wrapTexture(textureId, w, h, imageFlags);
    }

    public static void vgUpdateImage(long handle, int image, ByteBuffer data) {
        Context context = ctx(handle);
        int[] size = context.gl.textureSize(image);
        if (size != null) {
            context.gl.updateTexture(image, 0, 0, size[0], size[1], data);
        }
    }

    public static void vgImageSize(long handle, int image, int[] w, int[] h) {
        int[] size = ctx(handle).gl.textureSize(image);
        w[0] = size == null ? 0 : size[0];
        h[0] = size == null ? 0 : size[1];
    }

    public static void vgDeleteImage(long handle, int image) {
        ctx(handle).gl.deleteTexture(image);
    }

    public static void vgBeginPath(long handle) {
        Context context = ctx(handle);
        context.ncommands = 0;
        context.cache.clear();
    }

    private static void appendCommands(Context context, float[] vals, int nvals) {
        State state = context.state();
        if (context.ncommands + nvals > context.commands.length) {
            context.commands = java.util.Arrays.copyOf(context.commands,
                    Math.max(context.ncommands + nvals, context.commands.length + context.commands.length / 2));
        }

        int first = (int) vals[0];
        if (first != CMD_CLOSE && first != CMD_WINDING) {
            context.commandx = vals[nvals - 2];
            context.commandy = vals[nvals - 1];
        }

        int i = 0;
        float[] xf = state.xform;
        while (i < nvals) {
            int cmd = (int) vals[i];
            switch (cmd) {
                case CMD_MOVETO, CMD_LINETO -> {
                    transformPoint(xf, vals[i + 1], vals[i + 2], vals, i + 1);
                    i += 3;
                }
                case CMD_BEZIERTO -> {
                    transformPoint(xf, vals[i + 1], vals[i + 2], vals, i + 1);
                    transformPoint(xf, vals[i + 3], vals[i + 4], vals, i + 3);
                    transformPoint(xf, vals[i + 5], vals[i + 6], vals, i + 5);
                    i += 7;
                }
                case CMD_CLOSE -> i++;
                case CMD_WINDING -> i += 2;
                default -> i++;
            }
        }

        System.arraycopy(vals, 0, context.commands, context.ncommands, nvals);
        context.ncommands += nvals;
    }

    public static void vgMoveTo(long handle, float x, float y) {
        appendCommands(ctx(handle), new float[]{CMD_MOVETO, x, y}, 3);
    }

    public static void vgLineTo(long handle, float x, float y) {
        appendCommands(ctx(handle), new float[]{CMD_LINETO, x, y}, 3);
    }

    public static void vgBezierTo(long handle, float c1x, float c1y, float c2x, float c2y, float x, float y) {
        appendCommands(ctx(handle), new float[]{CMD_BEZIERTO, c1x, c1y, c2x, c2y, x, y}, 7);
    }

    public static void vgQuadTo(long handle, float cx, float cy, float x, float y) {
        Context context = ctx(handle);
        float x0 = context.commandx;
        float y0 = context.commandy;
        appendCommands(context, new float[]{CMD_BEZIERTO,
                x0 + 2.0F / 3.0F * (cx - x0), y0 + 2.0F / 3.0F * (cy - y0),
                x + 2.0F / 3.0F * (cx - x), y + 2.0F / 3.0F * (cy - y),
                x, y}, 7);
    }

    public static void vgClosePath(long handle) {
        appendCommands(ctx(handle), new float[]{CMD_CLOSE}, 1);
    }

    public static void vgPathWinding(long handle, int dir) {
        appendCommands(ctx(handle), new float[]{CMD_WINDING, dir}, 2);
    }

    public static void vgArc(long handle, float cx, float cy, float r, float a0, float a1, int dir) {
        Context context = ctx(handle);
        int move = context.ncommands > 0 ? CMD_LINETO : CMD_MOVETO;

        float da = a1 - a0;
        if (dir == VG_CW) {
            if (Math.abs(da) >= PI * 2) {
                da = PI * 2;
            } else {
                while (da < 0.0F) da += PI * 2;
            }
        } else {
            if (Math.abs(da) >= PI * 2) {
                da = -PI * 2;
            } else {
                while (da > 0.0F) da -= PI * 2;
            }
        }

        int ndivs = Math.max(1, Math.min((int) (Math.abs(da) / (PI * 0.5F) + 0.5F), 5));
        float hda = (da / (float) ndivs) / 2.0F;
        float kappa = Math.abs(4.0F / 3.0F * (1.0F - (float) Math.cos(hda)) / (float) Math.sin(hda));
        if (dir == VG_CCW) {
            kappa = -kappa;
        }

        float[] vals = new float[3 + 5 * 7 + 100];
        int nvals = 0;
        float px = 0;
        float py = 0;
        float ptanx = 0;
        float ptany = 0;
        for (int i = 0; i <= ndivs; i++) {
            float a = a0 + da * (i / (float) ndivs);
            float dx = (float) Math.cos(a);
            float dy = (float) Math.sin(a);
            float x = cx + dx * r;
            float y = cy + dy * r;
            float tanx = -dy * r * kappa;
            float tany = dx * r * kappa;

            if (i == 0) {
                vals[nvals++] = move;
                vals[nvals++] = x;
                vals[nvals++] = y;
            } else {
                vals[nvals++] = CMD_BEZIERTO;
                vals[nvals++] = px + ptanx;
                vals[nvals++] = py + ptany;
                vals[nvals++] = x - tanx;
                vals[nvals++] = y - tany;
                vals[nvals++] = x;
                vals[nvals++] = y;
            }
            px = x;
            py = y;
            ptanx = tanx;
            ptany = tany;
        }
        appendCommands(context, vals, nvals);
    }

    public static void vgRect(long handle, float x, float y, float w, float h) {
        appendCommands(ctx(handle), new float[]{
                CMD_MOVETO, x, y,
                CMD_LINETO, x, y + h,
                CMD_LINETO, x + w, y + h,
                CMD_LINETO, x + w, y,
                CMD_CLOSE
        }, 13);
    }

    public static void vgRoundedRect(long handle, float x, float y, float w, float h, float r) {
        vgRoundedRectVarying(handle, x, y, w, h, r, r, r, r);
    }

    private static float signf(float a) {
        return a >= 0.0F ? 1.0F : -1.0F;
    }

    public static void vgRoundedRectVarying(long handle, float x, float y, float w, float h,
                                            float radTopLeft, float radTopRight, float radBottomRight, float radBottomLeft) {
        if (radTopLeft < 0.1F && radTopRight < 0.1F && radBottomRight < 0.1F && radBottomLeft < 0.1F) {
            vgRect(handle, x, y, w, h);
            return;
        }
        float halfw = Math.abs(w) * 0.5F;
        float halfh = Math.abs(h) * 0.5F;
        float rxBL = Math.min(radBottomLeft, halfw) * signf(w);
        float ryBL = Math.min(radBottomLeft, halfh) * signf(h);
        float rxBR = Math.min(radBottomRight, halfw) * signf(w);
        float ryBR = Math.min(radBottomRight, halfh) * signf(h);
        float rxTR = Math.min(radTopRight, halfw) * signf(w);
        float ryTR = Math.min(radTopRight, halfh) * signf(h);
        float rxTL = Math.min(radTopLeft, halfw) * signf(w);
        float ryTL = Math.min(radTopLeft, halfh) * signf(h);
        float k = 1 - KAPPA90;
        appendCommands(ctx(handle), new float[]{
                CMD_MOVETO, x, y + ryTL,
                CMD_LINETO, x, y + h - ryBL,
                CMD_BEZIERTO, x, y + h - ryBL * k, x + rxBL * k, y + h, x + rxBL, y + h,
                CMD_LINETO, x + w - rxBR, y + h,
                CMD_BEZIERTO, x + w - rxBR * k, y + h, x + w, y + h - ryBR * k, x + w, y + h - ryBR,
                CMD_LINETO, x + w, y + ryTR,
                CMD_BEZIERTO, x + w, y + ryTR * k, x + w - rxTR * k, y, x + w - rxTR, y,
                CMD_LINETO, x + rxTL, y,
                CMD_BEZIERTO, x + rxTL * k, y, x, y + ryTL * k, x, y + ryTL,
                CMD_CLOSE
        }, 44);
    }

    public static void vgEllipse(long handle, float cx, float cy, float rx, float ry) {
        appendCommands(ctx(handle), new float[]{
                CMD_MOVETO, cx - rx, cy,
                CMD_BEZIERTO, cx - rx, cy + ry * KAPPA90, cx - rx * KAPPA90, cy + ry, cx, cy + ry,
                CMD_BEZIERTO, cx + rx * KAPPA90, cy + ry, cx + rx, cy + ry * KAPPA90, cx + rx, cy,
                CMD_BEZIERTO, cx + rx, cy - ry * KAPPA90, cx + rx * KAPPA90, cy - ry, cx, cy - ry,
                CMD_BEZIERTO, cx - rx * KAPPA90, cy - ry, cx - rx, cy - ry * KAPPA90, cx - rx, cy,
                CMD_CLOSE
        }, 32);
    }

    public static void vgCircle(long handle, float cx, float cy, float r) {
        vgEllipse(handle, cx, cy, r, r);
    }

    private static void addPath(Context context) {
        PathCache cache = context.cache;
        if (cache.npaths + 1 > cache.paths.length) {
            cache.paths = java.util.Arrays.copyOf(cache.paths, cache.paths.length * 2);
        }
        Path path = cache.paths[cache.npaths];
        if (path == null) {
            path = new Path();
            cache.paths[cache.npaths] = path;
        }
        path.reset();
        path.first = cache.npoints;
        path.winding = VG_CCW;
        cache.npaths++;
    }

    private static Path lastPath(Context context) {
        return context.cache.npaths > 0 ? context.cache.paths[context.cache.npaths - 1] : null;
    }

    private static boolean ptEquals(float x1, float y1, float x2, float y2, float tol) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return dx * dx + dy * dy < tol * tol;
    }

    private static void addPoint(Context context, float x, float y, int flags) {
        Path path = lastPath(context);
        if (path == null) {
            return;
        }
        PathCache cache = context.cache;
        if (path.count > 0 && cache.npoints > 0) {
            int last = cache.npoints - 1;
            if (ptEquals(cache.px[last], cache.py[last], x, y, context.distTol)) {
                cache.pflags[last] |= flags;
                return;
            }
        }
        cache.ensurePoints(cache.npoints + 1);
        int p = cache.npoints;
        cache.px[p] = x;
        cache.py[p] = y;
        cache.pdx[p] = 0.0F;
        cache.pdy[p] = 0.0F;
        cache.plen[p] = 0.0F;
        cache.pdmx[p] = 0.0F;
        cache.pdmy[p] = 0.0F;
        cache.pflags[p] = flags;
        cache.npoints++;
        path.count++;
    }

    private static void tesselateBezier(Context context, float x1, float y1, float x2, float y2,
                                        float x3, float y3, float x4, float y4, int level, int type) {
        if (level > 10) {
            return;
        }

        float x12 = (x1 + x2) * 0.5F;
        float y12 = (y1 + y2) * 0.5F;
        float x23 = (x2 + x3) * 0.5F;
        float y23 = (y2 + y3) * 0.5F;
        float x34 = (x3 + x4) * 0.5F;
        float y34 = (y3 + y4) * 0.5F;
        float x123 = (x12 + x23) * 0.5F;
        float y123 = (y12 + y23) * 0.5F;

        float dx = x4 - x1;
        float dy = y4 - y1;
        float d2 = Math.abs((x2 - x4) * dy - (y2 - y4) * dx);
        float d3 = Math.abs((x3 - x4) * dy - (y3 - y4) * dx);

        if ((d2 + d3) * (d2 + d3) < context.tessTol * (dx * dx + dy * dy)) {
            addPoint(context, x4, y4, type);
            return;
        }

        float x234 = (x23 + x34) * 0.5F;
        float y234 = (y23 + y34) * 0.5F;
        float x1234 = (x123 + x234) * 0.5F;
        float y1234 = (y123 + y234) * 0.5F;

        tesselateBezier(context, x1, y1, x12, y12, x123, y123, x1234, y1234, level + 1, 0);
        tesselateBezier(context, x1234, y1234, x234, y234, x34, y34, x4, y4, level + 1, type);
    }

    private static float triarea2(float ax, float ay, float bx, float by, float cx, float cy) {
        float abx = bx - ax;
        float aby = by - ay;
        float acx = cx - ax;
        float acy = cy - ay;
        return acx * aby - abx * acy;
    }

    private static float polyArea(PathCache cache, int first, int count) {
        float area = 0;
        for (int i = 2; i < count; i++) {
            int a = first;
            int b = first + i - 1;
            int c = first + i;
            area += triarea2(cache.px[a], cache.py[a], cache.px[b], cache.py[b], cache.px[c], cache.py[c]);
        }
        return area * 0.5F;
    }

    private static void polyReverse(PathCache cache, int first, int count) {
        int i = first;
        int j = first + count - 1;
        while (i < j) {
            float tx = cache.px[i];
            cache.px[i] = cache.px[j];
            cache.px[j] = tx;
            float ty = cache.py[i];
            cache.py[i] = cache.py[j];
            cache.py[j] = ty;
            int tf = cache.pflags[i];
            cache.pflags[i] = cache.pflags[j];
            cache.pflags[j] = tf;
            i++;
            j--;
        }
    }

    private static void flattenPaths(Context context) {
        PathCache cache = context.cache;
        if (cache.npaths > 0) {
            return;
        }

        float[] commands = context.commands;
        int i = 0;
        while (i < context.ncommands) {
            int cmd = (int) commands[i];
            switch (cmd) {
                case CMD_MOVETO -> {
                    addPath(context);
                    addPoint(context, commands[i + 1], commands[i + 2], PT_CORNER);
                    i += 3;
                }
                case CMD_LINETO -> {
                    addPoint(context, commands[i + 1], commands[i + 2], PT_CORNER);
                    i += 3;
                }
                case CMD_BEZIERTO -> {
                    if (cache.npoints > 0) {
                        int last = cache.npoints - 1;
                        tesselateBezier(context, cache.px[last], cache.py[last],
                                commands[i + 1], commands[i + 2], commands[i + 3], commands[i + 4],
                                commands[i + 5], commands[i + 6], 0, PT_CORNER);
                    }
                    i += 7;
                }
                case CMD_CLOSE -> {
                    Path path = lastPath(context);
                    if (path != null) {
                        path.closed = true;
                    }
                    i++;
                }
                case CMD_WINDING -> {
                    Path path = lastPath(context);
                    if (path != null) {
                        path.winding = (int) commands[i + 1];
                    }
                    i += 2;
                }
                default -> i++;
            }
        }

        cache.bounds[0] = cache.bounds[1] = 1e6F;
        cache.bounds[2] = cache.bounds[3] = -1e6F;

        for (int j = 0; j < cache.npaths; j++) {
            Path path = cache.paths[j];
            int first = path.first;

            int p0 = first + path.count - 1;
            int p1 = first;
            if (path.count > 0 && ptEquals(cache.px[p0], cache.py[p0], cache.px[p1], cache.py[p1], context.distTol)) {
                path.count--;
                p0 = first + path.count - 1;
                path.closed = true;
            }

            if (path.count > 2) {
                float area = polyArea(cache, first, path.count);
                if (path.winding == VG_CCW && area < 0.0F) {
                    polyReverse(cache, first, path.count);
                }
                if (path.winding == VG_CW && area > 0.0F) {
                    polyReverse(cache, first, path.count);
                }
            }

            for (int k = 0; k < path.count; k++) {
                float dx = cache.px[p1] - cache.px[p0];
                float dy = cache.py[p1] - cache.py[p0];
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > 1e-6F) {
                    float id = 1.0F / d;
                    dx *= id;
                    dy *= id;
                }
                cache.pdx[p0] = dx;
                cache.pdy[p0] = dy;
                cache.plen[p0] = d;
                cache.bounds[0] = Math.min(cache.bounds[0], cache.px[p0]);
                cache.bounds[1] = Math.min(cache.bounds[1], cache.py[p0]);
                cache.bounds[2] = Math.max(cache.bounds[2], cache.px[p0]);
                cache.bounds[3] = Math.max(cache.bounds[3], cache.py[p0]);
                p0 = p1++;
            }
        }
    }

    private static int curveDivs(float r, float arc, float tol) {
        float da = (float) Math.acos(r / (r + tol)) * 2.0F;
        return Math.max(2, (int) Math.ceil(arc / da));
    }

    private static void calculateJoins(Context context, float w, int lineJoin, float miterLimit) {
        PathCache cache = context.cache;
        float iw = 0.0F;
        if (w > 0.0F) {
            iw = 1.0F / w;
        }

        for (int i = 0; i < cache.npaths; i++) {
            Path path = cache.paths[i];
            int first = path.first;
            int p0 = first + path.count - 1;
            int p1 = first;
            int nleft = 0;

            path.nbevel = 0;

            for (int j = 0; j < path.count; j++) {
                float dlx0 = cache.pdy[p0];
                float dly0 = -cache.pdx[p0];
                float dlx1 = cache.pdy[p1];
                float dly1 = -cache.pdx[p1];
                float dmx = (dlx0 + dlx1) * 0.5F;
                float dmy = (dly0 + dly1) * 0.5F;
                float dmr2 = dmx * dmx + dmy * dmy;
                if (dmr2 > 0.000001F) {
                    float scale = 1.0F / dmr2;
                    if (scale > 600.0F) {
                        scale = 600.0F;
                    }
                    dmx *= scale;
                    dmy *= scale;
                }
                cache.pdmx[p1] = dmx;
                cache.pdmy[p1] = dmy;

                int flags = (cache.pflags[p1] & PT_CORNER) != 0 ? PT_CORNER : 0;

                float cross = cache.pdx[p1] * cache.pdy[p0] - cache.pdx[p0] * cache.pdy[p1];
                if (cross > 0.0F) {
                    nleft++;
                    flags |= PT_LEFT;
                }

                float limit = Math.max(1.01F, Math.min(cache.plen[p0], cache.plen[p1]) * iw);
                if ((dmr2 * limit * limit) < 1.0F) {
                    flags |= PT_INNERBEVEL;
                }

                if ((flags & PT_CORNER) != 0) {
                    if ((dmr2 * miterLimit * miterLimit) < 1.0F || lineJoin == VG_BEVEL || lineJoin == VG_ROUND) {
                        flags |= PT_BEVEL;
                    }
                }

                if ((flags & (PT_BEVEL | PT_INNERBEVEL)) != 0) {
                    path.nbevel++;
                }
                cache.pflags[p1] = flags;

                p0 = p1++;
            }

            path.convex = nleft == path.count;
        }
    }

    private static int vset(float[] v, int index, float x, float y, float u, float t) {
        int base = index * 4;
        v[base] = x;
        v[base + 1] = y;
        v[base + 2] = u;
        v[base + 3] = t;
        return index + 1;
    }

    private static void chooseBevel(PathCache c, boolean bevel, int p0, int p1, float w, float[] out) {
        if (bevel) {
            out[0] = c.px[p1] + c.pdy[p0] * w;
            out[1] = c.py[p1] - c.pdx[p0] * w;
            out[2] = c.px[p1] + c.pdy[p1] * w;
            out[3] = c.py[p1] - c.pdx[p1] * w;
        } else {
            out[0] = c.px[p1] + c.pdmx[p1] * w;
            out[1] = c.py[p1] + c.pdmy[p1] * w;
            out[2] = c.px[p1] + c.pdmx[p1] * w;
            out[3] = c.py[p1] + c.pdmy[p1] * w;
        }
    }

    private static int roundJoin(PathCache c, int dst, int p0, int p1, float lw, float rw, float lu, float ru, int ncap) {
        float[] v = c.verts;
        float dlx0 = c.pdy[p0];
        float dly0 = -c.pdx[p0];
        float dlx1 = c.pdy[p1];
        float dly1 = -c.pdx[p1];
        float x = c.px[p1];
        float y = c.py[p1];
        float[] b = new float[4];

        if ((c.pflags[p1] & PT_LEFT) != 0) {
            chooseBevel(c, (c.pflags[p1] & PT_INNERBEVEL) != 0, p0, p1, lw, b);
            float a0 = (float) Math.atan2(-dly0, -dlx0);
            float a1 = (float) Math.atan2(-dly1, -dlx1);
            if (a1 > a0) a1 -= PI * 2;

            dst = vset(v, dst, b[0], b[1], lu, 1);
            dst = vset(v, dst, x - dlx0 * rw, y - dly0 * rw, ru, 1);

            int n = Math.max(2, Math.min(ncap, (int) Math.ceil(((a0 - a1) / PI) * ncap)));
            for (int i = 0; i < n; i++) {
                float u = i / (float) (n - 1);
                float a = a0 + u * (a1 - a0);
                float rx = x + (float) Math.cos(a) * rw;
                float ry = y + (float) Math.sin(a) * rw;
                dst = vset(v, dst, x, y, 0.5F, 1);
                dst = vset(v, dst, rx, ry, ru, 1);
            }

            dst = vset(v, dst, b[2], b[3], lu, 1);
            dst = vset(v, dst, x - dlx1 * rw, y - dly1 * rw, ru, 1);
        } else {
            chooseBevel(c, (c.pflags[p1] & PT_INNERBEVEL) != 0, p0, p1, -rw, b);
            float a0 = (float) Math.atan2(dly0, dlx0);
            float a1 = (float) Math.atan2(dly1, dlx1);
            if (a1 < a0) a1 += PI * 2;

            dst = vset(v, dst, x + dlx0 * rw, y + dly0 * rw, lu, 1);
            dst = vset(v, dst, b[0], b[1], ru, 1);

            int n = Math.max(2, Math.min(ncap, (int) Math.ceil(((a1 - a0) / PI) * ncap)));
            for (int i = 0; i < n; i++) {
                float u = i / (float) (n - 1);
                float a = a0 + u * (a1 - a0);
                float lx = x + (float) Math.cos(a) * lw;
                float ly = y + (float) Math.sin(a) * lw;
                dst = vset(v, dst, lx, ly, lu, 1);
                dst = vset(v, dst, x, y, 0.5F, 1);
            }

            dst = vset(v, dst, x + dlx1 * rw, y + dly1 * rw, lu, 1);
            dst = vset(v, dst, b[2], b[3], ru, 1);
        }
        return dst;
    }

    private static int bevelJoin(PathCache c, int dst, int p0, int p1, float lw, float rw, float lu, float ru) {
        float[] v = c.verts;
        float dlx0 = c.pdy[p0];
        float dly0 = -c.pdx[p0];
        float dlx1 = c.pdy[p1];
        float dly1 = -c.pdx[p1];
        float x = c.px[p1];
        float y = c.py[p1];
        int flags = c.pflags[p1];
        float[] b = new float[4];

        if ((flags & PT_LEFT) != 0) {
            chooseBevel(c, (flags & PT_INNERBEVEL) != 0, p0, p1, lw, b);
            float lx0 = b[0];
            float ly0 = b[1];
            float lx1 = b[2];
            float ly1 = b[3];

            dst = vset(v, dst, lx0, ly0, lu, 1);
            dst = vset(v, dst, x - dlx0 * rw, y - dly0 * rw, ru, 1);

            if ((flags & PT_BEVEL) != 0) {
                dst = vset(v, dst, lx0, ly0, lu, 1);
                dst = vset(v, dst, x - dlx0 * rw, y - dly0 * rw, ru, 1);

                dst = vset(v, dst, lx1, ly1, lu, 1);
                dst = vset(v, dst, x - dlx1 * rw, y - dly1 * rw, ru, 1);
            } else {
                float rx0 = x - c.pdmx[p1] * rw;
                float ry0 = y - c.pdmy[p1] * rw;

                dst = vset(v, dst, x, y, 0.5F, 1);
                dst = vset(v, dst, x - dlx0 * rw, y - dly0 * rw, ru, 1);

                dst = vset(v, dst, rx0, ry0, ru, 1);
                dst = vset(v, dst, rx0, ry0, ru, 1);

                dst = vset(v, dst, x, y, 0.5F, 1);
                dst = vset(v, dst, x - dlx1 * rw, y - dly1 * rw, ru, 1);
            }

            dst = vset(v, dst, lx1, ly1, lu, 1);
            dst = vset(v, dst, x - dlx1 * rw, y - dly1 * rw, ru, 1);
        } else {
            chooseBevel(c, (flags & PT_INNERBEVEL) != 0, p0, p1, -rw, b);
            float rx0 = b[0];
            float ry0 = b[1];
            float rx1 = b[2];
            float ry1 = b[3];

            dst = vset(v, dst, x + dlx0 * lw, y + dly0 * lw, lu, 1);
            dst = vset(v, dst, rx0, ry0, ru, 1);

            if ((flags & PT_BEVEL) != 0) {
                dst = vset(v, dst, x + dlx0 * lw, y + dly0 * lw, lu, 1);
                dst = vset(v, dst, rx0, ry0, ru, 1);

                dst = vset(v, dst, x + dlx1 * lw, y + dly1 * lw, lu, 1);
                dst = vset(v, dst, rx1, ry1, ru, 1);
            } else {
                float lx0 = x + c.pdmx[p1] * lw;
                float ly0 = y + c.pdmy[p1] * lw;

                dst = vset(v, dst, x + dlx0 * lw, y + dly0 * lw, lu, 1);
                dst = vset(v, dst, x, y, 0.5F, 1);

                dst = vset(v, dst, lx0, ly0, lu, 1);
                dst = vset(v, dst, lx0, ly0, lu, 1);

                dst = vset(v, dst, x + dlx1 * lw, y + dly1 * lw, lu, 1);
                dst = vset(v, dst, x, y, 0.5F, 1);
            }

            dst = vset(v, dst, x + dlx1 * lw, y + dly1 * lw, lu, 1);
            dst = vset(v, dst, rx1, ry1, ru, 1);
        }
        return dst;
    }

    private static int buttCapStart(PathCache c, int dst, int p, float dx, float dy, float w, float d, float aa, float u0, float u1) {
        float[] v = c.verts;
        float px = c.px[p] - dx * d;
        float py = c.py[p] - dy * d;
        float dlx = dy;
        float dly = -dx;
        dst = vset(v, dst, px + dlx * w - dx * aa, py + dly * w - dy * aa, u0, 0);
        dst = vset(v, dst, px - dlx * w - dx * aa, py - dly * w - dy * aa, u1, 0);
        dst = vset(v, dst, px + dlx * w, py + dly * w, u0, 1);
        dst = vset(v, dst, px - dlx * w, py - dly * w, u1, 1);
        return dst;
    }

    private static int buttCapEnd(PathCache c, int dst, int p, float dx, float dy, float w, float d, float aa, float u0, float u1) {
        float[] v = c.verts;
        float px = c.px[p] + dx * d;
        float py = c.py[p] + dy * d;
        float dlx = dy;
        float dly = -dx;
        dst = vset(v, dst, px + dlx * w, py + dly * w, u0, 1);
        dst = vset(v, dst, px - dlx * w, py - dly * w, u1, 1);
        dst = vset(v, dst, px + dlx * w + dx * aa, py + dly * w + dy * aa, u0, 0);
        dst = vset(v, dst, px - dlx * w + dx * aa, py - dly * w + dy * aa, u1, 0);
        return dst;
    }

    private static int roundCapStart(PathCache c, int dst, int p, float dx, float dy, float w, int ncap, float u0, float u1) {
        float[] v = c.verts;
        float px = c.px[p];
        float py = c.py[p];
        float dlx = dy;
        float dly = -dx;
        for (int i = 0; i < ncap; i++) {
            float a = i / (float) (ncap - 1) * PI;
            float ax = (float) Math.cos(a) * w;
            float ay = (float) Math.sin(a) * w;
            dst = vset(v, dst, px - dlx * ax - dx * ay, py - dly * ax - dy * ay, u0, 1);
            dst = vset(v, dst, px, py, 0.5F, 1);
        }
        dst = vset(v, dst, px + dlx * w, py + dly * w, u0, 1);
        dst = vset(v, dst, px - dlx * w, py - dly * w, u1, 1);
        return dst;
    }

    private static int roundCapEnd(PathCache c, int dst, int p, float dx, float dy, float w, int ncap, float u0, float u1) {
        float[] v = c.verts;
        float px = c.px[p];
        float py = c.py[p];
        float dlx = dy;
        float dly = -dx;
        dst = vset(v, dst, px + dlx * w, py + dly * w, u0, 1);
        dst = vset(v, dst, px - dlx * w, py - dly * w, u1, 1);
        for (int i = 0; i < ncap; i++) {
            float a = i / (float) (ncap - 1) * PI;
            float ax = (float) Math.cos(a) * w;
            float ay = (float) Math.sin(a) * w;
            dst = vset(v, dst, px, py, 0.5F, 1);
            dst = vset(v, dst, px - dlx * ax + dx * ay, py - dly * ax + dy * ay, u0, 1);
        }
        return dst;
    }

    private static void expandStroke(Context context, float w, float fringe, int lineCap, int lineJoin, float miterLimit) {
        PathCache cache = context.cache;
        float aa = fringe;
        float u0 = 0.0F;
        float u1 = 1.0F;
        int ncap = curveDivs(w, PI, context.tessTol);

        w += aa * 0.5F;

        if (aa == 0.0F) {
            u0 = 0.5F;
            u1 = 0.5F;
        }

        calculateJoins(context, w, lineJoin, miterLimit);

        int cverts = 0;
        for (int i = 0; i < cache.npaths; i++) {
            Path path = cache.paths[i];
            boolean loop = path.closed;
            if (lineJoin == VG_ROUND) {
                cverts += (path.count + path.nbevel * (ncap + 2) + 1) * 2;
            } else {
                cverts += (path.count + path.nbevel * 5 + 1) * 2;
            }
            if (!loop) {
                if (lineCap == VG_ROUND) {
                    cverts += (ncap * 2 + 2) * 2;
                } else {
                    cverts += (3 + 3) * 2;
                }
            }
        }
        cache.ensureVerts(cverts);

        int verts = 0;
        for (int i = 0; i < cache.npaths; i++) {
            Path path = cache.paths[i];
            int first = path.first;
            boolean loop = path.closed;

            path.fill = 0;
            path.nfill = 0;

            int dst = verts;
            path.stroke = dst;

            int p0;
            int p1;
            int s;
            int e;
            if (loop) {
                p0 = first + path.count - 1;
                p1 = first;
                s = 0;
                e = path.count;
            } else {
                p0 = first;
                p1 = first + 1;
                s = 1;
                e = path.count - 1;
            }

            if (!loop) {
                float dx = cache.px[p1] - cache.px[p0];
                float dy = cache.py[p1] - cache.py[p0];
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > 1e-6F) {
                    float id = 1.0F / d;
                    dx *= id;
                    dy *= id;
                }
                if (lineCap == VG_BUTT) {
                    dst = buttCapStart(cache, dst, p0, dx, dy, w, -aa * 0.5F, aa, u0, u1);
                } else if (lineCap == VG_SQUARE) {
                    dst = buttCapStart(cache, dst, p0, dx, dy, w, w - aa, aa, u0, u1);
                } else if (lineCap == VG_ROUND) {
                    dst = roundCapStart(cache, dst, p0, dx, dy, w, ncap, u0, u1);
                }
            }

            for (int j = s; j < e; ++j) {
                if ((cache.pflags[p1] & (PT_BEVEL | PT_INNERBEVEL)) != 0) {
                    if (lineJoin == VG_ROUND) {
                        dst = roundJoin(cache, dst, p0, p1, w, w, u0, u1, ncap);
                    } else {
                        dst = bevelJoin(cache, dst, p0, p1, w, w, u0, u1);
                    }
                } else {
                    dst = vset(cache.verts, dst, cache.px[p1] + (cache.pdmx[p1] * w), cache.py[p1] + (cache.pdmy[p1] * w), u0, 1);
                    dst = vset(cache.verts, dst, cache.px[p1] - (cache.pdmx[p1] * w), cache.py[p1] - (cache.pdmy[p1] * w), u1, 1);
                }
                p0 = p1++;
            }

            if (loop) {
                float[] v = cache.verts;
                dst = vset(v, dst, v[verts * 4], v[verts * 4 + 1], u0, 1);
                dst = vset(v, dst, v[(verts + 1) * 4], v[(verts + 1) * 4 + 1], u1, 1);
            } else {
                float dx = cache.px[p1] - cache.px[p0];
                float dy = cache.py[p1] - cache.py[p0];
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > 1e-6F) {
                    float id = 1.0F / d;
                    dx *= id;
                    dy *= id;
                }
                if (lineCap == VG_BUTT) {
                    dst = buttCapEnd(cache, dst, p1, dx, dy, w, -aa * 0.5F, aa, u0, u1);
                } else if (lineCap == VG_SQUARE) {
                    dst = buttCapEnd(cache, dst, p1, dx, dy, w, w - aa, aa, u0, u1);
                } else if (lineCap == VG_ROUND) {
                    dst = roundCapEnd(cache, dst, p1, dx, dy, w, ncap, u0, u1);
                }
            }

            path.nstroke = dst - verts;
            verts = dst;
        }
    }

    private static void expandFill(Context context, float w, int lineJoin, float miterLimit) {
        PathCache cache = context.cache;
        float aa = context.fringeWidth;
        boolean fringe = w > 0.0F;

        calculateJoins(context, w, lineJoin, miterLimit);

        int cverts = 0;
        for (int i = 0; i < cache.npaths; i++) {
            Path path = cache.paths[i];
            cverts += path.count + path.nbevel + 1;
            if (fringe) {
                cverts += (path.count + path.nbevel * 5 + 1) * 2;
            }
        }
        cache.ensureVerts(cverts);
        float[] v = cache.verts;

        boolean convex = cache.npaths == 1 && cache.paths[0].convex;

        int verts = 0;
        for (int i = 0; i < cache.npaths; i++) {
            Path path = cache.paths[i];
            int first = path.first;

            float woff = 0.5F * aa;
            int dst = verts;
            path.fill = dst;

            if (fringe) {
                int p0 = first + path.count - 1;
                int p1 = first;
                for (int j = 0; j < path.count; ++j) {
                    if ((cache.pflags[p1] & PT_BEVEL) != 0) {
                        float dlx0 = cache.pdy[p0];
                        float dly0 = -cache.pdx[p0];
                        float dlx1 = cache.pdy[p1];
                        float dly1 = -cache.pdx[p1];
                        if ((cache.pflags[p1] & PT_LEFT) != 0) {
                            float lx = cache.px[p1] + cache.pdmx[p1] * woff;
                            float ly = cache.py[p1] + cache.pdmy[p1] * woff;
                            dst = vset(v, dst, lx, ly, 0.5F, 1);
                        } else {
                            float lx0 = cache.px[p1] + dlx0 * woff;
                            float ly0 = cache.py[p1] + dly0 * woff;
                            float lx1 = cache.px[p1] + dlx1 * woff;
                            float ly1 = cache.py[p1] + dly1 * woff;
                            dst = vset(v, dst, lx0, ly0, 0.5F, 1);
                            dst = vset(v, dst, lx1, ly1, 0.5F, 1);
                        }
                    } else {
                        dst = vset(v, dst, cache.px[p1] + (cache.pdmx[p1] * woff), cache.py[p1] + (cache.pdmy[p1] * woff), 0.5F, 1);
                    }
                    p0 = p1++;
                }
            } else {
                for (int j = 0; j < path.count; ++j) {
                    dst = vset(v, dst, cache.px[first + j], cache.py[first + j], 0.5F, 1);
                }
            }

            path.nfill = dst - verts;
            verts = dst;

            if (fringe) {
                float lw = w + woff;
                float rw = w - woff;
                float lu = 0;
                float ru = 1;
                dst = verts;
                path.stroke = dst;

                if (convex) {
                    lw = woff;
                    lu = 0.5F;
                }

                int p0 = first + path.count - 1;
                int p1 = first;

                for (int j = 0; j < path.count; ++j) {
                    if ((cache.pflags[p1] & (PT_BEVEL | PT_INNERBEVEL)) != 0) {
                        dst = bevelJoin(cache, dst, p0, p1, lw, rw, lu, ru);
                    } else {
                        dst = vset(v, dst, cache.px[p1] + (cache.pdmx[p1] * lw), cache.py[p1] + (cache.pdmy[p1] * lw), lu, 1);
                        dst = vset(v, dst, cache.px[p1] - (cache.pdmx[p1] * rw), cache.py[p1] - (cache.pdmy[p1] * rw), ru, 1);
                    }
                    p0 = p1++;
                }

                dst = vset(v, dst, v[verts * 4], v[verts * 4 + 1], lu, 1);
                dst = vset(v, dst, v[(verts + 1) * 4], v[(verts + 1) * 4 + 1], ru, 1);

                path.nstroke = dst - verts;
                verts = dst;
            } else {
                path.stroke = 0;
                path.nstroke = 0;
            }
        }
    }

    private static float averageScale(float[] t) {
        float sx = (float) Math.sqrt(t[0] * t[0] + t[2] * t[2]);
        float sy = (float) Math.sqrt(t[1] * t[1] + t[3] * t[3]);
        return (sx + sy) * 0.5F;
    }

    public static void vgFill(long handle) {
        Context context = ctx(handle);
        State state = context.state();
        VGPaint fillPaint = new VGPaint().set(state.fill);

        flattenPaths(context);
        if ((context.flags & VG_ANTIALIAS) != 0 && state.shapeAntiAlias) {
            expandFill(context, context.fringeWidth, VG_MITER, 2.4F);
        } else {
            expandFill(context, 0.0F, VG_MITER, 2.4F);
        }

        fillPaint.innerColor.a(fillPaint.innerColor.a() * state.alpha);
        fillPaint.outerColor.a(fillPaint.outerColor.a() * state.alpha);

        context.gl.renderFill(fillPaint, state.scissorXform, state.scissorExtent, context.fringeWidth,
                context.cache.bounds, context.cache);
    }

    public static void vgStroke(long handle) {
        Context context = ctx(handle);
        State state = context.state();
        float scale = averageScale(state.xform);
        float strokeWidth = Math.max(0.0F, Math.min(200.0F, state.strokeWidth * scale));
        VGPaint strokePaint = new VGPaint().set(state.stroke);

        if (strokeWidth < context.fringeWidth) {
            float alpha = Math.max(0.0F, Math.min(1.0F, strokeWidth / context.fringeWidth));
            strokePaint.innerColor.a(strokePaint.innerColor.a() * alpha * alpha);
            strokePaint.outerColor.a(strokePaint.outerColor.a() * alpha * alpha);
            strokeWidth = context.fringeWidth;
        }

        strokePaint.innerColor.a(strokePaint.innerColor.a() * state.alpha);
        strokePaint.outerColor.a(strokePaint.outerColor.a() * state.alpha);

        flattenPaths(context);

        if ((context.flags & VG_ANTIALIAS) != 0 && state.shapeAntiAlias) {
            expandStroke(context, strokeWidth * 0.5F, context.fringeWidth, state.lineCap, state.lineJoin, state.miterLimit);
        } else {
            expandStroke(context, strokeWidth * 0.5F, 0.0F, state.lineCap, state.lineJoin, state.miterLimit);
        }

        context.gl.renderStroke(strokePaint, state.scissorXform, state.scissorExtent, context.fringeWidth,
                strokeWidth, context.cache);
    }

    public static int vgCreateFontMem(long handle, String name, ByteBuffer data, boolean freeData) {
        return ctx(handle).fonts.addFont(name, data, freeData);
    }

    public static int vgFindFont(long handle, String name) {
        return ctx(handle).fonts.findFont(name);
    }

    public static void vgFontSize(long handle, float size) {
        ctx(handle).state().fontSize = size;
    }

    public static void vgFontBlur(long handle, float blur) {
        ctx(handle).state().fontBlur = blur;
    }

    public static void vgTextLetterSpacing(long handle, float spacing) {
        ctx(handle).state().letterSpacing = spacing;
    }

    public static void vgTextLineHeight(long handle, float lineHeight) {
        ctx(handle).state().lineHeight = lineHeight;
    }

    public static void vgTextAlign(long handle, int align) {
        ctx(handle).state().textAlign = align;
    }

    public static void vgFontFaceId(long handle, int font) {
        ctx(handle).state().fontId = font;
    }

    public static void vgFontFace(long handle, String font) {
        Context context = ctx(handle);
        context.state().fontId = context.fonts.findFont(font);
    }

    private static float quantize(float a, float d) {
        return ((int) (a / d + 0.5F)) * d;
    }

    private static float fontScale(State state) {
        return Math.min(quantize(averageScale(state.xform), 0.01F), 4.0F);
    }

    private static void applyFontState(Context context, State state, float scale) {
        VGFonts fonts = context.fonts;
        fonts.size = state.fontSize * scale;
        fonts.spacing = state.letterSpacing * scale;
        fonts.blur = state.fontBlur * scale;
        fonts.align = state.textAlign;
        fonts.font = state.fontId;
    }

    private static void uploadFontAtlas(Context context) {
        VGFonts fonts = context.fonts;
        if (context.fontImage == 0 || !fonts.isDirty()) {
            return;
        }
        int[] d = fonts.dirty;
        ByteBuffer atlas = fonts.atlas;
        long glyphArea = 0;
        for (int i = 0; i < fonts.ndirtyRects; i++) {
            glyphArea += (long) fonts.dirtyRects[i * 4 + 2] * fonts.dirtyRects[i * 4 + 3];
        }
        long boxArea = (long) (d[2] - d[0]) * (d[3] - d[1]);
        if (fonts.ndirtyRects > 0 && fonts.ndirtyRects <= 512 && glyphArea * 2 < boxArea) {
            for (int i = 0; i < fonts.ndirtyRects; i++) {
                int r = i * 4;
                context.gl.updateTexture(context.fontImage, fonts.dirtyRects[r], fonts.dirtyRects[r + 1],
                        fonts.dirtyRects[r + 2], fonts.dirtyRects[r + 3], atlas);
            }
        } else {
            context.gl.updateTexture(context.fontImage, d[0], d[1], d[2] - d[0], d[3] - d[1], atlas);
        }
        fonts.clearDirty();
    }

    private static boolean ensureFontImage(Context context) {
        if (context.fontImage != 0) {
            return true;
        }
        context.fonts.resetAtlas();
        context.fontImage = context.spareFontImages.isEmpty()
                ? context.gl.createTexture(VGGL.TEXTURE_ALPHA, VGFonts.ATLAS_SIZE, VGFonts.ATLAS_SIZE, 0, null)
                : context.spareFontImages.remove(context.spareFontImages.size() - 1);
        return context.fontImage != 0;
    }

    private static boolean allocTextAtlas(Context context) {
        if (context.retiredFontImages.size() >= MAX_FONT_IMAGES - 1) {
            return false;
        }
        uploadFontAtlas(context);
        if (context.fontImage != 0) {
            context.retiredFontImages.add(context.fontImage);
        }
        context.fontImage = 0;
        return ensureFontImage(context);
    }

    private static void renderText(Context context, float[] verts, int nverts) {
        State state = context.state();
        VGPaint paint = new VGPaint().set(state.fill);
        paint.image = context.fontImage;
        paint.innerColor.a(paint.innerColor.a() * state.alpha);
        paint.outerColor.a(paint.outerColor.a() * state.alpha);
        context.gl.renderTriangles(paint, state.scissorXform, state.scissorExtent, verts, nverts, context.fringeWidth);
    }

    public static float vgText(long handle, float x, float y, String string) {
        Context context = ctx(handle);
        State state = context.state();
        if (state.fontId == VGFonts.INVALID || string == null) {
            return x;
        }
        if (!ensureFontImage(context)) {
            return x;
        }

        float scale = fontScale(state) * context.devicePxRatio;
        float invscale = 1.0F / scale;
        applyFontState(context, state, scale);

        int cverts = Math.max(2, string.length()) * 6;
        if (context.textVerts.length < cverts * 4) {
            context.textVerts = new float[cverts * 4];
        }
        float[] verts = context.textVerts;
        int nverts = 0;

        VGFonts.TextIter iter = new VGFonts.TextIter();
        VGFonts.TextIter prevIter = new VGFonts.TextIter();
        VGFonts.Quad q = new VGFonts.Quad();
        if (!context.fonts.iterInit(iter, x * scale, y * scale, string, true)) {
            return x;
        }
        if (!VGFonts.isRenderable(iter)) {
            return iter.nextx / scale;
        }
        VGFonts.copyIter(prevIter, iter);
        float[] c = new float[8];
        float[] xf = state.xform;
        while (context.fonts.iterNext(iter, q)) {
            if (iter.prevGlyphIndex == -1) {
                if (nverts != 0) {
                    uploadFontAtlas(context);
                    renderText(context, verts, nverts);
                    nverts = 0;
                }
                if (!allocTextAtlas(context)) {
                    break;
                }
                VGFonts.copyIter(iter, prevIter);
                context.fonts.iterNext(iter, q);
                if (iter.prevGlyphIndex == -1) {
                    break;
                }
            }
            VGFonts.copyIter(prevIter, iter);

            transformPoint(xf, q.x0 * invscale, q.y0 * invscale, c, 0);
            transformPoint(xf, q.x1 * invscale, q.y0 * invscale, c, 2);
            transformPoint(xf, q.x1 * invscale, q.y1 * invscale, c, 4);
            transformPoint(xf, q.x0 * invscale, q.y1 * invscale, c, 6);
            if (nverts + 6 <= cverts) {
                nverts = vset(verts, nverts, c[0], c[1], q.s0, q.t0);
                nverts = vset(verts, nverts, c[4], c[5], q.s1, q.t1);
                nverts = vset(verts, nverts, c[2], c[3], q.s1, q.t0);
                nverts = vset(verts, nverts, c[0], c[1], q.s0, q.t0);
                nverts = vset(verts, nverts, c[6], c[7], q.s0, q.t1);
                nverts = vset(verts, nverts, c[4], c[5], q.s1, q.t1);
            }
        }

        if (nverts > 0) {
            renderText(context, verts, nverts);
        }
        return iter.nextx / scale;
    }

    public static float vgTextBounds(long handle, float x, float y, String string, float[] bounds) {
        Context context = ctx(handle);
        State state = context.state();
        if (state.fontId == VGFonts.INVALID || string == null) {
            return 0.0F;
        }
        float scale = fontScale(state) * context.devicePxRatio;
        float invscale = 1.0F / scale;
        applyFontState(context, state, scale);

        float width = context.fonts.textBounds(x * scale, y * scale, string, bounds);
        if (bounds != null) {
            context.fonts.lineBounds(y * scale, bounds);
            bounds[0] *= invscale;
            bounds[1] *= invscale;
            bounds[2] *= invscale;
            bounds[3] *= invscale;
        }
        return width * invscale;
    }
}
