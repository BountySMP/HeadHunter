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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 54-slot paginated storage GUI showing accumulated drops.
 */
public class SpawnerStorageGUI implements InventoryHolder {

    private final Location spawnerLocation;
    private final Inventory inventory;
    private final int page;
    private final SpawnerStackManager manager;

    private SpawnerStorageGUI(Location spawnerLocation, SpawnerStackManager manager, int page) {
        this.spawnerLocation = spawnerLocation;
        this.manager = manager;
        this.page = page;

        String locKey = manager.locKey(spawnerLocation);
        Map<Material, Long> drops = manager.getAccumulatedDrops(locKey);
        List<Map.Entry<Material, Long>> itemList = drops == null ? new ArrayList<>() : new ArrayList<>(drops.entrySet());

        int totalPages = Math.max(1, (int) Math.ceil(itemList.size() / 45.0));

        Component title = LegacyComponentSerializer.legacySection()
                .deserialize("§8Spawner Storage §7— Page " + page + "/" + totalPages);
        this.inventory = Bukkit.createInventory(this, 54, title);

        // Slots 0-44: paginated items
        int startIndex = (page - 1) * 45;
        for (int i = 0; i < 45 && startIndex + i < itemList.size(); i++) {
            Map.Entry<Material, Long> entry = itemList.get(startIndex + i);
            ItemStack item = new ItemStack(entry.getKey());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(LegacyComponentSerializer.legacySection()
                    .deserialize("§f" + formatMaterial(entry.getKey()) + " §7x" + entry.getValue()));
            item.setItemMeta(meta);
            inventory.setItem(i, item);
        }

        // Slot 45: Back (Barrier)
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§cBack"));
        back.setItemMeta(backMeta);
        inventory.setItem(45, back);

        // Slot 46: Collect All (Chest)
        ItemStack collect = new ItemStack(Material.CHEST);
        ItemMeta collectMeta = collect.getItemMeta();
        collectMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§aCollect All"));
        collect.setItemMeta(collectMeta);
        inventory.setItem(46, collect);

        // Slot 48: Previous Page (Arrow)
        if (page > 1) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            prevMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§7Previous Page"));
            prev.setItemMeta(prevMeta);
            inventory.setItem(48, prev);
        }

        // Slot 49: Page Indicator (Spectral Arrow)
        ItemStack pageIndicator = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta pageMeta = pageIndicator.getItemMeta();
        pageMeta.displayName(LegacyComponentSerializer.legacySection()
                .deserialize("§ePage " + page + " §7of §e" + totalPages));
        pageIndicator.setItemMeta(pageMeta);
        inventory.setItem(49, pageIndicator);

        // Slot 50: Next Page (Arrow)
        if (page < totalPages) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            nextMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§7Next Page"));
            next.setItemMeta(nextMeta);
            inventory.setItem(50, next);
        }

        // Slot 52: Drop All (Dropper)
        ItemStack drop = new ItemStack(Material.DROPPER);
        ItemMeta dropMeta = drop.getItemMeta();
        dropMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§aDrop All"));
        drop.setItemMeta(dropMeta);
        inventory.setItem(52, drop);

        // Slot 53: Sell All (Gold Ingot)
        ItemStack sell = new ItemStack(Material.GOLD_INGOT);
        ItemMeta sellMeta = sell.getItemMeta();
        sellMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§aSell All"));
        sell.setItemMeta(sellMeta);
        inventory.setItem(53, sell);
    }

    public Location getSpawnerLocation() {
        return spawnerLocation;
    }

    public int getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static void open(Player player, Location spawnerLoc, SpawnerStackManager manager, int page) {
        player.openInventory(new SpawnerStorageGUI(spawnerLoc, manager, page).inventory);
    }

    private static String formatMaterial(Material material) {
        String[] words = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(' ');
            }
        }
        return sb.toString().trim();
    }
}
