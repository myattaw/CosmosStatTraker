package me.rages.stattraker;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
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
import org.bukkit.Bukkit;
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
import org.bukkit.event.player.PlayerFishEvent;
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

    // convert this into an object later <trackerId, trackerObj>
    private final Map<String, ArmorTraker> armorTrakerMap = new HashMap<>();
    private final Map<String, EntityTraker> entityTrakerMap = new HashMap<>();
    private final Map<String, AugmentTraker> augmentTrakerMap = new HashMap<>();
    private final Map<Material, BlockTraker> blockTrakerMap = new HashMap<>();

    private final EntityTraker stackerTracker;
    private final FishTraker fishStreakTraker;


    private ArrowShotTraker arrowShotTraker;

    private Set<Traker> trakersSet = new HashSet<>();

    private StatTrakPlugin plugin;

    private boolean returnTrakerItem;

    public static final BiMap<String, String> OLD_ENTITY_NAMES;

    private NamespacedKey slayerBossKey;
    private BossMobTraker bossMobTraker;


    static {
        OLD_ENTITY_NAMES = HashBiMap.create();
        OLD_ENTITY_NAMES.put("SNOWMAN", "SNOW_GOLEM");
    }

    public StatTrakManager(StatTrakPlugin plugin) {
        this.plugin = plugin;
        this.returnTrakerItem = plugin.getConfig().getBoolean("stack-trak-remover.return-traker", true);
        this.stackerTracker = EntityTraker.create("STACKER", plugin);
        this.fishStreakTraker = FishTraker.create(plugin);
        trakersSet.add(this.stackerTracker);
        trakersSet.add(this.fishStreakTraker);


        ArmorTraker armorHitsTraker = ArmorTraker.create(true, plugin);
        ArmorTraker armorDamageTraker = ArmorTraker.create(false, plugin);
        armorTrakerMap.put(armorHitsTraker.getKey(), armorHitsTraker);
        armorTrakerMap.put(armorDamageTraker.getKey(), armorDamageTraker);
        trakersSet.add(armorHitsTraker);
        trakersSet.add(armorDamageTraker);

        this.arrowShotTraker = ArrowShotTraker.create(plugin);
        trakersSet.add(this.arrowShotTraker);

        boolean usingSlayer = plugin.getConfig().getBoolean("settings.use-slayer-trackers");
        if (usingSlayer) {
            slayerBossKey = new NamespacedKey("slayer", "boss_mob");
            this.bossMobTraker = BossMobTraker.create(plugin);
            trakersSet.add(bossMobTraker);
        }

        plugin.getConfig().getConfigurationSection("stat-trak-entities").getKeys(false).forEach(s -> {
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
        });

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
                if (slayerBossKey != null && type.equalsIgnoreCase("BOSSMOB")) {
                    return Optional.ofNullable(bossMobTraker);
                }

                EntityTraker entityTraker = entityTrakerMap.get(type.toUpperCase());
                if (entityTraker != null) {
                    return Optional.ofNullable(entityTrakerMap.get(type.toUpperCase()));
                } else if (augmentTrakerMap.containsKey(type.toUpperCase())) {
                    return Optional.ofNullable(augmentTrakerMap.get(type.toUpperCase()));
                } else if (armorTrakerMap.containsKey(type.toUpperCase())) {
                    return Optional.ofNullable(armorTrakerMap.get(type.toUpperCase()));
                } else if (type.toUpperCase().equals("FISH_STREAK")) {
                    return Optional.ofNullable(fishStreakTraker);
                } else if (type.toUpperCase().equals("STACKER")) {
                    return Optional.ofNullable(stackerTracker);
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

        Map<UUID, Long> lastFishCaught = new HashMap<>();

        Events.subscribe(PlayerFishEvent.class)
                .filter(event -> event.getState() == PlayerFishEvent.State.CAUGHT_FISH)
                .handler(event -> {
                    Player player = event.getPlayer();
                    UUID playerId = player.getUniqueId();
                    ItemStack itemStack = player.getInventory().getItem(event.getHand());

                    if (itemStack != null && fishStreakTraker != null && itemStack.hasItemMeta()
                            && itemStack.getItemMeta().getPersistentDataContainer().has(fishStreakTraker.getItemKey())) {

                        long currentTime = System.currentTimeMillis();

                        // Check if the player has caught a fish before
                        if (lastFishCaught.containsKey(playerId)) {
                            long lastCaughtTime = lastFishCaught.get(playerId);
                            // Check if last fish was caught within 30 seconds
                            if (currentTime - lastCaughtTime < 30 * 1000) {
                                player.getInventory().setItem(
                                        event.getHand(),
                                        fishStreakTraker.incrementLore(itemStack, 1)
                                );
                            }
                        }

                        // Update the last caught time to the current time
                        lastFishCaught.put(playerId, currentTime);
                    }
                }).bindWith(consumer);

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

        EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND};
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

                            if (data.equals("BOSSMOB")) {
                                traker = bossMobTraker;
                            } else if (entityTrakerMap.containsKey(data)) {
                                traker = entityTrakerMap.get(data);
                            } else if (augmentTrakerMap.containsKey(data)) {
                                traker = augmentTrakerMap.get(data);
                            } else if (armorTrakerMap.containsKey(data)) {
                                traker = armorTrakerMap.get(data);
                            } else if (data.equalsIgnoreCase("ARROWS_SHOT")) {
                                traker = arrowShotTraker;
                            } else if (data.equalsIgnoreCase("FISH_STREAK")) {
                                traker = fishStreakTraker;
                            } else if (data.equalsIgnoreCase("STACKER")) {
                                traker = stackerTracker;
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
                    if (itemStack.hasItemMeta()) {
                        if (entityTraker != null && itemStack.getItemMeta().getPersistentDataContainer().has(entityTraker.getItemKey())) {
                            player.getInventory().setItemInMainHand(entityTraker.incrementPlayerLore(itemStack, 1));
                        }
                    }

                }).bindWith(consumer);

        // Declare a map to store player UUIDs and their last usage time
        Map<UUID, Long> cooldownMap = new HashMap<>();

        Events.subscribe(EntityDeathEvent.class)
                .filter(event -> event.getEntity().getKiller() != null)
                .handler(event -> {
                    Player player = event.getEntity().getKiller();
                    ItemStack itemStack = player.getInventory().getItemInMainHand();
                    EntityType type = event.getEntity().getType();

                    if (slayerBossKey != null) {
                        if (event.getEntity().getPersistentDataContainer().has(slayerBossKey) &&
                                itemStack.hasItemMeta() && itemStack.getItemMeta().getPersistentDataContainer().has(bossMobTraker.getItemKey())) {
                            player.getInventory().setItemInMainHand(bossMobTraker.incrementLore(itemStack, 1));
                        }
                    }

                    EntityTraker entityTraker = entityTrakerMap.get(type.name());
                    if (entityTraker == null) {
                        entityTraker = entityTrakerMap.get(OLD_ENTITY_NAMES.inverse().getOrDefault(
                                type.name(),
                                type.name())
                        );
                    }

                    if (itemStack.hasItemMeta()) {
                        applyEntityTracker(cooldownMap, player, itemStack, entityTraker);
                        if (stackerTracker != null && itemStack.getItemMeta().getPersistentDataContainer().has(stackerTracker.getItemKey())) {
                            player.getInventory().setItemInMainHand(stackerTracker.incrementLore(itemStack, 1));
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

    private void applyEntityTracker(Map<UUID, Long> cooldownMap, Player player, ItemStack itemStack, EntityTraker entityTraker) {
        if (entityTraker != null && itemStack.getItemMeta().getPersistentDataContainer().has(entityTraker.getItemKey())) {
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
    }

    private ItemStack addTrakerToItem(Traker traker, ItemStack itemStack) {
        // Clone the original ItemStack to avoid modifying it directly
        ItemStack newItem = itemStack.clone();

        // Initialize or get the existing lore from the item
        List<String> lore = new ArrayList<>();
        if (newItem.hasItemMeta() && newItem.getItemMeta().hasLore()) {
            lore = new ArrayList<>(newItem.getItemMeta().getLore()); // Ensure it's mutable
        }

        // Add the new lore entry
        lore.add(Text.colorize(traker.getDataLore().replace("%amount%", "0")));
        ItemMeta itemMeta = newItem.hasItemMeta() ? newItem.getItemMeta() : Bukkit.getItemFactory().getItemMeta(newItem.getType());

        // Modify the item's meta

        if (itemMeta != null) {
            itemMeta.setLore(lore);
            itemMeta.getPersistentDataContainer().set(traker.getItemKey(), PersistentDataType.INTEGER, 0);
            newItem.setItemMeta(itemMeta);
        }

        return newItem;
    }

    private ItemStack removeTrakerFromItem(Player player, ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return null; // Return early if itemStack is null or has no meta
        }

        ItemMeta itemMeta = itemStack.getItemMeta();
        PersistentDataContainer persistentDataContainer = itemMeta.getPersistentDataContainer();

        for (NamespacedKey key : persistentDataContainer.getKeys()) {
            if (!StatTrakPlugin.TRACKER_KEYS.contains(key)) continue;

            Traker traker = entityTrakerMap.get(key.getKey().toUpperCase());

            if (key.equals(bossMobTraker.getItemKey())) {
                traker = bossMobTraker;
            } else if (key.equals(stackerTracker.getItemKey())) {
                traker = stackerTracker;
            } else if (key.equals(fishStreakTraker.getItemKey())) {
                traker = fishStreakTraker;
            } else if (traker == null && armorTrakerMap.containsKey(key.getKey().toUpperCase())) {
                traker = armorTrakerMap.get(key.getKey().toUpperCase());
            } else if (traker == null && augmentTrakerMap.containsKey(key.getKey().toUpperCase())) {
                traker = augmentTrakerMap.get(key.getKey().toUpperCase());
            } else if (traker == null) {
                try {
                    Material material = Material.valueOf(key.getKey().toUpperCase());
                    traker = blockTrakerMap.get(material);
                } catch (IllegalArgumentException e) {
                    // Ignore invalid material keys
                }
            }

            if (traker == null) {
                return null; // If no tracker is found, return null
            }

            // Remove the tracker key from the persistent data container
            persistentDataContainer.remove(key);

            // Update the item's lore by removing the tracker-specific lore
            List<String> oldLore = itemMeta.hasLore() ? itemMeta.getLore() : new ArrayList<>();
            List<String> updatedLore = new ArrayList<>();
            for (String lore : oldLore) {
                if (!lore.startsWith(traker.getPrefixLore())) {
                    updatedLore.add(lore);
                }
            }

            // Set the updated lore and remove enchant flags
            itemMeta.setLore(updatedLore);
            itemMeta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);

            // Update the item's meta
            itemStack.setItemMeta(itemMeta);

            // Optionally return the tracker item to the player's inventory
            if (returnTrakerItem) {
                Map<Integer, ItemStack> leftOvers = player.getInventory().addItem(traker.getItemStack());
                if (!leftOvers.isEmpty()) {
                    leftOvers.values().forEach(item -> player.getWorld().dropItem(player.getLocation(), item));
                }
            }

            return itemStack; // Return the updated item after processing
        }

        return null; // If no tracker was removed, return null
    }

}
