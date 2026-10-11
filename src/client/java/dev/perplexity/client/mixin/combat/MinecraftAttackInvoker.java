package dev.perplexity.client.mixin.combat;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the left-click attack path so the triggerbot can swing exactly like a real click.
 */
@Mixin(Minecraft.class)
public interface MinecraftAttackInvoker {
    @Invoker("startAttack")
    boolean perplexity$invokeStartAttack();
}
