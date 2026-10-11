package dev.perplexity.client.impl.modules.render;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Renders invisible players as if they were visible: body and nametag.
 * Only affects rendering on this client; the invisibility effect itself is untouched.
 */
public final class TrueSightModule extends Module {
    private static TrueSightModule instance;

    public TrueSightModule() {
        super("True Sight", "See invisible players", Category.RENDER);
        instance = this;
    }

    /** True when the render mixins should reveal this entity. */
    public static boolean reveal(LivingEntity entity) {
        if (instance == null || !instance.isEnabled()) {
            return false;
        }
        return entity instanceof AbstractClientPlayer && entity.isInvisible();
    }
}
