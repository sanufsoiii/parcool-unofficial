package com.alrex.parcool.mixin.client;

import com.alrex.parcool.client.input.KeyRecorder;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces {@code MovementInputUpdateEvent}, which has no Architectury counterpart.
 * <p>
 * NeoForge fires the event at the end of {@code KeyboardInput#tick} (or its patch thereof), so
 * sampling the key states from the RETURN of the same method gives ParCool the exact same data on
 * both loaders — including the timing relative to {@code LocalPlayer#aiStep}.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {

    @Inject(method = "tick", at = @At("RETURN"))
    private void parcool$recordKeys(boolean slowDown, float movingSpeed, CallbackInfo ci) {
        KeyRecorder.onClientTick();
    }
}
