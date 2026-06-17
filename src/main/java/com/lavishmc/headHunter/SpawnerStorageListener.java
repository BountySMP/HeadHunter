package com.lavishmc.headHunter;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Handles clicks in SpawnerStorageGUI.
 */
public class SpawnerStorageListener implements Listener {

    private final SpawnerStackManager spawnerStackManager;

    public SpawnerStorageListener(SpawnerStackManager spawnerStackManager) {
        this.spawnerStackManager = spawnerStackManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof SpawnerStorageGUI gui)) return;

        event.setCancelled(true);

        int raw = event.getRawSlot();
        if (raw < 0 || raw >= 54) return;

        org.bukkit.Location loc = gui.getSpawnerLocation();
        String locKey = spawnerStackManager.locKey(loc);
        int currentPage = gui.getPage();

        switch (raw) {
            case 45 -> // Back to main GUI
                    SpawnerMainGUI.open(player, loc, spawnerStackManager);
            case 46 -> { // Collect All
                spawnerStackManager.collectAll(locKey, player);
                player.closeInventory();
            }
            case 48 -> // Previous Page
                    SpawnerStorageGUI.open(player, loc, spawnerStackManager, currentPage - 1);
            case 50 -> // Next Page
                    SpawnerStorageGUI.open(player, loc, spawnerStackManager, currentPage + 1);
            case 52 -> { // Drop All
                spawnerStackManager.dropAll(locKey, player);
                player.closeInventory();
            }
            case 53 -> { // Sell All
                spawnerStackManager.sellAll(locKey, player);
                player.closeInventory();
            }
        }
    }
}
