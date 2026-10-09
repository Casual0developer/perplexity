package dev.perplexity.client.utils.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RotationUtils {
    private static final double MOUSE_SCALE = 0.15;

    public static Rotation playerRotation() {
        var player = Minecraft.getInstance().player;
        return player == null ? Rotation.ZERO : new Rotation(player.getYRot(), player.getXRot());
    }

    public static Rotation to(Vec3 point) {
        var player = Minecraft.getInstance().player;
        return player == null ? Rotation.ZERO : Rotation.lookingAt(point, player.getEyePosition());
    }

    public static Rotation to(Entity entity) {
        return to(entity.getBoundingBox().getCenter());
    }

    public static Rotation to(Entity entity, float tickDelta) {
        return to(entity.getEyePosition(tickDelta));
    }

    public static Rotation toNearest(Entity entity) {
        var player = Minecraft.getInstance().player;
        if (player == null) return Rotation.ZERO;
        Vec3 eyes = player.getEyePosition();
        return Rotation.lookingAt(nearestPoint(eyes, entity.getBoundingBox()), eyes);
    }

    public static Rotation to(BlockPos pos) {
        return to(Vec3.atCenterOf(pos));
    }

    public static Rotation to(BlockPos pos, Direction face) {
        return to(Vec3.atCenterOf(pos).relative(face, 0.5));
    }

    public static Vec3 nearestPoint(Vec3 point, AABB box) {
        return new Vec3(
            Mth.clamp(point.x, box.minX, box.maxX),
            Mth.clamp(point.y, box.minY, box.maxY),
            Mth.clamp(point.z, box.minZ, box.maxZ)
        );
    }

    public static float angleDifference(float first, float second) {
        return Mth.wrapDegrees(first - second);
    }

    public static double sensitivityGcd() {
        double sensitivity = Minecraft.getInstance().options.sensitivity().get();
        double factor = sensitivity * 0.6 + 0.2;
        return factor * factor * factor * 8.0 * MOUSE_SCALE;
    }

    public static float aimTolerance() {
        return (float) Math.max(0.25, sensitivityGcd() * 2.0);
    }

    public static float axisMargin(float degrees) {
        return 45f - Math.abs(((Mth.wrapDegrees(degrees) + 45f) % 90f + 90f) % 90f - 45f);
    }

    public static boolean aimStable(Rotation rotation) {
        return axisMargin(rotation.yaw()) >= 2f && axisMargin(rotation.pitch()) >= 2f;
    }

    public static Rotation snapToSensitivity(Rotation rotation, Rotation previous) {
        double gcd = sensitivityGcd();
        RotationDelta delta = previous.deltaTo(rotation);
        float yaw = previous.yaw() + (float) (Math.round(delta.yaw() / gcd) * gcd);
        float pitch = previous.pitch() + (float) (Math.round(delta.pitch() / gcd) * gcd);
        return new Rotation(yaw, Mth.clamp(pitch, -90f, 90f)).normalized();
    }

    public static Rotation applyMouseTurnDelta(Rotation rotation, double cursorDeltaX, double cursorDeltaY) {
        RotationDelta delta = mouseTurnDelta(cursorDeltaX, cursorDeltaY);
        return new Rotation(
            rotation.yaw() + delta.yaw(),
            Mth.clamp(rotation.pitch() + delta.pitch(), -90f, 90f)
        ).normalized();
    }

    public static RotationDelta mouseTurnDelta(double cursorDeltaX, double cursorDeltaY) {
        return new RotationDelta((float) cursorDeltaX * (float) MOUSE_SCALE, (float) cursorDeltaY * (float) MOUSE_SCALE);
    }

    public static boolean withinFov(Rotation view, Rotation target, float fov) {
        if (fov >= 360.0f) return true;
        return view.distanceTo(target) <= fov * 0.5f;
    }

    public static float crosshairAngle(Entity entity) {
        return playerRotation().distanceTo(toNearest(entity));
    }

    public static Input correctKeyboardInput(Input input, float playerYaw, float movementYaw) {
        if (!Float.isFinite(playerYaw) || !Float.isFinite(movementYaw)) return input;
        float forward = impulse(input.forward(), input.backward());
        float sideways = impulse(input.left(), input.right());
        if (forward == 0f && sideways == 0f) return input;

        float deltaYaw = Mth.wrapDegrees(Mth.wrapDegrees(playerYaw) - Mth.wrapDegrees(movementYaw));
        if (deltaYaw == 0) return input;
        // Choose the nearest of the eight legal keyboard directions. Rounding axes separately
        // skews the thresholds for diagonal input and can pick the wrong direction.
        double angle = Math.atan2(sideways, forward) - Math.toRadians(deltaYaw);
        double snappedAngle = Math.round(angle / (Math.PI / 4)) * (Math.PI / 4);
        int roundedForward = (int) Math.round(Math.cos(snappedAngle));
        int roundedSideways = (int) Math.round(Math.sin(snappedAngle));
        boolean correctedSprint = input.sprint() && roundedForward > 0;

        return inputFromMovement(roundedForward, roundedSideways, input.jump(), input.shift(), correctedSprint);
    }

    private static float impulse(boolean positive, boolean negative) {
        if (positive == negative) return 0f;
        return positive ? 1f : -1f;
    }

    private static Input inputFromMovement(int forward, int sideways, boolean jump, boolean shift, boolean sprint) {
        return new Input(
            forward > 0,
            forward < 0,
            sideways > 0,
            sideways < 0,
            jump,
            shift,
            sprint
        );
    }

    public static float movementYaw(float yaw, double forward, double sideways) {
        if (forward < 0) yaw += 180f;
        float forwardFactor = forward < 0 ? -0.5f : forward > 0 ? 0.5f : 1f;
        if (sideways > 0) yaw -= 90f * forwardFactor;
        if (sideways < 0) yaw += 90f * forwardFactor;
        return Mth.wrapDegrees(yaw);
    }

    private RotationUtils() {
    }
}
