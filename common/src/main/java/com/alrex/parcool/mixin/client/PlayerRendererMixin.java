package com.alrex.parcool.mixin.client;

import com.alrex.parcool.compat.IAvatarRenderStateEntity;

import com.alrex.parcool.client.animation.PlayerModelRotator;
import com.alrex.parcool.compat.IAvatarRenderStateEntity;
import com.alrex.parcool.common.data.client.Animation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs ParCool's per-frame action rotation ({@code Animation#rotatePre} / {@code rotatePost}) around
 * the player's own body rotation.
 *
 * <h2>What 1.21.9 changed</h2>
 * {@code PlayerRenderer} is gone; the same renderer is {@code AvatarRenderer}, and it no longer
 * receives the entity - it receives an {@link AvatarRenderState}. Its rotation hook lost the
 * {@code partialTick} argument and now takes {@code (state, poseStack, bodyRot, scale)}, where
 * {@code bodyRot} is what 1.21.1 passed as {@code yBodyRot}.
 *
 * <p>ParCool's animators need the {@link AbstractClientPlayer} itself and a render state carries no
 * entity, so {@link AvatarRenderStateExtractorMixin} stashes the player on the state as it is
 * extracted. The {@code instanceof} guard keeps the scope identical to 1.21.1's
 * {@code PlayerRenderer}: other avatar-like client entities are not animated by ParCool.
 */
@Mixin(AvatarRenderer.class)
public abstract class PlayerRendererMixin {

    @Unique
    private PlayerModelRotator parCool$rotator = null;

    // Explicit descriptor: AvatarRenderer declares setupRotations(AvatarRenderState, ...) and also
    // inherits the LivingEntityRenderState one, and a name-only target lets mixin pick the wrong one -
    // which silently drops the PoseStack rotation the animators do.
    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("RETURN"))
    protected void onSetupRotationsTail(AvatarRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        if (!(((IAvatarRenderStateEntity) state).parcool$getPlayer() instanceof AbstractClientPlayer player)) return;
        Animation animation = Animation.get(player);
        if (animation == null) {
            return;
        }
        if (parCool$rotator != null) {
            animation.rotatePost(player, parCool$rotator);
            parCool$rotator = null;
        }
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("HEAD"), cancellable = true)
    protected void onSetupRotationsHead(AvatarRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        if (!(((IAvatarRenderStateEntity) state).parcool$getPlayer() instanceof AbstractClientPlayer player)) return;
        Animation animation = Animation.get(player);
        if (animation == null) {
            return;
        }
        // 1.21.1 received the partial tick as an argument of setupRotations; the render state does not
        // carry it, and Minecraft's own delta tracker holds the very value that was passed in.
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        parCool$rotator = new PlayerModelRotator(poseStack, player, partialTick, bodyRot);
        if (animation.rotatePre(player, parCool$rotator)) {
            parCool$rotator = null;
            ci.cancel();
        }
    }
}
