package dev.perplexity.client.utils.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

public final class RotationRaycast {
    public static HitResult trace(
        Rotation rotation,
        double blockRange,
        double entityRange,
        ClipContext.Fluid fluids,
        Predicate<Entity> entityPredicate
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return null;

        BlockHitResult blockHit = blocks(rotation, blockRange, fluids);
        EntityHitResult entityHit = entities(rotation, entityRange, entityPredicate);
        if (entityHit == null) return blockHit;

        Vec3 eyes = minecraft.player.getEyePosition();
        return blockHit == null || eyes.distanceToSqr(entityHit.getLocation()) < eyes.distanceToSqr(blockHit.getLocation())
            ? entityHit
            : blockHit;
    }

    public static BlockHitResult blocks(Rotation rotation, double range, ClipContext.Fluid fluids) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return null;
        return blocks(rotation, range, fluids, minecraft.player.getEyePosition());
    }

    /** Trace from a previously reported eye position without changing the camera or player. */
    public static BlockHitResult blocks(Rotation rotation, double range, ClipContext.Fluid fluids, Vec3 eyes) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || eyes == null || !eyes.isFinite()
                || rotation == null || !Float.isFinite(rotation.yaw()) || !Float.isFinite(rotation.pitch())
                || Math.abs(rotation.pitch()) > 90 || !Double.isFinite(range) || range <= 0) return null;
        Vec3 end = eyes.add(rotation.direction().scale(range));
        return minecraft.level.clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE, fluids, minecraft.player));
    }

    public static EntityHitResult entities(Rotation rotation, double range, Predicate<Entity> predicate) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return null;

        Vec3 eyes = minecraft.player.getEyePosition();
        Vec3 direction = rotation.direction();
        Vec3 end = eyes.add(direction.scale(range));
        return ProjectileUtil.getEntityHitResult(
            minecraft.player,
            eyes,
            end,
            minecraft.player.getBoundingBox().expandTowards(direction.scale(range)).inflate(1),
            predicate,
            range * range
        );
    }

    private RotationRaycast() {
    }
}
