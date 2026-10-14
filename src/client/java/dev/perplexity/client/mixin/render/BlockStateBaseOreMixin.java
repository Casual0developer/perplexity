package dev.perplexity.client.mixin.render;

import dev.perplexity.client.impl.modules.render.OreViewModule;

import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drops non-whitelisted blocks out of chunk meshes while XRay is on. The mesher skips
 * {@code INVISIBLE} shapes entirely, so stone, dirt and the rest simply stop being built.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseOreMixin {
    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
    private void perplexity$oreHide(CallbackInfoReturnable<RenderShape> cir) {
        if (OreViewModule.isHidden((BlockState) (Object) this)) {
            cir.setReturnValue(RenderShape.INVISIBLE);
        }
    }
}
