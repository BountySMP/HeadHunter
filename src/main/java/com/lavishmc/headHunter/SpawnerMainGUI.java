package com.lavishmc.headHunter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 27-slot main spawner GUI showing storage, mode, XP, and spawner info.
 */
public class SpawnerMainGUI implements InventoryHolder {

    private final Location spawnerLocation;
    private final Inventory inventory;
    private final SpawnerStackManager manager;

    private SpawnerMainGUI(Location spawnerLocation, SpawnerStackManager manager) {
        this.spawnerLocation = spawnerLocation;
        this.manager = manager;

        Component title = LegacyComponentSerializer.legacySection()
                .deserialize("§8Spawner Menu");
        this.inventory = Bukkit.createInventory(this, 27, title);

        // Fill with gray glass panes
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        paneMeta.displayName(Component.empty());
        pane.setItemMeta(paneMeta);
        for (int i = 0; i < 27; i++) inventory.setItem(i, pane.clone());

        String locKey = manager.locKey(spawnerLocation);
        EntityType type = manager.getSpawnerType(locKey);
        int count = manager.getStackCount(locKey);
        String mode = getBlockMode(spawnerLocation);

        // Slot 11 — Storage (Chest)
        ItemStack storage = new ItemStack(Material.CHEST);
        ItemMeta storageMeta = storage.getItemMeta();
        storageMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§6Open Storage"));
        List<Component> storageLore = new ArrayList<>();
        if ("ECO".equals(mode)) {
            Map<Material, Long> drops = manager.getAccumulatedDrops(locKey);
            if (drops == null || drops.isEmpty()) {
                storageLore.add(LegacyComponentSerializer.legacySection().deserialize("§7Empty"));
            } else {
                long total = drops.values().stream().mapToLong(Long::longValue).sum();
                storageLore.add(LegacyComponentSerializer.legacySection().deserialize("§7Total items: §b" + total));
                // Show top 2 items
                drops.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                        .limit(2)
                        .forEach(e -> storageLore.add(LegacyComponentSerializer.legacySection()
                                .deserialize("§8• §f" + formatMaterial(e.getKey()) + " §7x" + e.getValue())));
            }
        } else {
            storageLore.add(LegacyComponentSerializer.legacySection().deserialize("§7Empty (XP mode)"));
        }
        storageMeta.lore(storageLore);
        storage.setItemMeta(storageMeta);
        inventory.setItem(11, storage);

        // Slot 13 — Spawner info (mob head)
        ItemStack spawnerInfo = new ItemStack(getMobHeadMaterial(type));
        ItemMeta spawnerMeta = spawnerInfo.getItemMeta();
        String mobName = formatMobName(type);
        spawnerMeta.displayName(LegacyComponentSerializer.legacySection()
                .deserialize("§e" + count + "x " + mobName + " Spawner"));
        List<Component> spawnerLore = new ArrayList<>();
        spawnerLore.add(LegacyComponentSerializer.legacySection().deserialize("§7Mode: " + (mode.equals("XP") ? "§a§lXP" : "§6§lECO")));
        spawnerMeta.lore(spawnerLore);
        spawnerInfo.setItemMeta(spawnerMeta);
        inventory.setItem(13, spawnerInfo);

        // Slot 15 — XP Collection (Experience Bottle)
        ItemStack xpBottle = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta xpMeta = xpBottle.getItemMeta();
        xpMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§aCollect XP"));
        List<Component> xpLore = new ArrayList<>();
        long accumulatedXP = manager.getAccumulatedXP(locKey);
        xpLore.add(LegacyComponentSerializer.legacySection().deserialize("§7Accumulated: §b" + accumulatedXP + " XP"));
        xpMeta.lore(xpLore);
        xpBottle.setItemMeta(xpMeta);
        inventory.setItem(15, xpBottle);

        // Slot 22 — Change Mode (Redstone)
        ItemStack changeMode = new ItemStack(Material.REDSTONE);
        ItemMeta modeMeta = changeMode.getItemMeta();
        modeMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§cChange Mode"));
        List<Component> modeLore = new ArrayList<>();
        modeLore.add(LegacyComponentSerializer.legacySection()
                .deserialize("§7Current: " + (mode.equals("XP") ? "§a§lXP" : "§6§lECO")));
        modeMeta.lore(modeLore);
        changeMode.setItemMeta(modeMeta);
        inventory.setItem(22, changeMode);
    }

    public Location getSpawnerLocation() {
        return spawnerLocation;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static void open(Player player, Location spawnerLoc, SpawnerStackManager manager) {
        player.openInventory(new SpawnerMainGUI(spawnerLoc, manager).inventory);
    }

    private static String getBlockMode(Location loc) {
        if (!(loc.getBlock().getState() instanceof CreatureSpawner cs)) return "ECO";
        String mode = cs.getPersistentDataContainer().get(
                SpawnerStackManager.SPAWNER_MODE_KEY, PersistentDataType.STRING);
        return "XP".equals(mode) ? "XP" : "ECO";
    }

    private static Material getMobHeadMaterial(EntityType type) {
        if (type == null) return Material.SKELETON_SKULL;
        return switch (type) {
            case ZOMBIE -> Material.ZOMBIE_HEAD;
            case SKELETON -> Material.SKELETON_SKULL;
            case CREEPER -> Material.CREEPER_HEAD;
            case WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL;
            case ENDER_DRAGON -> Material.DRAGON_HEAD;
            default -> Material.PLAYER_HEAD;
        };
    }

    private static String formatMobName(EntityType type) {
        if (type == null) return "Unknown";
        String[] words = type.name().toLowerCase().split("_");
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
