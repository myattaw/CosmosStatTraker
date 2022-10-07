package me.rages.stattraker;

import me.lucko.helper.Commands;
import me.lucko.helper.Events;
import me.lucko.helper.item.ItemStackBuilder;
import me.lucko.helper.terminable.TerminableConsumer;
import me.lucko.helper.terminable.module.TerminableModule;
import me.lucko.helper.text3.Text;
import me.rages.stattraker.trackers.BlockTraker;
import me.rages.stattraker.trackers.EntityTraker;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author : Michael
 * @since : 8/5/2022, Friday
 **/
public class StatTrakManager implements TerminableModule {

    private Map<String, EntityTraker> entityTrakerMap = new HashMap<>();
    private Map<Material, BlockTraker> blockTrakerMap = new HashMap<>();

    private StatTrakPlugin plugin;

    public StatTrakManager(StatTrakPlugin plugin) {
        this.plugin = plugin;

        plugin.getConfig().getConfigurationSection("stat-trak-entities").getKeys(false)
                .stream().map(EntityType::valueOf)
                .filter(Objects::nonNull)
                .forEach(entityType -> entityTrakerMap.put(entityType.name(), EntityTraker.create(entityType, plugin)));


        plugin.getConfig().getConfigurationSection("stat-trak-blocks").getKeys(false)
                .stream().map(str -> BlockTraker.create(str, plugin))
                .forEach(blockTraker -> blockTraker.getMaterials().forEach(material -> blockTrakerMap.put(material, blockTraker)));

    }

    @Override
    public void setup(@NotNull TerminableConsumer consumer) {

        Commands.parserRegistry().register(EntityTraker.class, s -> {
            try {
                return Optional.ofNullable(entityTrakerMap.get(s.toUpperCase()));
            } catch (Exception e) {
                return Optional.empty();
            }
        });

        Commands.create().assertPermission("stattrak.admin").assertUsage("[player] [type] <amount>").handler(cmd -> {
            Optional<Player> target = cmd.arg(0).parse(Player.class);
            Optional<EntityTraker> entityTraker = cmd.arg(1).parse(EntityTraker.class);
            Integer amount = cmd.arg(2).parse(Integer.class).orElse(1);

            if (target.isPresent() && entityTraker.isPresent()) {
                for (int i = 0; i < amount; ++i) {
                    Map<Integer, ItemStack> leftOvers = target.get().getInventory().addItem(entityTraker.get().getItemStack());
                    if (!leftOvers.isEmpty()) {
                        leftOvers.values().forEach(item -> target.get().getWorld().dropItem(target.get().getLocation(), item));
                    }
                }
            }

            target.get().sendMessage(Text.colorize(this.plugin.getConfig().getString("messages.traker-received"))
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%player%", target.get().getName()));

            cmd.sender().sendMessage(Text.colorize(this.plugin.getConfig().getString("messages.traker-given"))
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%player%", target.get().getName()));

        }).registerAndBind(consumer, new String[]{"stattrak", "stattrack"});

        Commands.create().assertPermission("stattrak.admin").assertUsage("[player] <amount>").handler(cmd -> {
            Optional<Player> target = cmd.arg(0).parse(Player.class);
            Integer amount = cmd.arg(1).parse(Integer.class).orElse(1);
            Object[] values = entityTrakerMap.values().toArray();

            if (target.isPresent()) {
                for (int i = 0; i < amount; ++i) {
                    EntityTraker randomValue = (EntityTraker) values[ThreadLocalRandom.current().nextInt(values.length)];
                    Map<Integer, ItemStack> leftOvers = target.get().getInventory().addItem(randomValue.getItemStack());
                    if (!leftOvers.isEmpty()) {
                        leftOvers.values().forEach(item -> target.get().getWorld().dropItem(target.get().getLocation(), item));
                    }
                }
            }

            target.get().sendMessage(Text.colorize(this.plugin.getConfig().getString("messages.traker-received"))
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%player%", target.get().getName()));

            cmd.sender().sendMessage(Text.colorize(this.plugin.getConfig().getString("messages.traker-given"))
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%player%", target.get().getName()));

        }).registerAndBind(consumer, new String[]{"randomtraker", "randomtracker", "rndtraker"});


        Events.subscribe(InventoryClickEvent.class, EventPriority.HIGH)
                .handler(event -> {
                    ItemStack cursor = event.getCursor();
                    Player player = (Player) event.getWhoClicked();
                    if (event.getAction() == InventoryAction.SWAP_WITH_CURSOR && cursor != null && cursor.hasItemMeta()) {
                        PersistentDataContainer container = cursor.getItemMeta().getPersistentDataContainer();
                        if (cursor.getType() == Material.NAME_TAG && container.has(plugin.getStatTrakItemKey())) {
                            EntityTraker entityTraker = entityTrakerMap.get(container.get(plugin.getStatTrakItemKey(), PersistentDataType.STRING));
                            ItemStack current = event.getCurrentItem();

                            if (current.hasItemMeta() && current.getItemMeta().getPersistentDataContainer().has(entityTraker.getItemKey())) {
                                player.sendMessage(Text.colorize(plugin.getConfig().getString("messages.already-exist")));
                                return;
                            }

                            if (entityTraker != null && plugin.getValidItems().contains(current.getType())) {
                                event.setCancelled(true);
                                if (player.getItemOnCursor().getAmount() == 1) {
                                    player.setItemOnCursor(null);
                                } else {
                                    player.getItemOnCursor().setAmount(cursor.getAmount() - 1);
                                }
                                event.setCurrentItem(addTrakerToItem(entityTraker, current));
                            } else {
                                player.sendMessage(Text.colorize(plugin.getConfig().getString("messages.invalid-item")));
                            }
                        }
                    }
                }).bindWith(consumer);

        Events.subscribe(PlayerDeathEvent.class)
                .filter(event -> event.getEntity().getKiller() != null)
                .handler(event -> {
                    Player player = event.getEntity().getKiller();
                    ItemStack itemStack = player.getInventory().getItemInMainHand();
                    EntityTraker entityTraker = entityTrakerMap.get(event.getEntity().getType().name());
                    if (entityTraker != null && itemStack.hasItemMeta() && itemStack.getItemMeta().getPersistentDataContainer().has(entityTraker.getItemKey())) {
                        player.getInventory().setItemInMainHand(entityTraker.incrementPlayerLore(itemStack, 1));
                    }
                }).bindWith(consumer);

        Events.subscribe(EntityDeathEvent.class)
                .filter(event -> event.getEntity().getKiller() != null)
                .handler(event -> {
                    Player player = event.getEntity().getKiller();
                    ItemStack itemStack = player.getInventory().getItemInMainHand();
                    EntityTraker entityTraker = entityTrakerMap.get(event.getEntity().getType().name());
                    if (entityTraker != null && itemStack.hasItemMeta() && itemStack.getItemMeta().getPersistentDataContainer().has(entityTraker.getItemKey())) {
                        player.getInventory().setItemInMainHand(entityTraker.incrementLore(itemStack, 1));
                    }
                }).bindWith(consumer);

    }

    private ItemStack addTrakerToItem(EntityTraker entityTraker, ItemStack itemStack) {
        return ItemStackBuilder.of(itemStack)
                .transformMeta(meta -> meta.getPersistentDataContainer().set(entityTraker.getItemKey(), PersistentDataType.INTEGER, 0))
                .lore(entityTraker.getDataLore().replace("%amount%", "0"))
                .build();
    }

}
