package com.alrex.parcool.common.item;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.block.Blocks;
import com.alrex.parcool.common.item.zipline.ZiplineRopeItem;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public class Items {
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ParCool.MOD_ID, Registries.ITEM);

    /**
     * 1.21.10's {@code Item} constructor resolves the description id from the properties and that
     * requires the item's registry key to be set
     * ({@code Objects.requireNonNull(this.id, "Item id not set")}) - NeoForge's
     * {@code DeferredRegister.Items} does that, Architectury's does not, so the key is set here. It is
     * the item's own registry id, i.e. exactly what the key would be.
     */
    private static Item.Properties properties(String name) {
        return new Item.Properties().setId(ResourceKey.create(
                Registries.ITEM, ResourceLocation.fromNamespaceAndPath(ParCool.MOD_ID, name)));
    }

    /**
     * The two hook items.
     *
     * <p>{@code useBlockDescriptionPrefix()} is what 1.21.2 added and what 1.21.1 got for free from
     * {@code BlockItem#getDescriptionId} delegating to the block: the description id is now resolved
     * from the {@link Item.Properties} alone, so without this call it would be
     * {@code item.parcool.wooden_zipline_hook} and every hook - name in the GUI, in the creative tab,
     * in the recipe book and in every language file - would be one key off.
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
