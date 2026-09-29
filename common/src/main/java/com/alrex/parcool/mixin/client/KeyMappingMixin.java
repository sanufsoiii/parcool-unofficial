package com.alrex.parcool.mixin.client;

import com.alrex.parcool.compat.IKeyMappingDuck;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Implements {@link IKeyMappingDuck} by shadowing {@code KeyMapping#key}. */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin implements IKeyMappingDuck {

    @Shadow
    private InputConstants.Key key;

    @Override
    public InputConstants.Key parcool$getKey() {
        return this.key;
    }
}
