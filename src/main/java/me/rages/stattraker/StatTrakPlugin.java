package me.rages.stattraker;

import com.google.common.collect.ImmutableSet;
import me.lucko.helper.plugin.ExtendedJavaPlugin;
import me.lucko.helper.plugin.ap.Plugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

@Plugin(
        name = "StatTrak",
        hardDepends = {"helper"},
        apiVersion = "1.18"
)
public final class StatTrakPlugin extends ExtendedJavaPlugin {

    public static final String ITEM_KEY = "stat-traker";
    private NamespacedKey statTrakItemKey;
    private ImmutableSet<Material> validItems = ImmutableSet.of(
            Material.DIAMOND_AXE, Material.NETHERITE_AXE, Material.IRON_AXE,
            Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL, Material.IRON_SHOVEL,
            Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE, Material.IRON_PICKAXE,
            Material.DIAMOND_SWORD, Material.NETHERITE_SWORD, Material.IRON_SWORD,
            Material.CROSSBOW, Material.BOW, Material.TRIDENT
    );

    @Override
    protected void enable() {
        this.statTrakItemKey = new NamespacedKey(this, ITEM_KEY);
        this.saveDefaultConfig();
        this.bindModule(new StatTrakManager(this));
    }

    public NamespacedKey getStatTrakItemKey() {
        return statTrakItemKey;
    }

    public ImmutableSet<Material> getValidItems() {
        return validItems;
    }
}
