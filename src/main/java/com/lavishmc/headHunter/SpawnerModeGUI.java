package com.lavishmc.headHunter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * 9-slot GUI for selecting a spawner's drop mode (ECO or XP).
 * Uses the InventoryHolder pattern so the click listener can identify
 * the inventory without title-string comparison.
 */
public class SpawnerModeGUI implements InventoryHolder {

    private final Location spawnerLocation;
    private final Inventory inventory;

    private SpawnerModeGUI(Location spawnerLocation, String currentMode) {
        this.spawnerLocation = spawnerLocation;

        Component title = LegacyComponentSerializer.legacySection()
                .deserialize("§8Spawner Mode");
        this.inventory = Bukkit.createInventory(this, 9, title);

        // Gray glass panes fill every slot by default.
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        paneMeta.displayName(Component.empty());
        pane.setItemMeta(paneMeta);
        for (int i = 0; i < 9; i++) inventory.setItem(i, pane.clone());

        boolean ecoActive = !"XP".equals(currentMode);

        // Slot 2 — Eco Mode
        ItemStack eco = new ItemStack(Material.GOLD_INGOT);
        ItemMeta ecoMeta = eco.getItemMeta();
        ecoMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(
                "§6§lEco Mode" + (ecoActive ? " §a✔" : "")));
        ecoMeta.lore(List.of(
                LegacyComponentSerializer.legacySection()
                        .deserialize("§7Mobs drop heads and loot as normal.")));
        eco.setItemMeta(ecoMeta);
        inventory.setItem(2, eco);

        // Slot 6 — XP Mode
        ItemStack xp = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta xpMeta = xp.getItemMeta();
        xpMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(
                "§a§lXP Mode" + (!ecoActive ? " §a✔" : "")));
        xpMeta.lore(List.of(
                LegacyComponentSerializer.legacySection()
                        .deserialize("§7Mobs drop XP only. No heads.")));
        xp.setItemMeta(xpMeta);
        inventory.setItem(6, xp);
    }

    public Location getSpawnerLocation() {
        return spawnerLocation;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static void open(Player player, Location spawnerLoc, String currentMode) {
        player.openInventory(new SpawnerModeGUI(spawnerLoc, currentMode).inventory);
    }
}
