package com.lavishmc.headHunter;

import com.google.gson.Gson;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stores per-player XP and rank level (1–25).
 * Data is persisted to plugins/HeadHunter/playerdata.json and loaded on startup.
 *
 * <p>XP is per-level: it counts up from 0 to the level's requirement, then resets
 * to 0 on each /rankup. The required XP per level is read from mobs.yml
 * under {@code level-xp-required.<level>}.</p>
 */
public class PlayerDataManager {

    private static final Gson GSON = new Gson();

    private static class DataStore {
        Map<String, Long>   xp             = new HashMap<>();
        Map<String, Object> level          = new HashMap<>();
        Map<String, Long>   totalHeadsSold = new HashMap<>();
    }

    private final JavaPlugin plugin;
    private final MobsConfig mobsConfig;
    private final File dataFile;

    // ConcurrentHashMap so async save reads and main-thread writes don't race.
    private final ConcurrentHashMap<UUID, Long>    playerXP          = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> playerLevel       = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long>    totalHeadsSold    = new ConcurrentHashMap<>();

    // Guards against queuing multiple redundant async saves when data changes rapidly.
    private final AtomicBoolean savePending = new AtomicBoolean(false);

    public PlayerDataManager(JavaPlugin plugin, MobsConfig mobsConfig) {
        this.plugin     = plugin;
        this.mobsConfig = mobsConfig;
        this.dataFile   = new File(plugin.getDataFolder(), "playerdata.json");
        load();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public int getMaxLevel() { return MobsConfig.MAX_LEVEL; }

    public long getXP(UUID uuid) {
        return playerXP.getOrDefault(uuid, 0L);
    }

    public void setXP(UUID uuid, long amount) {
        playerXP.put(uuid, Math.max(0, amount));
        save();
    }

    public void addXP(UUID uuid, long amount) {
        playerXP.merge(uuid, amount, Long::sum);
        // Cap XP at the requirement for the current level so it can't overflow.
        int currentLevel = getLevel(uuid);
        if (currentLevel < getMaxLevel()) {
            long cap = mobsConfig.getXpRequiredForLevel(currentLevel);
            Long current = playerXP.get(uuid);
            if (cap > 0 && current != null && current > cap) {
                playerXP.put(uuid, cap);
            }
        }
        save();
    }

    public int getLevel(UUID uuid) {
        return playerLevel.getOrDefault(uuid, 1);
    }

    public void setLevel(UUID uuid, int level) {
        playerLevel.put(uuid, Math.max(1, Math.min(level, getMaxLevel())));
        save();
    }

    public int getTier(UUID uuid) {
        return (getLevel(uuid) - 1) / 5 + 1;
    }

    public long getTotalHeadsSold(UUID uuid) {
        return totalHeadsSold.getOrDefault(uuid, 0L);
    }

    public void addHeadsSold(UUID uuid, long quantity) {
        totalHeadsSold.merge(uuid, quantity, Long::sum);
        save();
    }

    public Map<UUID, Long> getAllHeadsSold() {
        return new HashMap<>(totalHeadsSold);
    }

    /** Returns all player UUIDs that have data in this manager. */
    public java.util.Set<UUID> getAllPlayerUUIDs() {
        java.util.Set<UUID> allUUIDs = new java.util.HashSet<>();
        allUUIDs.addAll(playerXP.keySet());
        allUUIDs.addAll(playerLevel.keySet());
        allUUIDs.addAll(totalHeadsSold.keySet());
        return allUUIDs;
    }

    // -------------------------------------------------------------------------
    // XP helpers
    // -------------------------------------------------------------------------

    /** XP required to advance from the given level to the next. 0 at max level. */
    public long getXpRequiredForLevel(int level) {
        return mobsConfig.getXpRequiredForLevel(level);
    }

    public long getRankupCost(int level) {
        return mobsConfig.getRankupCost(level);
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    private void load() {
        plugin.getDataFolder().mkdirs();
        plugin.getLogger().info("[HH] Loading playerdata from: " + dataFile.getAbsolutePath());
        if (!dataFile.exists()) {
            plugin.getLogger().info("[HH] playerdata.json not found — starting fresh.");
            return;
        }
        try (Reader reader = new FileReader(dataFile)) {
            DataStore store = GSON.fromJson(reader, DataStore.class);
            if (store == null) {
                plugin.getLogger().warning("[HH] playerdata.json parsed as null — file may be empty or corrupt.");
                return;
            }
            if (store.xp != null) {
                for (Map.Entry<String, Long> e : store.xp.entrySet()) tryPutXP(e.getKey(), e.getValue());
            }
            if (store.level != null) {
                for (Map.Entry<String, ?> e : store.level.entrySet()) {
                    if (e.getValue() instanceof Number n) tryPutLevel(e.getKey(), n.intValue());
                }
            }
            if (store.totalHeadsSold != null) {
                for (Map.Entry<String, Long> e : store.totalHeadsSold.entrySet()) {
                    tryPutHeadsSold(e.getKey(), e.getValue());
                }
            }
            plugin.getLogger().info("[HH] Loaded playerdata.json — "
                    + playerXP.size() + " XP entries, " + playerLevel.size() + " level entries, "
                    + totalHeadsSold.size() + " heads sold entries.");
        } catch (Exception e) {
            plugin.getLogger().warning("[HH] Failed to load playerdata.json: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Debounced async save — coalesces rapid successive calls into a single write. */
    private void save() {
        if (savePending.compareAndSet(false, true)) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                savePending.set(false);
                doSave();
            });
        }
    }

    /** Synchronous save — call from plugin disable so in-flight data is not lost. */
    public void flush() {
        doSave();
    }

    private synchronized void doSave() {
        plugin.getDataFolder().mkdirs();
        DataStore store = new DataStore();
        for (Map.Entry<UUID, Long>    e : playerXP.entrySet())         store.xp.put(e.getKey().toString(), e.getValue());
        for (Map.Entry<UUID, Integer> e : playerLevel.entrySet())      store.level.put(e.getKey().toString(), e.getValue());
        for (Map.Entry<UUID, Long>    e : totalHeadsSold.entrySet())   store.totalHeadsSold.put(e.getKey().toString(), e.getValue());
        try (Writer writer = new FileWriter(dataFile)) {
            GSON.toJson(store, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("[HH] Failed to save playerdata.json: " + e.getMessage());
        }
    }

    private void tryPutXP(String key, Long value) {
        if (value == null) return;
        try { playerXP.put(UUID.fromString(key), value); } catch (IllegalArgumentException ignored) {}
    }

    public void wipeAll() {
        playerXP.clear();
        playerLevel.clear();
        totalHeadsSold.clear();
        dataFile.delete();
    }

    private void tryPutLevel(String key, int value) {
        try { playerLevel.put(UUID.fromString(key), value); } catch (IllegalArgumentException ignored) {}
    }

    private void tryPutHeadsSold(String key, Long value) {
        if (value == null) return;
        try { totalHeadsSold.put(UUID.fromString(key), value); } catch (IllegalArgumentException ignored) {}
    }
}
