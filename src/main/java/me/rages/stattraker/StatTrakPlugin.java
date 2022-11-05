package me.rages.stattraker;

import com.google.common.collect.ImmutableSet;
import me.lucko.helper.item.ItemStackBuilder;
import me.lucko.helper.plugin.ExtendedJavaPlugin;
import me.lucko.helper.plugin.ap.Plugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

@Plugin(
        name = "StatTrak",
        hardDepends = {"helper"},
        apiVersion = "1.18"
)
public final class StatTrakPlugin extends ExtendedJavaPlugin {

    public static final String ITEM_KEY = "stat-traker";
    private NamespacedKey statTrakItemKey;

    private ItemStack removerItemStack;

    private ImmutableSet<Material> validItems = ImmutableSet.of(
            Material.DIAMOND_AXE, Material.NETHERITE_AXE, Material.IRON_AXE,
            Material.DIAMOND_SWORD, Material.NETHERITE_SWORD, Material.IRON_SWORD,
            Material.CROSSBOW, Material.BOW, Material.TRIDENT
    );

    @Override
    protected void enable() {
        this.statTrakItemKey = new NamespacedKey(this, ITEM_KEY);
        this.saveDefaultConfig();
        this.bindModule(new StatTrakManager(this));

        this.removerItemStack = ItemStackBuilder.of(Material.valueOf(getConfig().getString("stack-trak-remover.type")))
                .name(getConfig().getString("stack-trak-remover.name"))
                .lore(getConfig().getStringList("stack-trak-remover.lore"))
                .build();

    }

    public ItemStack getRemoverItemStack() {
        return removerItemStack;
    }

    public NamespacedKey getStatTrakItemKey() {
        return statTrakItemKey;
    }

    public ImmutableSet<Material> getValidItems() {
        return validItems;
    }
}
