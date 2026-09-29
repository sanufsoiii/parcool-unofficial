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
 * <p>{@code LivingEntity#causeFallDamage(double, float, DamageSource)} (1.21.5 widened the fall
 * distance from {@code float} to {@code double}) delegates to
 * {@code super.causeFallDamage}, then computes the amount with {@code calculateFallDamage} and
 * applies it with {@code hurt(source, amount)}. Two hooks reproduce the NeoForge event exactly:
 * <ul>
 *     <li>HEAD + {@code cancellable} for {@link CompatEvents.LivingFallEvent#isCanceled()};</li>
 *     <li>a redirect of the single {@code hurt} call (which 1.21.2 turned into a final
 *     {@code void hurt(DamageSource, float)}) for
 *     {@link CompatEvents.LivingFallEvent#getDamageMultiplier()}. Redirecting {@code hurt} rather
 *     than {@code calculateFallDamage} keeps the fall sound and the method's return value intact
 *     and avoids needing access to the protected damage formula.</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFallMixin {

    /** Damage multiplier requested by the last dispatched event; reset on every entry. */
    @Unique
    private float parcool$fallDamageMultiplier = 1.0F;

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void parcool$onFall(double fallDistance, float damageMultiplier, DamageSource source,
                                CallbackInfoReturnable<Boolean> cir) {
        // The event keeps the 1.21.1 float distance, so the widened vanilla argument is narrowed back;
        // the parcool event is only compared against small thresholds.
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

    // 1.21.2 split Entity#hurt into a final void hurt(...) plus hurtOrSimulate/hurtServer; the call
    // LivingEntity#causeFallDamage makes is hurt(...), so the redirect target and the handler's return
    // type follow it to void.
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
