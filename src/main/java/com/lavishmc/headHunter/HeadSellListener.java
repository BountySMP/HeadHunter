package com.lavishmc.headHunter;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/**
 * Handles right-click head selling.
 *
 * <p>Right-click with a configured mob head → sell that stack.<br>
 * Shift + right-click with any configured mob head → sell all matching heads in inventory.</p>
 */
public class HeadSellListener implements Listener {

    private static final Set<Material> HEAD_MATERIALS = Set.of(
            Material.PLAYER_HEAD,
            Material.ZOMBIE_HEAD,
            Material.SKELETON_SKULL,
            Material.WITHER_SKELETON_SKULL,
            Material.CREEPER_HEAD,
            Material.PIGLIN_HEAD,
            Material.DRAGON_HEAD
    );

    private final Economy economy;
    private final PlayerDataManager playerData;
    private final MobsConfig mobsConfig;
    private final MessagesConfig messages;

    public HeadSellListener(JavaPlugin plugin, Economy economy, PlayerDataManager playerData,
                            MobsConfig mobsConfig, MessagesConfig messages) {
        this.economy       = economy;
        this.playerData    = playerData;
        this.mobsConfig    = mobsConfig;
        this.messages      = messages;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();

        if (!player.hasPermission("headhunter.basic")) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) return;

        if (!isMobHead(item)) return;

        String mobType = getMobType(item);
        if (mobType == null) return;
        if (mobsConfig.getMobSection(mobType) == null) return;

        event.setCancelled(true);

        ConfigurationSection section = mobsConfig.getMobSection(mobType);
        if (player.isSneaking()) {
            sellAllHeads(player, item, mobType, section);
        } else {
            sellHeadStack(player, item, mobType, section);
        }
    }

    private void sellHeadStack(Player player, ItemStack item,
                               String mobType, ConfigurationSection section) {
        int mobLevel    = section.getInt("level", 0);
        int playerLevel = playerData.getLevel(player.getUniqueId());

        if (mobLevel == 0) {
            player.sendMessage(messages.get("sell-no-level", "mob", formatMobName(mobType)));
            return;
        }

        if (mobLevel > playerLevel) {
            player.sendMessage(messages.get("sell-level-too-low", "level", String.valueOf(mobLevel)));
            return;
        }

        int count      = item.getAmount();
        long sellPrice = section.getLong("sell_price", 0);
        long xpPerHead = mobsConfig.getXpPerHead();
        long totalMoney = sellPrice * count;
        long totalXP = (mobLevel == playerLevel) ? xpPerHead * count : 0;

        // Remove item before depositing so a crash/disconnect mid-transaction
        // cannot result in money being paid without consuming the head.
        player.getInventory().setItemInMainHand(null);

        if (economy != null) economy.depositPlayer(player, (double) totalMoney);
        playerData.addHeadsSold(player.getUniqueId(), count);

        if (totalXP > 0) playerData.addXP(player.getUniqueId(), totalXP);

        player.sendMessage(messages.get("sell-success",
                "count", String.valueOf(count),
                "mob", formatMobName(mobType),
                "money", String.valueOf(totalMoney),
                "xp", String.valueOf(totalXP)));
    }

    private void sellAllHeads(Player player, ItemStack item,
                               String mobType, ConfigurationSection section) {
        int mobLevel    = section.getInt("level", 0);
        int playerLevel = playerData.getLevel(player.getUniqueId());

        if (mobLevel == 0) {
            player.sendMessage(messages.get("sell-no-level", "mob", formatMobName(mobType)));
            return;
        }

        if (mobLevel > playerLevel) {
            player.sendMessage(messages.get("sell-level-too-low", "level", String.valueOf(mobLevel)));
            return;
        }

        long sellPrice = section.getLong("sell_price", 0);
        long xpPerHead = mobsConfig.getXpPerHead();
        boolean awardsXP = mobLevel > 0 && mobLevel == playerLevel;

        int totalCount = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack slot = contents[i];
            if (slot == null) continue;
            if (!mobType.equals(getMobType(slot))) continue;
            totalCount += slot.getAmount();
            player.getInventory().setItem(i, null);
        }

        if (totalCount == 0) return;

        long totalMoney = sellPrice * totalCount;
        long totalXP    = awardsXP ? xpPerHead * totalCount : 0;

        if (economy != null) economy.depositPlayer(player, (double) totalMoney);
        playerData.addHeadsSold(player.getUniqueId(), totalCount);

        if (totalXP > 0) playerData.addXP(player.getUniqueId(), totalXP);

        player.sendMessage(messages.get("sell-success",
                "count", String.valueOf(totalCount),
                "mob", formatMobName(mobType),
                "money", String.valueOf(totalMoney),
                "xp", String.valueOf(totalXP)));
    }

    private static boolean isMobHead(ItemStack item) {
        return item != null && HEAD_MATERIALS.contains(item.getType());
    }

    private String getMobType(ItemStack item) {
        if (!isMobHead(item)) return null;

        if (item.getType() == Material.ZOMBIE_HEAD)           return "ZOMBIE";
        if (item.getType() == Material.SKELETON_SKULL)        return "SKELETON";
        if (item.getType() == Material.WITHER_SKELETON_SKULL) return "WITHER_SKELETON";
        if (item.getType() == Material.CREEPER_HEAD)          return "CREEPER";
        if (item.getType() == Material.PIGLIN_HEAD)           return "PIGLIN";
        if (item.getType() == Material.DRAGON_HEAD)           return "ENDER_DRAGON";

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String displayName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            for (String mobKey : mobsConfig.getSortedMobKeys()) {
                if (displayName.contains(mobsConfig.getFormattedMobName(mobKey))) return mobKey;
            }
        }

        return null;
    }

    private static String formatMobName(String typeName) {
        String[] words = typeName.toLowerCase().split("_");
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
