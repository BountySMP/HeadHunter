package com.lavishmc.headHunter;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class SidebarManager implements Listener {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd yyyy h:mm a", Locale.US);

    private final JavaPlugin plugin;
    private final PlayerDataManager playerData;
    private final Economy economy;
    private final SidebarConfig sidebarConfig;

    private final Map<UUID, Map<Integer, String>> prevLines = new HashMap<>();
    private Scoreboard mainScoreboard;
    private Objective sidebarObjective;

    public SidebarManager(JavaPlugin plugin, PlayerDataManager playerData,
                          Economy economy, SidebarConfig sidebarConfig) {
        this.plugin        = plugin;
        this.playerData    = playerData;
        this.economy       = economy;
        this.sidebarConfig = sidebarConfig;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Get the main scoreboard shared by all players
        mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        // Register the sidebar objective on the main scoreboard
        sidebarObjective = mainScoreboard.getObjective("hh_sidebar");
        if (sidebarObjective == null) {
            String serverName = sidebarConfig.getServerName();
            sidebarObjective = mainScoreboard.registerNewObjective(
                "hh_sidebar", "dummy", "§a§l" + serverName, RenderType.INTEGER);
            sidebarObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        prevLines.remove(uuid);
    }

    private void updateAll() {
        String dateTime = DATE_FORMAT.format(LocalDateTime.now());
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player, dateTime);
        }
        prevLines.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    private void updatePlayer(Player player, String dateTime) {
        UUID uuid = player.getUniqueId();
        Map<Integer, String> lines = prevLines.computeIfAbsent(uuid, k -> new HashMap<>());

        // Update the objective display name
        String serverName = sidebarConfig.getServerName();
        sidebarObjective.displayName(net.kyori.adventure.text.Component.text(serverName)
                .color(net.kyori.adventure.text.format.NamedTextColor.GREEN)
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

        int level = playerData.getLevel(uuid);
        boolean maxed = level >= playerData.getMaxLevel();

        String xpLine;
        if (maxed) {
            xpLine = sidebarConfig.getLine("xp-maxed");
        } else {
            long totalXP    = playerData.getXP(uuid);
            long xpRequired = playerData.getXpRequiredForLevel(level);
            int percentage = xpRequired > 0 ? (int) ((totalXP * 100L) / xpRequired) : 0;
            xpLine = sidebarConfig.getLine("xp",
                    "xp-current",  String.valueOf(percentage) + "%",
                    "xp-required", "100%");
        }

        String balanceLine;
        if (economy != null) {
            long bal = (long) economy.getBalance(player);
            balanceLine = sidebarConfig.getLine("balance", "balance", fmt(bal));
        } else {
            balanceLine = sidebarConfig.getLine("balance", "balance", "N/A");
        }

        setLine(sidebarObjective, mainScoreboard, lines, sidebarConfig.getLine("datetime", "datetime", dateTime), 6);
        setLine(sidebarObjective, mainScoreboard, lines, sidebarConfig.getLine("separator"),                       5);
        setLine(sidebarObjective, mainScoreboard, lines, sidebarConfig.getLine("player", "player", player.getName()), 4);
        setLine(sidebarObjective, mainScoreboard, lines, balanceLine,                                              3);
        setLine(sidebarObjective, mainScoreboard, lines, sidebarConfig.getLine("level", "level", String.valueOf(level)), 2);
        setLine(sidebarObjective, mainScoreboard, lines, xpLine,                                                   1);
    }

    private static void setLine(Objective obj, Scoreboard board,
                                Map<Integer, String> lines, String text, int score) {
        String prev = lines.get(score);
        if (text.equals(prev)) return;
        if (prev != null) board.resetScores(prev);
        obj.getScore(text).setScore(score);
        lines.put(score, text);
    }

    private static String fmt(long n) {
        if (n >= 1_000_000_000L) return trim(n / 1_000_000_000.0) + "b";
        if (n >= 1_000_000L)     return trim(n / 1_000_000.0)     + "m";
        if (n >= 1_000L)         return trim(n / 1_000.0)         + "k";
        return Long.toString(n);
    }

    private static String trim(double value) {
        long whole   = (long) value;
        long decimal = Math.round((value - whole) * 10);
        return decimal == 0 ? Long.toString(whole) : whole + "." + decimal;
    }
}
