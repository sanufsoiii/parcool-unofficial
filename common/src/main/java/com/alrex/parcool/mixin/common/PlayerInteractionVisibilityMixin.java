package com.alrex.parcool.mixin.common;

import com.alrex.parcool.common.handlers.PlayerVisibilityHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces {@code LivingEvent.LivingVisibilityEvent}.
 * <p>
 * NeoForge patched {@code LivingEntity#getVisibilityPercent(Entity)} and fired
 * {@code LivingVisibilityEvent} from it; ParCool's handler multiplied the score by 0.1. 1.21.2 has
 * that method back as a plain public one, so the upstream factor is applied here directly.
 * <p>
 * 1.21.1 did not have it, and the port used {@code Player#canInteractWithEntity(Entity, double)} -
 * the predicate the entity tracker used to decide whether a viewer may see a tracked entity -
 * returning {@code false} instead, which is behaviourally equivalent because a score below the
 * threshold never passes it. {@code canInteractWithEntity} still exists in 1.21.2; the 0.1 factor
 * was chosen because it is what upstream does and it survives a later removal of the tracker's use
 * of the predicate. The visible effect is the same either way: while HideInBlock is doing, the player
 * is far less likely to be seen by anything that consults the visibility score, and its name tag is
 * suppressed by {@code mixin.client.LivingRendererMixin}.
 */
@Mixin(LivingEntity.class)
public class PlayerInteractionVisibilityMixin {

    /** The factor the original {@code LivingVisibilityEvent} handler applied. */
    private static final double HIDDEN_FACTOR = 0.1;

    @Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
    private void parcool$hideFromViewer(Entity target, CallbackInfoReturnable<Double> cir) {
        if (!(((Object) this) instanceof Player viewer)) return;
        if (target instanceof Player other && PlayerVisibilityHandler.isHiddenFrom(viewer, other)) {
            cir.setReturnValue(cir.getReturnValue() * HIDDEN_FACTOR);
        }
    }
}
