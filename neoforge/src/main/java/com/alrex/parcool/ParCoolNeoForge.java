package com.alrex.parcool;

import com.alrex.parcool.registry.NeoForgeAttributes;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** NeoForge entrypoint. */
@Mod(ParCool.MOD_ID)
public class ParCoolNeoForge {

    public ParCoolNeoForge(ModContainer container) {
        // Must run before ParCool.init(): the built-in attribute registry is read by
        // common/api/Attributes, and NeoForge freezes BuiltInRegistries before this constructor is
        // reached, so the two entries are owned by this module's DeferredRegister instead.
        NeoForgeAttributes.register(container.getEventBus());
        ParCool.init();
    }
}
