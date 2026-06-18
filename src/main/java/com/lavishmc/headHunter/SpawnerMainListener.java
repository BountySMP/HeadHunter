package com.lavishmc.headHunter;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Handles clicks in SpawnerMainGUI.
 */
public class SpawnerMainListener implements Listener {

    private final SpawnerStackManager spawnerStackManager;

    public SpawnerMainListener(SpawnerStackManager spawnerStackManager) {
        this.spawnerStackManager = spawnerStackManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof SpawnerMainGUI gui)) return;

        event.setCancelled(true);

        int raw = event.getRawSlot();
        if (raw < 0 || raw >= 27) return;

        org.bukkit.Location loc = gui.getSpawnerLocation();
        String locKey = spawnerStackManager.locKey(loc);

        switch (raw) {
            case 11 -> // Open Storage
                    SpawnerStorageGUI.open(player, loc, spawnerStackManager, 1, spawnerStackManager.getMobsConfig());
            case 15 -> { // Collect XP
                spawnerStackManager.collectXP(locKey, player);
                player.closeInventory();
            }
            case 22 -> // Change Mode
                    SpawnerModeGUI.open(player, loc, spawnerStackManager.getBlockMode(loc.getBlock()));
        }
    }
}
