package dev.perplexity.client.module.setting;

public final class NumberSetting extends Setting {
    private final double min;
    private final double max;
    private final double step;
    private double value;

    public NumberSetting(String name, double value, double min, double max, double step) {
        super(name);
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = clamp(value);
    }

    public double get() {
        return value;
    }

    public void set(double raw) {
        double clamped = clamp(raw);
        if (step > 0.0) {
            clamped = Math.round(clamped / step) * step;
        }
        this.value = clamp(clamped);
    }

    public float normalized() {
        if (max - min <= 0.0) {
            return 0.0F;
        }
        return (float) ((value - min) / (max - min));
    }

    public void setNormalized(float fraction) {
        set(min + (max - min) * Math.max(0.0, Math.min(1.0, fraction)));
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public String display() {
        if (step >= 1.0) {
            return Long.toString(Math.round(value));
        }
        return String.format("%.2f", value);
    }

    private double clamp(double v) {
        return Math.max(min, Math.min(max, v));
    }
}
