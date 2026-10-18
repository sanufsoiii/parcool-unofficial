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
 * every vanilla attribute, and behaves identically on both loaders.
 *
 * <p>The raw {@link Attributes#MAX_STAMINA_ATTRIBUTE} / {@link Attributes#STAMINA_RECOVERY_ATTRIBUTE}
 * instances are added rather than the {@link net.minecraft.world.entity.ai.attributes.Attribute}s read
 * back out of the registry: this method runs from {@code DefaultAttributes'} static initialiser,
 * which the bootstrap pass reaches <i>before</i> any loader entry point on Fabric, so the registry
 * still has no entry to read at this moment - see {@code Attributes}' class documentation.
 */
@Mixin(Player.class)
public abstract class PlayerAttributesMixin {

    @Inject(method = "createAttributes", at = @At("RETURN"))
    private static void parcool$addParCoolAttributes(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
        cir.getReturnValue()
                .add(Attributes.MAX_STAMINA_ATTRIBUTE)
                .add(Attributes.STAMINA_RECOVERY_ATTRIBUTE);
    }
}
