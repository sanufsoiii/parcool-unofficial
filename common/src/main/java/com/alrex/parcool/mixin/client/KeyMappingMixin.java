package com.alrex.parcool.mixin.client;

import com.alrex.parcool.compat.IKeyMappingDuck;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Implements {@link IKeyMappingDuck} by shadowing {@code KeyMapping#key}. */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin implements IKeyMappingDuck {

    // Private in 1.21.2 (verified with javap on the mojmap jar). A shadow may neither widen nor
    // narrow, so this has to track the field exactly; the 1.21.11 tree widened it to `protected`
    // because that version made the field protected.
    @Shadow
    protected InputConstants.Key key;

    @Override
    public InputConstants.Key parcool$getKey() {
        return this.key;
    }
}
