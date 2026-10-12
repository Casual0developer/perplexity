package dev.perplexity.client;

import com.mojang.blaze3d.platform.InputConstants;

import dev.perplexity.client.gui.clickgui.ClickGuiScreen;
import dev.perplexity.client.module.ModuleManager;
import dev.perplexity.client.impl.modules.combat.AnchorMacroModule;
import dev.perplexity.client.impl.modules.combat.AntiKnockbackModule;
import dev.perplexity.client.impl.modules.combat.AutoClickerModule;
import dev.perplexity.client.impl.modules.combat.TriggerbotModule;
import dev.perplexity.client.impl.modules.misc.FastXPModule;
import dev.perplexity.client.impl.modules.misc.ProtectModule;
import dev.perplexity.client.impl.modules.movement.AutoSprintModule;
import dev.perplexity.client.impl.modules.movement.BoatFlyModule;
import dev.perplexity.client.impl.modules.movement.FlightModule;
import dev.perplexity.client.impl.modules.movement.InventoryWalkModule;
import dev.perplexity.client.impl.modules.movement.SpeedModule;
import dev.perplexity.client.impl.modules.player.AutoRespawnModule;
import dev.perplexity.client.impl.modules.player.NoFallModule;
import dev.perplexity.client.impl.modules.render.FullBrightModule;
import dev.perplexity.client.impl.modules.render.NoHurtCamModule;
import dev.perplexity.client.impl.modules.render.TrueSightModule;
import dev.perplexity.client.impl.modules.render.XRayModule;
import dev.perplexity.client.impl.modules.ui.ClickGuiModule;
import dev.perplexity.client.impl.modules.ui.NotificationsModule;
import dev.perplexity.client.impl.modules.ui.TabsModule;
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
        ModuleManager.get().register(new AntiKnockbackModule());
        ModuleManager.get().register(new AutoClickerModule());
        ModuleManager.get().register(new TriggerbotModule());
        ModuleManager.get().register(new FlightModule());
        ModuleManager.get().register(new SpeedModule());
        ModuleManager.get().register(new InventoryWalkModule());
        ModuleManager.get().register(new AutoSprintModule());
        ModuleManager.get().register(new BoatFlyModule());
        ModuleManager.get().register(new NoFallModule());
        ModuleManager.get().register(new AutoRespawnModule());
        ModuleManager.get().register(new ProtectModule());
        ModuleManager.get().register(new FastXPModule());
        ModuleManager.get().register(new TrueSightModule());
        ModuleManager.get().register(new FullBrightModule());
        ModuleManager.get().register(new NoHurtCamModule());
        ModuleManager.get().register(new XRayModule());
        ModuleManager.get().register(new NotificationsModule());
        ModuleManager.get().register(new ClickGuiModule());
        TabsModule tabs = new TabsModule();
        tabs.setEnabled(true);
        ModuleManager.get().register(tabs);

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        // CHANGE_LOOK rotations have to be applied to the camera every frame, after the tick
        // rotation has been committed, with the frame's partial tick.
        WorldRenderEvents.START_MAIN.register(context -> RotationManager.applyChangeLookRotation(
            Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)));
        WorldRenderEvents.END_MAIN.register(context -> TriggerbotModule.onFrame());
    }

    private void onEndTick(Minecraft minecraft) {
        ModuleManager.get().tick();
        RotationManager.tick(minecraft);

        int key = ClickGuiModule.openKey();
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
