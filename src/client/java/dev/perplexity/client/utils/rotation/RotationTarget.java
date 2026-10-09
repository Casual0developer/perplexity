package dev.perplexity.client.utils.rotation;

import java.util.Objects;

public final class RotationTarget {
    private final Rotation rotation;
    private final RotationSmoother smoother;
    private final RotationPriority priority;
    private final RotationMode mode;
    private final MovementCorrection movementCorrection;
    private final int ticksUntilReset;
    private final float resetThreshold;
    private final boolean sensitivityFix;
    private final boolean instantLook;
    private final RotationProvider lookRotation;

    private RotationTarget(Builder builder) {
        rotation = builder.rotation.normalized();
        smoother = builder.smoother;
        priority = builder.priority;
        mode = builder.mode;
        movementCorrection = builder.movementCorrection;
        ticksUntilReset = builder.ticksUntilReset;
        resetThreshold = builder.resetThreshold;
        sensitivityFix = builder.sensitivityFix;
        instantLook = builder.instantLook;
        lookRotation = builder.lookRotation;
    }

    public static Builder to(Rotation rotation) {
        return new Builder(rotation);
    }

    public Rotation rotation() { return rotation; }
    public RotationSmoother smoother() { return smoother; }
    public RotationPriority priority() { return priority; }
    public RotationMode mode() { return mode; }
    public MovementCorrection movementCorrection() { return movementCorrection; }
    public int ticksUntilReset() { return ticksUntilReset; }
    public float resetThreshold() { return resetThreshold; }
    public boolean sensitivityFix() { return sensitivityFix; }
    public boolean instantLook() { return instantLook; }
    public RotationProvider lookRotation() { return lookRotation; }

    public static final class Builder {
        private final Rotation rotation;
        private RotationSmoother smoother = RotationSmoother.instant();
        private RotationPriority priority = RotationPriority.NORMAL;
        private RotationMode mode = RotationMode.SILENT;
        private MovementCorrection movementCorrection = MovementCorrection.SILENT;
        private int ticksUntilReset = 1;
        private float resetThreshold = 1f;
        private boolean sensitivityFix = true;
        private boolean instantLook = false;
        private RotationProvider lookRotation = null;

        private Builder(Rotation rotation) {
            this.rotation = Objects.requireNonNull(rotation, "rotation");
        }

        public Builder smoother(RotationSmoother value) { smoother = Objects.requireNonNull(value); return this; }
        public Builder priority(RotationPriority value) { priority = Objects.requireNonNull(value); return this; }
        public Builder mode(RotationMode value) { mode = Objects.requireNonNull(value); return this; }
        public Builder movementCorrection(MovementCorrection value) { movementCorrection = Objects.requireNonNull(value); return this; }

        public Builder ticksUntilReset(int value) {
            if (value < 1) throw new IllegalArgumentException("ticksUntilReset must be at least 1.");
            ticksUntilReset = value;
            return this;
        }

        public Builder resetThreshold(float value) {
            if (value < 0) throw new IllegalArgumentException("resetThreshold cannot be negative.");
            resetThreshold = value;
            return this;
        }

        public Builder sensitivityFix(boolean value) { sensitivityFix = value; return this; }
        public Builder instantLook(boolean value) { instantLook = value; return this; }
        public Builder lookRotation(RotationProvider value) { lookRotation = value; return this; }
        public RotationTarget build() { return new RotationTarget(this); }
    }
}
