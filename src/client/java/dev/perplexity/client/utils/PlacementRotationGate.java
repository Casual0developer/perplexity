package dev.perplexity.client.utils;

import dev.perplexity.client.utils.rotation.Rotation;

/** Tracks actual placement turns without delaying unrelated, already-valid block rays. */
public final class PlacementRotationGate {
    private Rotation reported;
    private float lastYawChange;
    private float lastPlacedYawChange;
    private boolean rotated;
    private boolean placementPending;

    public void reset(Rotation baseline) {
        reported = finite(baseline) ? baseline : null;
        lastYawChange = lastPlacedYawChange = 0;
        rotated = placementPending = false;
    }

    public void observe(Rotation look) {
        if (!finite(look)) { reset(null); return; }
        if (reported != null) lastYawChange = Math.abs(look.yaw() - reported.yaw());
        reported = look;
        rotated = true;
    }

    public boolean ready(Rotation look) {
        if (reported == null || !finite(look)) return false;
        float yawChange = Math.abs(look.yaw() - reported.yaw());
        boolean changesLook = !look.equals(reported);
        float placementYawChange = changesLook ? yawChange : lastYawChange;
        return !(rotated || changesLook) || placementYawChange <= 2
                || Math.abs(placementYawChange - lastPlacedYawChange) >= .0001f;
    }

    public void placed() { placementPending = true; }

    /** Block uses precede movement; commit the delta actually sent in that packet window. */
    public void endTick() {
        if (placementPending && rotated) {
            lastPlacedYawChange = lastYawChange;
            rotated = false;
        }
        placementPending = false;
    }

    private static boolean finite(Rotation look) {
        return look != null && Float.isFinite(look.yaw()) && Float.isFinite(look.pitch()) && Math.abs(look.pitch()) <= 90;
    }
}
