package dev.perplexity.client.impl.modules.combat;

import dev.perplexity.client.mixin.combat.MinecraftAttackInvoker;
import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.DropdownSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Attacks whatever the crosshair rests on. Detection runs either once per client tick or once
 * per render frame, chosen by the dropdown. OS Clicks goes through the same path as a real
 * left click (cooldown, swing, miss handling); with it off the hit is sent directly.
 */
public final class TriggerbotModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();
    private static TriggerbotModule instance;

    private final DropdownSetting detection = add(new DropdownSetting("Detection", "Ticks",
            "Ticks", "Frames"));
    private final BooleanSetting osClicks = add(new BooleanSetting("OS Clicks",
            true));
    private final BooleanSetting swordOnly = add(new BooleanSetting("Sword Only",
            false));

    public TriggerbotModule() {
        super("Triggerbot", "Attacks your crosshair target", Category.COMBAT);
        instance = this;
    }

    @Override
    protected void onTick() {
        if (instance == null || !isEnabled() || !detection.is("Ticks")) {
            return;
        }
        tryAttack();
    }

    /** Per-frame entry point, called from the render event when Detection is Frames. */
    public static void onFrame() {
        if (instance != null && instance.isEnabled() && instance.detection.is("Frames")) {
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
        if (mc.player.getAttackStrengthScale(0.0F) < 0.9F) {
            return;
        }
        if (osClicks.get()) {
            ((MinecraftAttackInvoker) mc).perplexity$invokeStartAttack();
        } else {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private static boolean canRun() {
        return mc.player != null && mc.level != null && mc.gameMode != null
                && mc.screen == null && mc.isWindowActive()
                && !mc.player.isDeadOrDying() && !mc.player.isSpectator();
    }
}
