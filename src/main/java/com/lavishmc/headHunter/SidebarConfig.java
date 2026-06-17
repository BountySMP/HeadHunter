package com.lavishmc.headHunter;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class SidebarConfig {

    private static final String FILE_NAME = "sidebar.yml";

    private final JavaPlugin plugin;
    private YamlConfiguration config;

    public SidebarConfig(JavaPlugin plugin) {
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

    /** Returns a sidebar line with {placeholder} tokens replaced by the given pairs: key, value, key, value... */
    public String getLine(String key, String... replacements) {
        String val = config.getString("lines." + key, "");
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            val = val.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return ChatColor.translateAlternateColorCodes('&', val);
    }

    public String getServerName() {
        return config.getString("server-name", "HeadHunter");
    }

    public BossBar.Color getTierBossBarColor(int tier) {
        String name = config.getString("tier-colors." + tier, "WHITE").toUpperCase();
        try {
            return BossBar.Color.valueOf(name);
        } catch (IllegalArgumentException e) {
            return BossBar.Color.WHITE;
        }
    }

    private void saveDefault() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) plugin.saveResource(FILE_NAME, false);
    }
}
