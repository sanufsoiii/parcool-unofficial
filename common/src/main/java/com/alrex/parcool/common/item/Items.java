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
     * {@code setId(...)} is mandatory from 1.21.2 on, not merely from 1.21.4. {@code Item}'s
     * constructor resolves the description id and the item model straight out of the properties
     * ({@code Item$Properties#effectiveDescriptionId()} and {@code #effectiveModel()}), and both end
     * in {@code Objects.requireNonNull(this.id, "Item id not set")} - so an item built without the
     * key throws a {@link NullPointerException} inside {@code ITEMS.register(...)}, i.e. while the
     * registry is being filled, not later when something asks the item for its name. Vanilla sets the
     * key in {@code Items.registerBlock} and in the bootstrap, NeoForge's
     * {@code DeferredRegister.Items} sets it too, Architectury's {@link DeferredRegister} does not
     * (there is not a single reference to {@code setId} in architectury-fabric 14.0.4 or 16.1.4), so
     * the item's own registry key is supplied here. The name is the item's own id.
     *
     * <p>Do not "clean this up" as redundant: the {@code register} call right next to it knows the
     * same string but never passes it down to the properties. Removing this turns a green build into
     * a crash on startup.
     */
    private static Item.Properties properties(String name) {
        return new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM,
                        ResourceLocation.fromNamespaceAndPath(ParCool.MOD_ID, name)));
    }

    /**
     * The block-item half of the same 1.21.2 change. The description id stopped being a
     * {@code BlockItem#getDescriptionId()} override - on 1.21.2 that method is gone and
     * {@code Item#getDescriptionId()} is {@code final} - and the block-vs-item distinction moved into
     * {@code Item.Properties}. Vanilla reaches {@code block.<id>} through {@code Items.registerBlock},
     * which Architectury's register does not call, so without the prefix the two hooks would be named
     * {@code item.parcool.wooden_zipline_hook} / {@code item.parcool.iron_zipline_hook} - keys that
     * no lang file in this mod defines (the shipped files carry {@code block.parcool.*}, and only 4
     * of the 11 lang files carry even those: en_us, ja_jp, zh_cn, zh_tw), so their names would
     * silently degrade to the raw key. The 1.21.1 port got the same names from the override 1.21.2
     * deleted, which is why the flag has to be set explicitly here.
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
