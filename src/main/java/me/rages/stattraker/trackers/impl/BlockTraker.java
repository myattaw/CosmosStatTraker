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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * @author : Michael
 * @since : 10/7/2022, Friday
 **/
public class BlockTraker extends Traker {

    private HashSet<Material> materials = new HashSet<>();

    public BlockTraker(String key, StatTrakPlugin plugin) {
        ItemStackBuilder builder = ItemStackBuilder.of(Material.NAME_TAG)
                .name(plugin.getConfig().getString("stat-trak-blocks." + key + ".item-name"))
                .lore(plugin.getConfig().getStringList("stat-trak-blocks." + key + ".item-lore"))
                .transformMeta(itemMeta -> itemMeta.getPersistentDataContainer().set(plugin.getStatTrakItemKey(), PersistentDataType.STRING, key));

        plugin.getConfig().getStringList("stat-trak-blocks." + key + ".types")
                .stream().map(str -> Material.valueOf(str.toUpperCase()))
                .filter(Objects::nonNull)
                .forEach(material -> materials.add(material));

        plugin.getConfig().getStringList("stat-trak-blocks." + key + ".items")
                .stream().map(str -> Material.valueOf(str.toUpperCase()))
                .filter(Objects::nonNull)
                .forEach(material -> getAcceptableItems().add(material));

        setItemStack(builder.build());

        setItemKey(new NamespacedKey(plugin, key));
        setDataLore(plugin.getConfig().getString("stat-trak-blocks." + key + ".trak-lore"));
        setPrefixLore(Text.colorize(getDataLore().split("%amount%")[0]));
    }

    public static BlockTraker create(String key, StatTrakPlugin plugin) {
        return new BlockTraker(key, plugin);
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

    public HashSet<Material> getMaterials() {
        return materials;
    }

}
