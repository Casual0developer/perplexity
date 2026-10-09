package dev.perplexity.client.utils.rotation;

/** Equivalent angles stay on the server's current turn count rather than snapping to +/-180. */
public final class ContinuousYaw {
    private ContinuousYaw() { }

    public static float nearest(float yaw, float reference) {
        float delta = (yaw - reference) % 360f;
        if (delta >= 180f) delta -= 360f;
        if (delta < -180f) delta += 360f;
        return reference + delta;
    }
}
