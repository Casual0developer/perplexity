package dev.perplexity.client.mixin.movement;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the bound key of a {@link KeyMapping} so movement keys can be polled by their
 * physical state while a screen is open.
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("key")
    InputConstants.Key perplexity$getKey();
}
