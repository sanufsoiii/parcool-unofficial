package com.alrex.parcool.mixin.client;


import com.alrex.parcool.common.data.Parkourability;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Deliberately does not extend AbstractClientPlayer: a mixin class that extends the target's own
// supertype forces mixin to validate the inherited constructor at load time, and none of the two handlers
// below needs it. The cast at the use site is explicit instead.
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

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
     * <p>{@code LocalPlayer} does not override {@code move}, so the {@code ((Entity) player).move(…)}
     * call is a virtual dispatch straight back into this very injection. The movement enforcer keeps
     * answering "yes" for as long as it lives, so without a guard the pair is mutual recursion and the
     * client dies with a {@code StackOverflowError} the first time an action enforces a movement point
     * (verified on 1.21.11, a plain walk cycle in survival was enough):
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
     */
    @Unique
    private static boolean applyingEnforcedMove;

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    public void onMove(MoverType type, Vec3 pos, CallbackInfo ci) {
        if (applyingEnforcedMove) return;
        var player = (LocalPlayer) (Object) this;
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;
        if (type != MoverType.SELF) return;
        var enforcedMovePos = parkourability.getBehaviorEnforcer().getEnforcedMovePoint();
        if (enforcedMovePos != null) {
            ci.cancel();
            var dMove = enforcedMovePos.subtract(player.position());
            player.setDeltaMovement(dMove);
            // Entity#move, reached through the target: super.move(…) is only spellable from a mixin
            // class that extends the target's own supertype. This is the call that re-enters the
            // injection, hence the flag above.
            applyingEnforcedMove = true;
            try {
                ((Entity) player).move(type, dMove);
            } finally {
                applyingEnforcedMove = false;
            }
        }
    }

}
