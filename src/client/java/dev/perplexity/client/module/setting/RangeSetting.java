package dev.perplexity.client.module.setting;

/**
 * A bounded low/high numeric range. Combat macros drive their step timing from {@link #random()} so
 * consecutive actions never share one exact delay.
 */
public final class RangeSetting extends Setting {
    private final double min;
    private final double max;
    private final double step;

    private double low;
    private double high;

    public RangeSetting(String name, double low, double high, double min, double max, double step) {
        super(name);
        if (min > max || step <= 0.0) {
            throw new IllegalArgumentException("Invalid numeric range");
        }
        this.min = min;
        this.max = max;
        this.step = step;
        set(low, high);
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getLow() {
        return low;
    }

    public double getHigh() {
        return high;
    }

    public void set(double low, double high) {
        double a = snap(low);
        double b = snap(high);
        this.low = Math.min(a, b);
        this.high = Math.max(a, b);
    }

    public void setLow(double value) {
        set(value, high);
    }

    public void setHigh(double value) {
        set(low, value);
    }

    /** Uniformly samples the configured range; collapses to {@link #getLow()} when the range is empty. */
    public double random() {
        return high <= low ? low : low + Math.random() * (high - low);
    }

    public String display() {
        return format(low) + " - " + format(high);
    }

    public float normalizedLow() {
        return normalized(low);
    }

    public float normalizedHigh() {
        return normalized(high);
    }

    public void setNormalizedLow(float fraction) {
        setLow(min + (max - min) * clamp(fraction));
    }

    public void setNormalizedHigh(float fraction) {
        setHigh(min + (max - min) * clamp(fraction));
    }

    private float normalized(double value) {
        return max - min <= 0.0 ? 0.0F : (float) ((value - min) / (max - min));
    }

    private double snap(double value) {
        return Math.round(Math.max(min, Math.min(max, value)) / step) * step;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private String format(double value) {
        return step >= 1.0 ? Long.toString(Math.round(value)) : String.format("%.2f", value);
    }
}
