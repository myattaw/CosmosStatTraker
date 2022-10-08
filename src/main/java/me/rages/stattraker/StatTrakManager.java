package me.rages.stattraker;

import me.lucko.helper.Commands;
import me.lucko.helper.Events;
import me.lucko.helper.event.filter.EventFilters;
import me.lucko.helper.item.ItemStackBuilder;
import me.lucko.helper.terminable.TerminableConsumer;
import me.lucko.helper.terminable.module.TerminableModule;
import me.lucko.helper.text3.Text;
import me.rages.stattraker.trackers.BlockTraker;
import me.rages.stattraker.trackers.EntityTraker;
import me.rages.stattraker.trackers.Traker;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author : Michael
 * @since : 8/5/2022, Friday
 **/
public class StatTrakManager implements TerminableModule {

    private Map<String, EntityTraker> entityTrakerMap = new HashMap<>();
    private Map<Material, BlockTraker> blockTrakerMap = new HashMap<>();
    private Set<Traker> trakersSet = new HashSet<>();

    private StatTrakPlugin plugin;

    public StatTrakManager(StatTrakPlugin plugin) {
        this.plugin = plugin;

        plugin.getConfig().getConfigurationSection("stat-trak-entities").getKeys(false)
                .stream().map(EntityType::valueOf)
                .filter(Objects::nonNull)
                .forEach(entityType -> {
                    EntityTraker entityTraker = EntityTraker.create(entityType, plugin);
                    entityTrakerMap.put(entityType.name(), entityTraker);
                    trakersSet.add(entityTraker);
                });

        plugin.getConfig().getConfigurationSection("stat-trak-blocks").getKeys(false)
                .stream().map(str -> BlockTraker.create(str, plugin))
                .forEach(blockTraker -> {
                    blockTraker.getMaterials().forEach(material -> blockTrakerMap.put(material, blockTraker));
                    trakersSet.add(blockTraker);
                });
    }

    @Override
    public void setup(@NotNull TerminableConsumer consumer) {

        Commands.parserRegistry().register(Traker.class, type -> {
            try {
                EntityTraker entityTraker = entityTrakerMap.get(type.toUpperCase());
                if (entityTraker != null) {
                    return Optional.ofNullable(entityTrakerMap.get(type.toUpperCase()));
                } else {
                    Material material = Material.valueOf(type.toUpperCase());
                    return Optional.ofNullable(blockTrakerMap.get(material));
                }
            } catch (Exception e) {
                return Optional.empty();
            }
        });

        Commands.create().assertPermission("stattrak.admin").assertUsage("[player] [type] <amount>").handler(cmd -> {
            Optional<Player> target = cmd.arg(0).parse(Player.class);
            Optional<Traker> traker = cmd.arg(1).parse(Traker.class);
            Integer amount = cmd.arg(2).parse(Integer.class).orElse(1);

            if (target.isPresent() && traker.isPresent()) {
                for (int i = 0; i < amount; ++i) {
                    Map<Integer, ItemStack> leftOvers = target.get().getInventory().addItem(traker.get().getItemStack());
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
            Object[] values = trakersSet.toArray();

            if (target.isPresent()) {
                for (int i = 0; i < amount; ++i) {
                    Traker randomValue = (Traker) values[ThreadLocalRandom.current().nextInt(values.length)];
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

                            Traker traker = null;
                            String data = container.get(plugin.getStatTrakItemKey(), PersistentDataType.STRING).toUpperCase();
                            if (entityTrakerMap.containsKey(data)) {
                                traker = entityTrakerMap.get(data);
                            } else {
                                Material material = Material.valueOf(data);
                                if (material != null) traker = blockTrakerMap.get(material);
                            }


                            ItemStack current = event.getCurrentItem();

                            if (traker != null) {

                                if (current.hasItemMeta() && current.getItemMeta().getPersistentDataContainer().has(traker.getItemKey())) {
                                    player.sendMessage(Text.colorize(plugin.getConfig().getString("messages.already-exist")));
                                    return;
                                }

                                if (traker.getAcceptableItems().contains(current.getType()) || traker.getAcceptableItems().isEmpty() && plugin.getValidItems().contains(current.getType())) {
                                    event.setCancelled(true);
                                    if (player.getItemOnCursor().getAmount() == 1) {
                                        player.setItemOnCursor(null);
                                    } else {
                                        player.getItemOnCursor().setAmount(cursor.getAmount() - 1);
                                    }
                                    event.setCurrentItem(addTrakerToItem(traker, current));
                                } else {
                                    player.sendMessage(Text.colorize(plugin.getConfig().getString("messages.invalid-item")));
                                }
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

        Events.subscribe(BlockBreakEvent.class, EventPriority.HIGHEST)
                .filter(EventFilters.ignoreCancelled())
                .handler(event -> {
                    Player player = event.getPlayer();
                    ItemStack itemStack = player.getInventory().getItemInMainHand();
                    BlockTraker blockTraker = blockTrakerMap.get(event.getBlock().getType());

                    if (blockTraker != null && itemStack.hasItemMeta() && itemStack.getItemMeta().getPersistentDataContainer().has(blockTraker.getItemKey())) {
                        player.getInventory().setItemInMainHand(blockTraker.incrementLore(itemStack, 1));
                    }
                }).bindWith(consumer);

    }

    private ItemStack addTrakerToItem(Traker traker, ItemStack itemStack) {
        return ItemStackBuilder.of(itemStack)
                .transformMeta(meta -> meta.getPersistentDataContainer().set(traker.getItemKey(), PersistentDataType.INTEGER, 0))
                .lore(traker.getDataLore().replace("%amount%", "0"))
                .build();
    }

}
