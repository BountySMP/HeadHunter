package com.lavishmc.headHunter;

import com.lavishmc.headHunter.DropHeads.events.EntityBeheadEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Set;

/**
 * Adds sell-price and required-level lore to every head drop that has a
 * matching entry in the HeadHunter {@code mobs} config section, and renames
 * the item to a clean "[MobName] Head" format derived from the entity type.
 */
public class HeadLoreListener implements Listener {

    // Variant prefixes that DropHeads may prepend to the mob name (e.g. "Temperate Frog Head").
    private static final Set<String> VARIANT_PREFIXES = Set.of(
            "Temperate", "Warm", "Cold", "Desert", "Savanna", "Snowy", "Jungle",
            "Plains", "Swamp", "Taiga", "Sparse", "Pale", "Black", "Blue", "Brown",
            "Chestnut", "Creamy", "Dark", "Dapple", "Gray", "White", "Red", "Orange",
            "Yellow", "Green", "Cyan", "Purple", "Pink", "Magenta", "Light"
    );

    private final MobsConfig mobsConfig;

    public HeadLoreListener(MobsConfig mobsConfig) {
        this.mobsConfig = mobsConfig;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityBehead(EntityBeheadEvent event) {
        String typeName = event.getVictim().getType().name();

        ConfigurationSection section = mobsConfig.getMobSection(typeName);
        if (section == null) return;

        long sellPrice    = section.getLong("sell_price", 0);
        int  requiredLevel = section.getInt("level", 1);

        ItemStack head = event.getHeadItem();
        if (head == null) return;

        ItemMeta meta = head.getItemMeta();
        if (meta == null) return;

        List<Component> lore = List.of(
                component("§7Required Level: §b" + requiredLevel),
                component("§7Head Price: §a$" + sellPrice)
        );
        meta.lore(lore);
        meta.displayName(
                Component.text(cleanMobName(event.getVictim().getType()) + " Head")
                        .color(NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false)
        );
        head.setItemMeta(meta);
    }

    private static String cleanMobName(EntityType type) {
        // Convert WITHER_SKELETON → "Wither Skeleton", stripping variant prefixes.
        String[] words = type.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            String titled = Character.toUpperCase(word.charAt(0)) + word.substring(1);
            // Skip the word if it is a known variant prefix and not the only word.
            if (VARIANT_PREFIXES.contains(titled) && words.length > 1) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(titled);
        }
        return sb.toString();
    }

    private static Component component(String legacyText) {
        return LegacyComponentSerializer.legacySection().deserialize(legacyText)
                .decoration(TextDecoration.ITALIC, false);
    }
}
