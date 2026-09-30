package com.alrex.parcool.common.item;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.block.Blocks;
import com.alrex.parcool.common.item.zipline.ZiplineRopeItem;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public class Items {
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ParCool.MOD_ID, Registries.ITEM);
    public static final RegistrySupplier<Item> WOODEN_ZIPLINE_HOOK = ITEMS.register("wooden_zipline_hook", () -> new BlockItem(Blocks.WOODEN_ZIPLINE_HOOK.get(), new Item.Properties()));
    public static final RegistrySupplier<Item> IRON_ZIPLINE_HOOK = ITEMS.register("iron_zipline_hook", () -> new BlockItem(Blocks.IRON_ZIPLINE_HOOK.get(), new Item.Properties()));
    public static final RegistrySupplier<Item> ZIPLINE_ROPE = ITEMS.register("zipline_rope", () -> new ZiplineRopeItem(new Item.Properties()));

	public static void registerAll() {
        ITEMS.register();
    }

}
