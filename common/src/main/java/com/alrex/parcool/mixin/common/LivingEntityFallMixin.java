package com.alrex.parcool.mixin.common;

import com.alrex.parcool.common.event.CompatEvents;
import com.alrex.parcool.common.handlers.PlayerDamageHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces {@code LivingEvent.LivingFallEvent}, which has no Architectury counterpart.
 *
 * <p>{@code LivingEntity#causeFallDamage(float, float, DamageSource)} (1.21.4 still passes the fall
 * distance as a {@code float}; it widens to {@code double} only in 1.21.5) delegates to
 * {@code super.causeFallDamage}, then computes the amount with {@code calculateFallDamage} and
 * applies it with {@code hurtOrSimulate(source, amount)}. Two hooks reproduce the NeoForge event
 * exactly:
 * <ul>
 *     <li>HEAD + {@code cancellable} for {@link CompatEvents.LivingFallEvent#isCanceled()};</li>
 *     <li>a redirect of the single damage call for
 *     {@link CompatEvents.LivingFallEvent#getDamageMultiplier()}. Redirecting the damage call rather
 *     than {@code calculateFallDamage} keeps the fall sound and the method's return value intact and
 *     avoids needing access to the protected damage formula.</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFallMixin {

    /** Damage multiplier requested by the last dispatched event; reset on every entry. */
    @Unique
    private float parcool$fallDamageMultiplier = 1.0F;

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void parcool$onFall(float fallDistance, float damageMultiplier, DamageSource source,
                                CallbackInfoReturnable<Boolean> cir) {
        this.parcool$fallDamageMultiplier = 1.0F;
        CompatEvents.LivingFallEvent event =
                new CompatEvents.LivingFallEvent((LivingEntity) (Object) this, fallDistance, source);
        PlayerDamageHandler.onFall(event);
        if (event.isCanceled()) {
            cir.setReturnValue(false);
            return;
        }
        this.parcool$fallDamageMultiplier = event.getDamageMultiplier();
    }

    // Entity#hurt is a final void in 1.21.4, so the call causeFallDamage makes is
    // hurtOrSimulate(...), which is what returns the boolean this redirect has to give back.
    @Redirect(
            method = "causeFallDamage",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z")
    )
    private boolean parcool$applyDamageMultiplier(LivingEntity self, DamageSource source, float amount) {
        float multiplier = this.parcool$fallDamageMultiplier;
        return self.hurtOrSimulate(source, multiplier == 1.0F ? amount : amount * multiplier);
    }
}
