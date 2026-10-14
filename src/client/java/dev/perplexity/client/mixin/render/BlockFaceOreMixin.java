package dev.perplexity.client.mixin.render;

import dev.perplexity.client.impl.modules.render.OreViewModule;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps whitelisted blocks fully outlined under XRay. Their faces against hidden neighbours
 * would otherwise be culled away (the neighbour still occludes), leaving ores with holes.
 */
@Mixin(Block.class)
public abstract class BlockFaceOreMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void perplexity$oreFace(BlockState state, BlockState neighborState,
                                            Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (!OreViewModule.isHidden(state) && OreViewModule.isHidden(neighborState)) {
            cir.setReturnValue(true);
        }
    }
}
