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
 * Forge fires that event from {@code ForgeHooks#getEntityVisibilityMultiplier}, and on 1.20.3 (as
 * on 1.20.1) its only vanilla caller is {@code LivingEntity#getVisibilityPercent(Entity)} — the
 * score a viewer assigns to this entity when it looks for targets. (The newer ports inject into
 * {@code Player#canInteractWithEntity} instead; that method only appears in 1.20.5, which is why
 * this class targets a different one.) Upstream's handler multiplied the event's modifier by 0.1,
 * so the same factor is applied to the value vanilla computed.
 */
@Mixin(LivingEntity.class)
public abstract class PlayerInteractionVisibilityMixin {

	@Inject(method = "getVisibilityPercent(Lnet/minecraft/world/entity/Entity;)D", at = @At("RETURN"), cancellable = true)
	private void parcool$hideFromViewer(Entity viewer, CallbackInfoReturnable<Double> cir) {
		if ((Object) this instanceof Player target && viewer != null && PlayerVisibilityHandler.isHiddenFrom(viewer, target)) {
			cir.setReturnValue(cir.getReturnValueD() * 0.1);
		}
	}
}
