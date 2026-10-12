package dev.perplexity.client.impl.modules.combat;

import dev.perplexity.client.mixin.combat.MinecraftAttackInvoker;
import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.ModeSetting;
import dev.perplexity.client.module.setting.RangeSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Attacks whatever the crosshair rests on. Detection runs either once per client tick or once
 * per render frame, chosen by the dropdown. OS Clicks goes through the same path as a real
 * left click (cooldown, swing, miss handling); with it off the hit is sent directly.
 *
 * <p>Grim mode checks once per tick at tick start instead: Grim's Post check flags action
 * packets that arrive after the tick's movement packet, which covers both end-of-tick and
 * frame-time attacks, so Detection is bypassed while Grim is on.</p>
 */
public final class TriggerbotModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();
    private static TriggerbotModule instance;

    private final ModeSetting detection = add(new ModeSetting("Detection", "Ticks",
            "Ticks", "Frames"));
    private final ModeSetting mode = add(new ModeSetting("Mode", "Normal",
            "Normal", "Grim"));
    private final BooleanSetting osClicks = add(new BooleanSetting("OS Clicks",
            true));
    private final BooleanSetting swordOnly = add(new BooleanSetting("Sword Only",
            false));
    private final BooleanSetting autoCooldown = add(new BooleanSetting("Auto Cooldown",
            true));
    private final RangeSetting hitDelay = add(new RangeSetting("Hit Delay",
            0.5, 2.0, 0.0, 5.0, 0.1));

    private long nextHitAtMs;

    public TriggerbotModule() {
        super("Triggerbot", "Attacks your crosshair target", Category.COMBAT);
        instance = this;
    }

    @Override
    protected void onDisable() {
        nextHitAtMs = 0L;
    }

    @Override
    protected void onTick() {
        if (instance == null || !isEnabled() || mode.is("Grim") || !detection.is("Ticks")) {
            return;
        }
        tryAttack();
    }

    /** Per-frame entry point, called from the render event when Detection is Frames. */
    public static void onFrame() {
        if (instance != null && instance.isEnabled() && instance.detection.is("Frames")
                && !instance.mode.is("Grim")) {
            instance.tryAttack();
        }
    }

    /** Pre-tick entry point, driving every attack while Grim mode is on. */
    public static void preTick() {
        if (instance != null && instance.isEnabled() && instance.mode.is("Grim")) {
            instance.tryAttack();
        }
    }

    private void tryAttack() {
        if (!canRun()) {
            return;
        }
        if (swordOnly.get() && !mc.player.getMainHandItem().is(ItemTags.SWORDS)) {
            return;
        }
        if (!(mc.hitResult instanceof EntityHitResult entityHit)) {
            return;
        }
        if (!(entityHit.getEntity() instanceof LivingEntity target)
                || target == mc.player || !target.isAlive()) {
            return;
        }
        if (autoCooldown.get() && mc.player.getAttackStrengthScale(0.0F) < 0.9F) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextHitAtMs) {
            return;
        }
        if (osClicks.get()) {
            ((MinecraftAttackInvoker) mc).perplexity$invokeStartAttack();
        } else {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        nextHitAtMs = now + (long) (hitDelay.random() * 1000.0);
    }

    private static boolean canRun() {
        return mc.player != null && mc.level != null && mc.gameMode != null
                && mc.screen == null && mc.isWindowActive()
                && !mc.player.isDeadOrDying() && !mc.player.isSpectator();
    }
}
