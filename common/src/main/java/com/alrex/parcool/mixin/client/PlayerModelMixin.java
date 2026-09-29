package com.alrex.parcool.mixin.client;

import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.compat.IAvatarRenderStateEntity;
import com.alrex.parcool.common.data.client.Animation;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs ParCool's model animation ({@code Animation#animatePre} / {@code animatePost}) around
 * {@code PlayerModel#setupAnim}.
 *
 * <h2>What changed against 1.21.1</h2>
 * {@code PlayerModel} lost its type parameter (it extends {@code HumanoidModel<AvatarRenderState>} now
 * and sits back in {@code net.minecraft.client.model} on 1.21.9 - 1.21.11 moved it to
 * {@code net.minecraft.client.model.player}), and its setup hook takes a single
 * {@link AvatarRenderState} instead of
 * {@code (entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch)}. The five floats are
 * still there on the state under the names {@code walkAnimationPos}, {@code walkAnimationSpeed},
 * {@code ageInTicks}, {@code yRot} and {@code xRot}, and they carry the same values, so
 * {@link PlayerModelTransformer} is fed straight from it.
 *
 * <p>The state holds no entity, so the player comes from {@link AvatarRenderStateEntityMixin}; and
 * {@code ear} is not a model part any more (it became the {@code showHat} flag on the state), so the
 * 1.21.1 shadow of that field - which was never read - is gone.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {

    @Shadow
    @Final
    private boolean slim;






    @Unique
    private PlayerModelTransformer parCool$transformer = null;

    // Explicit descriptor: PlayerModel declares setupAnim(AvatarRenderState) plus the two bridge
    // overloads (HumanoidRenderState / EntityRenderState), and a name-only target lets mixin pick the
    // wrong one - the bridges are never called for a player, so the injection would silently no-op.
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    protected void onSetupAnimHead(AvatarRenderState state, CallbackInfo info) {
        if (!(((IAvatarRenderStateEntity) state).parcool$getPlayer() instanceof AbstractClientPlayer player)) return;
        PlayerModel model = (PlayerModel) (Object) this;

        parCool$transformer = new PlayerModelTransformer(
                player,
                model,
                slim,
                state.ageInTicks,
                state.walkAnimationPos,
                state.walkAnimationSpeed,
                state.yRot,
                state.xRot,
                state.attackTime,
                state.leftArmPose,
                state.rightArmPose
        );
        parCool$transformer.reset();

        Animation animation = Animation.get(player);
        if (animation == null) return;

        boolean shouldCancel = animation.animatePre(player, parCool$transformer);
        parCool$transformer.copyFromBodyToWear();
        if (shouldCancel) {
            parCool$transformer = null;
            info.cancel();
        }
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("TAIL"))
    protected void onSetupAnimTail(AvatarRenderState state, CallbackInfo info) {
        if (!(((IAvatarRenderStateEntity) state).parcool$getPlayer() instanceof AbstractClientPlayer player)) return;

        Animation animation = Animation.get(player);
        if (animation == null) {
            parCool$transformer = null;
            return;
        }

        if (parCool$transformer != null) {
            animation.animatePost(player, parCool$transformer);
            parCool$transformer.copyFromBodyToWear();
            parCool$transformer = null;
        }
    }
}
