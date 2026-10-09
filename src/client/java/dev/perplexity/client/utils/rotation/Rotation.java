package dev.perplexity.client.utils.rotation;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public record Rotation(float yaw, float pitch) {
    public static final Rotation ZERO = new Rotation(0, 0);

    public static Rotation lookingAt(Vec3 point, Vec3 origin) {
        Vec3 delta = point.subtract(origin);
        double horizontal = Math.hypot(delta.x, delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        return new Rotation(Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90f, 90f));
    }

    public Vec3 direction() {
        return Vec3.directionFromRotation(pitch, yaw);
    }

    public Rotation normalized() {
        return new Rotation(yaw, Mth.clamp(pitch, -90f, 90f));
    }

    public Rotation packetNormalized() {
        return new Rotation(Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90f, 90f));
    }

    public Rotation nearestTo(Rotation reference) {
        return new Rotation(
            ContinuousYaw.nearest(yaw, reference.yaw),
            Mth.clamp(pitch, -90f, 90f)
        );
    }

    public RotationDelta deltaTo(Rotation target) {
        return new RotationDelta(
            Mth.wrapDegrees(target.yaw - yaw),
            Mth.wrapDegrees(target.pitch - pitch)
        );
    }

    public float distanceTo(Rotation target) {
        return deltaTo(target).length();
    }

    public Rotation towards(Rotation target, float maxYawStep, float maxPitchStep) {
        RotationDelta delta = deltaTo(target);
        return new Rotation(
            yaw + Mth.clamp(delta.yaw(), -maxYawStep, maxYawStep),
            pitch + Mth.clamp(delta.pitch(), -maxPitchStep, maxPitchStep)
        ).normalized();
    }

    public Rotation interpolate(Rotation target, float yawFactor, float pitchFactor) {
        RotationDelta delta = deltaTo(target);
        return new Rotation(
            yaw + delta.yaw() * Mth.clamp(yawFactor, 0f, 1f),
            pitch + delta.pitch() * Mth.clamp(pitchFactor, 0f, 1f)
        ).normalized();
    }
}
