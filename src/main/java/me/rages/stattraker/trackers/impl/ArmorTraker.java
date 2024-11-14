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
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Objects;

public class ArmorTraker extends Traker {

    private static final ImmutableSet<Material> ARMOR_TYPES = ImmutableSet.<Material>builder()
            .add(
                    Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                    Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
                    Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
                    Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
                    Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
                    Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS, Material.SHIELD
            )
            .build();

    private String key;
    private boolean hits;

    public ArmorTraker(boolean hits, StatTrakPlugin plugin) {
        this.hits = hits;
        this.key = this.hits ? "HITS_TAKEN" : "DAMAGE_TAKEN";

        ItemStackBuilder builder = ItemStackBuilder.of(Material.NAME_TAG)
                .name(plugin.getConfig().getString("stat-trak-armor." + this.key + ".item-name"))
                .lore(plugin.getConfig().getStringList("stat-trak-armor." + this.key + ".item-lore"))
                .transformMeta(itemMeta -> itemMeta.getPersistentDataContainer().set(
                        plugin.getStatTrakItemKey(),
                        PersistentDataType.STRING,
                        this.key)
                );

        getAcceptableItems().addAll(ARMOR_TYPES);

        setItemStack(builder.build());
        setItemKey(new NamespacedKey(plugin, this.key));
        setDataLore(plugin.getConfig().getString("stat-trak-armor." + this.key + ".trak-lore"));
        setPrefixLore(Text.colorize(getDataLore().split("%amount%")[0]));
    }

    public static ArmorTraker create(boolean hits, StatTrakPlugin plugin) {
        return new ArmorTraker(hits, plugin);
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

    public boolean isHits() {
        return hits;
    }

    public String getKey() {
        return key;
    }
}
