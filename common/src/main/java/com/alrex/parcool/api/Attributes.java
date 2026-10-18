package com.alrex.parcool.api;

import com.alrex.parcool.ParCool;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * ParCool's two player attributes.
 *
 * <p>They are attached to the player by {@code mixin.common.PlayerAttributesMixin}, which extends the
 * builder {@code Player#createAttributes} assembles. That is additive on both loaders, whereas the
 * cross-loader {@code EntityAttributeRegistry} calls {@code DefaultAttributeRegistry.register}, which
 * <i>replaces</i> the player's whole supplier - costing the player {@code minecraft:generic.max_health}
 * and failing the world join - so the attachment stays in the mixin.
 *
 * <h2>The attribute instances exist before they are registered</h2>
 * The two {@link Attribute} objects are plain fields, and the mixin adds <i>those</i> to the player
 * builder instead of reading the registry entry back. The reason is an ordering trap that only 1.20.1
 * has:
 * <ul>
 *     <li>{@code Player#createAttributes} is called from {@code DefaultAttributes'}<i> static
 *     initialiser</i>, which {@code Bootstrap#validate} triggers at the very end of the bootstrap
 *     pass - {@code mixin.common.PlayerAttributesMixin} therefore runs while the game is still
 *     starting.</li>
 *     <li>Fabric invokes mod entry points <i>after</i> {@code Bootstrap#bootStrap} and
 *     {@code Bootstrap#validate} (its entry point hook is injected just before
 *     {@code Util#startTimerHackThread}), so neither loader entry point has run when the mixin
 *     fires. Reading {@code MAX_STAMINA.get()} there is a hard
 *     {@code NullPointerException: Registry Object not present: parcool:max_stamina}, which is exactly
 *     how this port first failed to boot.</li>
 * </ul>
 * Adding the raw instances sidesteps that completely: the player's attribute supplier is built from
 * objects that exist as soon as this class is initialised, and the same objects are what the
 * {@link DeferredRegister} hands to the registry later ({@code RegisterEvent} on Forge, the loader
 * entry point on Fabric - both still before any world or player exists).
 *
 * <p>On 1.21.1 the same two entries had to be registered by hand from the loader entry point, because
 * NeoForge's {@code Registry} refuses any write from the shared entry point and Architectury's
 * {@code DeferredRegister} rejected {@code Registries.ATTRIBUTE} outright. Neither is true on 1.20.1:
 * {@code minecraft:attribute} is backed by a Forge registry ({@code ForgeRegistries.ATTRIBUTES}) and
 * Architectury 9.2.14 registers into it through {@code RegisterEvent} on Forge and through
 * {@code Registry.register} on Fabric. The attributes are therefore an ordinary
 * {@link DeferredRegister} here, and {@code ParCool#init} calls {@link #registerAll()} first of all.
 */
public class Attributes {
    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ParCool.MOD_ID, Registries.ATTRIBUTE);

    public static final Attribute MAX_STAMINA_ATTRIBUTE =
            new RangedAttribute("parcool.max_stamina", 2000, 10, 10000).setSyncable(true);

    public static final Attribute STAMINA_RECOVERY_ATTRIBUTE =
            new RangedAttribute("parcool.stamina_recovery", 20, 0, 10000).setSyncable(true);

    public static final RegistrySupplier<Attribute> MAX_STAMINA =
            ATTRIBUTES.register("max_stamina", () -> MAX_STAMINA_ATTRIBUTE);

    public static final RegistrySupplier<Attribute> STAMINA_RECOVERY =
            ATTRIBUTES.register("stamina_recovery", () -> STAMINA_RECOVERY_ATTRIBUTE);

    private static boolean registered;

    /**
     * Hands the two entries to the registry. Idempotent: {@code DeferredRegister#register} is a hard
     * failure the second time it is called, and both {@code ParCool#init} and the loader entry points
     * reach this.
     */
    public static void registerAll() {
        if (registered) {
            return;
        }
        registered = true;
        ATTRIBUTES.register();
    }
}
