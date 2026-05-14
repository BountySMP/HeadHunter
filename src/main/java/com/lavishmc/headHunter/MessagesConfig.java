package com.lavishmc.headHunter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MessagesConfig {

    private static final String FILE_NAME = "messages.yml";

    private final JavaPlugin plugin;
    private YamlConfiguration config;

    public MessagesConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        saveDefault();
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        config = YamlConfiguration.loadConfiguration(file);
        InputStream bundled = plugin.getResource(FILE_NAME);
        if (bundled != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(bundled, StandardCharsets.UTF_8)));
        }
    }

    /** Returns a message Component with {placeholder} pairs replaced. Pairs: key, value, key, value... */
    public Component get(String key, String... replacements) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(getRaw(key, replacements));
    }

    /** Returns the raw string (with & codes) for a message key, with replacements applied. */
    public String getRaw(String key, String... replacements) {
        String val = config.getString("messages." + key, "&c[HH] Missing message: " + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            val = val.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return val;
    }

    // ── Player-head config ────────────────────────────────────────────────────

    public boolean isPlayerHeadsEnabled() {
        return config.getBoolean("player-heads.enabled", true);
    }

    public int getBalancePercent() {
        return config.getInt("player-heads.balance-percent", 25);
    }

    public int getDeathRestrictionSeconds() {
        return config.getInt("player-heads.death-restriction-seconds", 60);
    }

    public boolean isDropOnPvpOnly() {
        return config.getBoolean("player-heads.drop-on-pvp-only", false);
    }

    private void saveDefault() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) plugin.saveResource(FILE_NAME, false);
    }
}
