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

    // The 1.21.2 rework removed BlockItem#getDescriptionId(), which used to delegate to the block, and
    // Item#descriptionId is now a final field resolved from Item.Properties at construction. The
    // upstream lang files still ship `block.parcool.wooden_zipline_hook` / `block.parcool.iron_zipline_hook`,
    // so the two BlockItems have to ask for the block prefix explicitly - otherwise their names come
    // out as `item.parcool.*`, the two hooks show up as "Item.iron_zipline_hook" in the GUI and the
    // translation keys in every one of the eleven lang files are dead.
    public static final RegistrySupplier<Item> WOODEN_ZIPLINE_HOOK = ITEMS.register("wooden_zipline_hook", () -> new BlockItem(Blocks.WOODEN_ZIPLINE_HOOK.get(), new Item.Properties().useBlockDescriptionPrefix()));
    public static final RegistrySupplier<Item> IRON_ZIPLINE_HOOK = ITEMS.register("iron_zipline_hook", () -> new BlockItem(Blocks.IRON_ZIPLINE_HOOK.get(), new Item.Properties().useBlockDescriptionPrefix()));
    public static final RegistrySupplier<Item> ZIPLINE_ROPE = ITEMS.register("zipline_rope", () -> new ZiplineRopeItem(new Item.Properties()));

	public static void registerAll() {
        ITEMS.register();
    }

}
