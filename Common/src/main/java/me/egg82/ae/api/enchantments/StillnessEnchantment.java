package me.egg82.ae.api.enchantments;

import java.util.Arrays;
import java.util.UUID;
import me.egg82.ae.api.AdvancedEnchantment;
import me.egg82.ae.api.AdvancedEnchantmentTarget;

public class StillnessEnchantment extends AdvancedEnchantment {
    public StillnessEnchantment() {
        super(UUID.randomUUID(), "stillness", "Stillness", false, 1, 1);
        targets.add(AdvancedEnchantmentTarget.TOOL);
        conflicts.addAll(Arrays.asList(AdvancedEnchantment.SMELTING, AdvancedEnchantment.MISFORTUNE_CURSE));
    }
}
