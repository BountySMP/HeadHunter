package com.lavishmc.headHunter;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI expansion for HeadHunter.
 * Provides placeholders:
 * - %headhunter_level% — player's current level
 * - %headhunter_xp% — player's current XP
 * - %headhunter_heads_sold% — total heads sold
 * - %headhunter_rank% — player's current rank name from mobs.yml
 */
public class HeadHunterExpansion extends PlaceholderExpansion {

    private final HeadHunter plugin;
    private final PlayerDataManager playerDataManager;
    private final MobsConfig mobsConfig;

    public HeadHunterExpansion(HeadHunter plugin, PlayerDataManager playerDataManager, MobsConfig mobsConfig) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.mobsConfig = mobsConfig;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "headhunter";
    }

    @Override
    public @NotNull String getAuthor() {
        return "antony125";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Keep expansion loaded across reloads
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        return switch (params.toLowerCase()) {
            case "level" -> String.valueOf(playerDataManager.getLevel(player.getUniqueId()));
            case "xp" -> String.valueOf(playerDataManager.getXP(player.getUniqueId()));
            case "heads_sold" -> String.valueOf(playerDataManager.getTotalHeadsSold(player.getUniqueId()));
            case "rank" -> getRankName(playerDataManager.getLevel(player.getUniqueId()));
            case "progress" -> getProgress(player.getUniqueId());
            default -> null;
        };
    }

    private String getProgress(java.util.UUID uuid) {
        int level = playerDataManager.getLevel(uuid);
        if (level >= playerDataManager.getMaxLevel()) return "MAX";
        long xp = playerDataManager.getXP(uuid);
        long required = playerDataManager.getXpRequiredForLevel(level);
        if (required <= 0) return "0%";
        int pct = (int) Math.min(100, xp * 100 / required);
        return pct + "%";
    }

    /**
     * Returns the rank name for a given level by looking up the mob entry
     * in mobs.yml that matches the level.
     */
    private String getRankName(int level) {
        ConfigurationSection mobsSection = mobsConfig.getMobsSection();
        if (mobsSection == null) return "Unknown";

        for (String mobKey : mobsSection.getKeys(false)) {
            ConfigurationSection mobSection = mobsSection.getConfigurationSection(mobKey);
            if (mobSection != null && mobSection.getInt("level", 0) == level) {
                return mobsConfig.getFormattedMobName(mobKey);
            }
        }
        return "Unknown";
    }
}
