package com.lavishmc.headHunter;

import com.lavishmc.headHunter.DropHeads.DropHeads;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles all /hh admin subcommands.
 * Requires the {@code headhunter.admin} permission for every subcommand.
 */
public class HHAdminCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "headhunter.admin";

    private static final List<String> SUBCOMMANDS = List.of(
            "give", "setlevel", "setxp", "rankup", "reset", "info", "reload", "testlevelup", "top"
    );

    private static final List<String> RELOAD_TARGETS = List.of(
            "mobs", "spawners", "messages", "all"
    );

    private static final java.util.Map<String, Material> VANILLA_SKULLS = java.util.Map.of(
            "ZOMBIE",           Material.ZOMBIE_HEAD,
            "SKELETON",         Material.SKELETON_SKULL,
            "WITHER_SKELETON",  Material.WITHER_SKELETON_SKULL,
            "CREEPER",          Material.CREEPER_HEAD,
            "PIGLIN",           Material.PIGLIN_HEAD,
            "ENDER_DRAGON",     Material.DRAGON_HEAD
    );

    private final JavaPlugin plugin;
    private final PlayerDataManager playerData;
    private final Economy economy;
    private final MobsConfig mobsConfig;
    private final SpawnerConfig spawnerConfig;
    private final MessagesConfig messages;
    private final RankUpCommand rankUpCommand;

    public HHAdminCommand(JavaPlugin plugin, PlayerDataManager playerData, Economy economy,
                          MobsConfig mobsConfig, SpawnerConfig spawnerConfig,
                          MessagesConfig messages,
                          RankUpCommand rankUpCommand) {
        this.plugin          = plugin;
        this.playerData      = playerData;
        this.economy         = economy;
        this.mobsConfig      = mobsConfig;
        this.spawnerConfig   = spawnerConfig;
        this.messages        = messages;
        this.rankUpCommand   = rankUpCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(messages.get("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give"     -> cmdGive(sender, args);
            case "setlevel" -> cmdSetLevel(sender, args);
            case "setxp"    -> cmdSetXp(sender, args);
            case "rankup"   -> cmdRankUp(sender, args);
            case "reset"    -> cmdReset(sender, args);
            case "info"     -> cmdInfo(sender, args);
            case "reload"       -> cmdReload(sender, args);
            case "testlevelup"  -> cmdTestLevelUp(sender);
            case "top"          -> cmdTop(sender, args);
            default             -> sendUsage(sender);
        }
        return true;
    }

    // ── Subcommand handlers ───────────────────────────────────────────────────

    private void cmdGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messages.get("usage-hh-give"));
            return;
        }
        Player target = resolvePlayer(sender, args[1]);
        if (target == null) return;

        String mobType = args[2].toUpperCase();
        ConfigurationSection section = mobsConfig.getMobSection(mobType);
        if (section == null) {
            sender.sendMessage(messages.get("invalid-mob", "mob", mobType));
            return;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 1 || amount > 64) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                sender.sendMessage(messages.get("invalid-amount"));
                return;
            }
        }

        ItemStack head = buildHead(mobType, amount);
        target.getInventory().addItem(head);
        sender.sendMessage(messages.get("admin-gave-head",
                "amount", String.valueOf(amount),
                "mob", formatMobName(mobType),
                "player", target.getName()));
    }

    private void cmdSetLevel(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messages.get("usage-hh-setlevel"));
            return;
        }
        Player target = resolvePlayer(sender, args[1]);
        if (target == null) return;

        int level;
        try {
            level = Integer.parseInt(args[2]);
            if (level < 1 || level > playerData.getMaxLevel()) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(messages.get("invalid-level", "max", String.valueOf(playerData.getMaxLevel())));
            return;
        }

        playerData.setLevel(target.getUniqueId(), level);
        plugin.getLogger().info("[HH-Audit] " + sender.getName() + " set " + target.getName() + "'s level to " + level);
        sender.sendMessage(messages.get("admin-set-level",
                "player", target.getName(), "level", String.valueOf(level)));
        target.sendMessage(messages.get("admin-set-level-notify", "level", String.valueOf(level)));
    }

    private void cmdSetXp(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messages.get("usage-hh-setxp"));
            return;
        }
        Player target = resolvePlayer(sender, args[1]);
        if (target == null) return;

        long amount;
        try {
            amount = Long.parseLong(args[2]);
            if (amount < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(messages.get("invalid-xp"));
            return;
        }

        playerData.setXP(target.getUniqueId(), amount);
        plugin.getLogger().info("[HH-Audit] " + sender.getName() + " set " + target.getName() + "'s XP to " + amount);
        sender.sendMessage(messages.get("admin-set-xp",
                "player", target.getName(), "amount", String.valueOf(amount)));
        target.sendMessage(messages.get("admin-set-xp-notify", "amount", String.valueOf(amount)));
    }

    private void cmdRankUp(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messages.get("usage-hh-rankup"));
            return;
        }
        Player target = resolvePlayer(sender, args[1]);
        if (target == null) return;

        UUID uuid = target.getUniqueId();
        int current = playerData.getLevel(uuid);
        if (current >= playerData.getMaxLevel()) {
            sender.sendMessage(messages.get("admin-max-level", "player", target.getName()));
            return;
        }

        int next = current + 1;
        playerData.setLevel(uuid, next);
        plugin.getLogger().info("[HH-Audit] " + sender.getName() + " force-ranked up " + target.getName() + " to level " + next);
        sender.sendMessage(messages.get("admin-forced-rankup",
                "player", target.getName(), "level", String.valueOf(next)));
        target.sendMessage(messages.get("rankup-admin-notify", "level", String.valueOf(next)));
    }

    private void cmdReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messages.get("usage-hh-reset"));
            return;
        }
        Player target = resolvePlayer(sender, args[1]);
        if (target == null) return;

        UUID uuid = target.getUniqueId();
        playerData.setLevel(uuid, 1);
        playerData.setXP(uuid, 0);
        plugin.getLogger().info("[HH-Audit] " + sender.getName() + " reset " + target.getName() + "'s HeadHunter data");
        sender.sendMessage(messages.get("admin-reset", "player", target.getName()));
        target.sendMessage(messages.get("admin-reset-notify"));
    }

    private void cmdInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messages.get("usage-hh-info"));
            return;
        }
        Player target = resolvePlayer(sender, args[1]);
        if (target == null) return;

        UUID uuid  = target.getUniqueId();
        int  level = playerData.getLevel(uuid);
        long xp    = playerData.getXP(uuid);

        long xpRequired = playerData.getXpRequiredForLevel(level);
        String xpNeeded = level >= playerData.getMaxLevel()
                ? "MAX LEVEL"
                : xp + " / " + xpRequired + " XP (" + Math.max(0, xpRequired - xp) + " remaining)";

        String balance = economy != null
                ? "$" + String.format("%.2f", economy.getBalance(target))
                : "N/A (Vault unavailable)";

        String costStr = level >= playerData.getMaxLevel()
                ? "MAX LEVEL"
                : "$" + playerData.getRankupCost(level);

        sender.sendMessage(msg("&e--- " + target.getName() + " ---"));
        sender.sendMessage(msg("&7Level: &f" + level));
        sender.sendMessage(msg("&7XP: &f" + xp));
        sender.sendMessage(msg("&7Progress: &f" + xpNeeded));
        sender.sendMessage(msg("&7Rankup Cost: &f" + costStr));
        sender.sendMessage(msg("&7Balance: &f" + balance));
    }

    private void cmdTestLevelUp(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.getRaw("console-only"));
            return;
        }
        rankUpCommand.runTestEffects(player);
    }

    private void cmdReload(CommandSender sender, String[] args) {
        String target = args.length >= 2 ? args[1].toLowerCase() : "all";

        boolean reloadedMobs     = false;
        boolean reloadedSpawners = false;
        boolean reloadedMessages = false;

        switch (target) {
            case "mobs"     -> { mobsConfig.reload();     reloadedMobs     = true; }
            case "spawners" -> { spawnerConfig.reload();  reloadedSpawners = true; }
            case "messages" -> { messages.reload();       reloadedMessages = true; }
            default         -> {
                plugin.reloadConfig();
                mobsConfig.reload();
                spawnerConfig.reload();
                messages.reload();
                reloadedMobs = reloadedSpawners = reloadedMessages = true;
            }
        }

        String reloaded = buildReloadedStr(reloadedMobs, reloadedSpawners, reloadedMessages);
        sender.sendMessage(messages.get("admin-reloaded", "target", reloaded));
    }

    private static String buildReloadedStr(boolean mobs, boolean spawners, boolean msgs) {
        List<String> parts = new ArrayList<>();
        if (mobs)     parts.add("mobs");
        if (spawners) parts.add("spawners");
        if (msgs)     parts.add("messages");
        return String.join(", ", parts);
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String label, String[] args) {
        if (!sender.hasPermission(PERM)) return List.of();

        if (args.length == 1) return filter(SUBCOMMANDS, args[0]);

        String sub = args[0].toLowerCase();

        if (args.length == 2) {
            if (sub.equals("reload")) return filter(RELOAD_TARGETS, args[1]);
            return filter(onlinePlayerNames(), args[1]);
        }

        if (args.length == 3) {
            return switch (sub) {
                case "give"     -> filter(mobKeys(), args[2]);
                case "setlevel" -> filter(levelSuggestions(), args[2]);
                case "setxp"    -> List.of("0", "100", "500", "1000");
                default         -> List.of();
            };
        }

        if (args.length == 4 && sub.equals("give")) {
            return filter(List.of("1", "16", "32", "64"), args[3]);
        }

        return List.of();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ItemStack buildHead(String mobType, int amount) {
        Material mat = VANILLA_SKULLS.getOrDefault(mobType, Material.PLAYER_HEAD);

        if (mat != Material.PLAYER_HEAD) {
            return new ItemStack(mat, amount);
        }

        try {
            EntityType entityType = EntityType.valueOf(mobType);
            ItemStack item = DropHeads.getPlugin().getAPI().getHead(entityType, mobType);
            if (item != null) {
                item.setAmount(amount);
                return item;
            }
        } catch (Exception ignored) {}

        ItemStack item = new ItemStack(Material.PLAYER_HEAD, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection()
                    .deserialize("§f" + formatMobName(mobType) + " Head"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private Player resolvePlayer(CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) sender.sendMessage(messages.get("player-not-found", "player", name));
        return target;
    }

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    private List<String> mobKeys() {
        ConfigurationSection section = mobsConfig.getMobsSection();
        if (section == null) return List.of();
        return new ArrayList<>(section.getKeys(false));
    }

    private List<String> levelSuggestions() {
        List<String> levels = new ArrayList<>();
        for (int i = 1; i <= playerData.getMaxLevel(); i++) levels.add(String.valueOf(i));
        return levels;
    }

    private static List<String> filter(List<String> options, String partial) {
        String lower = partial.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
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

    private static Component msg(String legacy) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy);
    }

    private void cmdTop(CommandSender sender, String[] args) {
        // Parse page number
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid page number.");
                return;
            }
        }

        // Gather all players who have ever joined (exist in playerdata.json)
        java.util.Set<UUID> allPlayers = playerData.getAllPlayerUUIDs();

        // Build sorted list: all players sorted by heads sold descending
        List<Map.Entry<UUID, Long>> sorted = allPlayers.stream()
                .map(uuid -> Map.entry(uuid, playerData.getTotalHeadsSold(uuid)))
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toList());

        // Pagination
        int entriesPerPage = messages.getTopEntriesPerPage();
        int totalPages = sorted.isEmpty() ? 1 : (int) Math.ceil((double) sorted.size() / entriesPerPage);

        if (page < 1 || page > totalPages) {
            sender.sendMessage(messages.getTopInvalidPage("page", String.valueOf(page)));
            return;
        }

        int startIndex = (page - 1) * entriesPerPage;
        int endIndex = Math.min(startIndex + entriesPerPage, sorted.size());

        // Display header
        sender.sendMessage(messages.getTopHeader(
                "page", String.valueOf(page),
                "total-pages", String.valueOf(totalPages)));

        // Display entries (if any)
        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<UUID, Long> entry = sorted.get(i);
            int rank = i + 1;
            String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (playerName == null) playerName = "Unknown";
            long count = entry.getValue();
            String formatted = String.format("%,d", count);

            // Color rank based on position
            String coloredRank = switch (rank) {
                case 1 -> "§e#" + rank;
                case 2 -> "§6#" + rank;
                case 3 -> "§8#" + rank;
                default -> "§c#" + rank;
            };

            sender.sendMessage(messages.getTopEntry(
                    "rank", coloredRank,
                    "player", playerName,
                    "heads", formatted));
        }

        // Display footer
        sender.sendMessage(messages.getTopFooter());
    }

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage(msg("&b/hh give <player> <mob> [amount]"));
        sender.sendMessage(msg("&b/hh setlevel <player> <level>"));
        sender.sendMessage(msg("&b/hh setxp <player> <amount>"));
        sender.sendMessage(msg("&b/hh rankup <player>"));
        sender.sendMessage(msg("&b/hh reset <player>"));
        sender.sendMessage(msg("&b/hh info <player>"));
        sender.sendMessage(msg("&b/hh reload [mobs|spawners|messages|all]"));
        sender.sendMessage(msg("&b/hh testlevelup"));
        sender.sendMessage(msg("&b/hh top [page]"));
    }
}
