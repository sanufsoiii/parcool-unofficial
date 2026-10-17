package com.alrex.parcool.common.item;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.item.zipline.ZiplineRopeItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.architectury.registry.registries.DeferredRegister;

import java.util.Arrays;

public class CreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(ParCool.MOD_ID, Registries.CREATIVE_MODE_TAB);
    public static final RegistrySupplier<CreativeModeTab> ITEMS = TABS.register("items", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .icon(() -> new ItemStack(Items.IRON_ZIPLINE_HOOK.get()))
            .title(Component.translatable("itemGroup.ParCool"))
            .hideTitle()
            .displayItems((params, output) -> {
                output.accept(Items.IRON_ZIPLINE_HOOK.get());
                output.accept(Items.WOODEN_ZIPLINE_HOOK.get());
                output.accept(Items.ZIPLINE_ROPE.get());
                // DyeColor only exposes float rgb components on 1.20.1 (getTextureDiffuseColor, the
                // packed-int accessor, arrives in 1.21.x), so they are packed here. The alpha is
                // forced opaque: a rope colour without alpha renders transparent through ItemColor.
                Arrays.stream(DyeColor.values())
                        .map(color -> {
                            float[] rgb = color.getTextureDiffuseColors();
                            var coloredRope = new ItemStack(Items.ZIPLINE_ROPE.get());
                            ZiplineRopeItem.setColor(coloredRope, 0xFF000000
                                    | ((int) (rgb[0] * 255f) << 16)
                                    | ((int) (rgb[1] * 255f) << 8)
                                    | (int) (rgb[2] * 255f));
                            return coloredRope;
                        })
                        .forEach(output::accept);
            })
            .build()
    );

    public static void registerAll() {
        TABS.register();
    }
}
