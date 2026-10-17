package com.alrex.parcool.client.registry;

import com.alrex.parcool.common.item.Items;
import com.alrex.parcool.common.item.zipline.ZiplineRopeItem;
import dev.architectury.registry.client.rendering.ColorHandlerRegistry;

/**
 * Client-only item colour registration.
 *
 * <h2>Why this is not a method on {@link Items}</h2>
 * {@code Items} is loaded on a dedicated server (it holds the item registry), so its methods are
 * verified there too. A method that hands a client-only value to a client-only parameter type —
 * {@code ColorHandlerRegistry.registerItemColors(ItemColor, ...)} — forces the verifier to load
 * {@code ZiplineRopeItem.RopeColor} and therefore {@code net.minecraft.client.color.item.ItemColor},
 * and NeoForge's {@code RuntimeDistCleaner} aborts the server with
 * "Attempted to load class ... for invalid dist DEDICATED_SERVER". Keeping the call in a class that
 * only the client ever loads removes that verification edge.
 */
public final class ItemColors {

    private ItemColors() {
    }

    public static void register() {
        ColorHandlerRegistry.registerItemColors(new ZiplineRopeItem.RopeColor(), Items.ZIPLINE_ROPE);
    }
}
