package dev.perplexity.client.mixin.combat;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the click paths so automation swings exactly like real clicks.
 */
@Mixin(Minecraft.class)
public interface MinecraftAttackInvoker {
    @Invoker("startAttack")
    boolean perplexity$invokeStartAttack();

    @Invoker("startUseItem")
    void perplexity$invokeStartUseItem();
}
