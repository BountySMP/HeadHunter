package com.lavishmc.headHunter;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class HHTopListener implements Listener {

    private final PlayerDataManager playerData;

    public HHTopListener(PlayerDataManager playerData) {
        this.playerData = playerData;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (title.equals(ChatColor.GOLD + "Top Players")) {
            event.setCancelled(true);

            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }

            int slot = event.getSlot();

            // Determine page from current inventory state
            // Since we don't track sessions, we recreate the GUI logic inline
            // Navigation clicks will call handleClick which opens a new GUI
            if (slot == 48 || slot == 50) {
                // For navigation, we need to determine current page
                // This is a limitation - ideally we'd track sessions
                // For now, just close and reopen at page 0
                // A better approach would be to track GUI sessions like BountyCore does

                // Simple navigation: extract page from the paper item
                org.bukkit.inventory.ItemStack pageItem = event.getInventory().getItem(49);
                if (pageItem != null && pageItem.hasItemMeta()) {
                    String displayName = pageItem.getItemMeta().getDisplayName();
                    // Parse "Page X/Y" to get current page
                    int currentPage = parseCurrentPage(displayName);

                    if (slot == 48 && currentPage > 0) {
                        new HHTopGUI(playerData, player, currentPage - 1).open();
                    } else if (slot == 50) {
                        new HHTopGUI(playerData, player, currentPage + 1).open();
                    }
                }
            }
        }
    }

    private int parseCurrentPage(String displayName) {
        try {
            // Extract number from "§7Page §eX§7/§eY"
            String stripped = ChatColor.stripColor(displayName);
            if (stripped.startsWith("Page ")) {
                String pageNum = stripped.substring(5).split("/")[0].trim();
                return Integer.parseInt(pageNum) - 1; // Convert to 0-indexed
            }
        } catch (Exception e) {
            // If parsing fails, default to page 0
        }
        return 0;
    }
}
