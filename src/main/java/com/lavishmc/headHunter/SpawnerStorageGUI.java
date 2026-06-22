package com.lavishmc.headHunter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 54-slot paginated storage GUI showing accumulated drops.
 */
public class SpawnerStorageGUI implements InventoryHolder {

    private final Location spawnerLocation;
    private final Inventory inventory;
    private final int page;
    private final int totalPages;
    private final SpawnerStackManager manager;
    private final MobsConfig mobsConfig;

    // Head materials that should be displayed with special formatting
    private static final Set<Material> SKULL_MATERIALS = Set.of(
            Material.PLAYER_HEAD,
            Material.ZOMBIE_HEAD,
            Material.SKELETON_SKULL,
            Material.WITHER_SKELETON_SKULL,
            Material.CREEPER_HEAD,
            Material.PIGLIN_HEAD,
            Material.DRAGON_HEAD
    );

    private SpawnerStorageGUI(Location spawnerLocation, SpawnerStackManager manager, int page, MobsConfig mobsConfig) {
        this.spawnerLocation = spawnerLocation;
        this.manager = manager;
        this.page = page;
        this.mobsConfig = mobsConfig;

        String locKey = manager.locKey(spawnerLocation);
        List<ItemStack> itemList = manager.getAccumulatedItems(locKey);

        this.totalPages = Math.max(1, (int) Math.ceil(itemList.size() / 45.0));

        Component title = LegacyComponentSerializer.legacySection()
                .deserialize("§8Spawner Storage §7— Page " + page);
        this.inventory = Bukkit.createInventory(this, 54, title);

        // Slots 0-44: paginated items (now using actual ItemStacks with metadata)
        int startIndex = (page - 1) * 45;
        for (int i = 0; i < 45 && startIndex + i < itemList.size(); i++) {
            ItemStack storedItem = itemList.get(startIndex + i);
            if (storedItem == null) continue;

            ItemStack displayItem = storedItem.clone();
            ItemMeta meta = displayItem.getItemMeta();
            if (meta == null) continue;

            // Special formatting for heads
            if (SKULL_MATERIALS.contains(storedItem.getType())) {
                // The head already has proper texture from DropHeads
                // Just add price lore
                String mobType = getMobTypeFromSpawner(locKey);
                double price = getPriceForMob(mobType);
                double totalWorth = price * storedItem.getAmount();

                List<Component> lore = new ArrayList<>();
                if (price > 0) {
                    lore.add(lc("§7Price: §a$" + String.format("%.2f", price) + " §7each"));
                    lore.add(lc("§7Total Worth: §a$" + String.format("%.2f", totalWorth)));
                }
                meta.lore(lore);
            }

            displayItem.setItemMeta(meta);
            inventory.setItem(i, displayItem);
        }

        // Slot 45: Back (Barrier)
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(lc("§cBack"));
        back.setItemMeta(backMeta);
        inventory.setItem(45, back);

        // Slot 46: Collect All (Chest)
        ItemStack collect = new ItemStack(Material.CHEST);
        ItemMeta collectMeta = collect.getItemMeta();
        collectMeta.displayName(lc("§aCollect All"));
        collect.setItemMeta(collectMeta);
        inventory.setItem(46, collect);

        // Slot 48: Previous Page (Arrow)
        ItemStack prev = new ItemStack(Material.ARROW);
        ItemMeta prevMeta = prev.getItemMeta();
        prevMeta.displayName(lc("§7Previous Page"));
        prev.setItemMeta(prevMeta);
        inventory.setItem(48, prev);

        // Slot 50: Next Page (Arrow)
        ItemStack next = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = next.getItemMeta();
        nextMeta.displayName(lc("§7Next Page"));
        next.setItemMeta(nextMeta);
        inventory.setItem(50, next);

        // Slot 52: Drop All (Dropper)
        ItemStack drop = new ItemStack(Material.DROPPER);
        ItemMeta dropMeta = drop.getItemMeta();
        dropMeta.displayName(lc("§aDrop All"));
        drop.setItemMeta(dropMeta);
        inventory.setItem(52, drop);

        // Slot 53: Sell All (Gold Ingot)
        ItemStack sell = new ItemStack(Material.GOLD_INGOT);
        ItemMeta sellMeta = sell.getItemMeta();
        sellMeta.displayName(lc("§aSell All"));
        sell.setItemMeta(sellMeta);
        inventory.setItem(53, sell);
    }

    public Location getSpawnerLocation() {
        return spawnerLocation;
    }

    public int getPage() {
        return page;
    }

    public int getTotalPages() {
        return totalPages;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static void open(Player player, Location spawnerLoc, SpawnerStackManager manager, int page, MobsConfig mobsConfig) {
        player.openInventory(new SpawnerStorageGUI(spawnerLoc, manager, Math.max(1, page), mobsConfig).inventory);
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

    /**
     * Gets the mob type from the spawner itself.
     */
    private String getMobTypeFromSpawner(String locKey) {
        org.bukkit.entity.EntityType type = manager.getSpawnerType(locKey);
        return type != null ? type.name() : null;
    }

    /**
     * Gets the sell price for a mob from mobs.yml.
     */
    private double getPriceForMob(String mobKey) {
        if (mobKey == null || mobsConfig == null) return 0;
        ConfigurationSection mobSection = mobsConfig.getMobSection(mobKey);
        if (mobSection == null) return 0;
        return mobSection.getDouble("sell_price", 0);
    }

    private static Component lc(String legacyText) {
        return LegacyComponentSerializer.legacySection().deserialize(legacyText)
                .decoration(TextDecoration.ITALIC, false);
    }
}
