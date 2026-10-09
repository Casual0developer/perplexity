package dev.perplexity.client.mixin.anchor;

import dev.perplexity.client.module.combat.AnchorMacroModule;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Snapshots the placement target before vanilla consumes the stack, then records the anchor after an
 * accepted interaction so the macro can charge and detonate it.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeAnchorMixin {
    @WrapMethod(method = "useItemOn")
    private InteractionResult perplexity$recordManualAnchor(LocalPlayer player, InteractionHand hand,
                                                            BlockHitResult hit,
                                                            Operation<InteractionResult> original) {
        BlockPos candidate = AnchorMacroModule.placementCandidate(player, hand, hit);
        InteractionResult result = original.call(player, hand, hit);
        if (candidate != null && result.consumesAction()) {
            AnchorMacroModule.onAnchorPlaced(player, candidate);
        }
        return result;
    }
}
