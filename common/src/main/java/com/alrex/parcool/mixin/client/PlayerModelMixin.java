package com.alrex.parcool.mixin.client;

import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.compat.IPlayerRenderStateEntity;
import com.alrex.parcool.common.data.client.Animation;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Runs ParCool's model animation ({@code Animation#animatePre} / {@code animatePost}) around
 * {@code PlayerModel#setupAnim}.
 *
 * <h2>What 1.21.2 changed</h2>
 * {@code PlayerModel} lost its type parameter (it extends {@code HumanoidModel<PlayerRenderState>} now
 * - the state class is still called {@code PlayerRenderState}, not {@code AvatarRenderState} as in
 * 1.21.11), and its setup hook takes a single {@link PlayerRenderState} instead of
 * {@code (entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch)}. The five floats are
 * still there on the state under the names {@code walkAnimationPos}, {@code walkAnimationSpeed},
 * {@code ageInTicks}, {@code yRot} and {@code xRot}, and they carry the same values, so
 * {@link PlayerModelTransformer} is fed straight from it.
 *
 * <p>What the animators used to read off the model itself - {@code attackTime} and the two arm poses -
 * moved onto {@code HumanoidRenderState} as well, and are handed to the transformer as three more
 * constructor arguments instead.
 *
 * <p>The state holds no entity, so the player comes from {@link PlayerRenderStateEntityMixin}; and
 * {@code ear} is not a model part any more (it became the {@code showExtraEars} flag), so the 1.21.1
 * shadow of that field - which was never read - is gone.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {

    @Shadow
    @Final
    private boolean slim;

    @Unique
    private PlayerModelTransformer parCool$transformer = null;

    // Explicit descriptor: the erasure of HumanoidModel#setupAnim is (HumanoidRenderState), and
    // PlayerModel narrows it to PlayerRenderState. Naming the narrowed type keeps the injection on
    // the override ParCool actually wraps rather than on the bridge the compiler generated.
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
                // 1.21.2 has no leftArmPose / rightArmPose field: the pose is derived from the two
                // HandState entries by PlayerRenderer#getArmPose, and that is the same derivation the
                // model itself used, so asking it here yields exactly the values the animators saw on
                // PlayerModel in 1.21.1.
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
