package com.alrex.parcool.mixin.client;

import com.alrex.parcool.common.data.Parkourability;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides the name tag of players ParCool is currently hiding (HideInBlock, WallSlide, ...), which used
 * to be {@code LivingEvent.LivingVisibilityEvent}'s consumer.
 *
 * <h2>Why this mixin declares no supertype</h2>
 * An earlier version of this file read
 * {@code abstract class LivingRendererMixin<T, S, M> extends LivingEntityRenderer<T, S, M>} with a
 * matching copy constructor, on the assumption that the mixin has to re-declare the target's
 * generics for the handler parameter to resolve. That is wrong twice over, and Mixin rejects it
 * at config-preparation time before any class is transformed:
 *
 * <pre>
 * [FabricLoader/Mixin] parcool-common.mixins.json:client.LivingRendererMixin from mod parcool:
 *   Super class 'net.minecraft.client.renderer.entity.LivingEntityRenderer' of
 *   client.LivingRendererMixin was not found in the hierarchy of target class
 *   'net/minecraft/client/renderer/entity/LivingEntityRenderer'
 *   at MixinInfo$SubType$Standard.validate(MixinInfo.java:593)
 *   at MixinInfo$State.validate(MixinInfo.java:327)
 *   at MixinConfig.postInitialise(MixinConfig.java:884)
 * </pre>
 *
 * The message looks self-contradictory - same class on both sides - and that is exactly the
 * point. {@code MixinInfo$SubType$Standard.validate} searches for the mixin's supertype in the
 * target's *ancestor* chain, starting above the target itself. A mixin may not extend its own
 * target: the target is never in its own ancestor list, so the lookup always fails. Note also
 * that the failure is logged at ERROR and the boot continues, so the mod loads and looks fine
 * while this mixin is silently dead - HideInBlock and WallSlide keep rendering other players'
 * name tags. Nothing but a live client reveals it.
 *
 * The generics were never needed either: {@code shouldShowName} erases to
 * {@code (LivingEntity, double)}, so the handler takes {@link LivingEntity} directly. That is the
 * same shape the 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9 and 1.21.10 ports use,
 * and it is why those eight never hit this.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingRendererMixin {

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    protected void onShouldShowName(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player player) {
            Parkourability parkourability = Parkourability.get(player);
            if (parkourability == null) return;
            if (parkourability.getBehaviorEnforcer().cancelShowingName()) {
                cir.setReturnValue(false);
            }
        }
    }
}
