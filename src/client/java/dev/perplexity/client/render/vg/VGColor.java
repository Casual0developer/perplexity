package dev.perplexity.client.render.vg;

public final class VGColor {
    private float r;
    private float g;
    private float b;
    private float a;

    public VGColor() {
    }

    public VGColor(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    public static VGColor rgba(int r, int g, int b, int a) {
        return new VGColor(r / 255.0F, g / 255.0F, b / 255.0F, a / 255.0F);
    }

    public float r() {
        return r;
    }

    public float g() {
        return g;
    }

    public float b() {
        return b;
    }

    public float a() {
        return a;
    }

    public VGColor r(float value) {
        r = value;
        return this;
    }

    public VGColor g(float value) {
        g = value;
        return this;
    }

    public VGColor b(float value) {
        b = value;
        return this;
    }

    public VGColor a(float value) {
        a = value;
        return this;
    }

    public VGColor set(VGColor other) {
        r = other.r;
        g = other.g;
        b = other.b;
        a = other.a;
        return this;
    }

    public VGColor copy() {
        return new VGColor(r, g, b, a);
    }
}
