package me.rages.stattraker;

import com.google.common.collect.ImmutableMap;
import me.lucko.helper.Commands;
import me.lucko.helper.Events;
import me.lucko.helper.Schedulers;
import me.lucko.helper.event.filter.EventFilters;
import me.lucko.helper.item.ItemStackBuilder;
import me.lucko.helper.terminable.TerminableConsumer;
import me.lucko.helper.terminable.module.TerminableModule;
import me.lucko.helper.text3.Text;
import me.lucko.helper.utils.Players;
import me.rages.augments.AugmentType;
import me.rages.augments.event.AugmentRewardEvent;
import me.rages.stattraker.trackers.Traker;
import me.rages.stattraker.trackers.impl.*;
import org.apache.commons.lang3.EnumUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * @author : Michael
 * @since : 8/5/2022, Friday
 **/
public class StatTrakManager implements TerminableModule {

    private final Map<String, ArmorTraker> armorTrakerMap = new HashMap<>();
    private final Map<String, EntityTraker> entityTrakerMap = new HashMap<>();
    private final Map<String, AugmentTraker> augmentTrakerMap = new HashMap<>();
    private final Map<Material, BlockTraker> blockTrakerMap = new HashMap<>();

    private ArrowShotTraker arrowShotTraker;

    private Set<Traker> trakersSet = new HashSet<>();

    private StatTrakPlugin plugin;

    private boolean returnTrakerItem;

    private final static ImmutableMap<String, String> OLD_ENTITY_NAMES = ImmutableMap.of(
            "SNOWMAN", "SNOW_GOLEM"
    );


    public StatTrakManager(StatTrakPlugin plugin) {
        this.plugin = plugin;

        this.returnTrakerItem = plugin.getConfig().getBoolean("stack-trak-remover.return-traker", true);

        ArmorTraker armorHitsTraker = ArmorTraker.create(true, plugin);
        ArmorTraker armorDamageTraker = ArmorTraker.create(false, plugin);
        armorTrakerMap.put(armorHitsTraker.getKey(), armorHitsTraker);
        armorTrakerMap.put(armorDamageTraker.getKey(), armorDamageTraker);
        trakersSet.add(armorHitsTraker);
        trakersSet.add(armorDamageTraker);

        this.arrowShotTraker = ArrowShotTraker.create(plugin);
        trakersSet.add(this.arrowShotTraker);

        for (String s : plugin.getConfig().getConfigurationSection("stat-trak-entities").getKeys(false)) {
            if (EnumUtils.isValidEnum(EntityType.class, s)) {
                EntityType entityType = EntityType.valueOf(s);
                EntityTraker entityTraker = EntityTraker.create(s, plugin);
                entityTrakerMap.put(entityType.name(), entityTraker);
                trakersSet.add(entityTraker);
            } else {
                String convertedName = OLD_ENTITY_NAMES.getOrDefault(s, s);
                if (EnumUtils.isValidEnum(EntityType.class, convertedName)) {
                    EntityTraker entityTraker = EntityTraker.create(s, plugin);
                    entityTrakerMap.put(s, entityTraker);
                    trakersSet.add(entityTraker);
                } else {
                    plugin.getLogger().log(Level.INFO, s + " is not a valid entity type.");
                }
            }
        }

        plugin.getConfig().getConfigurationSection("stat-trak-augments").getKeys(false)
                .stream().map(AugmentType::valueOf)
                .filter(Objects::nonNull)
                .forEach(augmentType -> {
                    AugmentTraker augmentTraker = AugmentTraker.create(augmentType, plugin);
                    augmentTrakerMap.put(augmentType.name(), augmentTraker);
                    trakersSet.add(augmentTraker);
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
                } else if (augmentTrakerMap.containsKey(type.toUpperCase())) {
                    return Optional.ofNullable(augmentTrakerMap.get(type.toUpperCase()));
                } else if (armorTrakerMap.containsKey(type.toUpperCase())) {
                    return Optional.ofNullable(armorTrakerMap.get(type.toUpperCase()));
                } else if (type.toUpperCase().equals("ARROWS_SHOT")) {
                    return Optional.ofNullable(arrowShotTraker);
                } else {
                    Material material = Material.valueOf(type.toUpperCase());
                    return Optional.ofNullable(blockTrakerMap.get(material));
                }
            } catch (Exception e) {
                return Optional.empty();
            }
        });

        Commands.create().assertPermission("stattrak.admin").assertUsage("[player] <amount>").handler(cmd -> {
            Optional<Player> target = cmd.arg(0).parse(Player.class);
            Integer amount = cmd.arg(1).parse(Integer.class).orElse(1);

            if (target.isPresent()) {
                for (int i = 0; i < amount; ++i) {
                    Map<Integer, ItemStack> leftOvers = target.get().getInventory().addItem(plugin.getRemoverItemStack());
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

        }).registerAndBind(consumer, "trakremover");

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

        }).registerAndBind(consumer, "stattrak", "stattrack");

        Commands.create()
                .assertPermission("stattrak.remove")
                .assertPlayer()
                .handler(cmd -> {
                    // check if player has tracker in inventory
                    @Nullable ItemStack @NotNull [] contents = cmd.sender().getInventory().getContents();
                    for (int i = 0, contentsLength = contents.length; i < contentsLength; i++) {
                        ItemStack item = contents[i];
                        if (item == null) continue;

                        ItemStack check = item.clone();
                        check.setAmount(1);

                        if (check.equals(plugin.getRemoverItemStack())) {
                            ItemStack removedTraker = removeTrakerFromItem(
                                    cmd.sender(),
                                    cmd.sender().getInventory().getItemInMainHand()
                            );
                            if (removedTraker != null) {
                                if (item.getAmount() == 1) {
                                    cmd.sender().getInventory().setItem(i, null);
                                } else {
                                    item.setAmount(item.getAmount() - 1);
                                }
                                cmd.sender().sendMessage(
                                        Text.colorize(this.plugin.getConfig().getString("messages.traker-removed"))
                                );
                                cmd.sender().getInventory().setItemInMainHand(removedTraker);
                            }
                        }
                    }
                }).registerAndBind(consumer, "removetracker");

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

        }).registerAndBind(consumer, "randomtraker", "randomtracker", "rndtraker");

        Events.subscribe(EntityShootBowEvent.class)
                .filter(event -> event.getEntity() instanceof Player)
                .handler(event -> {
                    Player player = (Player) event.getEntity();
                    ItemStack itemStack = player.getInventory().getItem(event.getHand());
                    if (itemStack != null) {
                        ArrowShotTraker arrowShotTraker = this.arrowShotTraker;
                        if (arrowShotTraker != null && itemStack.hasItemMeta()
                                && itemStack.getItemMeta().getPersistentDataContainer().has(arrowShotTraker.getItemKey())) {
                            player.getInventory().setItem(
                                    event.getHand(),
                                    arrowShotTraker.incrementLore(itemStack, 1)
                            );
                        }
                    }
                }).bindWith(consumer);

        EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        Events.subscribe(EntityDamageByEntityEvent.class)
                .filter(event -> event.getEntity() instanceof Player)
                .filter(event -> event.getDamager() instanceof Player)
                .handler(event -> {
                    Player player = (Player) event.getEntity();
                    Arrays.stream(armorSlots).map(equipmentSlot -> player.getInventory().getItem(equipmentSlot))
                            .filter(itemStack -> itemStack.hasItemMeta()).forEach(itemStack -> {
                                ItemMeta meta = itemStack.getItemMeta();
                                armorTrakerMap.values().stream()
                                        .filter(traker -> meta.getPersistentDataContainer().has(traker.getItemKey()))
                                        .forEach(traker -> traker.incrementLore(itemStack, traker.isHits() ? 1 :
                                                (int) event.getFinalDamage()
                                        ));
                            });

                }).bindWith(consumer);

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
                            } else if (augmentTrakerMap.containsKey(data)) {
                                traker = augmentTrakerMap.get(data);
                            } else if (armorTrakerMap.containsKey(data)) {
                                traker = armorTrakerMap.get(data);
                            } else if (data.equalsIgnoreCase("ARROWS_SHOT")) {
                                traker = arrowShotTraker;
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

                        ItemStack check = cursor.clone();
                        check.setAmount(1);
                        if (check.equals(plugin.getRemoverItemStack())) {
                            ItemStack current = event.getCurrentItem();
                            ItemStack removedTraker = removeTrakerFromItem(player, current);
                            if (removedTraker != null) {
                                event.setCancelled(true);
                                if (player.getItemOnCursor().getAmount() == 1) {
                                    player.setItemOnCursor(null);
                                } else {
                                    player.getItemOnCursor().setAmount(cursor.getAmount() - 1);
                                }
                                event.setCurrentItem(removedTraker);
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

        // Declare a map to store player UUIDs and their last usage time
        Map<UUID, Long> cooldownMap = new HashMap<>();

        Events.subscribe(EntityDeathEvent.class)
                .filter(event -> event.getEntity().getKiller() != null)
                .handler(event -> {
                    Player player = event.getEntity().getKiller();
                    ItemStack itemStack = player.getInventory().getItemInMainHand();
                    EntityTraker entityTraker = entityTrakerMap.get(event.getEntity().getType().name());
                    if (entityTraker != null && itemStack.hasItemMeta() && itemStack.getItemMeta().getPersistentDataContainer().has(entityTraker.getItemKey())) {
//                        player.getInventory().setItemInMainHand(entityTraker.incrementLore(itemStack, 1));
                        UUID playerId = player.getUniqueId();
                        long currentTime = System.currentTimeMillis();
                        long cooldownTime = cooldownMap.getOrDefault(playerId, 0L);

                        // Check if the player is still on cooldown
                        if (currentTime - cooldownTime >= 1000) {
                            // Update the cooldown time for the player
                            cooldownMap.put(playerId, currentTime);
                            // Perform the action (setting the item in the player's main hand)
                            player.getInventory().setItemInMainHand(entityTraker.incrementLore(itemStack, 1));
                        }
                    }
                }).bindWith(consumer);

        if (plugin.getServer().getPluginManager().isPluginEnabled("Augments")) {
            Events.subscribe(AugmentRewardEvent.class)
                    .filter(event -> event.getAugmentType() != null)
                    .handler(event -> {
                        Player player = event.getPlayer();
                        ItemStack itemStack = player.getInventory().getItemInMainHand();
                        AugmentTraker augmentTraker = augmentTrakerMap.get(event.getAugmentType().name());
                        if (augmentTraker != null && itemStack.hasItemMeta()
                                && itemStack.getItemMeta().getPersistentDataContainer().has(augmentTraker.getItemKey())) {
                            player.getInventory().setItemInMainHand(augmentTraker.incrementLore(itemStack, 1));
                        }
                    }).bindWith(consumer);
        }

        Map<UUID, Map<BlockTraker, Integer>> cachedAmounts = new HashMap<>();

//        player.getInventory().setItemInMainHand(blockTraker.incrementLore(itemStack, 1));

        Events.subscribe(BlockBreakEvent.class, EventPriority.HIGHEST)
                .filter(EventFilters.ignoreCancelled())
                .handler(event -> {
                    Player player = event.getPlayer();
                    ItemStack itemStack = player.getInventory().getItemInMainHand();
                    BlockTraker blockTraker = blockTrakerMap.get(event.getBlock().getType());

                    if (blockTraker != null && itemStack.hasItemMeta() && itemStack.getItemMeta().getPersistentDataContainer().has(blockTraker.getItemKey())) {
                        UUID playerUUID = player.getUniqueId();
                        Map<BlockTraker, Integer> playerCache = cachedAmounts.getOrDefault(playerUUID, new HashMap<>());

                        playerCache.put(blockTraker, playerCache.getOrDefault(blockTraker, 0) + 1);
                        cachedAmounts.put(playerUUID, playerCache);
                    }
                }).bindWith(consumer);

        Events.subscribe(PlayerItemHeldEvent.class)
                .filter(event -> cachedAmounts.containsKey(event.getPlayer().getUniqueId()))
                .filter(event -> event.getPlayer().getInventory().getItem(event.getPreviousSlot()) != null)
                .handler(event -> {

                    Player player = event.getPlayer();
                    ItemStack itemStack = player.getInventory().getItem(event.getPreviousSlot());

                    cachedAmounts.get(player.getUniqueId()).entrySet().stream()
                            .filter(entry -> itemStack.hasItemMeta() && itemStack.getItemMeta()
                                    .getPersistentDataContainer().has(entry.getKey().getItemKey()))
                            .forEach(entry -> player.getInventory().setItemInMainHand(entry.getKey().incrementLore(
                                    itemStack,
                                    entry.getValue())
                            ));
                    cachedAmounts.remove(player.getUniqueId());

                }).bindWith(consumer);

        Schedulers.sync().runRepeating(() -> {

            if (!cachedAmounts.isEmpty()) {
                cachedAmounts.keySet().stream().map(Players::get).filter(Optional::isPresent).forEach(player -> {
                    ItemStack itemStack = player.get().getInventory().getItemInMainHand();
                    if (itemStack != null) {
                        cachedAmounts.get(player.get().getUniqueId()).entrySet().stream()
                                .filter(entry -> itemStack.hasItemMeta() && itemStack.getItemMeta()
                                        .getPersistentDataContainer().has(entry.getKey().getItemKey()))
                                .forEach(entry -> player.get().getInventory().setItemInMainHand(
                                                entry.getKey().incrementLore(itemStack, entry.getValue())
                                        )
                                );
                    }
                });
                cachedAmounts.clear();
            }
        }, 15L, TimeUnit.SECONDS, 15L, TimeUnit.SECONDS).bindWith(consumer);

    }

    private ItemStack addTrakerToItem(Traker traker, ItemStack itemStack) {
        return ItemStackBuilder.of(itemStack)
                .transformMeta(meta -> meta.getPersistentDataContainer().set(traker.getItemKey(), PersistentDataType.INTEGER, 0))
                .lore(traker.getDataLore().replace("%amount%", "0"))
                .unflag(ItemFlag.HIDE_ENCHANTS)
                .build();
    }

    private ItemStack removeTrakerFromItem(Player player, ItemStack itemStack) {
        if (itemStack.hasItemMeta()) {
            PersistentDataContainer persistentDataContainer = itemStack.getItemMeta().getPersistentDataContainer();
            for (NamespacedKey key : persistentDataContainer.getKeys()) {

                if (!StatTrakPlugin.TRACKER_KEYS.contains(key)) continue;

                Traker traker = entityTrakerMap.get(key.getKey().toUpperCase());

                if (traker == null && augmentTrakerMap.containsKey(key.getKey().toUpperCase())) {
                    traker = augmentTrakerMap.get(key.getKey().toUpperCase());
                }

                if (traker == null) {
                    traker = blockTrakerMap.get(Material.valueOf(key.getKey().toUpperCase()));
                }

                if (traker == null) {
                    return null;
                }

                List<String> oldLore = itemStack.getLore();

                ItemStackBuilder builder = ItemStackBuilder.of(itemStack).transformMeta(itemMeta ->
                        itemMeta.getPersistentDataContainer().remove(key)
                );
                builder.clearLore();
                if (traker != null) {
                    for (String lore : oldLore) {
                        if (!lore.startsWith(traker.getPrefixLore())) {
                            builder.lore(lore);
                        }
                    }
                    itemStack.removeItemFlags(ItemFlag.HIDE_ENCHANTS);

                    if (returnTrakerItem) {
                        Map<Integer, ItemStack> leftOvers = player.getInventory().addItem(traker.getItemStack());
                        if (!leftOvers.isEmpty()) {
                            leftOvers.values().forEach(item -> player.getWorld().dropItem(player.getLocation(), item));
                        }
                    }

                    return builder.build();
                }

            }
        }
        return null;
    }

}
