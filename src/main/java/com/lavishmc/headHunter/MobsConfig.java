package com.lavishmc.headHunter;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MobsConfig {

    private static final String FILE_NAME = "mobs.yml";
    public static final int MAX_LEVEL = 25;

    private final JavaPlugin plugin;
    private YamlConfiguration config;

    // Cached and sorted once per reload so getMobType() doesn't rebuild on every right-click.
    private List<String>        cachedSortedKeys    = new ArrayList<>();
    private Map<String, String> cachedFormattedNames = new HashMap<>();

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
        buildMobKeyCache();
    }

    public long getXpPerHead() {
        return Math.max(0L, config.getLong("xp-per-head", 1L));
    }

    /**
     * XP required to advance FROM the given level to the next.
     * Returns 0 for MAX_LEVEL (cannot advance further).
     */
    public long getXpRequiredForLevel(int level) {
        if (level >= MAX_LEVEL) return 0;
        return Math.max(0, config.getLong("level-xp-required." + level, 0));
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

    /** XP orbs to spawn per mob (×stack size) when a spawner is in XP mode. */
    public int getXpModeOrbs(String mobType) {
        ConfigurationSection section = getMobSection(mobType);
        if (section != null && section.isInt("xp_mode_orbs")) {
            return Math.max(1, section.getInt("xp_mode_orbs"));
        }
        return Math.max(1, config.getInt("xp-mode-orbs-default", 5));
    }

    public ConfigurationSection getMobSection(String mobType) {
        return config.getConfigurationSection("mobs." + mobType);
    }

    public ConfigurationSection getMobsSection() {
        return config.getConfigurationSection("mobs");
    }

    /** Mob keys sorted by formatted-name length descending — for longest-match display-name lookup. */
    public List<String> getSortedMobKeys() {
        return cachedSortedKeys;
    }

    /** Formatted display name for a mob key (e.g. "WITHER_SKELETON" → "Wither Skeleton"). */
    public String getFormattedMobName(String key) {
        return cachedFormattedNames.getOrDefault(key, formatMobName(key));
    }

    private void buildMobKeyCache() {
        ConfigurationSection mobs = config.getConfigurationSection("mobs");
        if (mobs == null) {
            cachedSortedKeys     = new ArrayList<>();
            cachedFormattedNames = new HashMap<>();
            return;
        }
        Map<String, String> names = new HashMap<>();
        List<String> keys = new ArrayList<>(mobs.getKeys(false));
        for (String key : keys) names.put(key, formatMobName(key));
        keys.sort((a, b) -> names.get(b).length() - names.get(a).length());
        cachedSortedKeys     = Collections.unmodifiableList(keys);
        cachedFormattedNames = Collections.unmodifiableMap(names);
    }

    private static String formatMobName(String typeName) {
        String[] words = typeName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }

    private void saveDefault() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) plugin.saveResource(FILE_NAME, false);
    }
}
