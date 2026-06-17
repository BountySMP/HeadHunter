package com.lavishmc.headHunter;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnerShopCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by a player.");
            return true;
        }

        if (!player.hasPermission("headhunter.basic")) {
            player.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        SpawnerShopGUI.open(player);
        return true;
    }
}
