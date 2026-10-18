package com.alrex.parcool;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge entrypoint.
 *
 * <p>The mod event bus has to be handed to Architectury <i>before</i> {@link ParCool#init()} runs:
 * the first {@code DeferredRegister.create(...)} in {@code :common} resolves the bus through
 * Architectury's {@code EventBuses}, and that lookup throws
 * ({@code Can't get event bus for mod 'parcool'}) until {@code registerModEventBus} has been called.
 * The registration itself happens later, when Forge fires {@code RegisterEvent} on this bus - which
 * is also why nothing in {@code :common} may resolve a {@code RegistrySupplier} during construction.
 */
@Mod(ParCool.MOD_ID)
public class ParCoolForge {

    public ParCoolForge() {
        EventBuses.registerModEventBus(ParCool.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        ParCool.init();
        // Does not load ParCoolForgeClient on a dedicated server: the supplier is only evaluated when
        // the physical side is the client.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ParCoolForgeClient::init);
    }
}
