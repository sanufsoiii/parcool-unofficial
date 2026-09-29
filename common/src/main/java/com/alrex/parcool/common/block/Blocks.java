package com.alrex.parcool.common.block;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.block.zipline.IronZiplineHookBlock;
import com.alrex.parcool.common.block.zipline.WoodenZiplineHookBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.architectury.registry.registries.DeferredRegister;

public class Blocks {
    private static final DeferredRegister<Block> REGISTER = DeferredRegister.create(ParCool.MOD_ID, Registries.BLOCK);

    /**
     * The 1.21.2 rework added {@code BlockBehaviour.Properties#id} and both accessors that need it -
     * {@code getDrops} (the loot table) and {@code getDescriptionId} - now do
     * {@code Objects.requireNonNull(this.id, "Block id not set")}. NeoForge's
     * {@code DeferredRegister.Blocks} sets the key; Architectury's does not (verified against
     * architectury-fabric 14.0.4 and architectury 14.0.4, neither of which references
     * {@code setId} anywhere), so it is set here. It is the block's own registry id, i.e. exactly the
     * key the entry below is registered under.
     */
    private static ResourceKey<Block> key(String name) {
        return ResourceKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ParCool.MOD_ID, name));
    }

    public static final RegistrySupplier<Block> WOODEN_ZIPLINE_HOOK = REGISTER.register(
            "wooden_zipline_hook",
            () -> new WoodenZiplineHookBlock(BlockBehaviour.Properties
                    .of()
                    .setId(key("wooden_zipline_hook"))
                    .mapColor(MapColor.WOOD)
                    .strength(1.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
            )
    );

    public static final RegistrySupplier<Block> IRON_ZIPLINE_HOOK = REGISTER.register(
            "iron_zipline_hook",
            () -> new IronZiplineHookBlock(BlockBehaviour.Properties
                    .of()
                    .setId(key("iron_zipline_hook"))
                    .mapColor(MapColor.METAL)
                    .strength(1.0f, 3.0f)
                    .noCollission()
                    .sound(SoundType.CHAIN)
            )
    );

    public static void registerAll() {
        REGISTER.register();
    }
}
