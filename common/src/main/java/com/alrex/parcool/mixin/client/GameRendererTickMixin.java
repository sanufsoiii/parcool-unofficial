package com.alrex.parcool.mixin.client;

import com.alrex.parcool.common.event.CompatEvents;
import com.alrex.parcool.client.action.ClientActionProcessor;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces {@code RenderFrameEvent.Pre}, which has no Architectury counterpart.
 * <p>
 * {@code GameRenderer#renderLevel} is the last point before entity rendering begins, which is
 * exactly where ParCool's per-frame action rotation ({@code Action#onRenderTick}, used to drive
 * {@code yBodyRot} / {@code yRot} / head yaw) has to run. NeoForge fired the event once per frame
 * from Minecraft's tick loop; this hook is one frame tighter, which is harmless because the
 * consumers only write rotation fields that are read later, during entity rendering.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererTickMixin {

    // 1.20.6's renderLevel takes the render partial tick as a plain float (no DeltaTracker); the
    // handler's parameter list has to mirror the target's, or the injection does not apply.
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void parcool$onRenderFrame(float partialTick, long finishTimeNano, CallbackInfo ci) {
        ClientActionProcessor.onRenderFrame(new CompatEvents.RenderFrameEvent.Pre(partialTick));
    }
}
