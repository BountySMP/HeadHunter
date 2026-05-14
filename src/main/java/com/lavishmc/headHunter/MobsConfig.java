package com.lavishmc.headHunter;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MobsConfig {

    private static final String FILE_NAME = "mobs.yml";
    public static final int MAX_LEVEL = 25;

    private final JavaPlugin plugin;
    private YamlConfiguration config;

    public MobsConfig(JavaPlugin plugin) {
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

    public long getXpPerHead() {
        return config.getLong("xp-per-head", 1L);
    }

    public long xpToReachLevel(int n) {
        return config.getLong("level-thresholds." + n, 0L);
    }

    public long xpForLevel(int n) {
        if (n >= MAX_LEVEL) return Long.MAX_VALUE;
        return xpToReachLevel(n + 1) - xpToReachLevel(n);
    }

    public long getRankupCost(int level) {
        ConfigurationSection mobs = config.getConfigurationSection("mobs");
        if (mobs != null) {
            for (String key : mobs.getKeys(false)) {
                ConfigurationSection mob = mobs.getConfigurationSection(key);
                if (mob != null && mob.getInt("level", 0) == level) {
                    return mob.getLong("cost_to_rankup", 500L);
                }
            }
        }
        return config.getLong("rankup-cost", 500L);
    }

    public ConfigurationSection getMobSection(String mobType) {
        return config.getConfigurationSection("mobs." + mobType);
    }

    public ConfigurationSection getMobsSection() {
        return config.getConfigurationSection("mobs");
    }

    private void saveDefault() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) plugin.saveResource(FILE_NAME, false);
    }
}
