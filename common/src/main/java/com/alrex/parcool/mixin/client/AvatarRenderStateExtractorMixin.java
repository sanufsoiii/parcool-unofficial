package com.alrex.parcool.mixin.client;

import com.alrex.parcool.compat.IAvatarRenderStateEntity;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stashes the player on the render state, see {@link AvatarRenderStateEntityMixin}.
 *
 * <p>1.21.10's {@code AvatarRenderer#extractRenderState(Avatar, AvatarRenderState, float)} - the
 * renderer's type parameter is bounded by {@code Avatar & ClientAvatarEntity}, and the descriptor uses
 * the erasure {@code Avatar} - is the only place where the renderer still holds the entity. It runs once
 * per frame before any {@code submit}, so it is the exact equivalent of the entity that 1.21.1's
 * {@code setupRotations} used to read.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRenderStateExtractorMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void parcool$keepPlayer(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer player) {
            ((IAvatarRenderStateEntity) state).parcool$setPlayer(player);
        }
    }
}
