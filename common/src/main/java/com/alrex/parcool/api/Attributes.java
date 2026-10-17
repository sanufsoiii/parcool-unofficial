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
 * and failing the world join.
 *
 * <p>On 1.21.1 the two entries had to be registered by hand from the loader entry point, because
 * NeoForge's {@code Registry} refuses any write from the shared entry point and Architectury's
 * {@code DeferredRegister} rejected {@code Registries.ATTRIBUTE} outright. Neither is true on 1.20.1:
 * {@code minecraft:attribute} is backed by a Forge registry ({@code ForgeRegistries.ATTRIBUTES}) and
 * Architectury 9.2.14 registers into it through {@code RegisterEvent} on Forge and through
 * {@code Registry.register} on Fabric, which is exactly what the mod's other registries do. The
 * attributes are therefore an ordinary {@link DeferredRegister} here.
 *
 * <p>{@code EntityAttributeModificationEvent} (what upstream ParCool uses on 1.20.1) is Forge-only, so
 * the attachment stays in the mixin, which behaves identically on both loaders.
 */
public class Attributes {
    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ParCool.MOD_ID, Registries.ATTRIBUTE);

    public static final RegistrySupplier<Attribute> MAX_STAMINA =
            ATTRIBUTES.register("max_stamina", () -> new RangedAttribute("parcool.max_stamina", 2000, 10, 10000).setSyncable(true));

    public static final RegistrySupplier<Attribute> STAMINA_RECOVERY =
            ATTRIBUTES.register("stamina_recovery", () -> new RangedAttribute("parcool.stamina_recovery", 20, 0, 10000).setSyncable(true));

    public static void registerAll() {
        ATTRIBUTES.register();
    }
}
