package me.egg82.ae.events.enchants;

import java.util.*;
import me.egg82.ae.APIException;
import me.egg82.ae.api.AdvancedEnchantment;
import me.egg82.ae.api.BukkitEnchantableItem;
import me.egg82.ae.events.EventHolder;
import me.egg82.ae.services.entity.EntityItemHandler;
import me.egg82.ae.services.material.MaterialLookup;
import me.egg82.ae.utils.ItemDurabilityUtil;
import me.egg82.ae.utils.PermissionUtil;
import ninja.egg82.events.BukkitEventFilters;
import ninja.egg82.events.BukkitEvents;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;

public class SmeltingEvents extends EventHolder {
    private static final List<FurnaceRecipe> recipes = new ArrayList<>();

    private static final Optional<Material> redSand;

    static {
        redSand = MaterialLookup.get("RED_SAND");

        for (Iterator<Recipe> i = Bukkit.recipeIterator(); i.hasNext();) {
            Recipe r = i.next();
            if (r instanceof FurnaceRecipe) {
                if (redSand.isPresent() && ((FurnaceRecipe) r).getInput().getType() == redSand.get()) {
                    ((FurnaceRecipe) r).setInput(Material.SAND);
                }
                recipes.add((FurnaceRecipe) r);
            }
        }
    }

    public SmeltingEvents(Plugin plugin) {
        events.add(
                BukkitEvents.subscribe(plugin, BlockBreakEvent.class, EventPriority.NORMAL)
                        .filter(BukkitEventFilters.ignoreCancelled())
                        .filter(e -> e.getPlayer().getGameMode() != GameMode.CREATIVE)
                        .filter(e -> PermissionUtil.canUseEnchant(e.getPlayer(), "ae.enchant.smelting"))
                        .handler(this::blockBreak)
        );
    }

    private void blockBreak(BlockBreakEvent event) {
        EntityItemHandler entityItemHandler = getItemHandler();
        if (entityItemHandler == null) {
            return;
        }

        Optional<ItemStack> mainHand = entityItemHandler.getItemInMainHand(event.getPlayer());
        Collection<ItemStack> droppedItems = mainHand.isPresent() ? event.getBlock().getDrops(mainHand.get()) : new ArrayList<>();
        if (droppedItems.isEmpty()) {
            return;
        }

        BukkitEnchantableItem enchantableMainHand = BukkitEnchantableItem.fromItemStack(mainHand.get());

        boolean hasEnchantment;
        try {
            hasEnchantment = api.anyHasEnchantment(AdvancedEnchantment.SMELTING, enchantableMainHand);
        } catch (APIException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        if (!hasEnchantment) {
            return;
        }

        Location dropLoc = event.getBlock().getLocation();
        boolean isSmelted = false;

        for (ItemStack i : droppedItems) {
            Material type = i.getType();
            if (redSand.isPresent() && type == redSand.get()) {
                type = Material.SAND;
            }

            boolean dropped = false;

            for (FurnaceRecipe r : recipes) {
                if (type != r.getInput().getType()) {
                    continue;
                }

                short targetDurability = r.getInput().getDurability();
                if (targetDurability != Short.MAX_VALUE && targetDurability != Short.MIN_VALUE) {
                    if (i.getDurability() != targetDurability) {
                        continue;
                    }
                }

                ItemStack result = r.getResult().clone();
                result.setAmount(i.getAmount());
                event.getPlayer().getWorld().dropItemNaturally(dropLoc, result);
                dropped = true;
                break;
            }

            if (dropped) {
                isSmelted = true;
            }
        }

        if (isSmelted) {
            // Don't drop exp
            event.setCancelled(true);
            event.getBlock().setType(Material.AIR, true);

            if (!ItemDurabilityUtil.removeDurability(event.getPlayer(), enchantableMainHand, (isSmelted) ? 2 : 1, event.getPlayer().getLocation())) {
                entityItemHandler.setItemInMainHand(event.getPlayer(), null);
            }
        }
    }
}
