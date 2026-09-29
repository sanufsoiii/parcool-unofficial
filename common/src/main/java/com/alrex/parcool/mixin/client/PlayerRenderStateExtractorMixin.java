package com.alrex.parcool.mixin.client;

import com.alrex.parcool.compat.IPlayerRenderStateEntity;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stashes the player on the render state, see {@link PlayerRenderStateEntityMixin}.
 *
 * <p>{@code PlayerRenderer#extractRenderState(AbstractClientPlayer, PlayerRenderState, float)} is the
 * only place where the renderer still holds the entity: 1.21.2 moved it out of
 * {@code setupRotations(AbstractClientPlayer, …)}, which used to receive it directly on 1.21.1. The
 * extractor runs once per frame before any {@code render}, so it is the exact equivalent.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRenderStateExtractorMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V", at = @At("TAIL"))
    private void parcool$keepPlayer(AbstractClientPlayer player, PlayerRenderState state, float partialTick, CallbackInfo ci) {
        ((IPlayerRenderStateEntity) state).parcool$setPlayer(player);
    }
}
