package com.alrex.parcool.common.block;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.block.zipline.ZiplineHookTileEntity;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

public class TileEntities {
    private static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create(ParCool.MOD_ID, Registries.BLOCK_ENTITY_TYPE);

    /**
     * 1.21.7 deleted {@code BlockEntityType.Builder} (it exists neither on Fabric nor on NeoForge in
     * this version - verified with javap against the mojmap 1.21.7 jar), and it never had a static
     * {@code register} either: what is left is the type's own package-private constructor.
     *
     * <p>It is called directly here. {@code BlockEntityType} and its nested
     * {@code BlockEntitySupplier} are not accessible from this package, so both are widened through
     * {@code parcool.accesswidener} (Fabric) and {@code accesstransformer.cfg} (NeoForge) - the same
     * per-loader split the rest of the mod uses, and the reason this port needs no platform seam for
     * the block entity type at all.
     *
     * <p>The set of valid blocks is what {@code BlockEntityType.Builder.of(factory, blocks...)} used
     * to wrap into an immutable set, spelled out so no collection helper is needed.
     */
    public static final RegistrySupplier<BlockEntityType<ZiplineHookTileEntity>> ZIPLINE_HOOK = REGISTER.register(
            "zipline_hook",
            () -> new BlockEntityType<>(
                    (pos, state) -> new ZiplineHookTileEntity(TileEntities.ZIPLINE_HOOK.get(), pos, state),
                    Set.of(Blocks.WOODEN_ZIPLINE_HOOK.get(), Blocks.IRON_ZIPLINE_HOOK.get())
            )
    );

    public static void registerAll() {
        REGISTER.register();
    }
}
