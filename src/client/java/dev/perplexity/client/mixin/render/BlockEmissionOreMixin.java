package dev.perplexity.client.mixin.render;

import dev.perplexity.client.impl.modules.render.OreViewModule;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes whitelisted blocks emit full light while XRay is on. Night Vision only lifts darkness
 * to dim blue, so deep ores still read dark — baked emission makes the mesh vertices themselves
 * fullbright regardless of surrounding light.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockEmissionOreMixin {
    @Inject(method = "getLightEmission", at = @At("HEAD"), cancellable = true)
    private void perplexity$oreGlow(CallbackInfoReturnable<Integer> cir) {
        BlockState state = (BlockState) (Object) this;
        if (OreViewModule.active() && !OreViewModule.isHidden(state)) {
            cir.setReturnValue(15);
        }
    }
}
