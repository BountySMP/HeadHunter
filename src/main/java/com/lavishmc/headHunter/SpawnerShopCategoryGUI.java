package com.lavishmc.headHunter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Per-tier 9-slot GUI showing up to 5 spawners in slots 0-4, sorted ascending by level.
 * Every item is a SPAWNER block — locked status is shown via a lore line, never via barriers.
 */
public class SpawnerShopCategoryGUI implements InventoryHolder {

    public record MobEntry(String typeName, int level, long cost, long sellPrice) {}

    private static final String[] TIER_LABELS = {
        "", "Basic", "Advanced", "Extreme", "Mythic", "Elite", "Divine"
    };

    private static final NamedTextColor[] TIER_COLORS = {
        null,
        NamedTextColor.GREEN,
        NamedTextColor.BLUE,
        NamedTextColor.GOLD,
        NamedTextColor.DARK_PURPLE,
        NamedTextColor.YELLOW,
        NamedTextColor.LIGHT_PURPLE
    };

    private final Inventory inventory;
    private final List<MobEntry> mobEntries;

    private SpawnerShopCategoryGUI(int tier, int playerLevel, MobsConfig mobsConfig) {
        this.mobEntries = loadEntries(tier, mobsConfig);

        Component title = Component.text("Spawner Shop")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false);
        this.inventory = Bukkit.createInventory(this, 9, title);

        // Gray glass panes fill all slots first.
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        paneMeta.displayName(Component.empty().decoration(TextDecoration.ITALIC, false));
        pane.setItemMeta(paneMeta);
        for (int i = 0; i < 9; i++) inventory.setItem(i, pane.clone());

        // Get the color for this tier (clamped to valid range).
        int tierIndex = Math.max(1, Math.min(tier, TIER_COLORS.length - 1));
        NamedTextColor tierColor = TIER_COLORS[tierIndex];

        boolean maxLevel = playerLevel >= MobsConfig.MAX_LEVEL;
        for (int i = 0; i < mobEntries.size() && i < 5; i++) {
            MobEntry entry = mobEntries.get(i);
            boolean unlocked = maxLevel || entry.level() <= playerLevel;
            inventory.setItem(i, buildEntry(entry, tierColor, unlocked));
        }
    }

    public List<MobEntry> getMobEntries() { return mobEntries; }

    @Override
    public Inventory getInventory() { return inventory; }

    public static void open(Player player, int tier, int playerLevel, MobsConfig mobsConfig) {
        player.openInventory(
                new SpawnerShopCategoryGUI(tier, playerLevel, mobsConfig).inventory);
    }

    // -------------------------------------------------------------------------
    // Data loading
    // -------------------------------------------------------------------------

    private static List<MobEntry> loadEntries(int tier, MobsConfig mobsConfig) {
        ConfigurationSection mobs = mobsConfig.getMobsSection();
        if (mobs == null) return List.of();
        List<MobEntry> result = new ArrayList<>();
        for (String key : mobs.getKeys(false)) {
            ConfigurationSection sec = mobs.getConfigurationSection(key);
            if (sec == null || sec.getInt("tier", 0) != tier) continue;
            result.add(new MobEntry(
                    key,
                    sec.getInt("level", 0),
                    sec.getLong("cost_to_rankup", 500),
                    sec.getLong("sell_price", 0)));
        }
        result.sort(Comparator.comparingInt(MobEntry::level));
        return Collections.unmodifiableList(result);
    }

    // -------------------------------------------------------------------------
    // Item builder
    // -------------------------------------------------------------------------

    private static ItemStack buildEntry(MobEntry entry, NamedTextColor color, boolean unlocked) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(formatMobName(entry.typeName()))
                .color(color)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();

        lore.add(Component.empty().decoration(TextDecoration.ITALIC, false)
                .append(Component.text("Price: ").color(NamedTextColor.GRAY))
                .append(Component.text("$" + entry.cost()).color(NamedTextColor.YELLOW)));

        lore.add(Component.empty().decoration(TextDecoration.ITALIC, false)
                .append(Component.text("Required Level: ").color(NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(entry.level())).color(NamedTextColor.YELLOW)));

        lore.add(Component.empty().decoration(TextDecoration.ITALIC, false)
                .append(Component.text("Sell Price: ").color(NamedTextColor.GRAY))
                .append(Component.text("$" + entry.sellPrice()).color(NamedTextColor.YELLOW))
                .append(Component.text(" per head").color(NamedTextColor.GRAY)));

        if (unlocked) {
            lore.add(Component.text("✔ Unlocked")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.empty().decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("✖ Requires HeadHunter Level ").color(NamedTextColor.RED))
                    .append(Component.text(String.valueOf(entry.level())).color(NamedTextColor.YELLOW)));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    static String formatMobName(String typeName) {
        String[] words = typeName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty())
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
