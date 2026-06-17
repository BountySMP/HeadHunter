package com.lavishmc.headHunter;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HHTopCommand implements CommandExecutor {

    private final PlayerDataManager playerData;

    public HHTopCommand(PlayerDataManager playerData) {
        this.playerData = playerData;
    }

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

        // Parse page number (0-indexed internally)
        int page = 0;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]) - 1; // Convert to 0-indexed
                if (page < 0) page = 0;
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid page number.");
                return true;
            }
        }

        new HHTopGUI(playerData, player, page).open();
        return true;
    }
}
