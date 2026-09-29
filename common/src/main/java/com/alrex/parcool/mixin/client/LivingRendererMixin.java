package com.alrex.parcool.mixin.client;

import com.alrex.parcool.common.data.Parkourability;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
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
 * <p>The generics follow 1.21.2's {@code LivingEntityRenderer<T, S, M>} - the render state
 * parameter was added in the same rework that took the entity away from the renderer, and
 * {@code RenderLayerParent} is now {@code RenderLayerParent<S, M>}, i.e. state first. The mixin still
 * has to declare the supertypes it inherits, because the handler's parameter is the erasure
 * {@code LivingEntity} that the target resolves to.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends LivingEntityRenderer<T, S, M> {

    public LivingRendererMixin(EntityRendererProvider.Context p_i46179_1_, M p_i174290_, float p_i174291_) {
        super(p_i46179_1_, p_i174290_, p_i174291_);
    }

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    protected void onShouldShowName(T entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player) {
            Player player = (Player) entity;
            Parkourability parkourability = Parkourability.get(player);
            if (parkourability == null) return;
            if (parkourability.getBehaviorEnforcer().cancelShowingName()) {
                cir.setReturnValue(false);
            }
        }
    }
}
