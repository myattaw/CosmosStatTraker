package me.rages.stattraker.trackers.impl;

import me.lucko.helper.item.ItemStackBuilder;
import me.lucko.helper.text3.Text;
import me.rages.stattraker.StatTrakPlugin;
import me.rages.stattraker.trackers.Traker;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class FishTraker extends Traker {
    
    private String key;

    public FishTraker(StatTrakPlugin plugin) {
        this.key = "FISH_STREAK";

        ItemStackBuilder builder = ItemStackBuilder.of(Material.NAME_TAG)
                .name(plugin.getConfig().getString("stat-trak-fish." + this.key + ".item-name"))
                .lore(plugin.getConfig().getStringList("stat-trak-fish." + this.key + ".item-lore"))
                .transformMeta(itemMeta -> itemMeta.getPersistentDataContainer().set(
                        plugin.getStatTrakItemKey(),
                        PersistentDataType.STRING,
                        this.key)
                );

        getAcceptableItems().add(Material.FISHING_ROD);

        setItemStack(builder.build());
        setItemKey(new NamespacedKey(plugin, this.key));
        setDataLore(plugin.getConfig().getString("stat-trak-fish." + this.key + ".trak-lore"));
        setPrefixLore(Text.colorize(getDataLore().split("%amount%")[0]));
    }

    public static FishTraker create(StatTrakPlugin plugin) {
        return new FishTraker(plugin);
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

    public String getKey() {
        return key;
    }

}
