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
     * <p>1.21.7 still has that {@code setSprinting(false)} inline at the end of {@code Player#attack}
     * (1.21.11 extracted the whole knockback block into {@code Player#causeExtraKnockback}, which does
     * not exist yet on this side of the seam), so the wrap targets {@code attack} - exactly as it did
     * on 1.21.1.
     *
     * <p>The jump hooks are not here: {@code jumpFromGround} moved up from {@code Player} to
     * {@code LivingEntity}, so they live in {@code mixin.common.LivingEntityJumpMixin}.
     */
    @WrapWithCondition(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V"))
    public boolean wrapSetSprinting(Player instance, boolean b) {
        return !Parkourability.get(instance).get(FastRun.class).isDoing();
    }
}
