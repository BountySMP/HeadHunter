package com.lavishmc.headHunter;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class SpawnerConfig {

    private static final String FILE_NAME = "spawners.yml";
    private static final double DEFAULT_RATE = 5.0;
    private static final double MIN_RATE = 0.05;

    private final JavaPlugin plugin;
    private YamlConfiguration config;

    public SpawnerConfig(JavaPlugin plugin) {
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

    public int getStackMax() {
        return Math.max(1, Math.min(10_000, config.getInt("stack-max", 50)));
    }

    public double getSpawnRate(String mobType) {
        double rate;
        if (config.contains("spawn-rates." + mobType)) {
            rate = config.getDouble("spawn-rates." + mobType, DEFAULT_RATE);
        } else {
            rate = config.getDouble("spawn-rates.default", DEFAULT_RATE);
        }
        return Math.max(rate, MIN_RATE);
    }

    /**
     * Gets the XP amount per mob in XP mode.
     * Default is 0.1 XP per mob.
     * Supports decimal values for fine-tuned XP rates.
     */
    public double getXpPerMob() {
        return Math.max(0.01, config.getDouble("xp-per-mob", 0.1));
    }

    private void saveDefault() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) plugin.saveResource(FILE_NAME, false);
    }
}
