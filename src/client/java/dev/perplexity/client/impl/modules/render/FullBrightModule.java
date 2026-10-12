package dev.perplexity.client.impl.modules.render;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.utils.NightVision;

/**
 * Full brightness via Night Vision. The gamma slider caps at 1.0 (the option validates every
 * set), so pushing it higher is a no-op — an invisible, iconless effect is what actually
 * brightens caves.
 */
public final class FullBrightModule extends Module {
    private final NightVision nightVision = new NightVision();

    public FullBrightModule() {
        super("Full Bright", "Maximum brightness", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        nightVision.enable();
    }

    @Override
    protected void onDisable() {
        nightVision.disable();
    }

    @Override
    protected void onTick() {
        nightVision.tick();
    }
}
