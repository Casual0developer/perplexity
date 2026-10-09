package dev.perplexity.client.render.vg;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

final class VGGL {
    static final int TEXTURE_ALPHA = 1;
    static final int TEXTURE_RGBA = 2;

    private static final int CALL_FILL = 1;
    private static final int CALL_CONVEXFILL = 2;
    private static final int CALL_STROKE = 3;
    private static final int CALL_TRIANGLES = 4;

    private static final int SHADER_FILLGRAD = 0;
    private static final int SHADER_FILLIMG = 1;
    private static final int SHADER_SIMPLE = 2;
    private static final int SHADER_IMG = 3;

    private static final int FRAG_SIZE = 44;

    private static final String VERTEX_SHADER = """
            #version 150 core
            uniform vec2 viewSize;
            in vec2 vertex;
            in vec2 tcoord;
            out vec2 ftcoord;
            out vec2 fpos;
            void main(void) {
                ftcoord = tcoord;
                fpos = vertex;
                gl_Position = vec4(2.0 * vertex.x / viewSize.x - 1.0, 1.0 - 2.0 * vertex.y / viewSize.y, 0, 1);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 150 core
            uniform vec4 frag[11];
            uniform sampler2D tex;
            in vec2 ftcoord;
            in vec2 fpos;
            out vec4 outColor;
            #define scissorMat mat3(frag[0].xyz, frag[1].xyz, frag[2].xyz)
            #define paintMat mat3(frag[3].xyz, frag[4].xyz, frag[5].xyz)
            #define innerCol frag[6]
            #define outerCol frag[7]
            #define scissorExt frag[8].xy
            #define scissorScale frag[8].zw
            #define extent frag[9].xy
            #define radius frag[9].z
            #define feather frag[9].w
            #define strokeMult frag[10].x
            #define strokeThr frag[10].y
            #define texType int(frag[10].z)
            #define type int(frag[10].w)

            float sdroundrect(vec2 pt, vec2 ext, float rad) {
                vec2 ext2 = ext - vec2(rad, rad);
                vec2 d = abs(pt) - ext2;
                return min(max(d.x, d.y), 0.0) + length(max(d, 0.0)) - rad;
            }

            float scissorMask(vec2 p) {
                vec2 sc = (abs((scissorMat * vec3(p, 1.0)).xy) - scissorExt);
                sc = vec2(0.5, 0.5) - sc * scissorScale;
                return clamp(sc.x, 0.0, 1.0) * clamp(sc.y, 0.0, 1.0);
            }

            float strokeMask() {
                return min(1.0, (1.0 - abs(ftcoord.x * 2.0 - 1.0)) * strokeMult) * min(1.0, ftcoord.y);
            }

            void main(void) {
                vec4 result;
                float scissor = scissorMask(fpos);
                float strokeAlpha = strokeMask();
                if (strokeAlpha < strokeThr) discard;
                if (type == 0) {
                    vec2 pt = (paintMat * vec3(fpos, 1.0)).xy;
                    float d = clamp((sdroundrect(pt, extent, radius) + feather * 0.5) / feather, 0.0, 1.0);
                    vec4 color = mix(innerCol, outerCol, d);
                    color *= strokeAlpha * scissor;
                    result = color;
                } else if (type == 1) {
                    vec2 pt = (paintMat * vec3(fpos, 1.0)).xy / extent;
                    vec4 color = texture(tex, pt);
                    if (texType == 1) color = vec4(color.xyz * color.w, color.w);
                    if (texType == 2) color = vec4(color.x);
                    color *= innerCol;
                    color *= strokeAlpha * scissor;
                    result = color;
                } else if (type == 2) {
                    result = vec4(1, 1, 1, 1);
                } else if (type == 3) {
                    vec4 color = texture(tex, ftcoord);
                    if (texType == 1) color = vec4(color.xyz * color.w, color.w);
                    if (texType == 2) color = vec4(color.x);
                    color *= scissor;
                    result = color * innerCol;
                }
                outColor = result;
            }
            """;

    static final class Texture {
        int id;
        int tex;
        int width;
        int height;
        int type;
        int flags;
    }

    private final int flags;
    private final Map<Integer, Texture> textures = new HashMap<>();
    private int textureId;

    private int program;
    private int locViewSize;
    private int locTex;
    private int locFrag;
    private int vao;
    private int vbo;
    private FloatBuffer vertexUpload;
    private FloatBuffer fragUpload;

    private final float[] view = new float[2];

    private int[] calls = new int[7 * 64];
    private int ncalls;
    private int[] paths = new int[4 * 128];
    private int npaths;
    private float[] verts = new float[4 * 4096];
    private int nverts;
    private float[] uniforms = new float[FRAG_SIZE * 128];
    private int nuniforms;

    VGGL(int flags) {
        this.flags = flags;
    }

    boolean create() {
        int vertexShader = 0;
        int fragmentShader = 0;
        try {
            vertexShader = compile(GL20C.GL_VERTEX_SHADER, VERTEX_SHADER);
            fragmentShader = compile(GL20C.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
            if (vertexShader == 0 || fragmentShader == 0) {
                return false;
            }
            program = GL20C.glCreateProgram();
            GL20C.glAttachShader(program, vertexShader);
            GL20C.glAttachShader(program, fragmentShader);
            GL20C.glBindAttribLocation(program, 0, "vertex");
            GL20C.glBindAttribLocation(program, 1, "tcoord");
            GL30C.glBindFragDataLocation(program, 0, "outColor");
            GL20C.glLinkProgram(program);
            if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) != GL11C.GL_TRUE) {
                GL20C.glDeleteProgram(program);
                program = 0;
                return false;
            }
            locViewSize = GL20C.glGetUniformLocation(program, "viewSize");
            locTex = GL20C.glGetUniformLocation(program, "tex");
            locFrag = GL20C.glGetUniformLocation(program, "frag");

            vao = GL30C.glGenVertexArrays();
            vbo = GL15C.glGenBuffers();
            fragUpload = MemoryUtil.memAllocFloat(FRAG_SIZE);
            vertexUpload = MemoryUtil.memAllocFloat(4 * 4096);
            return true;
        } finally {
            if (vertexShader != 0) {
                GL20C.glDeleteShader(vertexShader);
            }
            if (fragmentShader != 0) {
                GL20C.glDeleteShader(fragmentShader);
            }
        }
    }

    private static int compile(int type, String source) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) != GL11C.GL_TRUE) {
            GL20C.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    void delete() {
        if (program != 0) {
            GL20C.glDeleteProgram(program);
            program = 0;
        }
        if (vbo != 0) {
            GL15C.glDeleteBuffers(vbo);
            vbo = 0;
        }
        if (vao != 0) {
            GL30C.glDeleteVertexArrays(vao);
            vao = 0;
        }
        for (Texture texture : textures.values()) {
            if (texture.tex != 0 && (texture.flags & VG.VG_IMAGE_NODELETE) == 0) {
                GL11C.glDeleteTextures(texture.tex);
            }
        }
        textures.clear();
        if (vertexUpload != null) {
            MemoryUtil.memFree(vertexUpload);
            vertexUpload = null;
        }
        if (fragUpload != null) {
            MemoryUtil.memFree(fragUpload);
            fragUpload = null;
        }
    }

    Texture findTexture(int id) {
        return id == 0 ? null : textures.get(id);
    }

    int createTexture(int type, int width, int height, int imageFlags, ByteBuffer data) {
        Texture texture = new Texture();
        texture.id = ++textureId;
        texture.tex = GL11C.glGenTextures();
        texture.width = width;
        texture.height = height;
        texture.type = type;
        texture.flags = imageFlags;
        textures.put(texture.id, texture);

        GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture.tex);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 1);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, width);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0);

        if (type == TEXTURE_RGBA) {
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA, width, height, 0,
                    GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, data);
        } else {
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_R8, width, height, 0,
                    GL11C.GL_RED, GL11C.GL_UNSIGNED_BYTE, data);
        }

        boolean nearest = (imageFlags & VG.VG_IMAGE_NEAREST) != 0;
        int minFilter;
        if ((imageFlags & VG.VG_IMAGE_GENERATE_MIPMAPS) != 0) {
            minFilter = nearest ? GL11C.GL_NEAREST_MIPMAP_NEAREST : GL11C.GL_LINEAR_MIPMAP_LINEAR;
        } else {
            minFilter = nearest ? GL11C.GL_NEAREST : GL11C.GL_LINEAR;
        }
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, minFilter);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, nearest ? GL11C.GL_NEAREST : GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S,
                (imageFlags & VG.VG_IMAGE_REPEATX) != 0 ? GL11C.GL_REPEAT : GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T,
                (imageFlags & VG.VG_IMAGE_REPEATY) != 0 ? GL11C.GL_REPEAT : GL12C.GL_CLAMP_TO_EDGE);

        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 4);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0);

        if ((imageFlags & VG.VG_IMAGE_GENERATE_MIPMAPS) != 0) {
            GL30C.glGenerateMipmap(GL11C.GL_TEXTURE_2D);
        }
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, 0);
        return texture.id;
    }

    int wrapTexture(int glTexture, int width, int height, int imageFlags) {
        Texture texture = new Texture();
        texture.id = ++textureId;
        texture.tex = glTexture;
        texture.width = width;
        texture.height = height;
        texture.type = TEXTURE_RGBA;
        texture.flags = imageFlags;
        textures.put(texture.id, texture);
        return texture.id;
    }

    void updateTexture(int image, int x, int y, int width, int height, ByteBuffer data) {
        Texture texture = findTexture(image);
        if (texture == null) {
            return;
        }
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture.tex);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 1);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, texture.width);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, x);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, y);
        if (texture.type == TEXTURE_RGBA) {
            GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, x, y, width, height, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, data);
        } else {
            GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, x, y, width, height, GL11C.GL_RED, GL11C.GL_UNSIGNED_BYTE, data);
        }
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 4);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, 0);
    }

    boolean deleteTexture(int image) {
        Texture texture = textures.remove(image);
        if (texture == null) {
            return false;
        }
        if (texture.tex != 0 && (texture.flags & VG.VG_IMAGE_NODELETE) == 0) {
            GL11C.glDeleteTextures(texture.tex);
        }
        return true;
    }

    int[] textureSize(int image) {
        Texture texture = findTexture(image);
        return texture == null ? null : new int[]{texture.width, texture.height};
    }

    void viewport(float width, float height) {
        view[0] = width;
        view[1] = height;
    }

    void cancel() {
        ncalls = 0;
        npaths = 0;
        nverts = 0;
        nuniforms = 0;
    }

    private int allocCall() {
        if ((ncalls + 1) * 7 > calls.length) {
            calls = java.util.Arrays.copyOf(calls, calls.length * 2);
        }
        int offset = ncalls * 7;
        java.util.Arrays.fill(calls, offset, offset + 7, 0);
        ncalls++;
        return offset;
    }

    private int allocPaths(int count) {
        if ((npaths + count) * 4 > paths.length) {
            paths = java.util.Arrays.copyOf(paths, Math.max(paths.length * 2, (npaths + count) * 4 + 64));
        }
        int offset = npaths;
        java.util.Arrays.fill(paths, offset * 4, (offset + count) * 4, 0);
        npaths += count;
        return offset;
    }

    private int allocVerts(int count) {
        if ((nverts + count) * 4 > verts.length) {
            verts = java.util.Arrays.copyOf(verts, Math.max(verts.length * 2, (nverts + count) * 4 + 4096));
        }
        int offset = nverts;
        nverts += count;
        return offset;
    }

    private int allocFragUniforms(int count) {
        if ((nuniforms + count) * FRAG_SIZE > uniforms.length) {
            uniforms = java.util.Arrays.copyOf(uniforms, Math.max(uniforms.length * 2, (nuniforms + count) * FRAG_SIZE));
        }
        int offset = nuniforms;
        java.util.Arrays.fill(uniforms, offset * FRAG_SIZE, (offset + count) * FRAG_SIZE, 0.0F);
        nuniforms += count;
        return offset;
    }

    private void setVertex(int index, float x, float y, float u, float v) {
        int base = index * 4;
        verts[base] = x;
        verts[base + 1] = y;
        verts[base + 2] = u;
        verts[base + 3] = v;
    }

    private static void xformToMat3x4(float[] dst, int offset, float[] t) {
        dst[offset] = t[0];
        dst[offset + 1] = t[1];
        dst[offset + 2] = 0.0F;
        dst[offset + 3] = 0.0F;
        dst[offset + 4] = t[2];
        dst[offset + 5] = t[3];
        dst[offset + 6] = 0.0F;
        dst[offset + 7] = 0.0F;
        dst[offset + 8] = t[4];
        dst[offset + 9] = t[5];
        dst[offset + 10] = 1.0F;
        dst[offset + 11] = 0.0F;
    }

    private void convertPaint(int frag, VGPaint paint, float[] scissorXform, float[] scissorExtent,
                              float width, float fringe, float strokeThr) {
        int base = frag * FRAG_SIZE;
        float[] u = uniforms;
        java.util.Arrays.fill(u, base, base + FRAG_SIZE, 0.0F);

        VGColor inner = paint.innerColor;
        VGColor outer = paint.outerColor;
        u[base + 24] = inner.r() * inner.a();
        u[base + 25] = inner.g() * inner.a();
        u[base + 26] = inner.b() * inner.a();
        u[base + 27] = inner.a();
        u[base + 28] = outer.r() * outer.a();
        u[base + 29] = outer.g() * outer.a();
        u[base + 30] = outer.b() * outer.a();
        u[base + 31] = outer.a();

        float[] inverse = new float[6];
        if (scissorExtent[0] < -0.5F || scissorExtent[1] < -0.5F) {
            u[base + 32] = 1.0F;
            u[base + 33] = 1.0F;
            u[base + 34] = 1.0F;
            u[base + 35] = 1.0F;
        } else {
            VG.transformInverse(inverse, scissorXform);
            xformToMat3x4(u, base, inverse);
            u[base + 32] = scissorExtent[0];
            u[base + 33] = scissorExtent[1];
            u[base + 34] = (float) Math.sqrt(scissorXform[0] * scissorXform[0] + scissorXform[2] * scissorXform[2]) / fringe;
            u[base + 35] = (float) Math.sqrt(scissorXform[1] * scissorXform[1] + scissorXform[3] * scissorXform[3]) / fringe;
        }

        u[base + 36] = paint.extent[0];
        u[base + 37] = paint.extent[1];
        u[base + 40] = (width * 0.5F + fringe * 0.5F) / fringe;
        u[base + 41] = strokeThr;

        if (paint.image != 0) {
            Texture texture = findTexture(paint.image);
            if (texture == null) {
                return;
            }
            if ((texture.flags & VG.VG_IMAGE_FLIPY) != 0) {
                float[] m1 = new float[6];
                float[] m2 = new float[6];
                VG.transformTranslate(m1, 0.0F, paint.extent[1] * 0.5F);
                VG.transformMultiply(m1, paint.xform);
                VG.transformScale(m2, 1.0F, -1.0F);
                VG.transformMultiply(m2, m1);
                VG.transformTranslate(m1, 0.0F, -paint.extent[1] * 0.5F);
                VG.transformMultiply(m1, m2);
                VG.transformInverse(inverse, m1);
            } else {
                VG.transformInverse(inverse, paint.xform);
            }
            u[base + 43] = SHADER_FILLIMG;
            if (texture.type == TEXTURE_RGBA) {
                u[base + 42] = (texture.flags & VG.VG_IMAGE_PREMULTIPLIED) != 0 ? 0 : 1;
            } else {
                u[base + 42] = 2;
            }
        } else {
            u[base + 43] = SHADER_FILLGRAD;
            u[base + 38] = paint.radius;
            u[base + 39] = paint.feather;
            VG.transformInverse(inverse, paint.xform);
        }
        xformToMat3x4(u, base + 12, inverse);
    }

    private int copyPaths(VG.PathCache cache, boolean includeFill, int pathOffset) {
        int total = 0;
        for (int i = 0; i < cache.npaths; i++) {
            VG.Path path = cache.paths[i];
            if (includeFill) {
                total += path.nfill;
            }
            total += path.nstroke;
        }
        int offset = allocVerts(total);
        for (int i = 0; i < cache.npaths; i++) {
            VG.Path path = cache.paths[i];
            int p = (pathOffset + i) * 4;
            if (includeFill && path.nfill > 0) {
                paths[p] = offset;
                paths[p + 1] = path.nfill;
                System.arraycopy(cache.verts, path.fill * 4, verts, offset * 4, path.nfill * 4);
                offset += path.nfill;
            }
            if (path.nstroke > 0) {
                paths[p + 2] = offset;
                paths[p + 3] = path.nstroke;
                System.arraycopy(cache.verts, path.stroke * 4, verts, offset * 4, path.nstroke * 4);
                offset += path.nstroke;
            }
        }
        return offset;
    }

    void renderFill(VGPaint paint, float[] scissorXform, float[] scissorExtent, float fringe, float[] bounds, VG.PathCache cache) {
        int npathsIn = cache.npaths;
        int call = allocCall();
        calls[call] = CALL_FILL;
        calls[call + 5] = 4;
        calls[call + 2] = allocPaths(npathsIn);
        calls[call + 3] = npathsIn;
        calls[call + 1] = paint.image;

        if (npathsIn == 1 && cache.paths[0].convex) {
            calls[call] = CALL_CONVEXFILL;
            calls[call + 5] = 0;
        }

        copyPaths(cache, true, calls[call + 2]);

        if (calls[call] == CALL_FILL) {
            int quad = allocVerts(4);
            calls[call + 4] = quad;
            setVertex(quad, bounds[2], bounds[3], 0.5F, 1.0F);
            setVertex(quad + 1, bounds[2], bounds[1], 0.5F, 1.0F);
            setVertex(quad + 2, bounds[0], bounds[3], 0.5F, 1.0F);
            setVertex(quad + 3, bounds[0], bounds[1], 0.5F, 1.0F);

            int frag = allocFragUniforms(2);
            calls[call + 6] = frag;
            int base = frag * FRAG_SIZE;
            uniforms[base + 41] = -1.0F;
            uniforms[base + 43] = SHADER_SIMPLE;
            convertPaint(frag + 1, paint, scissorXform, scissorExtent, fringe, fringe, -1.0F);
        } else {
            int frag = allocFragUniforms(1);
            calls[call + 6] = frag;
            convertPaint(frag, paint, scissorXform, scissorExtent, fringe, fringe, -1.0F);
        }
    }

    void renderStroke(VGPaint paint, float[] scissorXform, float[] scissorExtent, float fringe, float strokeWidth, VG.PathCache cache) {
        int call = allocCall();
        calls[call] = CALL_STROKE;
        calls[call + 2] = allocPaths(cache.npaths);
        calls[call + 3] = cache.npaths;
        calls[call + 1] = paint.image;

        copyPaths(cache, false, calls[call + 2]);

        if ((flags & VG.VG_STENCIL_STROKES) != 0) {
            int frag = allocFragUniforms(2);
            calls[call + 6] = frag;
            convertPaint(frag, paint, scissorXform, scissorExtent, strokeWidth, fringe, -1.0F);
            convertPaint(frag + 1, paint, scissorXform, scissorExtent, strokeWidth, fringe, 1.0F - 0.5F / 255.0F);
        } else {
            int frag = allocFragUniforms(1);
            calls[call + 6] = frag;
            convertPaint(frag, paint, scissorXform, scissorExtent, strokeWidth, fringe, -1.0F);
        }
    }

    void renderTriangles(VGPaint paint, float[] scissorXform, float[] scissorExtent, float[] triangleVerts, int count, float fringe) {
        int call = allocCall();
        calls[call] = CALL_TRIANGLES;
        calls[call + 1] = paint.image;
        int offset = allocVerts(count);
        calls[call + 4] = offset;
        calls[call + 5] = count;
        System.arraycopy(triangleVerts, 0, verts, offset * 4, count * 4);

        int frag = allocFragUniforms(1);
        calls[call + 6] = frag;
        convertPaint(frag, paint, scissorXform, scissorExtent, 1.0F, fringe, -1.0F);
        uniforms[frag * FRAG_SIZE + 43] = SHADER_IMG;
    }

    private void setUniforms(int frag, int image) {
        fragUpload.clear();
        fragUpload.put(uniforms, frag * FRAG_SIZE, FRAG_SIZE).flip();
        GL20C.glUniform4fv(locFrag, fragUpload);
        Texture texture = image != 0 ? findTexture(image) : null;
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture != null ? texture.tex : 0);
    }

    void flush() {
        if (ncalls > 0 && program != 0) {
            GL20C.glUseProgram(program);

            GL11C.glEnable(GL11C.GL_CULL_FACE);
            GL11C.glCullFace(GL11C.GL_BACK);
            GL11C.glFrontFace(GL11C.GL_CCW);
            GL11C.glEnable(GL11C.GL_BLEND);
            GL11C.glDisable(GL11C.GL_DEPTH_TEST);
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            GL11C.glColorMask(true, true, true, true);
            GL11C.glStencilMask(0xFFFFFFFF);
            GL11C.glStencilOp(GL11C.GL_KEEP, GL11C.GL_KEEP, GL11C.GL_KEEP);
            GL11C.glStencilFunc(GL11C.GL_ALWAYS, 0, 0xFFFFFFFF);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, 0);

            int floats = nverts * 4;
            if (vertexUpload.capacity() < floats) {
                MemoryUtil.memFree(vertexUpload);
                vertexUpload = MemoryUtil.memAllocFloat(Math.max(floats, vertexUpload.capacity() * 2));
            }
            vertexUpload.clear();
            vertexUpload.put(verts, 0, floats).flip();

            GL30C.glBindVertexArray(vao);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, vbo);
            GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, vertexUpload, GL15C.GL_STREAM_DRAW);
            GL20C.glEnableVertexAttribArray(0);
            GL20C.glEnableVertexAttribArray(1);
            GL20C.glVertexAttribPointer(0, 2, GL11C.GL_FLOAT, false, 16, 0L);
            GL20C.glVertexAttribPointer(1, 2, GL11C.GL_FLOAT, false, 16, 8L);

            GL20C.glUniform1i(locTex, 0);
            GL20C.glUniform2f(locViewSize, view[0], view[1]);

            GL14C.glBlendFuncSeparate(GL11C.GL_ONE, GL11C.GL_ONE_MINUS_SRC_ALPHA, GL11C.GL_ONE, GL11C.GL_ONE_MINUS_SRC_ALPHA);

            for (int i = 0; i < ncalls; i++) {
                int call = i * 7;
                switch (calls[call]) {
                    case CALL_FILL -> fill(call);
                    case CALL_CONVEXFILL -> convexFill(call);
                    case CALL_STROKE -> stroke(call);
                    case CALL_TRIANGLES -> triangles(call);
                    default -> {
                    }
                }
            }

            GL20C.glDisableVertexAttribArray(0);
            GL20C.glDisableVertexAttribArray(1);
            GL30C.glBindVertexArray(0);
            GL11C.glDisable(GL11C.GL_CULL_FACE);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
            GL20C.glUseProgram(0);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, 0);
        }
        cancel();
    }

    private void fill(int call) {
        int pathOffset = calls[call + 2];
        int pathCount = calls[call + 3];
        int frag = calls[call + 6];
        int image = calls[call + 1];

        GL11C.glEnable(GL11C.GL_STENCIL_TEST);
        GL11C.glStencilMask(0xFF);
        GL11C.glStencilFunc(GL11C.GL_ALWAYS, 0, 0xFF);
        GL11C.glColorMask(false, false, false, false);

        setUniforms(frag, 0);

        GL20C.glStencilOpSeparate(GL11C.GL_FRONT, GL11C.GL_KEEP, GL11C.GL_KEEP, GL14C.GL_INCR_WRAP);
        GL20C.glStencilOpSeparate(GL11C.GL_BACK, GL11C.GL_KEEP, GL11C.GL_KEEP, GL14C.GL_DECR_WRAP);
        GL11C.glDisable(GL11C.GL_CULL_FACE);
        for (int i = 0; i < pathCount; i++) {
            int p = (pathOffset + i) * 4;
            GL11C.glDrawArrays(GL11C.GL_TRIANGLE_FAN, paths[p], paths[p + 1]);
        }
        GL11C.glEnable(GL11C.GL_CULL_FACE);

        GL11C.glColorMask(true, true, true, true);

        setUniforms(frag + 1, image);

        if ((flags & VG.VG_ANTIALIAS) != 0) {
            GL11C.glStencilFunc(GL11C.GL_EQUAL, 0x00, 0xFF);
            GL11C.glStencilOp(GL11C.GL_KEEP, GL11C.GL_KEEP, GL11C.GL_KEEP);
            for (int i = 0; i < pathCount; i++) {
                int p = (pathOffset + i) * 4;
                GL11C.glDrawArrays(GL11C.GL_TRIANGLE_STRIP, paths[p + 2], paths[p + 3]);
            }
        }

        GL11C.glStencilFunc(GL11C.GL_NOTEQUAL, 0x00, 0xFF);
        GL11C.glStencilOp(GL11C.GL_ZERO, GL11C.GL_ZERO, GL11C.GL_ZERO);
        GL11C.glDrawArrays(GL11C.GL_TRIANGLE_STRIP, calls[call + 4], calls[call + 5]);

        GL11C.glDisable(GL11C.GL_STENCIL_TEST);
    }

    private void convexFill(int call) {
        int pathOffset = calls[call + 2];
        int pathCount = calls[call + 3];

        setUniforms(calls[call + 6], calls[call + 1]);
        for (int i = 0; i < pathCount; i++) {
            int p = (pathOffset + i) * 4;
            GL11C.glDrawArrays(GL11C.GL_TRIANGLE_FAN, paths[p], paths[p + 1]);
            if (paths[p + 3] > 0) {
                GL11C.glDrawArrays(GL11C.GL_TRIANGLE_STRIP, paths[p + 2], paths[p + 3]);
            }
        }
    }

    private void stroke(int call) {
        int pathOffset = calls[call + 2];
        int pathCount = calls[call + 3];
        int frag = calls[call + 6];
        int image = calls[call + 1];

        if ((flags & VG.VG_STENCIL_STROKES) != 0) {
            GL11C.glEnable(GL11C.GL_STENCIL_TEST);
            GL11C.glStencilMask(0xFF);

            GL11C.glStencilFunc(GL11C.GL_EQUAL, 0x00, 0xFF);
            GL11C.glStencilOp(GL11C.GL_KEEP, GL11C.GL_KEEP, GL11C.GL_INCR);
            setUniforms(frag + 1, image);
            for (int i = 0; i < pathCount; i++) {
                int p = (pathOffset + i) * 4;
                GL11C.glDrawArrays(GL11C.GL_TRIANGLE_STRIP, paths[p + 2], paths[p + 3]);
            }

            setUniforms(frag, image);
            GL11C.glStencilFunc(GL11C.GL_EQUAL, 0x00, 0xFF);
            GL11C.glStencilOp(GL11C.GL_KEEP, GL11C.GL_KEEP, GL11C.GL_KEEP);
            for (int i = 0; i < pathCount; i++) {
                int p = (pathOffset + i) * 4;
                GL11C.glDrawArrays(GL11C.GL_TRIANGLE_STRIP, paths[p + 2], paths[p + 3]);
            }

            GL11C.glColorMask(false, false, false, false);
            GL11C.glStencilFunc(GL11C.GL_ALWAYS, 0x00, 0xFF);
            GL11C.glStencilOp(GL11C.GL_ZERO, GL11C.GL_ZERO, GL11C.GL_ZERO);
            for (int i = 0; i < pathCount; i++) {
                int p = (pathOffset + i) * 4;
                GL11C.glDrawArrays(GL11C.GL_TRIANGLE_STRIP, paths[p + 2], paths[p + 3]);
            }
            GL11C.glColorMask(true, true, true, true);

            GL11C.glDisable(GL11C.GL_STENCIL_TEST);
        } else {
            setUniforms(frag, image);
            for (int i = 0; i < pathCount; i++) {
                int p = (pathOffset + i) * 4;
                GL11C.glDrawArrays(GL11C.GL_TRIANGLE_STRIP, paths[p + 2], paths[p + 3]);
            }
        }
    }

    private void triangles(int call) {
        setUniforms(calls[call + 6], calls[call + 1]);
        GL11C.glDrawArrays(GL11C.GL_TRIANGLES, calls[call + 4], calls[call + 5]);
    }
}
