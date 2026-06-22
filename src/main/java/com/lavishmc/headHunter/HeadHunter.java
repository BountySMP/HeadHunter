package com.lavishmc.headHunter;

import com.lavishmc.headHunter.DropHeads.DropHeads;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * HeadHunter — plugin entry point.
 *
 * Extends DropHeads (which extends EvPlugin → JavaPlugin) so that all existing
 * DropHeads lifecycle hooks, static accessors, and API references continue to
 * work without modification.
 */
public final class HeadHunter extends DropHeads {

    private static PlayerDataManager playerDataManager;
    private SpawnerStackManager spawnerStackManager;

    public static PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    @Override
    public void onEvDisable() {
        if (playerDataManager != null) playerDataManager.flush();
        if (spawnerStackManager != null) spawnerStackManager.shutdown();
        super.onEvDisable();
    }

    @Override
    public void onEvEnable() {
        super.onEvEnable();
        getLogger().info("HeadHunter economy system loaded - HeadSellListener registered");

        Economy economy = null;
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp =
                    getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        if (economy == null) {
            getLogger().warning("Vault not found or no economy plugin loaded — head selling disabled.");
        }

        // ── Config objects ────────────────────────────────────────────────────
        MobsConfig     mobsConfig     = new MobsConfig(this);
        SpawnerConfig  spawnerConfig  = new SpawnerConfig(this);
        MessagesConfig messagesConfig = new MessagesConfig(this);

        playerDataManager = new PlayerDataManager(this, mobsConfig);

        // ── Register listeners ────────────────────────────────────────────────
        getServer().getPluginManager().registerEvents(
                new PlayerHeadListener(this, economy, messagesConfig), this);
        MobStackManager mobStackManager = new MobStackManager(this, mobsConfig);
        getServer().getPluginManager().registerEvents(mobStackManager, this);
        getServer().getPluginManager().registerEvents(
                new HeadLoreListener(mobsConfig), this);
        getServer().getPluginManager().registerEvents(
                new HeadSellListener(this, economy, playerDataManager,
                        mobsConfig, messagesConfig), this);
        getServer().getPluginManager().registerEvents(new SunlightProtectionListener(), this);
        spawnerStackManager = new SpawnerStackManager(this, spawnerConfig, mobsConfig, economy);
        spawnerStackManager.setPlayerDataManager(playerDataManager);
        getServer().getPluginManager().registerEvents(spawnerStackManager, this);
        getServer().getPluginManager().registerEvents(new SpawnerModeListener(spawnerStackManager), this);
        getServer().getPluginManager().registerEvents(new SpawnerMainListener(spawnerStackManager), this);
        getServer().getPluginManager().registerEvents(new SpawnerStorageListener(spawnerStackManager), this);
        // Link managers so MobStackManager can accumulate drops
        mobStackManager.setSpawnerStackManager(spawnerStackManager);


        // /hhdebug
        CommandExecutor hhDebug = (sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be run by a player.");
                return true;
            }

            ItemStack item = player.getInventory().getItemInMainHand();
            ItemMeta meta = item.getItemMeta();

            boolean hasDisplayName = meta != null && meta.hasDisplayName();
            String displayNamePlain = hasDisplayName
                    ? PlainTextComponentSerializer.plainText().serialize(
                            Objects.requireNonNull(meta.displayName()))
                    : "(none)";

            ConfigurationSection mobsSection = mobsConfig.getMobsSection();
            String mobKeys = mobsSection == null ? "(none)"
                    : mobsSection.getKeys(false).stream().collect(Collectors.joining(", "));

            player.sendMessage(ChatColor.GOLD + "--- HHDebug ---");
            player.sendMessage(ChatColor.YELLOW + "Material: " + ChatColor.WHITE + item.getType());
            player.sendMessage(ChatColor.YELLOW + "Has display name: " + ChatColor.WHITE + hasDisplayName);
            player.sendMessage(ChatColor.YELLOW + "Display name plain text: " + ChatColor.WHITE + displayNamePlain);
            player.sendMessage(ChatColor.YELLOW + "Mobs config keys: " + ChatColor.WHITE + mobKeys);
            return true;
        };
        Objects.requireNonNull(getCommand("hhdebug")).setExecutor(hhDebug);

        // /givespawner
        GiveSpawnerCommand giveSpawner = new GiveSpawnerCommand(mobsConfig, messagesConfig);
        Objects.requireNonNull(getCommand("givespawner")).setExecutor(giveSpawner);
        Objects.requireNonNull(getCommand("givespawner")).setTabCompleter(giveSpawner);

        // /giveplayerhead
        GivePlayerHeadCommand givePlayerHead = new GivePlayerHeadCommand(messagesConfig);
        Objects.requireNonNull(getCommand("giveplayerhead")).setExecutor(givePlayerHead);
        Objects.requireNonNull(getCommand("giveplayerhead")).setTabCompleter(givePlayerHead);

        // /rankup
        RankUpCommand rankUpCommand = new RankUpCommand(
                this, playerDataManager, economy, messagesConfig);
        getServer().getPluginManager().registerEvents(rankUpCommand, this);

        // /hh
        HHAdminCommand hhAdmin = new HHAdminCommand(this, playerDataManager, economy,
                mobsConfig, spawnerConfig, messagesConfig, rankUpCommand);
        Objects.requireNonNull(getCommand("hh")).setExecutor(hhAdmin);
        Objects.requireNonNull(getCommand("hh")).setTabCompleter(hhAdmin);
        Objects.requireNonNull(getCommand("rankup")).setExecutor(rankUpCommand);

        // /testlevelup
        Objects.requireNonNull(getCommand("testlevelup")).setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be run by a player.");
                return true;
            }
            rankUpCommand.runTestEffects(player);
            return true;
        });

        // /hhtest
        CommandExecutor hhTest = (sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be run by a player.");
                return true;
            }

            ItemStack item = player.getInventory().getItemInMainHand();
            ItemMeta meta  = item.getItemMeta();

            String displayName = (meta != null && meta.hasDisplayName())
                    ? PlainTextComponentSerializer.plainText().serialize(
                            Objects.requireNonNull(meta.displayName()))
                    : "(none)";

            String metaClass = meta != null ? meta.getClass().getName() : "(no meta)";

            String pdcKeys = "(no meta)";
            if (meta != null) {
                pdcKeys = StreamSupport
                        .stream(meta.getPersistentDataContainer().getKeys().spliterator(), false)
                        .map(k -> k.namespace() + ":" + k.key())
                        .collect(Collectors.joining(", "));
                if (pdcKeys.isEmpty()) pdcKeys = "(none)";
            }

            player.sendMessage(ChatColor.GOLD + "--- HHTest ---");
            player.sendMessage(ChatColor.YELLOW + "Material:     " + ChatColor.WHITE + item.getType());
            player.sendMessage(ChatColor.YELLOW + "Display name: " + ChatColor.WHITE + displayName);
            player.sendMessage(ChatColor.YELLOW + "Meta class:   " + ChatColor.WHITE + metaClass);
            player.sendMessage(ChatColor.YELLOW + "PDC keys:     " + ChatColor.WHITE + pdcKeys);
            return true;
        };
        Objects.requireNonNull(getCommand("hhtest")).setExecutor(hhTest);

        // /spawnershop
        Objects.requireNonNull(getCommand("spawnershop")).setExecutor(new SpawnerShopCommand());
        getServer().getPluginManager().registerEvents(
                new SpawnerShopListener(mobsConfig, playerDataManager, economy), this);

        // PlaceholderAPI integration
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new HeadHunterExpansion(this, playerDataManager, mobsConfig).register();
            getLogger().info("PlaceholderAPI expansion registered successfully.");
        }
    }
}
