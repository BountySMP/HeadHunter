package com.lavishmc.headHunter;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class HHTopGUI {
    private final PlayerDataManager playerData;
    private final Player viewer;
    private final int page;

    private static final int SLOTS_PER_PAGE = 45;

    public HHTopGUI(PlayerDataManager playerData, Player viewer, int page) {
        this.playerData = playerData;
        this.viewer = viewer;
        this.page = page;
    }

    public void open() {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.GOLD + "Top Players");

        // Gather all players and their stats
        Set<UUID> allPlayers = playerData.getAllPlayerUUIDs();
        List<PlayerEntry> playerList = new ArrayList<>();

        for (UUID uuid : allPlayers) {
            int level = playerData.getLevel(uuid);
            long xp = playerData.getXP(uuid);
            long headsSold = playerData.getTotalHeadsSold(uuid);
            playerList.add(new PlayerEntry(uuid, level, xp, headsSold));
        }

        // Sort by level DESC, then XP DESC
        playerList.sort((a, b) -> {
            int levelCompare = Integer.compare(b.level, a.level);
            if (levelCompare != 0) return levelCompare;
            return Long.compare(b.xp, a.xp);
        });

        int startIndex = page * SLOTS_PER_PAGE;
        int endIndex = Math.min(startIndex + SLOTS_PER_PAGE, playerList.size());

        // Fill top 5 rows (slots 0-44)
        for (int i = startIndex; i < endIndex; i++) {
            int slot = i - startIndex;
            if (slot >= SLOTS_PER_PAGE) break;

            PlayerEntry entry = playerList.get(i);
            int rank = i + 1;
            inv.setItem(slot, createPlayerHead(entry, rank));
        }

        // Bottom row - navigation buttons
        // Previous page button at slot 48
        inv.setItem(48, createPreviousPage());

        // Page indicator at slot 49
        int totalPages = playerList.isEmpty() ? 1 : (playerList.size() - 1) / SLOTS_PER_PAGE + 1;
        inv.setItem(49, createPageIndicator(totalPages));

        // Next page button at slot 50
        inv.setItem(50, createNextPage());

        viewer.openInventory(inv);
    }

    private ItemStack createPlayerHead(PlayerEntry entry, int rank) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        OfflinePlayer target = Bukkit.getOfflinePlayer(entry.uuid);
        meta.setOwningPlayer(target);

        String playerName = target.getName();
        if (playerName == null) playerName = "Unknown";
        meta.setDisplayName(ChatColor.YELLOW + playerName);

        List<String> lore = new ArrayList<>();

        // Color rank based on position
        String coloredRank = switch (rank) {
            case 1 -> ChatColor.GOLD + "#" + rank;
            case 2 -> ChatColor.GRAY + "#" + rank;
            case 3 -> ChatColor.DARK_GRAY + "#" + rank;
            default -> ChatColor.RED + "#" + rank;
        };

        lore.add(ChatColor.GRAY + "Rank: " + coloredRank);
        lore.add(ChatColor.GRAY + "Level: " + ChatColor.AQUA + entry.level);
        lore.add(ChatColor.GRAY + "XP: " + ChatColor.YELLOW + String.format("%,d", entry.xp));
        meta.setLore(lore);

        head.setItemMeta(meta);
        return head;
    }

    private ItemStack createPreviousPage() {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "Previous Page");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNextPage() {
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Next Page");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPageIndicator(int totalPages) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "Page " + ChatColor.YELLOW + (page + 1) + ChatColor.GRAY + "/" + ChatColor.YELLOW + totalPages);
        item.setItemMeta(meta);
        return item;
    }

    public void handleClick(int slot) {
        if (slot >= 45) {
            // Previous page button at slot 48
            if (slot == 48) {
                if (page > 0) {
                    new HHTopGUI(playerData, viewer, page - 1).open();
                }
                return;
            }

            // Page indicator at slot 49 - do nothing
            if (slot == 49) {
                return;
            }

            // Next page button at slot 50
            if (slot == 50) {
                Set<UUID> allPlayers = playerData.getAllPlayerUUIDs();
                int maxPage = allPlayers.isEmpty() ? 0 : (allPlayers.size() - 1) / SLOTS_PER_PAGE;
                if (page < maxPage) {
                    new HHTopGUI(playerData, viewer, page + 1).open();
                }
                return;
            }
        }
    }

    private record PlayerEntry(UUID uuid, int level, long xp, long headsSold) {}
}
