package me.rages.stattraker.trackers.impl;

import com.google.common.collect.ImmutableSet;
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
import java.util.Objects;
import java.util.logging.Level;

public class BossMobTraker extends Traker {


    public BossMobTraker(StatTrakPlugin plugin) {

        ItemStackBuilder builder = ItemStackBuilder.of(Material.NAME_TAG)
                .name(plugin.getConfig().getString("stat-trak-bossmob.item-name"))
                .lore(plugin.getConfig().getStringList("stat-trak-bossmob.item-lore"))
                .transformMeta(itemMeta -> itemMeta.getPersistentDataContainer()
                        .set(plugin.getStatTrakItemKey(), PersistentDataType.STRING, "BOSSMOB")
                );

        plugin.getConfig().getStringList("stat-trak-bossmob.items")
                .stream().map(str -> Material.valueOf(str.toUpperCase()))
                .filter(Objects::nonNull)
                .forEach(material -> getAcceptableItems().add(material));

        setItemStack(builder.build());
        setItemKey(new NamespacedKey(plugin, "BOSSMOB"));
        setDataLore(plugin.getConfig().getString("stat-trak-bossmob.trak-lore"));
        setPrefixLore(Text.colorize(getDataLore().split("%amount%")[0]));
    }

    public static BossMobTraker create(StatTrakPlugin plugin) {
        return new BossMobTraker(plugin);
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
