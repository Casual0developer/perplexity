package dev.perplexity.client.render.vg;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;

import java.awt.Color;
import java.nio.ByteBuffer;

import static dev.perplexity.client.render.vg.VG.VG_ALIGN_LEFT;
import static dev.perplexity.client.render.vg.VG.VG_ALIGN_TOP;
import static dev.perplexity.client.render.vg.VG.VG_HOLE;
import static dev.perplexity.client.render.vg.VG.VG_ROUND;
import static dev.perplexity.client.render.vg.VG.vgBeginFrame;
import static dev.perplexity.client.render.vg.VG.vgBeginPath;
import static dev.perplexity.client.render.vg.VG.vgBoxGradient;
import static dev.perplexity.client.render.vg.VG.vgCircle;
import static dev.perplexity.client.render.vg.VG.vgClosePath;
import static dev.perplexity.client.render.vg.VG.vgEndFrame;
import static dev.perplexity.client.render.vg.VG.vgFill;
import static dev.perplexity.client.render.vg.VG.vgFillColor;
import static dev.perplexity.client.render.vg.VG.vgFillPaint;
import static dev.perplexity.client.render.vg.VG.vgFontBlur;
import static dev.perplexity.client.render.vg.VG.vgFontFace;
import static dev.perplexity.client.render.vg.VG.vgFontSize;
import static dev.perplexity.client.render.vg.VG.vgIntersectScissor;
import static dev.perplexity.client.render.vg.VG.vgLineCap;
import static dev.perplexity.client.render.vg.VG.vgLineJoin;
import static dev.perplexity.client.render.vg.VG.vgLineTo;
import static dev.perplexity.client.render.vg.VG.vgLinearGradient;
import static dev.perplexity.client.render.vg.VG.vgMoveTo;
import static dev.perplexity.client.render.vg.VG.vgPathWinding;
import static dev.perplexity.client.render.vg.VG.vgRect;
import static dev.perplexity.client.render.vg.VG.vgRadialGradient;
import static dev.perplexity.client.render.vg.VG.vgResetScissor;
import static dev.perplexity.client.render.vg.VG.vgRestore;
import static dev.perplexity.client.render.vg.VG.vgRotate;
import static dev.perplexity.client.render.vg.VG.vgRoundedRect;
import static dev.perplexity.client.render.vg.VG.vgRoundedRectVarying;
import static dev.perplexity.client.render.vg.VG.vgSave;
import static dev.perplexity.client.render.vg.VG.vgScale;
import static dev.perplexity.client.render.vg.VG.vgScissor;
import static dev.perplexity.client.render.vg.VG.vgStroke;
import static dev.perplexity.client.render.vg.VG.vgStrokeColor;
import static dev.perplexity.client.render.vg.VG.vgStrokeWidth;
import static dev.perplexity.client.render.vg.VG.vgText;
import static dev.perplexity.client.render.vg.VG.vgTextAlign;
import static dev.perplexity.client.render.vg.VG.vgTextBounds;
import static dev.perplexity.client.render.vg.VG.vgTextLetterSpacing;
import static dev.perplexity.client.render.vg.VG.vgTextLineHeight;
import static dev.perplexity.client.render.vg.VG.vgTranslate;

public final class VGRenderer {
    private static boolean inFrame;
    private static GlState savedState;
    private static int canvasWidth;
    private static int canvasHeight;

    private VGRenderer() {
    }

    public static void init() {
        boolean ownsState = !inFrame && savedState == null;
        try {
            if (ownsState) {
                captureState();
            }
            initContext();
        } finally {
            if (ownsState) {
                restoreState();
            }
        }
    }

    private static void initContext() {
        if (!VGContext.isInitialized() || !VGContext.isValid()) {
            VGContext.init();
        }

        if (VGContext.isValid()) {
            VGFont.load();
        }
    }

    public static boolean beginFrame() {
        Minecraft client = Minecraft.getInstance();
        int framebufferWidth = client == null || client.getWindow() == null
                ? canvasWidth
                : client.getWindow().getWidth();
        int framebufferHeight = client == null || client.getWindow() == null
                ? canvasHeight
                : client.getWindow().getHeight();
        return beginFrame(framebufferWidth, framebufferHeight);
    }

    public static boolean beginFrame(int framebufferWidth, int framebufferHeight) {
        if (inFrame) {
            return true;
        }
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            return false;
        }

        try {
            captureState();
            initContext();

            if (!VGContext.isValid()) {
                restoreState();
                return false;
            }

            canvasWidth = framebufferWidth;
            canvasHeight = framebufferHeight;

            GL11.glViewport(0, 0, framebufferWidth, framebufferHeight);
            GL11.glEnable(GL11.GL_BLEND);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(false);
            GL11.glStencilMask(0xFFFFFFFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFFFFFFFF);

            GL20.glUseProgram(0);
            GL30.glBindVertexArray(0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
            GL33.glBindSampler(0, 0);

            vgBeginFrame(VGContext.handle(), canvasWidth, canvasHeight, 1.0F);

            inFrame = true;
            return true;
        } catch (Throwable throwable) {
            inFrame = false;
            restoreState();
            return false;
        }
    }

    public static void endFrame() {
        if (!inFrame) {
            return;
        }

        try {
            vgEndFrame(VGContext.handle());
        } catch (Throwable ignored) {
        } finally {
            inFrame = false;
            restoreState();
        }
    }

    public static boolean isInFrame() {
        return inFrame && VGContext.isValid();
    }

    public static int canvasWidth() {
        Minecraft client = Minecraft.getInstance();
        return client == null || client.getWindow() == null
                ? Math.max(1, canvasWidth)
                : Math.max(1, client.getWindow().getWidth());
    }

    public static int canvasHeight() {
        Minecraft client = Minecraft.getInstance();
        return client == null || client.getWindow() == null
                ? Math.max(1, canvasHeight)
                : Math.max(1, client.getWindow().getHeight());
    }

    public static void roundedRect(float x, float y, float width, float height, float radius, Color color) {
        if (!isDrawable(width, height, color)) {
            return;
        }

        long vg = VGContext.handle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            vgBeginPath(vg);
            vgRoundedRect(vg, x, y, width, height, Math.max(0.0F, radius));
            vgFillColor(vg, color(stack, color));
            vgFill(vg);
        }
    }

    /**
     * Strokes a border completely <em>inside</em> the given rounded rect, leaving the shape's outer
     * silhouette untouched.
     *
     * <p>The path is inset by half the stroke and its radius is reduced by the same amount, so the
     * stroke's outer edge lands exactly on the given rect and its corner arcs share a centre with
     * the fill's. Drawn any other way the corner arc centres shift away from the fill's while
     * keeping their radius, which makes the border swell outward at the corners and only there.</p>
     */
    public static void roundedOutline(float x, float y, float width, float height, float radius, float stroke, Color color) {
        if (!isDrawable(width, height, color) || stroke <= 0.0F
                || width <= stroke || height <= stroke) {
            return;
        }

        float inset = stroke * 0.5F;
        long vg = VGContext.handle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            vgBeginPath(vg);
            vgRoundedRect(vg, x + inset, y + inset, width - stroke, height - stroke,
                    Math.max(0.0F, radius - inset));
            vgStrokeWidth(vg, stroke);
            vgStrokeColor(vg, color(stack, color));
            vgStroke(vg);
        }
    }

    public static void roundedRectVarying(float x, float y, float width, float height,
                                          float radiusTopLeft, float radiusTopRight,
                                          float radiusBottomRight, float radiusBottomLeft, Color color) {
        if (!isDrawable(width, height, color)) {
            return;
        }

        long vg = VGContext.handle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            vgBeginPath(vg);
            vgRoundedRectVarying(vg, x, y, width, height,
                    Math.max(0.0F, radiusTopLeft), Math.max(0.0F, radiusTopRight),
                    Math.max(0.0F, radiusBottomRight), Math.max(0.0F, radiusBottomLeft));
            vgFillColor(vg, color(stack, color));
            vgFill(vg);
        }
    }

    public static void rect(float x, float y, float width, float height, Color color) {
        if (!isDrawable(width, height, color)) {
            return;
        }

        long vg = VGContext.handle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            vgBeginPath(vg);
            vgRect(vg, x, y, width, height);
            vgFillColor(vg, color(stack, color));
            vgFill(vg);
        }
    }

    public static void line(float x1, float y1, float x2, float y2, float width, Color color) {
        if (width <= 0.0F || color == null || color.getAlpha() == 0 || !isInFrame()) {
            return;
        }

        long vg = VGContext.handle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            vgBeginPath(vg);
            vgMoveTo(vg, x1, y1);
            vgLineTo(vg, x2, y2);
            vgStrokeWidth(vg, width);
            vgStrokeColor(vg, color(stack, color));
            vgStroke(vg);
        }
    }

    public static void polyline(float[] points, int pointCount, float width, boolean closed, Color color) {
        if (points == null || pointCount < 2 || points.length < pointCount * 2
                || width <= 0.0F || color == null || color.getAlpha() == 0 || !isInFrame()) {
            return;
        }

        long vg = VGContext.handle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            vgSave(vg);
            vgBeginPath(vg);
            vgMoveTo(vg, points[0], points[1]);
            for (int i = 1; i < pointCount; i++) {
                vgLineTo(vg, points[i * 2], points[i * 2 + 1]);
            }
            if (closed) {
                vgClosePath(vg);
            }
            vgLineCap(vg, VG_ROUND);
            vgLineJoin(vg, VG_ROUND);
            vgStrokeWidth(vg, width);
            vgStrokeColor(vg, color(stack, color));
            vgStroke(vg);
            vgRestore(vg);
        }
    }

    public static void polygon(float[] points, Color color) {
        if (points == null || points.length < 6 || color == null || color.getAlpha() == 0 || !isInFrame()) {
            return;
        }

        long vg = VGContext.handle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            vgBeginPath(vg);
            vgMoveTo(vg, points[0], points[1]);
            for (int i = 2; i < points.length; i += 2) {
                vgLineTo(vg, points[i], points[i + 1]);
            }
            vgFillColor(vg, color(stack, color));
            vgFill(vg);
        }
    }

    public static void circle(float x, float y, float radius, Color color) {
        if (radius <= 0.0F || color == null || color.getAlpha() == 0 || !isInFrame()) {
            return;
        }

        long vg = VGContext.handle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            vgBeginPath(vg);
            vgCircle(vg, x, y, radius);
            vgFillColor(vg, color(stack, color));
            vgFill(vg);
        }
    }

    public static void circleOutline(float x, float y, float radius, float stroke, Color color) {
        if (radius <= 0.0F || stroke <= 0.0F || color == null || color.getAlpha() == 0 || !isInFrame()) {
            return;
        }

        long vg = VGContext.handle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            vgBeginPath(vg);
            vgCircle(vg, x, y, radius);
            vgStrokeWidth(vg, stroke);
            vgStrokeColor(vg, color(stack, color));
            vgStroke(vg);
        }
    }

    public static void verticalGradient(float x, float y, float width, float height, float radius, Color top, Color bottom) {
        if (width <= 0.0F || height <= 0.0F || top == null || bottom == null || !isInFrame()) {
            return;
        }

        long vg = VGContext.handle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VGPaint paint = vgLinearGradient(vg, x, y, x, y + height, color(stack, top), color(stack, bottom), new VGPaint());

            vgBeginPath(vg);
            vgRoundedRect(vg, x, y, width, height, Math.max(0.0F, radius));
            vgFillPaint(vg, paint);
            vgFill(vg);
        }
    }

    public static void horizontalGradient(float x, float y, float width, float height, float radius, Color left, Color right) {
        if (width <= 0.0F || height <= 0.0F || left == null || right == null || !isInFrame()) {
            return;
        }

        long vg = VGContext.handle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VGPaint paint = vgLinearGradient(vg, x, y, x + width, y, color(stack, left), color(stack, right), new VGPaint());

            vgBeginPath(vg);
            vgRoundedRect(vg, x, y, width, height, Math.max(0.0F, radius));
            vgFillPaint(vg, paint);
            vgFill(vg);
        }
    }

    public static void radialGradient(float centerX, float centerY, float innerRadius, float outerRadius, Color inner, Color outer) {
        if (outerRadius <= 0.0F || inner == null || outer == null || !isInFrame()) {
            return;
        }

        long vg = VGContext.handle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VGPaint paint = vgRadialGradient(vg, centerX, centerY, Math.max(0.0F, innerRadius), outerRadius,
                    color(stack, inner), color(stack, outer), new VGPaint());

            vgBeginPath(vg);
            vgCircle(vg, centerX, centerY, outerRadius);
            vgFillPaint(vg, paint);
            vgFill(vg);
        }
    }

    public static void shadow(float x, float y, float width, float height, float radius, float blur, Color color) {
        if (width <= 0.0F || height <= 0.0F || color == null || color.getAlpha() == 0 || !isInFrame()) {
            return;
        }

        long vg = VGContext.handle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VGColor inner = color(stack, color);
            VGColor outer = color(stack, new Color(color.getRed(), color.getGreen(), color.getBlue(), 0));
            VGPaint paint = vgBoxGradient(vg, x, y, width, height, radius, blur, inner, outer, new VGPaint());

            vgBeginPath(vg);
            vgRect(vg, x - blur, y - blur, width + blur * 2.0F, height + blur * 2.0F);
            vgRoundedRect(vg, x, y, width, height, radius);
            vgPathWinding(vg, VG_HOLE);
            vgFillPaint(vg, paint);
            vgFill(vg);
        }
    }

    public static void text(String text, float x, float y, float size, Color color) {
        text(text, x, y, size, VGFont.face(), 0.0F, 0.0F, color);
    }

    public static void text(String text, float x, float y, float size, String face, float embolden, float spacing, Color color) {
        if (text == null || text.isEmpty() || color == null || color.getAlpha() == 0 || !isInFrame()) {
            return;
        }

        long vg = VGContext.handle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            vgFontFace(vg, face == null ? VGFont.face() : face);
            vgFontSize(vg, size);
            vgFontBlur(vg, 0.0F);
            vgTextLetterSpacing(vg, spacing);
            vgTextLineHeight(vg, 1.0F);
            vgTextAlign(vg, VG_ALIGN_LEFT | VG_ALIGN_TOP);
            vgFillColor(vg, color(stack, color));
            vgText(vg, x, y, text);
            if (embolden > 0.0F) {
                vgText(vg, x + embolden, y, text);
            }
        }
    }

    private static final float CAP_HEIGHT = 0.7275F;

    /**
     * Left-aligned text whose cap glyphs are vertically centred on {@code centerY}, so rows of
     * different font sizes line up on the same optical middle.
     */
    public static void textLeft(String text, float x, float centerY, float size, String face, Color color) {
        textLeft(text, x, centerY, size, face, 0.0F, color);
    }

    /** As {@link #textLeft}, with extra tracking between glyphs. */
    public static void textLeft(String text, float x, float centerY, float size, String face,
                                float spacing, Color color) {
        if (text == null || text.isEmpty() || color == null || color.getAlpha() == 0 || !isInFrame()) {
            return;
        }
        long vg = VGContext.handle();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vgFontFace(vg, face == null ? VGFont.face() : face);
            vgFontSize(vg, size);
            vgFontBlur(vg, 0.0F);
            vgTextLetterSpacing(vg, spacing);
            vgTextLineHeight(vg, 1.0F);
            vgTextAlign(vg, VG_ALIGN_LEFT | VG.VG_ALIGN_BASELINE);
            vgFillColor(vg, color(stack, color));
            vgText(vg, x + 0.5F, centerY + size * CAP_HEIGHT * 0.5F + 0.5F, text);
        }
    }

    public static void textCentered(String text, float centerX, float centerY, float size, String face, Color color) {
        if (text == null || text.isEmpty() || color == null || color.getAlpha() == 0 || !isInFrame()) {
            return;
        }
        long vg = VGContext.handle();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vgFontFace(vg, face == null ? VGFont.face() : face);
            vgFontSize(vg, size);
            vgFontBlur(vg, 0.0F);
            vgTextLetterSpacing(vg, 0.0F);
            vgTextLineHeight(vg, 1.0F);
            vgTextAlign(vg, VG.VG_ALIGN_CENTER | VG.VG_ALIGN_BASELINE);
            vgFillColor(vg, color(stack, color));
            vgText(vg, centerX + 0.5F, centerY + size * CAP_HEIGHT * 0.5F + 0.5F, text);
        }
    }

    public static float textWidth(String text, float size) {
        return textWidth(text, size, VGFont.face(), 0.0F);
    }

    public static float textWidth(String text, float size, String face, float spacing) {
        if (text == null || text.isEmpty()) {
            return 0.0F;
        }

        try {
            init();

            if (!VGContext.isValid()) {
                return text.length() * size * 0.55F;
            }

            long vg = VGContext.handle();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                float[] bounds = new float[4];

                vgFontFace(vg, face == null ? VGFont.face() : face);
                vgFontSize(vg, size);
                vgTextLetterSpacing(vg, spacing);
                vgTextBounds(vg, 0.0F, 0.0F, text, bounds);

                return Math.max(0.0F, bounds[2] - bounds[0]);
            }
        } catch (Throwable ignored) {
            return text.length() * size * 0.55F;
        }
    }

    public static void image(String path, float x, float y, float width, float height, Color tint) {
        if (!isInFrame()) {
            return;
        }

        VGImage.draw(path, x, y, width, height, tint);
    }

    public static void preloadImage(String path) {
        boolean ownsState = !inFrame && savedState == null;
        try {
            if (ownsState) {
                captureState();
            }
            initContext();
            if (VGContext.isValid()) {
                VGImage.load(path);
            }
        } finally {
            if (ownsState) {
                restoreState();
            }
        }
    }

    public static void save() {
        if (isInFrame()) {
            vgSave(VGContext.handle());
        }
    }

    public static void restore() {
        if (isInFrame()) {
            vgRestore(VGContext.handle());
        }
    }

    public static void translate(float x, float y) {
        if (isInFrame()) {
            vgTranslate(VGContext.handle(), x, y);
        }
    }

    public static void scale(float x, float y) {
        if (isInFrame()) {
            vgScale(VGContext.handle(), x, y);
        }
    }

    public static void rotate(float angle) {
        if (isInFrame()) {
            vgRotate(VGContext.handle(), angle);
        }
    }

    public static void scissor(float x, float y, float width, float height) {
        if (isInFrame()) {
            vgScissor(VGContext.handle(), x, y, width, height);
        }
    }

    public static void intersectScissor(float x, float y, float width, float height) {
        if (isInFrame()) {
            vgIntersectScissor(VGContext.handle(), x, y, width, height);
        }
    }

    public static void resetScissor() {
        if (isInFrame()) {
            vgResetScissor(VGContext.handle());
        }
    }

    private static boolean isDrawable(float width, float height, Color color) {
        return width > 0.0F && height > 0.0F && color != null && color.getAlpha() > 0 && isInFrame();
    }

    private static VGColor color(MemoryStack stack, Color color) {
        VGColor result = new VGColor();

        result.r(color.getRed() / 255.0F);
        result.g(color.getGreen() / 255.0F);
        result.b(color.getBlue() / 255.0F);
        result.a(color.getAlpha() / 255.0F);

        return result;
    }

    private static void captureState() {
        if (savedState != null) {
            return;
        }

        GlState state = new GlState();
        state.program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        state.activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        try {
            state.texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            state.sampler0 = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        } finally {
            GL13.glActiveTexture(state.activeTexture);
        }
        state.vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        state.arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        state.elementBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        state.pixelUnpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        state.uniformBuffer = GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
        state.uniformBuffer0 = GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING, 0);
        state.uniformBuffer0Start = GL32.glGetInteger64i(GL31.GL_UNIFORM_BUFFER_START, 0);
        state.uniformBuffer0Size = GL32.glGetInteger64i(GL31.GL_UNIFORM_BUFFER_SIZE, 0);
        state.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        state.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        state.blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        state.blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        state.blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        state.blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        state.blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        state.blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        state.cullFaceMode = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
        state.frontFace = GL11.glGetInteger(GL11.GL_FRONT_FACE);
        state.depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        state.stencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
        state.stencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF);
        state.stencilValueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
        state.stencilWriteMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
        state.stencilFail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
        state.stencilDepthFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
        state.stencilDepthPass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
        state.stencilBackFunc = GL11.glGetInteger(GL20.GL_STENCIL_BACK_FUNC);
        state.stencilBackRef = GL11.glGetInteger(GL20.GL_STENCIL_BACK_REF);
        state.stencilBackValueMask = GL11.glGetInteger(GL20.GL_STENCIL_BACK_VALUE_MASK);
        state.stencilBackWriteMask = GL11.glGetInteger(GL20.GL_STENCIL_BACK_WRITEMASK);
        state.stencilBackFail = GL11.glGetInteger(GL20.GL_STENCIL_BACK_FAIL);
        state.stencilBackDepthFail = GL11.glGetInteger(GL20.GL_STENCIL_BACK_PASS_DEPTH_FAIL);
        state.stencilBackDepthPass = GL11.glGetInteger(GL20.GL_STENCIL_BACK_PASS_DEPTH_PASS);
        state.unpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        state.unpackRowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        state.unpackSkipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        state.unpackSkipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, state.viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, state.scissorBox);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer colorMask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
            state.colorMaskRed = colorMask.get(0) != 0;
            state.colorMaskGreen = colorMask.get(1) != 0;
            state.colorMaskBlue = colorMask.get(2) != 0;
            state.colorMaskAlpha = colorMask.get(3) != 0;
        }
        state.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        state.blend = GL11.glIsEnabled(GL11.GL_BLEND);
        state.depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        state.cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        state.scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        state.stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        savedState = state;
    }

    private static void restoreState() {
        GlState state = savedState;
        if (state == null) {
            return;
        }

        try {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, state.drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, state.readFramebuffer);
            GL11.glViewport(state.viewport[0], state.viewport[1], state.viewport[2], state.viewport[3]);
            GL11.glScissor(state.scissorBox[0], state.scissorBox[1], state.scissorBox[2], state.scissorBox[3]);
            GL11.glColorMask(state.colorMaskRed, state.colorMaskGreen, state.colorMaskBlue, state.colorMaskAlpha);
            GL11.glDepthMask(state.depthMask);
            GL20.glBlendEquationSeparate(state.blendEquationRgb, state.blendEquationAlpha);
            GL14.glBlendFuncSeparate(state.blendSrcRgb, state.blendDstRgb, state.blendSrcAlpha, state.blendDstAlpha);
            GL11.glCullFace(state.cullFaceMode);
            GL11.glFrontFace(state.frontFace);
            GL11.glDepthFunc(state.depthFunc);
            GL20.glStencilFuncSeparate(GL11.GL_FRONT, state.stencilFunc, state.stencilRef, state.stencilValueMask);
            GL20.glStencilMaskSeparate(GL11.GL_FRONT, state.stencilWriteMask);
            GL20.glStencilOpSeparate(GL11.GL_FRONT, state.stencilFail, state.stencilDepthFail, state.stencilDepthPass);
            GL20.glStencilFuncSeparate(GL11.GL_BACK, state.stencilBackFunc, state.stencilBackRef, state.stencilBackValueMask);
            GL20.glStencilMaskSeparate(GL11.GL_BACK, state.stencilBackWriteMask);
            GL20.glStencilOpSeparate(GL11.GL_BACK, state.stencilBackFail, state.stencilBackDepthFail, state.stencilBackDepthPass);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, state.unpackAlignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, state.unpackRowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, state.unpackSkipPixels);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, state.unpackSkipRows);
            GL20.glUseProgram(state.program);
            GL30.glBindVertexArray(state.vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, state.arrayBuffer);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, state.elementBuffer);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, state.pixelUnpackBuffer);
            if (state.uniformBuffer0 == 0) {
                GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, 0, 0);
            } else {
                GL30.glBindBufferRange(GL31.GL_UNIFORM_BUFFER, 0, state.uniformBuffer0, state.uniformBuffer0Start, state.uniformBuffer0Size);
            }
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, state.uniformBuffer);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, state.texture0);
            GL33.glBindSampler(0, state.sampler0);
            GL13.glActiveTexture(state.activeTexture);
            setEnabled(GL11.GL_BLEND, state.blend);
            setEnabled(GL11.GL_DEPTH_TEST, state.depth);
            setEnabled(GL11.GL_CULL_FACE, state.cull);
            setEnabled(GL11.GL_SCISSOR_TEST, state.scissor);
            setEnabled(GL11.GL_STENCIL_TEST, state.stencil);
        } finally {
            savedState = null;
        }
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }

    private static final class GlState {
        private final int[] viewport = new int[4];
        private final int[] scissorBox = new int[4];
        private int program;
        private int activeTexture;
        private int texture0;
        private int sampler0;
        private int vao;
        private int arrayBuffer;
        private int elementBuffer;
        private int pixelUnpackBuffer;
        private int uniformBuffer;
        private int uniformBuffer0;
        private long uniformBuffer0Start;
        private long uniformBuffer0Size;
        private int drawFramebuffer;
        private int readFramebuffer;
        private int blendSrcRgb;
        private int blendDstRgb;
        private int blendSrcAlpha;
        private int blendDstAlpha;
        private int blendEquationRgb;
        private int blendEquationAlpha;
        private int cullFaceMode;
        private int frontFace;
        private int depthFunc;
        private int stencilFunc;
        private int stencilRef;
        private int stencilValueMask;
        private int stencilWriteMask;
        private int stencilFail;
        private int stencilDepthFail;
        private int stencilDepthPass;
        private int stencilBackFunc;
        private int stencilBackRef;
        private int stencilBackValueMask;
        private int stencilBackWriteMask;
        private int stencilBackFail;
        private int stencilBackDepthFail;
        private int stencilBackDepthPass;
        private int unpackAlignment;
        private int unpackRowLength;
        private int unpackSkipPixels;
        private int unpackSkipRows;
        private boolean colorMaskRed;
        private boolean colorMaskGreen;
        private boolean colorMaskBlue;
        private boolean colorMaskAlpha;
        private boolean depthMask;
        private boolean blend;
        private boolean depth;
        private boolean cull;
        private boolean scissor;
        private boolean stencil;
    }
}
