package com.lavishmc.headHunter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Third-layer quantity-selection GUI (27-slot, 3 rows).
 *
 * <pre>
 * Row 1 (0-8):   All gray glass panes
 * Row 2 (9-17):  [--] Spawner [--] 1x 16x 32x 64x [--] Purchase
 *                  9    10    11   12  13  14  15  16    17
 * Row 3 (18-26): All gray glass panes
 * </pre>
 *
 * Slot 17 updates in-place as quantity changes — no GUI reopen needed.
 */
public class SpawnerShopQuantityGUI implements InventoryHolder {

    private final SpawnerShopCategoryGUI.MobEntry entry;
    private int quantity = 1;
    private final Inventory inventory;
    private long lastClickTime = 0;
    private static final long CLICK_COOLDOWN_MS = 200;

    private SpawnerShopQuantityGUI(SpawnerShopCategoryGUI.MobEntry entry, MobsConfig mobsConfig) {
        this.entry = entry;

        Component title = Component.text("Spawner Shop")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false);
        this.inventory = Bukkit.createInventory(this, 27, title);

        // Fill all slots with gray glass panes first.
        ItemStack grayPane = buildGrayPane();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, grayPane.clone());
        }

        // Slot 10 — spawner preview (uses the existing buildSpawnerItem method).
        EntityType type = null;
        try { type = EntityType.valueOf(entry.typeName()); } catch (IllegalArgumentException ignored) {}
        if (type != null) {
            inventory.setItem(10, SpawnerStackManager.buildSpawnerItem(type, 1, mobsConfig));
        } else {
            inventory.setItem(10, namedPane(Material.SPAWNER,
                    Component.text(entry.typeName())
                            .color(NamedTextColor.RED)
                            .decoration(TextDecoration.BOLD, true)
                            .decoration(TextDecoration.ITALIC, false)));
        }

        // Slots 12-15 — quantity buttons (blue glass panes).
        inventory.setItem(12, quantityButton(1));
        inventory.setItem(13, quantityButton(16));
        inventory.setItem(14, quantityButton(32));
        inventory.setItem(15, quantityButton(64));

        // Slot 17 — live purchase button with lore.
        inventory.setItem(17, buildPurchaseSlot());
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public SpawnerShopCategoryGUI.MobEntry getEntry() { return entry; }

    public int getQuantity() { return quantity; }

    /** Adds {@code amount} to current quantity (cap 64) and refreshes slot 17. */
    public void addQuantity(int amount) {
        long now = System.currentTimeMillis();
        if (now - lastClickTime < CLICK_COOLDOWN_MS) return;
        lastClickTime = now;

        quantity = Math.min(64, quantity + amount);
        inventory.setItem(17, buildPurchaseSlot());
    }

    /** Checks if enough time has passed since last click for purchase action. */
    public boolean canPurchase() {
        long now = System.currentTimeMillis();
        if (now - lastClickTime < CLICK_COOLDOWN_MS) return false;
        lastClickTime = now;
        return true;
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public static void open(Player player, SpawnerShopCategoryGUI.MobEntry entry,
                            MobsConfig mobsConfig) {
        player.openInventory(new SpawnerShopQuantityGUI(entry, mobsConfig).inventory);
    }

    // -------------------------------------------------------------------------
    // Item builders
    // -------------------------------------------------------------------------

    private static ItemStack buildGrayPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty().decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack quantityButton(int amount) {
        ItemStack item = namedPane(Material.BLUE_STAINED_GLASS_PANE,
                Component.text(amount + "x")
                        .color(NamedTextColor.BLUE)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false));
        item.setAmount(amount);
        return item;
    }

    private ItemStack buildPurchaseSlot() {
        long total = entry.cost() * quantity;

        Component name = Component.empty()
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("Purchase ").color(NamedTextColor.GREEN))
                .append(Component.text(quantity + "x").color(NamedTextColor.YELLOW));

        List<Component> lore = List.of(
                Component.empty()
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("Total: ").color(NamedTextColor.GRAY))
                        .append(Component.text("$" + total).color(NamedTextColor.YELLOW))
        );

        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        item.setAmount(quantity);
        return item;
    }

    private static ItemStack namedPane(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
