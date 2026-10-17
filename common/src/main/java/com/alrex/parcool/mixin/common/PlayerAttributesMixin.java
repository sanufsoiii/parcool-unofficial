package com.alrex.parcool.mixin.common;

import com.alrex.parcool.api.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds ParCool's two entity attributes to the player.
 *
 * <h2>Why a mixin instead of a loader registration</h2>
 * <ul>
 *     <li>Upstream uses {@code EntityAttributeModificationEvent}, which is Forge-only.</li>
 *     <li>Architectury's cross-loader {@code EntityAttributeRegistry} cannot be used either: it routes
 *     through {@code DefaultAttributeRegistry.register}, which <i>replaces</i> the supplier instead of
 *     extending it - the player would then be missing {@code minecraft:generic.max_health} and joining
 *     a world fails with "Can't find attribute minecraft:generic.max_health".</li>
 * </ul>
 *
 * Extending the builder that {@code Player#createAttributes} already assembles is additive, keeps
 * every vanilla attribute, and behaves identically on both loaders. The attributes themselves are
 * registered by {@link Attributes}; this hook runs later than that registration on both loaders
 * (Fabric registers while the built-in registries are still writable, Forge on {@code RegisterEvent},
 * which precedes {@code DefaultAttributes}' initialisation that calls this method).
 */
@Mixin(Player.class)
public abstract class PlayerAttributesMixin {

    @Inject(method = "createAttributes", at = @At("RETURN"))
    private static void parcool$addParCoolAttributes(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
        cir.getReturnValue()
                .add(Attributes.MAX_STAMINA.get())
                .add(Attributes.STAMINA_RECOVERY.get());
    }
}
