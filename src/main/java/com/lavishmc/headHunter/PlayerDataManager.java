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
 * <p>XP is earned passively by selling heads and acts as the gate for /rankup.
 * The stored level only advances when a player explicitly uses /rankup and pays
 * the money cost — it is never automatically derived from XP.</p>
 */
public class PlayerDataManager {

    private static final Gson GSON = new Gson();

    private static class DataStore {
        Map<String, Long>   xp    = new HashMap<>();
        Map<String, Object> level = new HashMap<>();
    }

    private final JavaPlugin plugin;
    private final MobsConfig mobsConfig;
    private final File dataFile;

    // ConcurrentHashMap so async save reads and main-thread writes don't race.
    private final ConcurrentHashMap<UUID, Long>    playerXP    = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> playerLevel = new ConcurrentHashMap<>();

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
        int currentLevel = getLevel(uuid);
        if (currentLevel < getMaxLevel()) {
            long cap = xpToReachLevel(currentLevel + 1);
            if (playerXP.get(uuid) > cap) playerXP.put(uuid, cap);
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

    // -------------------------------------------------------------------------
    // XP math helpers — delegate to MobsConfig
    // -------------------------------------------------------------------------

    public long xpToReachLevel(int n) {
        return mobsConfig.xpToReachLevel(n);
    }

    public long getRankupCost(int level) {
        return mobsConfig.getRankupCost(level);
    }

    public long xpForLevel(int n) {
        return mobsConfig.xpForLevel(n);
    }

    public int levelFromXP(long xp) {
        int level = 1;
        for (int n = 2; n <= getMaxLevel(); n++) {
            if (xp >= xpToReachLevel(n)) level = n;
            else break;
        }
        return level;
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
            plugin.getLogger().info("[HH] Loaded playerdata.json — "
                    + playerXP.size() + " XP entries, " + playerLevel.size() + " level entries.");
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
        for (Map.Entry<UUID, Long>    e : playerXP.entrySet())    store.xp.put(e.getKey().toString(), e.getValue());
        for (Map.Entry<UUID, Integer> e : playerLevel.entrySet()) store.level.put(e.getKey().toString(), e.getValue());
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

    private void tryPutLevel(String key, int value) {
        try { playerLevel.put(UUID.fromString(key), value); } catch (IllegalArgumentException ignored) {}
    }
}
