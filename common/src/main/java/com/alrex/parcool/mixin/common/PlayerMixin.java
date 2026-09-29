package com.alrex.parcool.mixin.common;

import com.alrex.parcool.common.action.impl.FastRun;
import com.alrex.parcool.common.data.Parkourability;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity {

    protected PlayerMixin(EntityType<? extends LivingEntity> p_i48577_1_, Level p_i48577_2_) {
        super(p_i48577_1_, p_i48577_2_);
    }

    @Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
    public void onTryToStartFallFlying(CallbackInfoReturnable<Boolean> cir) {
        var player = (Player) (Object) this;
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability != null && parkourability.getBehaviorEnforcer().cancelFallFlying()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
    public void onIsStayingOnGroundSurface(CallbackInfoReturnable<Boolean> cir) {
        Parkourability parkourability = Parkourability.get((Player) (Object) this);
        if (parkourability == null) return;
        if (parkourability.getBehaviorEnforcer().cancelDescendFromEdge()) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Stops FastRun from being cancelled by the sprint reset that attacking performs.
     *
     * <p>1.21.11 extracted the whole knockback block out of {@code Player#attack} into
     * {@code Player#causeExtraKnockback} and the call moved there; 1.21.6 still has the
     * {@code setSprinting(false)} inline in {@code attack} (verified with {@code javap}: the only
     * {@code setSprinting} call site in {@code Player} is at offset 635 of {@code attack}), so the
     * wrap targets {@code attack}. Targeting the 1.21.11 name here would not even compile-verify -
     * {@code causeExtraKnockback} does not exist in 1.21.6 - and the wrap would silently never run.
     */
    @WrapWithCondition(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V"))
    public boolean wrapSetSprinting(Player instance, boolean b) {
        return !Parkourability.get(instance).get(FastRun.class).isDoing();
    }
}
