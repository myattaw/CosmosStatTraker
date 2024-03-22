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
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * @author : Michael
 * @since : 3/22/2024, Friday
 **/
public class AugmentTraker extends Traker {

    public AugmentTraker(AugmentType augmentType, StatTrakPlugin plugin) {
        ItemStackBuilder builder = ItemStackBuilder.of(Material.NAME_TAG)
                .name(plugin.getConfig().getString("stat-trak-augments." + augmentType.name() + ".item-name"))
                .lore(plugin.getConfig().getStringList("stat-trak-augments." + augmentType.name() + ".item-lore"))
                .transformMeta(itemMeta -> itemMeta.getPersistentDataContainer().set(plugin.getStatTrakItemKey(), PersistentDataType.STRING, augmentType.name()));

        setItemStack(builder.build());
        setItemKey(new NamespacedKey(plugin, augmentType.name()));
        setDataLore(plugin.getConfig().getString("stat-trak-augments." + augmentType.name() + ".trak-lore"));
        setPrefixLore(Text.colorize(getDataLore().split("%amount%")[0]));
    }

    public static AugmentTraker create(AugmentType entityType, StatTrakPlugin plugin) {
        return new AugmentTraker(entityType, plugin);
    }

    public ItemStack incrementLore(ItemStack itemStack, int amount) {
        int total = itemStack.getItemMeta().getPersistentDataContainer().get(getItemKey(), PersistentDataType.INTEGER) + amount;
        ItemStackBuilder builder = ItemStackBuilder.of(itemStack);
        builder.transformMeta(meta -> meta.getPersistentDataContainer().set(getItemKey(), PersistentDataType.INTEGER, total));
        List<String> oldItemLore = itemStack.getItemMeta().getLore();
        builder.clearLore();
        builder.unflag(ItemFlag.HIDE_ENCHANTS);
        oldItemLore.forEach(currLore -> {
            if (currLore.startsWith(getPrefixLore())) {
                builder.lore(getDataLore().replace("%amount%", String.format("%,d", total)));
            } else {
                builder.lore(currLore);
            }
        });
        return builder.build();
    }

}

