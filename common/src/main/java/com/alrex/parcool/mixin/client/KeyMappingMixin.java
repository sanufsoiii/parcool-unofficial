package com.alrex.parcool.mixin.client;

import com.alrex.parcool.compat.IKeyMappingDuck;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Implements {@link IKeyMappingDuck} by shadowing {@code KeyMapping#key}. */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin implements IKeyMappingDuck {

    // 1.21.6 still keeps `key` private (1.21.11 widened it to protected). A shadow may widen the
    // target's access but never narrow it, so `private` is the only declaration that is correct on
    // both: it matches 1.21.6's private field and would still be valid if the field were protected.
    @Shadow
    private InputConstants.Key key;

    @Override
    public InputConstants.Key parcool$getKey() {
        return this.key;
    }
}
