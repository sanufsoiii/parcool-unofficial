package com.alrex.parcool.common.block;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.block.zipline.IronZiplineHookBlock;
import com.alrex.parcool.common.block.zipline.WoodenZiplineHookBlock;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.registries.Registries;
import dev.architectury.registry.registries.DeferredRegister;

public class Blocks {
    private static final DeferredRegister<Block> REGISTER = DeferredRegister.create(ParCool.MOD_ID, Registries.BLOCK);
    public static final RegistrySupplier<Block> WOODEN_ZIPLINE_HOOK = REGISTER.register(
            "wooden_zipline_hook",
            () -> new WoodenZiplineHookBlock(BlockBehaviour.Properties
                    .of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.0f, 3.0f)
                    .sound(SoundType.WOOD)
            )
    );
    public static final RegistrySupplier<Block> IRON_ZIPLINE_HOOK = REGISTER.register(
            "iron_zipline_hook",
            () -> new IronZiplineHookBlock(BlockBehaviour.Properties
                    .of()
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
