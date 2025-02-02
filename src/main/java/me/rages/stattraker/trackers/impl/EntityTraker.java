package me.rages.stattraker.trackers.impl;

import me.lucko.helper.item.ItemStackBuilder;
import me.lucko.helper.text3.Text;
import me.rages.stattraker.StatTrakPlugin;
import me.rages.stattraker.trackers.Traker;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * @author : Michael
 * @since : 8/7/2022, Sunday
 **/
public class EntityTraker extends Traker {

    public EntityTraker(String entityType, StatTrakPlugin plugin) {
        if (!plugin.getConfig().contains("stat-trak-entities." + entityType + ".item-name")) {
            plugin.getLogger().log(Level.WARNING, String.format("Could not find %s in config", entityType));
        }
        ItemStackBuilder builder = ItemStackBuilder.of(Material.NAME_TAG)
                .name(plugin.getConfig().getString("stat-trak-entities." + entityType + ".item-name"))
                .lore(plugin.getConfig().getStringList("stat-trak-entities." + entityType + ".item-lore"))
                .transformMeta(itemMeta -> itemMeta.getPersistentDataContainer().set(plugin.getStatTrakItemKey(), PersistentDataType.STRING, entityType));

        setItemStack(builder.build());
        setItemKey(new NamespacedKey(plugin, entityType));
        setDataLore(plugin.getConfig().getString("stat-trak-entities." + entityType + ".trak-lore"));
        setPrefixLore(Text.colorize(getDataLore().split("%amount%")[0]));
    }

    public static EntityTraker create(String entityType, StatTrakPlugin plugin) {
        return new EntityTraker(entityType, plugin);
    }

    public ItemStack incrementPlayerLore(ItemStack itemStack, int amount) {
        int total = itemStack.getItemMeta().getPersistentDataContainer().get(getItemKey(), PersistentDataType.INTEGER) + amount;
        ItemStackBuilder builder = ItemStackBuilder.of(itemStack);
        builder.transformMeta(meta -> meta.getPersistentDataContainer().set(getItemKey(), PersistentDataType.INTEGER, total));
        List<String> oldItemLore = itemStack.getItemMeta().getLore();
        builder.clearLore();
        builder.unflag(ItemFlag.HIDE_ENCHANTS);
        oldItemLore.stream().filter(currLore -> !currLore.startsWith(getPrefixLore())).forEach(builder::lore);
        builder.lore(getDataLore().replace("%amount%", String.format("%,d", total)));
        return builder.build();
    }

    public ItemStack incrementLore(ItemStack itemStack, int amount) {
        ItemMeta meta = itemStack.getItemMeta();

        // Increment the persistent data value
        NamespacedKey key = getItemKey();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        int total = container.getOrDefault(key, PersistentDataType.INTEGER, 0) + amount;
        container.set(key, PersistentDataType.INTEGER, total);

        // Update lore
        List<String> lore = meta.getLore();
        if (lore != null) {
            List<String> newLore = new ArrayList<>(lore.size());
            for (String currLore : lore) {
                if (currLore.startsWith(getPrefixLore()) || Text.colorize(currLore).startsWith(getPrefixLore())) {
                    newLore.add(Text.colorize(getDataLore().replace("%amount%", String.format("%,d", total))));
                } else {
                    newLore.add(currLore);
                }
            }
            meta.setLore(newLore);
        }

        // Clear item flags and return the modified ItemStack
        itemStack.setItemMeta(meta);
        return itemStack;
    }

}
