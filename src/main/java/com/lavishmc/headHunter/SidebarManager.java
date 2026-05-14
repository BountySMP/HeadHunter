package com.lavishmc.headHunter;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SidebarManager {

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("MMM dd yyyy h:mm a");

    private final JavaPlugin plugin;
    private final PlayerDataManager playerData;
    private final Economy economy;
    private final SidebarConfig sidebarConfig;
    private final String serverName;

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Map<Integer, String>> prevLines = new HashMap<>();

    public SidebarManager(JavaPlugin plugin, PlayerDataManager playerData,
                          Economy economy, SidebarConfig sidebarConfig) {
        this.plugin        = plugin;
        this.playerData    = playerData;
        this.economy       = economy;
        this.sidebarConfig = sidebarConfig;
        this.serverName    = plugin.getConfig().getString("server-name", "HeadHunter");
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
    }

    private void updateAll() {
        String dateTime = DATE_FORMAT.format(new Date());
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player, dateTime);
        }
        boards.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        prevLines.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    private void updatePlayer(Player player, String dateTime) {
        UUID uuid = player.getUniqueId();

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = boards.computeIfAbsent(uuid, k -> manager.getNewScoreboard());
        Map<Integer, String> lines = prevLines.computeIfAbsent(uuid, k -> new HashMap<>());

        Objective obj = board.getObjective("hh_sidebar");
        if (obj == null) {
            obj = board.registerNewObjective("hh_sidebar", "dummy", "§a§l" + serverName, RenderType.INTEGER);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        int level = playerData.getLevel(uuid);
        boolean maxed = level >= playerData.getMaxLevel();

        String xpLine;
        if (maxed) {
            xpLine = sidebarConfig.getLine("xp-maxed");
        } else {
            long totalXP    = playerData.getXP(uuid);
            long xpAtLevel  = playerData.xpToReachLevel(level);
            long xpForLevel = playerData.xpForLevel(level);
            int percent = xpForLevel > 0
                    ? (int) Math.min(100, (totalXP - xpAtLevel) * 100 / xpForLevel)
                    : 100;
            xpLine = sidebarConfig.getLine("xp", "xp-percent", String.valueOf(percent));
        }

        String balanceLine;
        if (economy != null) {
            long bal = (long) economy.getBalance(player);
            balanceLine = sidebarConfig.getLine("balance", "balance", fmt(bal));
        } else {
            balanceLine = sidebarConfig.getLine("balance", "balance", "N/A");
        }

        setLine(obj, board, lines, sidebarConfig.getLine("datetime", "datetime", dateTime), 6);
        setLine(obj, board, lines, sidebarConfig.getLine("separator"),                       5);
        setLine(obj, board, lines, sidebarConfig.getLine("player", "player", player.getName()), 4);
        setLine(obj, board, lines, balanceLine,                                              3);
        setLine(obj, board, lines, sidebarConfig.getLine("level", "level", String.valueOf(level)), 2);
        setLine(obj, board, lines, xpLine,                                                   1);

        player.setScoreboard(board);
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
