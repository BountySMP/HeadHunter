package com.lavishmc.headHunter;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles /givespawner <player> <mobtype> [amount].
 * Requires the {@code headhunter.admin} permission.
 */
public class GiveSpawnerCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "headhunter.admin";

    /** Sorted list of all spawnable EntityType names, built once at class load. */
    private static final List<String> ENTITY_NAMES = Arrays.stream(EntityType.values())
            .filter(e -> e.isSpawnable() && e.isAlive())
            .map(Enum::name)
            .sorted()
            .collect(Collectors.toUnmodifiableList());

    private final MobsConfig mobsConfig;
    private final MessagesConfig messages;

    public GiveSpawnerCommand(MobsConfig mobsConfig, MessagesConfig messages) {
        this.mobsConfig = mobsConfig;
        this.messages   = messages;
    }

    // ── Command ───────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(messages.get("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.get("usage-givespawner"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messages.get("player-not-found", "player", args[0]));
            return true;
        }

        EntityType entityType;
        try {
            entityType = EntityType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(messages.get("invalid-entity", "type", args[1]));
            return true;
        }
        if (!entityType.isSpawnable() || !entityType.isAlive()) {
            sender.sendMessage(messages.get("entity-not-spawnable", "type", entityType.name()));
            return true;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 1 || amount > 64) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                sender.sendMessage(messages.get("invalid-amount"));
                return true;
            }
        }

        ItemStack spawner = SpawnerStackManager.buildSpawnerItem(entityType, 1, mobsConfig);
        spawner.setAmount(amount);

        var leftover = target.getInventory().addItem(spawner);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(stack ->
                    target.getWorld().dropItemNaturally(target.getLocation(), stack));
        }

        target.sendMessage(messages.get("spawner-received",
                "amount", String.valueOf(amount),
                "type", formatName(entityType)));
        return true;
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String label, String[] args) {
        if (!sender.hasPermission(PERM)) return List.of();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String partial = args[1].toUpperCase();
            return ENTITY_NAMES.stream()
                    .filter(n -> n.startsWith(partial))
                    .collect(Collectors.toList());
        }

        if (args.length == 3) {
            return List.of("1", "4", "16", "32", "64");
        }

        return List.of();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Converts WITHER_SKELETON → "Wither Skeleton". */
    private static String formatName(EntityType type) {
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

}
