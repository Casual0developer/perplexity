package dev.perplexity.client.utils.rotation;

@FunctionalInterface
public interface RotationSmoother {
    Rotation smooth(Rotation current, Rotation target);

    static RotationSmoother instant() {
        return (current, target) -> target;
    }

    static RotationSmoother linear(float yawSpeed, float pitchSpeed) {
        if (yawSpeed < 0 || pitchSpeed < 0) throw new IllegalArgumentException("Rotation speed cannot be negative.");
        return (current, target) -> current.towards(target, yawSpeed, pitchSpeed);
    }

    static RotationSmoother interpolate(float yawFactor, float pitchFactor) {
        return (current, target) -> current.interpolate(target, yawFactor, pitchFactor);
    }

    static RotationSmoother eased(float yawSpeed, float pitchSpeed, float factor) {
        if (!Float.isFinite(yawSpeed) || !Float.isFinite(pitchSpeed) || !Float.isFinite(factor)
                || yawSpeed <= 0 || pitchSpeed <= 0 || factor <= 0 || factor > 1)
            throw new IllegalArgumentException("Expected positive turn speeds and an easing factor in (0, 1].");
        return (current, target) -> {
            RotationDelta delta = current.deltaTo(target);
            // Finish small corrections instead of asymptotically following a moving hit point.
            float yawStep = Math.min(yawSpeed, Math.max(Math.abs(delta.yaw()) * factor, Math.min(2, yawSpeed)));
            float pitchStep = Math.min(pitchSpeed, Math.max(Math.abs(delta.pitch()) * factor, Math.min(2, pitchSpeed)));
            return current.towards(target, yawStep, pitchStep);
        };
    }

    static RotationSmoother sigmoid(float speed) {
        if (speed <= 0) throw new IllegalArgumentException("Sigmoid speed must be positive.");
        return (current, target) -> {
            RotationDelta delta = current.deltaTo(target);
            float yawStep = sigmoidStep(Math.abs(delta.yaw()), speed);
            float pitchStep = sigmoidStep(Math.abs(delta.pitch()), speed);
            return current.towards(target, yawStep, pitchStep);
        };
    }

    private static float sigmoidStep(float difference, float speed) {
        float normalized = Math.min(difference / 180f, 1f);
        float curve = (float) (1.0 / (1.0 + Math.exp(-8.0 * (normalized - 0.35))));
        return Math.max(0.05f, difference * curve * speed);
    }
}
