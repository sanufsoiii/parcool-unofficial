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
 *
 * <p>Explicit {@code ()V} and a handler without arguments: {@code KeyboardInput#tick} has taken no
 * parameters well before 1.21.1 (the 1.21.1 and 1.21.4 trees both still declare the handler as
 * {@code (boolean, float, CallbackInfo)}, which resolves against no method at all). A wrong handler
 * arity is an invalid injection signature, i.e. with {@code defaultRequire: 1} a hard boot failure.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {

    @Inject(method = "tick()V", at = @At("RETURN"))
    private void parcool$recordKeys(CallbackInfo ci) {
        KeyRecorder.onClientTick();
    }
}
