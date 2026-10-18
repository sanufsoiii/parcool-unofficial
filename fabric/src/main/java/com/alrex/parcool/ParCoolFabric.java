package com.alrex.parcool;

import net.fabricmc.api.ModInitializer;

/** Fabric {@code main} entrypoint. */
public class ParCoolFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ParCool.init();
    }
}
