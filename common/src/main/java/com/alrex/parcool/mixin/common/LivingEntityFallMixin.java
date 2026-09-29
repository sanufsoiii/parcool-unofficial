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
 * <p>{@code LivingEntity#causeFallDamage(double, float, DamageSource)} delegates to
 * {@code super.causeFallDamage}, then computes the amount with {@code calculateFallDamage} and
 * applies it with {@code hurt(source, amount)}. Two hooks reproduce the NeoForge event exactly:
 * <ul>
 *     <li>HEAD + {@code cancellable} for {@link CompatEvents.LivingFallEvent#isCanceled()};</li>
 *     <li>a redirect of the single damage call for
 *     {@link CompatEvents.LivingFallEvent#getDamageMultiplier()}. Redirecting the damage call rather
 *     than {@code calculateFallDamage} keeps the fall sound and the method's return value intact and
 *     avoids needing access to the protected damage formula.</li>
 * </ul>
 *
 * <h2>1.21.5 widened the fall distance</h2>
 * {@code causeFallDamage}'s first parameter is a {@code double} here; it was a {@code float} in 1.21.4.
 * A handler still declared as {@code (float, float, DamageSource, CallbackInfo)} compiles and then
 * never applies - and with {@code defaultRequire: 1} a mismatch is a hard boot failure rather than a
 * silent no-op, so the handler below is spelled with the widened type and narrows back to the
 * {@code float} the ParCool event has always carried.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFallMixin {

    /** Damage multiplier requested by the last dispatched event; reset on every entry. */
    @Unique
    private float parcool$fallDamageMultiplier = 1.0F;

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void parcool$onFall(double fallDistance, float damageMultiplier, DamageSource source,
                                CallbackInfoReturnable<Boolean> cir) {
        this.parcool$fallDamageMultiplier = 1.0F;
        CompatEvents.LivingFallEvent event =
                new CompatEvents.LivingFallEvent((LivingEntity) (Object) this, (float) fallDistance, source);
        PlayerDamageHandler.onFall(event);
        if (event.isCanceled()) {
            cir.setReturnValue(false);
            return;
        }
        this.parcool$fallDamageMultiplier = event.getDamageMultiplier();
    }

    // The call causeFallDamage makes is Entity#hurt, which is `final void` since 1.21.2 - it dispatches
    // to hurtOrSimulate (client) / hurtServer (server) internally. The 1.21.1 code redirected
    // `hurt(DamageSource;F)Z`; a redirect aimed at `hurtOrSimulate(DamageSource;F)Z` finds no such call
    // site in causeFallDamage and silently does nothing, so the target and the handler's return type
    // both follow the void call.
    @Redirect(
            method = "causeFallDamage",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)V")
    )
    private void parcool$applyDamageMultiplier(LivingEntity self, DamageSource source, float amount) {
        float multiplier = this.parcool$fallDamageMultiplier;
        self.hurt(source, multiplier == 1.0F ? amount : amount * multiplier);
    }
}
