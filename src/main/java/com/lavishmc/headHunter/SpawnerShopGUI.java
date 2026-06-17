package com.lavishmc.headHunter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Main 9-slot Spawner Shop GUI.
 * Tiers 1-6 occupy slots 1-6; gray glass panes fill slots 0 and 8.
 */
public class SpawnerShopGUI implements InventoryHolder {

    private record TierDef(int slot, String label, NamedTextColor color, String loreMid) {}

    private static final TierDef[] TIERS = {
        new TierDef(1, "Basic",    NamedTextColor.GREEN,        "Tier 1"),
        new TierDef(2, "Advanced", NamedTextColor.BLUE,         "Tier 2"),
        new TierDef(3, "Extreme",  NamedTextColor.GOLD,         "Tier 3"),
        new TierDef(4, "Mythic",   NamedTextColor.DARK_PURPLE,  "Tier 4"),
        new TierDef(5, "Elite",    NamedTextColor.YELLOW,       "Tier 5"),
        new TierDef(6, "Divine",   NamedTextColor.LIGHT_PURPLE, "Divine"),
    };

    private final Inventory inventory;

    private SpawnerShopGUI() {
        Component title = Component.text("Spawner Shop")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false);
        this.inventory = Bukkit.createInventory(this, 9, title);

        // Gray glass panes at the outer edges.
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        paneMeta.displayName(Component.empty().decoration(TextDecoration.ITALIC, false));
        pane.setItemMeta(paneMeta);
        inventory.setItem(0, pane.clone());
        inventory.setItem(7, pane.clone());
        inventory.setItem(8, pane.clone());

        // One SPAWNER item per tier.
        for (TierDef t : TIERS) {
            ItemStack item = new ItemStack(Material.SPAWNER);
            ItemMeta meta = item.getItemMeta();

            meta.displayName(Component.text(t.label())
                    .color(t.color())
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(
                    Component.empty()
                            .decoration(TextDecoration.ITALIC, false)
                            .append(Component.text("Contains ").color(NamedTextColor.GRAY))
                            .append(Component.text(t.loreMid()).color(t.color()))
                            .append(Component.text(" spawners").color(NamedTextColor.GRAY))));

            item.setItemMeta(meta);
            inventory.setItem(t.slot(), item);
        }
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public static void open(Player player) {
        player.openInventory(new SpawnerShopGUI().inventory);
    }
}
