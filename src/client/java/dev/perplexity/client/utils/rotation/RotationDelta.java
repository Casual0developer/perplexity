package dev.perplexity.client.utils.rotation;

import net.minecraft.world.phys.Vec2;

public record RotationDelta(float yaw, float pitch) {
    public float length() {
        return (float) Math.hypot(yaw, pitch);
    }

    public Vec2 asVector() {
        return new Vec2(yaw, pitch);
    }
}
