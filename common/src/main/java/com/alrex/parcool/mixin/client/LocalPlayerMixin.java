package com.alrex.parcool.mixin.client;


import com.alrex.parcool.common.action.BehaviorEnforcer;
import com.alrex.parcool.common.data.Parkourability;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends AbstractClientPlayer {

    public LocalPlayerMixin(ClientLevel p_250460_, GameProfile p_249912_) {
        super(p_250460_, p_249912_);
    }

    @Inject(method = "isShiftKeyDown", at = @At("HEAD"), cancellable = true)
    public void onIsShiftKeyDown(CallbackInfoReturnable<Boolean> cir) {
        Parkourability parkourability = Parkourability.get((Player) (Object) this);

        if (parkourability == null) return;
        if (parkourability.getBehaviorEnforcer().cancelSneak()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Guards the re-entrant {@code Entity#move} call below.
     *
     * <p>{@code LocalPlayer} does not override {@code move}, so the {@code super.move(…)} call is
     * a virtual dispatch on the instance straight back into this very injection. The movement enforcer
     * keeps answering "yes" for as long as it lives, so without a guard the two are mutual recursion
     * and the client dies with a {@code StackOverflowError} the first time an action enforces a
     * movement point:
     *
     * <pre>
     * at ...EntityMixin.onMove           (injected into Entity#move, HEAD)
     * at ...LocalPlayerMixin.onMove      (this handler)
     * at ...EntityMixin.onMove           (injected into the same Entity#move, HEAD)
     * at ...LocalPlayerMixin.onMove      (this handler)
     * ...
     * </pre>
     *
     * <p>Both injections sit on {@code Entity#move} because that is the method the enforcer actually
     * moves the player with. The flag makes the nested call a no-op for this handler only, so the
     * re-entrant pass still reaches {@code EntityMixin} - that one is the position enforcer
     * (HideInBlock) and must keep working on the nested call.
     *
     * <p>The flag lives in {@link BehaviorEnforcer}, not here. An earlier revision declared it as a
     * {@code @Unique private static} field on this mixin, which does not work: Mixin does not carry
     * such a field into the target, and the exported mixed class contained no reference to it at all,
     * so the guard was compiled away in effect.
     */
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    public void onMove(MoverType type, Vec3 pos, CallbackInfo ci) {
        if (BehaviorEnforcer.isApplyingEnforcedMove()) return;
        var player = (LocalPlayer) (Object) this;
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;
        if (type != MoverType.SELF) return;
        var enforcedMovePos = parkourability.getBehaviorEnforcer().getEnforcedMovePoint();
        if (enforcedMovePos != null) {
            ci.cancel();
            var dMove = enforcedMovePos.subtract(player.position());
            player.setDeltaMovement(dMove);
            BehaviorEnforcer.setApplyingEnforcedMove(true);
            try {
                super.move(type, dMove);
            } finally {
                BehaviorEnforcer.setApplyingEnforcedMove(false);
            }
        }
    }

}
