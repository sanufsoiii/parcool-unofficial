package com.alrex.parcool.common.block;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.block.zipline.IronZiplineHookBlock;
import com.alrex.parcool.common.block.zipline.WoodenZiplineHookBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.architectury.registry.registries.DeferredRegister;

public class Blocks {
    private static final DeferredRegister<Block> REGISTER = DeferredRegister.create(ParCool.MOD_ID, Registries.BLOCK);

    /**
     * 1.21.11's {@code BlockBehaviour} constructor resolves the loot table and the description id
     * from the properties, and both now require the block's registry key to be set
     * ({@code Objects.requireNonNull(this.id, "Block id not set")}) - NeoForge's
     * {@code DeferredRegister.Blocks} does that, Architectury's does not, so the key is set here.
     * It is the block's own registry id, i.e. exactly what the key would be.
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
            )
    );
    public static final RegistrySupplier<Block> IRON_ZIPLINE_HOOK = REGISTER.register(
            "iron_zipline_hook",
            () -> new IronZiplineHookBlock(BlockBehaviour.Properties
                    .of()
                    .setId(key("iron_zipline_hook"))
                    .mapColor(MapColor.METAL)
                    .strength(1.0f, 3.0f)
                    // Vanilla's spelling. NeoForge adds a correctly spelled noCollision() alias, but
                    // :common compiles against vanilla, where only the typo exists.
                    .noCollission()
                    .sound(SoundType.CHAIN)
            )
    );

    public static void registerAll() {
        REGISTER.register();
    }

}
