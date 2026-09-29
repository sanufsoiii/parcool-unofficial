package com.alrex.parcool.common.potion;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Was registered through {@code RegisterBrewingRecipesEvent}, which only exists on NeoForge 21.1
 * (where brewing is data driven) and had no Fabric counterpart at all — on Fabric, 1.21.1 brewing
 * mixes are plain {@link PotionBrewing.Builder} entries.
 * <p>
 * The mixes are now applied through {@link #addMixes(PotionBrewing.Builder)}, which is invoked from
 * {@code mixin.common.PotionBrewingBuilderMixin} on {@code PotionBrewing.Builder#build} TAIL. That
 * hook is vanilla and therefore behaves identically on both loaders.
 */
public class ParCoolBrewingRecipe {
    public static void addMixes(PotionBrewing.Builder builder) {
        builder.addMix(Potions.AWKWARD, Items.POISONOUS_POTATO, com.alrex.parcool.common.potion.Potions.POOR_ENERGY_DRINK);
        builder.addMix(Potions.AWKWARD, Items.CHICKEN, com.alrex.parcool.common.potion.Potions.POOR_ENERGY_DRINK);
        builder.addMix(Potions.AWKWARD, Items.QUARTZ, com.alrex.parcool.common.potion.Potions.ENERGY_DRINK);
        builder.addMix(com.alrex.parcool.common.potion.Potions.POOR_ENERGY_DRINK, Items.QUARTZ, com.alrex.parcool.common.potion.Potions.ENERGY_DRINK);
    }
}
