package com.lavishmc.headHunter;

import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles clicks inside the {@link SpawnerModeGUI}.
 * Identified via the InventoryHolder pattern — no title-string comparison needed.
 */
public class SpawnerModeListener implements Listener {

    private final SpawnerStackManager spawnerStackManager;

    public SpawnerModeListener(SpawnerStackManager spawnerStackManager) {
        this.spawnerStackManager = spawnerStackManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Only handle clicks in our GUI (identified by holder type).
        if (!(event.getInventory().getHolder() instanceof SpawnerModeGUI gui)) return;

        event.setCancelled(true);

        // Ignore clicks outside the top 9 slots (i.e. player's own inventory row).
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= 9) return;

        String newMode = switch (raw) {
            case 2 -> "ECO";
            case 6 -> "XP";
            default -> null;
        };
        if (newMode == null) return;

        // Persist the chosen mode on the spawner's tile-entity PDC.
        org.bukkit.Location loc = gui.getSpawnerLocation();
        if (loc.getBlock().getType() != Material.SPAWNER) return;
        if (!(loc.getBlock().getState() instanceof CreatureSpawner cs)) return;
        cs.getPersistentDataContainer().set(
                SpawnerStackManager.SPAWNER_MODE_KEY, PersistentDataType.STRING, newMode);
        cs.update();

        // Refresh the floating hologram to reflect the new mode immediately.
        spawnerStackManager.refreshLabel(loc);

        player.closeInventory();
        if ("XP".equals(newMode)) {
            player.sendMessage("§a§lXP Mode §r§7activated — mobs will drop XP only.");
        } else {
            player.sendMessage("§6§lEco Mode §r§7activated — mobs drop heads and loot.");
        }
    }
}
