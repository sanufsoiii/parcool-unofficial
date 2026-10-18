package com.alrex.parcool.client;

import com.alrex.parcool.ParCool;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only NeoForge entrypoint.
 * <p>
 * Architectury's {@code InitEvent.Client} is the loader-agnostic hook, but registering a client-only
 * hook keeps the entry points symmetric with the Fabric module and avoids pulling the
 * architectury-neoforge client event bridge into {@code common}.
 * <p>
 * 1.20.6 puts this on a second {@code @Mod} class annotated {@code dist = Dist.CLIENT}; NeoForge
 * 20.5's {@code @Mod} has no {@code dist} attribute yet, so the client-only hook has to be an event
 * subscriber instead - FML only registers {@code Dist.CLIENT} subscribers on the physical client,
 * which is exactly the guarantee the {@code dist} attribute gives on 1.20.6.
 */
@EventBusSubscriber(modid = ParCool.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ParCoolNeoForgeClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ParCool.initClient();
    }
}
