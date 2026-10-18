package com.alrex.parcool.mixin.common;

import com.alrex.parcool.common.potion.ParCoolBrewingRecipe;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Appends ParCool's brewing mixes to vanilla's brewing table.
 *
 * <p>Replaces the 1.21.1 version, which hooked {@code PotionBrewing.Builder#build} - a type that does
 * not exist on 1.20.1, where the mixes are a static list filled by {@code PotionBrewing.bootStrap()}
 * and the append helper {@code addMix(Potion, Item, Potion)} is private.
 *
 * <h2>Why the mixes are added from a query instead of from {@code bootStrap}</h2>
 * {@code Bootstrap.bootStrap()} runs before mods register anything on both loaders: Forge reaches mod
 * construction from {@code Minecraft}'s constructor, Fabric from its entrypoints, and vanilla calls
 * {@code PotionBrewing.bootStrap()} inside bootstrap. Adding the mixes at the tail of that method would
 * therefore resolve ParCool's potions while their registry entries do not exist yet. Both queries this
 * mixin guards, on the other hand, can only run once a player or a brewing stand touches brewing, long
 * after registration, and the {@code ParCoolBrewingRecipe.markApplied()} flag keeps it one-shot.
 *
 * <p>{@code isIngredient} covers the client's ingredient-slot check and Forge's diverted
 * {@code BrewingRecipeRegistry} path; {@code hasMix} covers vanilla's "can this stand brew" check.
 * {@code mix} always runs after one of the two in both paths.
 */
@Mixin(PotionBrewing.class)
public abstract class PotionBrewingMixin {

    @Inject(method = "isIngredient", at = @At("HEAD"))
    private static void parcool$addBrewingMixes(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        parcool$addBrewingMixes();
    }

    @Inject(method = "hasMix", at = @At("HEAD"))
    private static void parcool$addBrewingMixes(ItemStack ingredient, ItemStack input, CallbackInfoReturnable<Boolean> cir) {
        parcool$addBrewingMixes();
    }

    private static void parcool$addBrewingMixes() {
        if (!ParCoolBrewingRecipe.markApplied()) return;
        // Poor quality energy drink: awkward potion + poisonous potato or chicken.
        parcool$addMix(net.minecraft.world.item.alchemy.Potions.AWKWARD, net.minecraft.world.item.Items.POISONOUS_POTATO, com.alrex.parcool.common.potion.Potions.POOR_ENERGY_DRINK.get());
        parcool$addMix(net.minecraft.world.item.alchemy.Potions.AWKWARD, net.minecraft.world.item.Items.CHICKEN, com.alrex.parcool.common.potion.Potions.POOR_ENERGY_DRINK.get());
        // Energy drink: awkward potion or poor quality energy drink, both with quartz.
        parcool$addMix(net.minecraft.world.item.alchemy.Potions.AWKWARD, net.minecraft.world.item.Items.QUARTZ, com.alrex.parcool.common.potion.Potions.ENERGY_DRINK.get());
        parcool$addMix(com.alrex.parcool.common.potion.Potions.POOR_ENERGY_DRINK.get(), net.minecraft.world.item.Items.QUARTZ, com.alrex.parcool.common.potion.Potions.ENERGY_DRINK.get());
    }

    /**
     * {@code PotionBrewing#addMix} is private on 1.20.1. The invoker is called from this same mixin,
     * which is where Mixin resolves the declared method to the target's private one; the throwing body
     * is never reached.
     */
    @Invoker("addMix")
    private static void parcool$addMix(Potion input, Item ingredient, Potion output) {
        throw new AssertionError("mixin generated invoker was not applied");
    }
}
