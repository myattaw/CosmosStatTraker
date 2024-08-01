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
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class ArrowShotTraker extends Traker {

    private static final ImmutableSet<Material> BOW_TYPES = ImmutableSet.<Material>builder()
            .add(Material.BOW, Material.CROSSBOW)
            .build();

    public ArrowShotTraker(StatTrakPlugin plugin) {

        String key = "ARROWS_SHOT";
        ItemStackBuilder builder = ItemStackBuilder.of(Material.NAME_TAG)
                .name(plugin.getConfig().getString("stat-trak-arrows-shot.item-name"))
                .lore(plugin.getConfig().getStringList("stat-trak-arrows-shot.item-lore"))
                .transformMeta(itemMeta -> itemMeta.getPersistentDataContainer().set(
                        plugin.getStatTrakItemKey(),
                        PersistentDataType.STRING,
                        key)
                );

        getAcceptableItems().addAll(BOW_TYPES);

        setItemStack(builder.build());
        setItemKey(new NamespacedKey(plugin, key));
        setDataLore(plugin.getConfig().getString("stat-trak-arrows-shot.trak-lore"));
        setPrefixLore(Text.colorize(getDataLore().split("%amount%")[0]));
    }

    public static ArrowShotTraker create(StatTrakPlugin plugin) {
        return new ArrowShotTraker(plugin);
    }

    public ItemStack incrementLore(ItemStack itemStack, int amount) {
        int total = itemStack.getItemMeta().getPersistentDataContainer().get(getItemKey(), PersistentDataType.INTEGER) + amount;
        ItemStackBuilder builder = ItemStackBuilder.of(itemStack);
        builder.transformMeta(meta -> meta.getPersistentDataContainer().set(getItemKey(), PersistentDataType.INTEGER, total));

        if (itemStack.getType() == Material.CROSSBOW) {
            builder.transformMeta(itemMeta -> {
                CrossbowMeta crossbowMeta = (CrossbowMeta) itemMeta;
                crossbowMeta.setChargedProjectiles(null);
            });
        }

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