package dev.perplexity.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.ModuleManager;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.DropdownSetting;
import dev.perplexity.client.module.setting.KeybindSetting;
import dev.perplexity.client.module.setting.ModeSetting;
import dev.perplexity.client.module.setting.NumberSetting;
import dev.perplexity.client.module.setting.RangeSetting;
import dev.perplexity.client.module.setting.Setting;
import dev.perplexity.client.module.setting.ToggleListSetting;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-account local profiles: module toggles, settings and keybinds, keyed by account ID
 * only. No email, no password, nothing to log in with — just which switches were on.
 * Anonymous users share the {@code default} profile. Writes happen at most every few
 * seconds when something actually changed, plus once on shutdown.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long AUTOSAVE_INTERVAL_TICKS = 100L;

    private static String loadedId;
    private static String snapshot = "";
    private static long tickCounter;

    private ConfigManager() {
    }

    /** Loads the anonymous profile; call once after module registration. */
    public static synchronized void loadDefault() {
        loadedId = null;
        load();
        snapshot = serialize();
    }

    /** Switches profiles on login/skip: persists the outgoing one, loads the incoming. */
    public static synchronized void onAccountChanged(String id) {
        save();
        loadedId = id;
        load();
        snapshot = serialize();
    }

    /** Throttled autosave hook for the client tick. */
    public static synchronized void tick() {
        if (++tickCounter < AUTOSAVE_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0L;
        String current = serialize();
        if (!current.equals(snapshot)) {
            snapshot = current;
            write(current);
        }
    }

    /** Flushes pending changes; call on shutdown. */
    public static synchronized void flush() {
        String current = serialize();
        if (!current.equals(snapshot)) {
            snapshot = current;
            write(current);
        }
    }

    private static synchronized void save() {
        String current = serialize();
        if (!current.equals(snapshot)) {
            snapshot = current;
            write(current);
        }
    }

    private static String serialize() {
        JsonObject modules = new JsonObject();
        for (Module module : ModuleManager.get().getModules()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", module.isEnabled());
            entry.addProperty("keybind", module.getKeybind());
            entry.addProperty("visible", module.isVisibleInMenu());
            JsonObject settings = new JsonObject();
            for (Setting setting : module.getSettings()) {
                JsonObject saved = saveSetting(setting);
                if (saved != null) {
                    settings.add(setting.getName(), saved);
                }
            }
            entry.add("settings", settings);
            modules.add(module.getClass().getSimpleName(), entry);
        }
        JsonObject root = new JsonObject();
        root.add("modules", modules);
        return GSON.toJson(root);
    }

    private static JsonObject saveSetting(Setting setting) {
        JsonObject saved = new JsonObject();
        if (setting instanceof BooleanSetting bool) {
            saved.addProperty("type", "boolean");
            saved.addProperty("value", bool.get());
        } else if (setting instanceof NumberSetting number) {
            saved.addProperty("type", "number");
            saved.addProperty("value", number.get());
        } else if (setting instanceof RangeSetting range) {
            saved.addProperty("type", "range");
            saved.addProperty("low", range.getLow());
            saved.addProperty("high", range.getHigh());
        } else if (setting instanceof ModeSetting mode) {
            saved.addProperty("type", "mode");
            saved.addProperty("index", mode.getIndex());
        } else if (setting instanceof DropdownSetting dropdown) {
            saved.addProperty("type", "dropdown");
            saved.addProperty("index", dropdown.getSelected());
        } else if (setting instanceof ToggleListSetting toggle) {
            saved.addProperty("type", "togglelist");
            JsonArray states = new JsonArray();
            for (int i = 0; i < toggle.optionCount(); i++) {
                states.add(toggle.isOn(i));
            }
            saved.add("states", states);
        } else if (setting instanceof KeybindSetting keybind) {
            saved.addProperty("type", "keybind");
            saved.addProperty("value", keybind.get());
        } else {
            return null;
        }
        return saved;
    }

    private static void load() {
        JsonObject root = read();
        if (root == null || !root.has("modules") || !root.get("modules").isJsonObject()) {
            return;
        }
        JsonObject modules = root.getAsJsonObject("modules");
        Map<String, Module> byClass = new HashMap<>();
        for (Module module : ModuleManager.get().getModules()) {
            byClass.put(module.getClass().getSimpleName(), module);
        }
        for (Map.Entry<String, JsonElement> fileEntry : modules.entrySet()) {
            Module module = byClass.get(fileEntry.getKey());
            if (module == null || !fileEntry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject saved = fileEntry.getValue().getAsJsonObject();
            if (saved.has("keybind") && saved.get("keybind").isJsonPrimitive()) {
                try {
                    module.setKeybind(saved.get("keybind").getAsInt());
                } catch (Exception ignored) {
                }
            }
            if (saved.has("visible") && saved.get("visible").isJsonPrimitive()) {
                try {
                    module.setVisibleInMenu(saved.get("visible").getAsBoolean());
                } catch (Exception ignored) {
                }
            }
            if (saved.has("settings") && saved.get("settings").isJsonObject()) {
                Map<String, Setting> byName = new HashMap<>();
                for (Setting setting : module.getSettings()) {
                    byName.put(setting.getName(), setting);
                }
                for (Map.Entry<String, JsonElement> settingEntry :
                        saved.getAsJsonObject("settings").entrySet()) {
                    Setting setting = byName.get(settingEntry.getKey());
                    if (setting != null && settingEntry.getValue().isJsonObject()) {
                        loadSetting(setting, settingEntry.getValue().getAsJsonObject());
                    }
                }
            }
            if (saved.has("enabled") && saved.get("enabled").isJsonPrimitive()) {
                try {
                    module.setEnabled(saved.get("enabled").getAsBoolean());
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void loadSetting(Setting setting, JsonObject saved) {
        try {
            if (setting instanceof BooleanSetting bool && saved.has("value")) {
                bool.set(saved.get("value").getAsBoolean());
            } else if (setting instanceof NumberSetting number && saved.has("value")) {
                number.set(saved.get("value").getAsDouble());
            } else if (setting instanceof RangeSetting range
                    && saved.has("low") && saved.has("high")) {
                range.set(saved.get("low").getAsDouble(), saved.get("high").getAsDouble());
            } else if (setting instanceof ModeSetting mode && saved.has("index")) {
                mode.select(saved.get("index").getAsInt());
            } else if (setting instanceof DropdownSetting dropdown && saved.has("index")) {
                dropdown.select(saved.get("index").getAsInt());
            } else if (setting instanceof ToggleListSetting toggle && saved.has("states")
                    && saved.get("states").isJsonArray()) {
                JsonArray states = saved.getAsJsonArray("states");
                for (int i = 0; i < states.size(); i++) {
                    if (states.get(i).isJsonPrimitive()) {
                        toggle.set(i, states.get(i).getAsBoolean());
                    }
                }
            } else if (setting instanceof KeybindSetting keybind && saved.has("value")) {
                keybind.set(saved.get("value").getAsInt());
            }
        } catch (Exception ignored) {
        }
    }

    private static Path file() throws Exception {
        Path dir = FabricLoader.getInstance().getConfigDir()
                .resolve("perplexity").resolve("profiles");
        Files.createDirectories(dir);
        String name = loadedId == null ? "default" : loadedId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return dir.resolve(name + ".json");
    }

    private static void write(String text) {
        try {
            Files.writeString(file(), text, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static JsonObject read() {
        try {
            Path path = file();
            if (!Files.isRegularFile(path)) {
                return null;
            }
            String text = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) {
                return null;
            }
            JsonElement parsed = JsonParser.parseString(text);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
