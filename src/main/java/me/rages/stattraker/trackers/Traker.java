package me.rages.stattraker.trackers;

import me.rages.stattraker.StatTrakPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;

/**
 * @author : Michael
 * @since : 10/7/2022, Friday
 **/
public class Traker {

    private ItemStack itemStack;
    private NamespacedKey itemKey;
    private String dataLore;
    private String prefixLore;


    private HashSet<Material> acceptableItems = new HashSet<>();

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public void setItemKey(NamespacedKey itemKey) {
        this.itemKey = itemKey;
        StatTrakPlugin.TRACKER_KEYS.add(itemKey);
    }

    public NamespacedKey getItemKey() {
        return itemKey;
    }

    public void setDataLore(String dataLore) {
        this.dataLore = dataLore;
    }

    public String getDataLore() {
        return dataLore;
    }

    public void setPrefixLore(String prefixLore) {
        this.prefixLore = prefixLore;
    }

    public String getPrefixLore() {
        return prefixLore;
    }

    public HashSet<Material> getAcceptableItems() {
        return acceptableItems;
    }
}
