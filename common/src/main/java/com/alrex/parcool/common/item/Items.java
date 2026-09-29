package com.alrex.parcool.common.item;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.block.Blocks;
import com.alrex.parcool.common.item.zipline.ZiplineRopeItem;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public class Items {
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ParCool.MOD_ID, Registries.ITEM);

    /**
     * The 1.21.2 rework added {@code Item.Properties#id}, and the {@link Item} constructor resolves
     * the description id from the properties through
     * {@code Objects.requireNonNull(this.id, "Item id not set")} - so an item built from a bare
     * {@code new Item.Properties()} throws before it is even registered. NeoForge's
     * {@code DeferredRegister.Items} sets the key; Architectury's does not (verified against
     * architectury-fabric 14.0.4 and architectury 14.0.4, neither of which references {@code setId}
     * anywhere), so it is set here. It is the item's own registry id, i.e. exactly the key the entry
     * is registered under.
     */
    private static Item.Properties properties(String name) {
        return new Item.Properties().setId(ResourceKey.create(
                Registries.ITEM, ResourceLocation.fromNamespaceAndPath(ParCool.MOD_ID, name)));
    }

    /**
     * The two hooks also drop the block description prefix. The 1.21.2 rework removed
     * {@code BlockItem#getDescriptionId()}, which used to delegate to the block, and
     * {@link Item#descriptionId} is now a final field resolved at construction; without this the two
     * hooks would be named {@code item.parcool.wooden_zipline_hook} and the upstream lang files' ten
     * {@code block.parcool.*} keys would be dead.
     */
    private static Item.Properties blockItemProperties(String name) {
        return properties(name).useBlockDescriptionPrefix();
    }

    public static final RegistrySupplier<Item> WOODEN_ZIPLINE_HOOK = ITEMS.register("wooden_zipline_hook", () -> new BlockItem(Blocks.WOODEN_ZIPLINE_HOOK.get(), blockItemProperties("wooden_zipline_hook")));
    public static final RegistrySupplier<Item> IRON_ZIPLINE_HOOK = ITEMS.register("iron_zipline_hook", () -> new BlockItem(Blocks.IRON_ZIPLINE_HOOK.get(), blockItemProperties("iron_zipline_hook")));
    public static final RegistrySupplier<Item> ZIPLINE_ROPE = ITEMS.register("zipline_rope", () -> new ZiplineRopeItem(properties("zipline_rope")));

	public static void registerAll() {
        ITEMS.register();
    }

}
