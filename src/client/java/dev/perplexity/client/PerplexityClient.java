package dev.perplexity.client;

import com.mojang.blaze3d.platform.InputConstants;

import dev.perplexity.client.gui.clickgui.ClickGuiConfig;
import dev.perplexity.client.gui.clickgui.ClickGuiScreen;
import dev.perplexity.client.module.ModuleManager;
import dev.perplexity.client.impl.modules.combat.AnchorMacroModule;
import dev.perplexity.client.utils.rotation.RotationManager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;

public class PerplexityClient implements ClientModInitializer {
    private boolean clickGuiDown;

    @Override
    public void onInitializeClient() {
        ModuleManager.get().register(new AnchorMacroModule());

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        // CHANGE_LOOK rotations have to be applied to the camera every frame, after the tick
        // rotation has been committed, with the frame's partial tick.
        WorldRenderEvents.START_MAIN.register(context -> RotationManager.applyChangeLookRotation(
            Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)));
    }

    private void onEndTick(Minecraft minecraft) {
        ModuleManager.get().tick();
        RotationManager.tick(minecraft);

        int key = ClickGuiConfig.openKey;
        if (key == 0 || minecraft.getWindow() == null) {
            return;
        }

        boolean down = InputConstants.isKeyDown(minecraft.getWindow(), key);
        if (down && !clickGuiDown) {
            if (minecraft.screen instanceof ClickGuiScreen) {
                minecraft.setScreen(null);
            } else if (minecraft.screen == null) {
                minecraft.setScreen(new ClickGuiScreen());
            }
        }
        clickGuiDown = down;
    }
}
