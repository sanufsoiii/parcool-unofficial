package com.alrex.parcool.mixin.client;

import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.compat.IPlayerRenderStateEntity;
import com.alrex.parcool.common.data.client.Animation;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;
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
 * <h2>What the 1.21.2 rework changed</h2>
 * {@code PlayerModel} is now {@code HumanoidModel<PlayerRenderState>}: it kept its own class but lost
 * its type parameter, and its setup hook takes a single {@link PlayerRenderState} instead of
 * {@code (entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch)}. The five floats are
 * still there on the state under the names {@code walkAnimationPos}, {@code walkAnimationSpeed},
 * {@code ageInTicks}, {@code yRot} and {@code xRot}, and they carry the same values, so
 * {@link PlayerModelTransformer} is fed straight from it.
 *
 * <p>{@code attackTime} also moved to the state ({@code HumanoidRenderState#attackTime}). The two arm
 * poses did not: 1.21.3 keeps deriving them from the state's two hand states, and the derivation is
 * {@code PlayerRenderer#getArmPose(PlayerRenderState, HumanoidArm)} - a public static - so the mixin
 * calls exactly the function {@code setupAnim} itself is about to call, which keeps the animators'
 * "is this arm actually holding something" test in step with the pose vanilla is about to apply.
 *
 * <p>The state holds no entity, so the player comes from
 * {@link PlayerRenderStateExtractorMixin}.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {

    @Shadow
    @Final
    private boolean slim;

    @Unique
    private PlayerModelTransformer parCool$transformer = null;

    // Explicit descriptor: 1.21.3 declares setupAnim(PlayerRenderState) on PlayerModel and also
    // inherits the bridge from HumanoidModel/HumanoidRenderState, and a name-only target lets mixin
    // pick the wrong one - which silently drops the animation entirely.
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    protected void onSetupAnimHead(PlayerRenderState state, CallbackInfo info) {
        if (!(((IPlayerRenderStateEntity) state).parcool$getPlayer() instanceof AbstractClientPlayer player)) return;
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
                PlayerRenderer.getArmPose(state, HumanoidArm.LEFT),
                PlayerRenderer.getArmPose(state, HumanoidArm.RIGHT)
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

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;)V",
            at = @At("TAIL"))
    protected void onSetupAnimTail(PlayerRenderState state, CallbackInfo info) {
        if (!(((IPlayerRenderStateEntity) state).parcool$getPlayer() instanceof AbstractClientPlayer player)) return;

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
