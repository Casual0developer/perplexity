package dev.perplexity.client.impl.modules.render;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;

import net.minecraft.client.Minecraft;

/**
 * Full brightness via maxed gamma, restored on disable.
 */
public final class FullBrightModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    private double prevGamma = 1.0;

    public FullBrightModule() {
        super("Full Bright", "Maximum brightness", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        prevGamma = mc.options.gamma().get();
        mc.options.gamma().set(10.0);
    }

    @Override
    protected void onDisable() {
        mc.options.gamma().set(prevGamma);
    }
}
