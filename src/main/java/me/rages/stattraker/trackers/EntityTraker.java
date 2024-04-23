package me.rages.stattraker.trackers;

import me.lucko.helper.item.ItemStackBuilder;
import me.lucko.helper.text3.Text;
import me.rages.augments.AugmentType;
import me.rages.stattraker.StatTrakPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : Michael
 * @since : 8/7/2022, Sunday
 **/
public class EntityTraker extends Traker {

    public EntityTraker(EntityType entityType, StatTrakPlugin plugin) {
        ItemStackBuilder builder = ItemStackBuilder.of(Material.NAME_TAG)
                .name(plugin.getConfig().getString("stat-trak-entities." + entityType.name() + ".item-name"))
                .lore(plugin.getConfig().getStringList("stat-trak-entities." + entityType.name() + ".item-lore"))
                .transformMeta(itemMeta -> itemMeta.getPersistentDataContainer().set(plugin.getStatTrakItemKey(), PersistentDataType.STRING, entityType.name()));

        setItemStack(builder.build());
        setItemKey(new NamespacedKey(plugin, entityType.name()));
        setDataLore(plugin.getConfig().getString("stat-trak-entities." + entityType.name() + ".trak-lore"));
        setPrefixLore(Text.colorize(getDataLore().split("%amount%")[0]));
    }

    public static EntityTraker create(EntityType entityType, StatTrakPlugin plugin) {
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
