package com.alrex.parcool.common.potion;

/**
 * Guard for the one-shot brewing-mix registration.
 *
 * <p>On 1.20.1 vanilla keeps its brewing mixes in a plain static list ({@code PotionBrewing.POTION_MIXES})
 * that nothing ever rebuilds, but the method that appends to it - {@code PotionBrewing.addMix} - is
 * private, and the mixes have to be added once our potions exist, which is too late for a
 * static-initialiser hook: vanilla runs {@code PotionBrewing.bootStrap()} from
 * {@code Bootstrap.bootStrap()} <i>before</i> mods register anything on either loader (Forge reaches
 * mod construction from {@code Minecraft}'s constructor, Fabric from its entrypoints, both after
 * bootstrap). A tail-injection there would therefore have to resolve the potions while the registries
 * still hold nothing.
 *
 * <p>So the mixes are appended lazily, from {@code mixin.common.PotionBrewingMixin}, right before the
 * first {@code PotionBrewing} query that can observe them - the brewing stand asks both on the server
 * ({@code isIngredient}/{@code hasMix} while it ticks) and on the client (the ingredient slot's
 * {@code mayPlace}, which is why the client needs the mixes too). That mixin owns the private-method
 * invoker, this class only carries the "already done" flag so repeated queries stay cheap.
 *
 * <p>Both loaders run the same vanilla queries, so one hook covers them: on Forge the brewing stand is
 * diverted through {@code BrewingRecipeRegistry}, whose default {@code VanillaBrewingRecipe} delegates
 * straight back to {@code PotionBrewing.isIngredient} and {@code PotionBrewing.mix}.
 */
public final class ParCoolBrewingRecipe {

    private static boolean applied = false;

    private ParCoolBrewingRecipe() {
    }

    /**
     * @return {@code true} the first time it is called, {@code false} afterwards.
     */
    public static boolean markApplied() {
        if (applied) return false;
        applied = true;
        return true;
    }
}
