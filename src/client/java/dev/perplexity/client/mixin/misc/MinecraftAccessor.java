package dev.perplexity.client.mixin.misc;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the use-item repeat throttle so Fast XP can hold vanilla's own repeats off while it
 * drives the throw rate itself.
 */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("rightClickDelay")
    void perplexity$setRightClickDelay(int delay);
}
