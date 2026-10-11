package dev.perplexity.client.impl.modules.player;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;

/**
 * Clicks respawn the moment the death screen appears.
 */
public final class AutoRespawnModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    public AutoRespawnModule() {
        super("Auto Respawn", "Respawns instantly on death", Category.PLAYER);
    }

    @Override
    protected void onTick() {
        if (mc.player != null && mc.screen instanceof DeathScreen) {
            mc.player.respawn();
        }
    }
}
