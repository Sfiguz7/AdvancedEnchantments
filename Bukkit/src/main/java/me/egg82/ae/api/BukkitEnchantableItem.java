package me.egg82.ae.api;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.*;
import java.util.concurrent.TimeUnit;
import me.egg82.ae.core.ItemData;
import me.egg82.ae.utils.ConfigUtil;
import me.egg82.ae.utils.EnchantmentUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BukkitEnchantableItem extends GenericEnchantableItem {
    private static Logger logger = LoggerFactory.getLogger(BukkitEnchantableItem.class);

    private static Cache<ItemData, BukkitEnchantableItem> cache = Caffeine.newBuilder().expireAfterAccess(5L, TimeUnit.MINUTES).build();
    private static LoadingCache<Material, Set<GenericEnchantmentTarget>> targetCache = Caffeine.newBuilder().build(k -> getTargetsExpensive(k));

    public static BukkitEnchantableItem fromItemStack(ItemStack item) {
        if (item == null) {
            return null;
        }

        return cache.get(getItemData(item), k -> new BukkitEnchantableItem(item)).clone(item);
    }

    public static void forceCache(ItemStack item, BukkitEnchantableItem enchantableItem) {
        if (item.hasItemMeta()) {
            cache.put(getItemData(item), enchantableItem);
        }
    }

    private static ItemData getItemData(ItemStack item) {
        ItemMeta meta = getMeta(item);
        if (meta == null) {
            return new ItemData();
        }

        return new ItemData(meta.getEnchants(), meta.hasLore() ? meta.getLore() : null, targetCache.get(item.getType()));
    }

    private ItemStack item;

    private BukkitEnchantableItem(ItemStack item) {
        super(item);

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Creating BukkitEnchantableItem for " + item);
        }

        this.item = item;
        targets.addAll(targetCache.get(item.getType()));
        enchantments.putAll(getBukkitEnchantments(item));
        enchantments.putAll(getAdvancedEnchantments(item));
        // TODO: Enforce conflicts (with vanilla enchants as well - except durability 0), accounting for forced enchants (somehow)
        souls = getNumSouls(item);
    }

    private BukkitEnchantableItem(ItemStack item, Set<GenericEnchantmentTarget> targets, Map<GenericEnchantment, Integer> enchantments, int souls) {
        super(item);
        this.item = item;
        this.targets.addAll(targets);
        this.enchantments.putAll(enchantments);
        // TODO: Enforce conflicts (with vanilla enchants as well - except durability 0), accounting for forced enchants (somehow)
        this.souls = souls;
    }

    private BukkitEnchantableItem clone(ItemStack item) { return new BukkitEnchantableItem(item, targets, enchantments, souls); }

    private static Set<GenericEnchantmentTarget> getTargetsExpensive(Material material) {
        Set<GenericEnchantmentTarget> retVal = new HashSet<>();
        for (EnchantmentTarget target : EnchantmentTarget.values()) {
            if (target.includes(material)) {
                if (ConfigUtil.getDebugOrFalse()) {
                    logger.info("Found target for " + material + ": " + target.name());
                }
                retVal.add(BukkitEnchantmentTarget.fromEnchantmentTarget(target));
            }
        }
        // Axes as weapons
        if (material.name().endsWith("_AXE")) {
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info("Created target for " + material + ": " + EnchantmentTarget.WEAPON.name());
            }
            retVal.add(BukkitEnchantmentTarget.fromEnchantmentTarget(EnchantmentTarget.WEAPON));
        }
        return retVal;
    }

    private static Map<GenericEnchantment, Integer> getBukkitEnchantments(ItemStack item) {
        Map<GenericEnchantment, Integer> retVal = new HashMap<>();
        for (Map.Entry<Enchantment, Integer> kvp : item.getEnchantments().entrySet()) {
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info("Found Bukkit enchant for " + item.getType() + ": " + EnchantmentUtil.getName(kvp.getKey()) + " " + getNumerals(kvp.getValue()));
            }
            retVal.put(BukkitEnchantment.fromEnchant(kvp.getKey()), kvp.getValue());
        }
        return retVal;
    }

    private static Map<GenericEnchantment, Integer> getAdvancedEnchantments(ItemStack item) {
        Map<GenericEnchantment, Integer> retVal = new HashMap<>();

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return retVal;
        }

        for (String line : meta.getLore()) {
            line = ChatColor.stripColor(line).trim();
            String[] split = line.split("\\s+");
            if (split.length <= 1) {
                continue;
            }

            String[] enchantName = Arrays.copyOf(split, split.length - 1, String[].class);
            Optional<AdvancedEnchantment> enchant = AdvancedEnchantment.getByName(String.join(" ", enchantName));
            if (!enchant.isPresent()) {
                continue;
            }

            Optional<Integer> level = getLevel(split[split.length - 1]);
            if (!level.isPresent()) {
                continue;
            }

            // TODO: Enforce level requirements, accounting for forced enchants (somehow)

            if (ConfigUtil.getDebugOrFalse()) {
                logger.info("Found AE enchant for " + item.getType() + ": " + enchant.get().getName() + " " + getNumerals(level.get()));
            }
            retVal.put(enchant.get(), level.get());
        }

        return retVal;
    }

    private static int getNumSouls(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return 0;
        }

        for (String line : meta.getLore()) {
            line = ChatColor.stripColor(line).trim();
            String[] split = line.split("\\s+");
            if (split.length <= 1) {
                continue;
            }

            if (!split[0].equals("Souls:")) {
                continue;
            }

            Optional<Integer> level = getLevel(split[split.length - 1]);
            if (!level.isPresent()) {
                continue;
            }

            if (ConfigUtil.getDebugOrFalse()) {
                logger.info("Found soul num for " + item.getType() + ": " + getNumerals(level.get()));
            }
            return level.get();
        }

        return 0;
    }

    private static Optional<Integer> getLevel(String numerals) {
        numerals = numerals.toUpperCase().replaceAll("[^MDCLXVIO]", "");
        if (numerals.isEmpty()) {
            return Optional.empty();
        }

        int retVal = 0;

        for (int i = 0; i < numerals.length(); i++) {
            int one = getValue(numerals.charAt(i));
            int two = i < numerals.length() - 1 ? getValue(numerals.charAt(i + 1)) : -1;

            if (one <= 0) {
                continue;
            }
            if (i < numerals.length() - 1 && two <= 0) {
                i++;
                continue;
            }

            if (one < two) {
                retVal += two - one;
                i++;
            } else {
                retVal += one;
            }
        }

        return Optional.of(retVal);
    }

    private static int getValue(char c) {
        switch (c) {
            case 'I':
                return 1;
            case 'V':
                return 5;
            case 'X':
                return 10;
            case 'L':
                return  50;
            case 'C':
                return 100;
            case 'D':
                return 500;
            case 'M':
                return 1000;
            default:
                return 0;
        }
    }

    public void setEnchantmentLevel(GenericEnchantment enchantment, int level) {
        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Setting enchant level for " + item + ": " + enchantment.getName() + " " + getNumerals(level));
        }
        super.setEnchantmentLevel(enchantment, level);
        rewriteEnchantMeta();
    }

    public void setEnchantmentLevels(Map<GenericEnchantment, Integer> enchantments) {
        if (ConfigUtil.getDebugOrFalse()) {
            if (enchantments != null) {
                for (Map.Entry<GenericEnchantment, Integer> kvp : enchantments.entrySet()) {
                    logger.info("Setting enchant level for " + item + ": " + kvp.getKey().getName() + " " + getNumerals(kvp.getValue()));
                }
            }
        }
        super.setEnchantmentLevels(enchantments);
        rewriteEnchantMeta();
    }

    public void addEnchantment(GenericEnchantment enchantment) {
        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Adding enchant for " + item + ": " + enchantment.getName() + " " + getNumerals(enchantment.getMinLevel()));
        }
        super.addEnchantment(enchantment);
        rewriteEnchantMeta();
    }

    public void addEnchantments(Collection<GenericEnchantment> enchantments) {
        if (ConfigUtil.getDebugOrFalse()) {
            if (enchantments != null) {
                for (GenericEnchantment enchantment : enchantments) {
                    logger.info("Adding enchant for " + item + ": " + (enchantment == null ? "null" : enchantment.getName() + getNumerals(enchantment.getMinLevel())));
                }
            }
        }
        super.addEnchantments(enchantments);
        rewriteEnchantMeta();
    }

    public void removeEnchantment(GenericEnchantment enchantment) {
        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Removing enchant for " + item + ": " + (enchantment == null ? "null" : enchantment.getName()));
        }
        super.removeEnchantment(enchantment);
        rewriteEnchantMeta();
    }

    public void removeEnchantments(Collection<GenericEnchantment> enchantments) {
        if (ConfigUtil.getDebugOrFalse()) {
            if (enchantments != null) {
                for (GenericEnchantment enchantment : enchantments) {
                    logger.info("Removing enchant for " + item + ": " + (enchantment == null ? "null" : enchantment.getName()));
                }
            }
        }
        super.removeEnchantments(enchantments);
        rewriteEnchantMeta();
    }

    public void setSouls(int souls) {
        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Setting souls for " + item + ": " + souls);
        }

        if (souls != this.souls) {
            super.setSouls(souls);
            rewriteSoulsMeta();
        }
    }

    public void rewriteEnchantMeta() {
        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Rewriting enchant meta for " + item.getType());
        }

        ItemMeta meta = getMeta(item);
        if (meta == null) {
            return;
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Resetting enchant meta for " + item.getType());
        }

        List<String> lore = !meta.hasLore() ? new ArrayList<>() : stripEnchantsAndSouls(meta.getLore()); // Remove all custom enchants from lore, we'll put them back later
        // Remove any Bukkit enchants that don't exist on the item any more
        for (Map.Entry<Enchantment, Integer> kvp : item.getEnchantments().entrySet()) {
            if (!enchantments.containsKey(BukkitEnchantment.fromEnchant(kvp.getKey()))) {
                meta.removeEnchant(kvp.getKey());
            }
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Rebuilding enchant meta for " + item.getType());
        }

        // Re-build enchant lists, souls, and lore
        Set<BukkitEnchantment> bukkitEnchants = new HashSet<>();
        Set<GenericEnchantment> otherEnchants = new HashSet<>();

        for (Map.Entry<GenericEnchantment, Integer> kvp : enchantments.entrySet()) {
            if (kvp.getKey() instanceof BukkitEnchantment) {
                if (ConfigUtil.getDebugOrFalse()) {
                    logger.info("Setting Bukkit enchant for " + item.getType() + ": " + kvp.getKey().getName() + " " + getNumerals(kvp.getValue()));
                }
                bukkitEnchants.add((BukkitEnchantment) kvp.getKey());
                meta.addEnchant((Enchantment) kvp.getKey().getConcrete(), kvp.getValue(), true);
            } else {
                if (ConfigUtil.getDebugOrFalse()) {
                    logger.info("Setting AE enchant for " + item.getType() + ": " + kvp.getKey().getName() + " " + getNumerals(kvp.getValue()));
                }
                otherEnchants.add(kvp.getKey());
                // Only add AE enchants to lore
                lore.add((kvp.getKey().isCurse() ? ChatColor.RED : ChatColor.GRAY) + kvp.getKey().getFriendlyName() + " " + getNumerals(kvp.getValue()));
            }
        }

        if (souls > 0) {
            lore.add(ChatColor.GRAY + "Souls: " + getNumerals(souls));
        }

        setShinyMeta(meta, lore, bukkitEnchants, otherEnchants);

        forceCache(item, this);
    }

    public void rewriteSoulsMeta() {
        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Rewriting soul meta for " + item.getType());
        }

        ItemMeta meta = getMeta(item);
        if (meta == null) {
            return;
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Resetting soul meta for " + item.getType());
        }

        List<String> lore = !meta.hasLore() ? new ArrayList<>() : stripSouls(meta.getLore()); // Remove souls from lore, we'll put them back later

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Rebuilding soul meta for " + item.getType());
        }

        // Re-build enchant lists, souls and lore
        Set<BukkitEnchantment> bukkitEnchants = new HashSet<>();
        Set<GenericEnchantment> otherEnchants = new HashSet<>();

        for (Map.Entry<GenericEnchantment, Integer> kvp : enchantments.entrySet()) {
            if (kvp.getKey() instanceof BukkitEnchantment) {
                bukkitEnchants.add((BukkitEnchantment) kvp.getKey());
            } else {
                otherEnchants.add(kvp.getKey());
            }
        }

        if (souls > 0) {
            lore.add(ChatColor.GRAY + "Souls: " + getNumerals(souls));
        }

        setShinyMeta(meta, lore, bukkitEnchants, otherEnchants);

        forceCache(item, this);
    }

    private void setShinyMeta(ItemMeta meta, List<String> lore, Set<BukkitEnchantment> bukkitEnchants, Set<GenericEnchantment> otherEnchants) {
        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Ensuring shiny meta for " + item.getType());
        }

        boolean hasBukkitEnchants = false;
        boolean hasHackyEnchant = false;
        for (BukkitEnchantment bukkitEnchant : bukkitEnchants) {
            if (bukkitEnchant.getConcrete().equals(Enchantment.DURABILITY) && enchantments.get(bukkitEnchant) == 0) {
                hasHackyEnchant = true;
            } else {
                hasBukkitEnchants = true;
            }
        }

        // TODO: Get ProtocolLib working?
        /*if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
            if (hasHackyEnchant) {
                enchantments.remove(BukkitEnchantment.fromEnchant(Enchantment.DURABILITY));
                meta.removeEnchant(Enchantment.DURABILITY);
                meta.removeItemFlags(ItemFlag.HIDE_POTION_EFFECTS, ItemFlag.HIDE_ENCHANTS);
                hasHackyEnchant = false;
            }

            meta.setLore(lore);
            item.setItemMeta(meta);

            if (!hasBukkitEnchants) {
                if (!otherEnchants.isEmpty() || souls > 0) {
                    ProtocolLibHook.setGlowing(item);
                } else {
                    ProtocolLibHook.removeGlowing(item);
                }
            }
        } else {*/
        // All this does is ensure we have a "shiny" item while keeping the item as "pure" as possible
        if (hasBukkitEnchants) {
            if (hasHackyEnchant) {
                enchantments.remove(BukkitEnchantment.fromEnchant(Enchantment.DURABILITY));
                meta.removeEnchant(Enchantment.DURABILITY);
                hasHackyEnchant = false;
            }
            meta.removeItemFlags(ItemFlag.HIDE_POTION_EFFECTS, ItemFlag.HIDE_ENCHANTS);
        } else {
            if ((!otherEnchants.isEmpty() || souls > 0) && !hasHackyEnchant) {
                enchantments.put(BukkitEnchantment.fromEnchant(Enchantment.DURABILITY), 0);
                meta.addEnchant(Enchantment.DURABILITY, 0, true);
                hasHackyEnchant = true;
            }

            if (hasHackyEnchant) {
                if (!otherEnchants.isEmpty() || souls > 0) {
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    meta.removeItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
                } else {
                    enchantments.remove(BukkitEnchantment.fromEnchant(Enchantment.DURABILITY));
                    meta.removeEnchant(Enchantment.DURABILITY);
                    hasHackyEnchant = false;
                }
            }
        }

        if (!hasHackyEnchant) {
            meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        //}
    }

    private static List<String> stripEnchantsAndSouls(List<String> lore) {
        List<String> retVal = new ArrayList<>();

        for (String line : lore) {
            String newLine = ChatColor.stripColor(line).trim();
            String[] split = newLine.split("\\s+");
            if (split.length <= 1) {
                retVal.add(line);
                continue;
            }

            if (split[0].equals("Souls:")) {
                continue;
            }

            String[] enchantName = Arrays.copyOf(split, split.length - 1, String[].class);
            Optional<AdvancedEnchantment> enchant = AdvancedEnchantment.getByName(String.join(" ", enchantName));
            if (enchant.isPresent()) {
                continue;
            }

            retVal.add(line);
        }

        return retVal;
    }

    private static List<String> stripSouls(List<String> lore) {
        List<String> retVal = new ArrayList<>();

        for (String line : lore) {
            String newLine = ChatColor.stripColor(line).trim();
            String[] split = newLine.split("\\s+");
            if (split.length <= 1) {
                retVal.add(line);
                continue;
            }

            if (split[0].equals("Souls:")) {
                continue;
            }

            retVal.add(line);
        }

        return retVal;
    }

    private static ItemMeta getMeta(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            meta = Bukkit.getItemFactory().getItemMeta(item.getType());
            item.setItemMeta(meta);
        }
        return meta;
    }

    private static String getNumerals(int level) {
        if (level <= 0) {
            return "O";
        }

        StringBuilder retVal = new StringBuilder();

        while (level >= 1000) {
            retVal.append('M');
            level -= 1000;
        }
        while (level >= 900) {
            retVal.append("CM");
            level -= 900;
        }
        while (level >= 500) {
            retVal.append('D');
            level -= 500;
        }
        while (level >= 400) {
            retVal.append("CD");
            level -= 400;
        }
        while (level >= 100) {
            retVal.append('C');
            level -= 100;
        }
        while (level >= 90) {
            retVal.append("XC");
            level -= 90;
        }
        while (level >= 50) {
            retVal.append('L');
            level -= 50;
        }
        while (level >= 40) {
            retVal.append("XL");
            level -= 40;
        }
        while (level >= 10) {
            retVal.append('X');
            level -= 10;
        }
        while (level >= 9) {
            retVal.append("IX");
            level -= 9;
        }
        while (level >= 5) {
            retVal.append('V');
            level -= 5;
        }
        while (level >= 4) {
            retVal.append("IV");
            level -= 4;
        }
        while (level >= 1) {
            retVal.append('I');
            level -= 1;
        }

        return retVal.toString();
    }

    public int hashCode() { return Objects.hash(getItemData(item)); }
}
