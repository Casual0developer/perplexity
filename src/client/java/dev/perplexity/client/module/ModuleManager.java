package dev.perplexity.client.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry for the client's features. Currently empty: register your own modules here and the
 * ClickGUI will pick them up per {@link Category} automatically.
 */
public final class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    private ModuleManager() {
    }

    private static final class Holder {
        private static final ModuleManager INSTANCE = new ModuleManager();
    }

    public static ModuleManager get() {
        return Holder.INSTANCE;
    }

    public void register(Module module) {
        modules.add(module);
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }

    /** Ticks every enabled module once; disabled modules are skipped inside {@link Module#tick()}. */
    public void tick() {
        for (Module module : modules) {
            module.tick();
        }
    }

    public List<Module> byCategory(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.getCategory() == category) {
                result.add(module);
            }
        }
        return result;
    }
}
