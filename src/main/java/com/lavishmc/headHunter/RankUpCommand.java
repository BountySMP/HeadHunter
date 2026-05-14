package com.lavishmc.headHunter;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.HashMap;
import java.util.UUID;

/**
 * Handles the /rankup (alias /levelup) command.
 *
 * <p>Requirements to rank up:
 * <ol>
 *   <li>Player's total XP must be ≥ the XP threshold for the next level.</li>
 *   <li>Player must have ≥ {@code cost_to_rankup} money (Vault).</li>
 * </ol>
 * On success, money is deducted and the stored level is incremented by 1.</p>
 */
public class RankUpCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final PlayerDataManager playerData;
    private final Economy economy;
    private final MessagesConfig messages;
    private final SidebarConfig sidebarConfig;
    private final HashMap<UUID, BossBar> activeBossBars = new HashMap<>();

    public RankUpCommand(JavaPlugin plugin, PlayerDataManager playerData, Economy economy,
                         MessagesConfig messages, SidebarConfig sidebarConfig) {
        this.plugin        = plugin;
        this.playerData    = playerData;
        this.economy       = economy;
        this.messages      = messages;
        this.sidebarConfig = sidebarConfig;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.getRaw("console-only"));
            return true;
        }

        UUID uuid         = player.getUniqueId();
        int  currentLevel = playerData.getLevel(uuid);

        if (currentLevel >= playerData.getMaxLevel()) {
            player.sendMessage(messages.get("rankup-max-level"));
            return true;
        }

        int nextLevel = currentLevel + 1;

        long currentXP  = playerData.getXP(uuid);
        long xpRequired = playerData.xpToReachLevel(nextLevel);
        if (currentXP < xpRequired) {
            player.sendMessage(messages.get("rankup-xp-missing",
                    "current", String.valueOf(currentXP),
                    "required", String.valueOf(xpRequired)));
            return true;
        }

        long rankupCost = playerData.getRankupCost(currentLevel);
        if (economy != null && economy.getBalance(player) < rankupCost) {
            long difference = rankupCost - (long) economy.getBalance(player);
            player.sendMessage(messages.get("rankup-money-missing",
                    "amount", String.valueOf(difference),
                    "level", String.valueOf(nextLevel)));
            return true;
        }

        if (economy != null) economy.withdrawPlayer(player, (double) rankupCost);

        int oldTier = playerData.getTier(uuid);
        playerData.setLevel(uuid, nextLevel);
        playerData.setXP(uuid, 0);
        int newTier = playerData.getTier(uuid);

        player.sendMessage(messages.get("rankup-success", "level", String.valueOf(nextLevel)));
        if (newTier > oldTier) {
            player.sendMessage(messages.get("rankup-tier-unlock", "tier", String.valueOf(newTier)));
        }

        showRankUpBar(player, nextLevel, newTier);
        playRankUpEffects(player, nextLevel, oldTier, newTier);
        return true;
    }

    private void playRankUpEffects(Player player, int newLevel, int oldTier, int newTier) {
        Title title = Title.title(
                LegacyComponentSerializer.legacySection().deserialize(
                        messages.getRaw("rankup-title-top").replace("&", "§")),
                LegacyComponentSerializer.legacySection().deserialize(
                        messages.getRaw("rankup-title-sub", "level", String.valueOf(newLevel)).replace("&", "§")),
                Title.Times.times(
                        Duration.ofMillis(500),
                        Duration.ofMillis(3000),
                        Duration.ofMillis(1000)
                )
        );
        player.showTitle(title);

        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0), 200, 0.5, 1.0, 0.5, 0.3);
        player.getWorld().spawnParticle(Particle.END_ROD,
                player.getLocation().add(0, 1, 0), 150, 1.5, 1.5, 1.5, 0.1);

        org.bukkit.Color[] brightColors = {
                org.bukkit.Color.AQUA, org.bukkit.Color.FUCHSIA, org.bukkit.Color.YELLOW,
                org.bukkit.Color.LIME, org.bukkit.Color.RED, org.bukkit.Color.ORANGE
        };
        org.bukkit.Color randomColor = brightColors[(int) (Math.random() * brightColors.length)];

        org.bukkit.entity.Firework firework = player.getWorld().spawn(
                player.getLocation(), org.bukkit.entity.Firework.class);
        org.bukkit.inventory.meta.FireworkMeta meta = firework.getFireworkMeta();
        meta.setPower(1);
        meta.addEffect(org.bukkit.FireworkEffect.builder()
                .withColor(randomColor)
                .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                .flicker(true).trail(true).build());
        firework.setFireworkMeta(meta);

        if (newTier > oldTier) {
            Component broadcast = LegacyComponentSerializer.legacySection().deserialize(
                    messages.getRaw("rankup-broadcast",
                            "player", player.getName(),
                            "tier", String.valueOf(newTier)).replace("&", "§"));
            plugin.getServer().getOnlinePlayers().forEach(p -> p.sendMessage(broadcast));
        }
    }

    private void showRankUpBar(Player player, int newLevel, int tier) {
        UUID uuid = player.getUniqueId();

        long totalXP          = playerData.getXP(uuid);
        long xpAtCurrentLevel = playerData.xpToReachLevel(newLevel);
        long xpInLevel        = Math.max(0, totalXP - xpAtCurrentLevel);
        long xpForLevel       = playerData.xpForLevel(newLevel);
        boolean maxed = newLevel >= playerData.getMaxLevel();
        float fill    = maxed ? 1.0f : Math.min(1.0f, (float) xpInLevel / xpForLevel);

        BossBar.Color color = sidebarConfig.getTierBossBarColor(tier);

        String titleStr = messages.getRaw("rankup-bar", "level", String.valueOf(newLevel));
        if (tier > ((newLevel - 2) / 5 + 1)) {
            titleStr += messages.getRaw("rankup-bar-tier", "tier", String.valueOf(tier));
        }
        titleStr = titleStr.replace("&", "§");

        Component title = LegacyComponentSerializer.legacySection().deserialize(titleStr);
        BossBar bar = BossBar.bossBar(title, fill, color, BossBar.Overlay.PROGRESS);

        BossBar existing = activeBossBars.remove(uuid);
        if (existing != null) player.hideBossBar(existing);

        activeBossBars.put(uuid, bar);
        player.showBossBar(bar);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (activeBossBars.get(uuid) == bar) {
                player.hideBossBar(bar);
                activeBossBars.remove(uuid);
            }
        }, 60L);
    }

    public void runTestEffects(Player player) {
        int level = playerData.getLevel(player.getUniqueId());
        int tier  = playerData.getTier(player.getUniqueId());
        playRankUpEffects(player, level, tier - 1, tier);
    }
}
