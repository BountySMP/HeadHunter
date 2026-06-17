package com.lavishmc.headHunter;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class SpawnerShopListener implements Listener {

    private final MobsConfig mobsConfig;
    private final PlayerDataManager playerData;
    private final Economy economy;

    public SpawnerShopListener(MobsConfig mobsConfig, PlayerDataManager playerData, Economy economy) {
        this.mobsConfig = mobsConfig;
        this.playerData = playerData;
        this.economy    = economy;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof SpawnerShopGUI)
                && !(holder instanceof SpawnerShopCategoryGUI)
                && !(holder instanceof SpawnerShopQuantityGUI)) return;

        event.setCancelled(true);

        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getInventory().getSize()) return;

        if (holder instanceof SpawnerShopGUI) {
            handleMainShop(player, raw);
        } else if (holder instanceof SpawnerShopCategoryGUI categoryGUI) {
            handleCategory(player, raw, categoryGUI);
        } else if (holder instanceof SpawnerShopQuantityGUI quantityGUI) {
            handleQuantity(player, raw, quantityGUI);
        }
    }

    // -------------------------------------------------------------------------
    // Layer 1 — main shop
    // -------------------------------------------------------------------------

    private void handleMainShop(Player player, int slot) {
        // Tier buttons are at slots 1-6 (tier = slot value).
        if (slot < 1 || slot > 6) return;
        int tier = slot;
        int playerLevel = playerData.getLevel(player.getUniqueId());
        SpawnerShopCategoryGUI.open(player, tier, playerLevel, mobsConfig);
    }

    // -------------------------------------------------------------------------
    // Layer 2 — tier category
    // -------------------------------------------------------------------------

    private void handleCategory(Player player, int slot,
                                SpawnerShopCategoryGUI categoryGUI) {
        List<SpawnerShopCategoryGUI.MobEntry> entries = categoryGUI.getMobEntries();
        if (slot >= entries.size()) return; // glass pane — ignore silently

        SpawnerShopCategoryGUI.MobEntry entry = entries.get(slot);
        int playerLevel = playerData.getLevel(player.getUniqueId());

        // Locked — cancel silently, no message.
        if (entry.level() > playerLevel && playerLevel < MobsConfig.MAX_LEVEL) return;

        SpawnerShopQuantityGUI.open(player, entry, mobsConfig);
    }

    // -------------------------------------------------------------------------
    // Layer 3 — quantity selection
    // -------------------------------------------------------------------------

    private void handleQuantity(Player player, int slot,
                                SpawnerShopQuantityGUI quantityGUI) {
        switch (slot) {
            case 12 -> quantityGUI.addQuantity(1);
            case 13 -> quantityGUI.addQuantity(16);
            case 14 -> quantityGUI.addQuantity(32);
            case 15 -> quantityGUI.addQuantity(64);
            case 17 -> {
                if (quantityGUI.canPurchase()
                        && doPurchase(player, quantityGUI.getEntry(), quantityGUI.getQuantity())) {
                    player.closeInventory();
                }
            }
            // All other slots (spawner preview, gray panes) — ignore
        }
    }

    // -------------------------------------------------------------------------
    // Purchase helper
    // -------------------------------------------------------------------------

    /**
     * Validates balance, withdraws money, and gives spawner items.
     * Returns true on success, false if the purchase could not proceed.
     */
    private boolean doPurchase(Player player, SpawnerShopCategoryGUI.MobEntry entry, int quantity) {
        if (economy == null) {
            player.sendMessage("§cThe economy system is unavailable.");
            return false;
        }

        long totalCost = entry.cost() * quantity;
        if (economy.getBalance(player) < totalCost) {
            long shortfall = totalCost - (long) economy.getBalance(player);
            player.sendMessage("§cYou need §e$" + shortfall
                    + " §cmore to purchase §e" + quantity + "x§c spawner(s).");
            return false;
        }

        EntityType type;
        try {
            type = EntityType.valueOf(entry.typeName());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cInvalid spawner type — please contact an admin.");
            return false;
        }

        economy.withdrawPlayer(player, (double) totalCost);

        // Give spawners in Minecraft stack sizes of up to 64.
        ItemStack template = SpawnerStackManager.buildSpawnerItem(type, 1, mobsConfig);
        int remaining = quantity;
        while (remaining > 0) {
            int batch = Math.min(remaining, 64);
            ItemStack stack = template.clone();
            stack.setAmount(batch);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            leftover.values().forEach(
                    item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            remaining -= batch;
        }

        String mobName = SpawnerShopCategoryGUI.formatMobName(entry.typeName());
        player.sendMessage("§aPurchased §b" + quantity + "x " + mobName
                + " §aSpawner(s) for §e$" + totalCost + "§a!");
        return true;
    }
}
