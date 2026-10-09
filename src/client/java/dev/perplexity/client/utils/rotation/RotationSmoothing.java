package dev.perplexity.client.utils.rotation;

public enum RotationSmoothing {
    INSTANT,
    LINEAR,
    INTERPOLATE,
    SIGMOID;

    public RotationSmoother smoother(double yawSpeed, double pitchSpeed, double interpolationFactor, double sigmoidStrength) {
        return switch (this) {
            case INSTANT -> RotationSmoother.instant();
            case LINEAR -> RotationSmoother.linear((float) yawSpeed, (float) pitchSpeed);
            case INTERPOLATE -> RotationSmoother.interpolate((float) interpolationFactor, (float) interpolationFactor);
            case SIGMOID -> RotationSmoother.sigmoid((float) sigmoidStrength);
        };
    }
}
