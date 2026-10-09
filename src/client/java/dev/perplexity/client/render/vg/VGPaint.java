package dev.perplexity.client.render.vg;

public final class VGPaint {
    final float[] xform = new float[6];
    final float[] extent = new float[2];
    float radius;
    float feather;
    final VGColor innerColor = new VGColor();
    final VGColor outerColor = new VGColor();
    int image;

    public VGPaint() {
    }

    VGPaint set(VGPaint other) {
        System.arraycopy(other.xform, 0, xform, 0, 6);
        extent[0] = other.extent[0];
        extent[1] = other.extent[1];
        radius = other.radius;
        feather = other.feather;
        innerColor.set(other.innerColor);
        outerColor.set(other.outerColor);
        image = other.image;
        return this;
    }

    void setColor(VGColor color) {
        VG.transformIdentity(xform);
        extent[0] = 0.0F;
        extent[1] = 0.0F;
        radius = 0.0F;
        feather = 1.0F;
        innerColor.set(color);
        outerColor.set(color);
        image = 0;
    }

    void clear() {
        java.util.Arrays.fill(xform, 0.0F);
        extent[0] = 0.0F;
        extent[1] = 0.0F;
        radius = 0.0F;
        feather = 0.0F;
        innerColor.r(0.0F).g(0.0F).b(0.0F).a(0.0F);
        outerColor.r(0.0F).g(0.0F).b(0.0F).a(0.0F);
        image = 0;
    }

    public int image() {
        return image;
    }
}
