package com.alrex.parcool.mixin.client;

import com.alrex.parcool.client.action.ClientActionProcessor;
import com.alrex.parcool.common.event.CompatEvents;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces {@code ViewportEvent.ComputeCameraAngles}, which has no Architectury counterpart.
 *
 * <h2>Vanilla has no camera roll</h2>
 * NeoForge patches {@link Camera} with a roll field that its {@code ComputeCameraAngles} event can
 * set. Vanilla 1.21.11 has no such field, so roll is applied here by re-rotating the camera basis and
 * the exported {@link Camera#rotation()} quaternion.
 *
 * <p>1.21.11 changed how the basis is handed out: {@code getUpVector()} / {@code getLeftVector()} are
 * gone and {@link Camera#upVector()} / {@link Camera#leftVector()} return the read-only
 * {@code Vector3fc} view of the very same fields. Rotating the {@code Vector3f} fields in place is
 * therefore what keeps the view - and everything that reads it - consistent.
 */
@Mixin(Camera.class)
public abstract class CameraAnglesMixin {

    // Not final: a shadowed field cannot carry a field initialiser, and javac rejects a final field
    // without one. Mixin only ever reads these.
    @Shadow
    private Vector3f up;

    @Shadow
    private Vector3f left;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;ZZF)V", at = @At("TAIL"))
    private void parcool$computeCameraAngles(Level level, Entity entity, boolean detached,
                                             boolean mirrored, float partialTick, CallbackInfo ci) {
        Camera self = (Camera) (Object) this;
        CompatEvents.ViewportEvent.ComputeCameraAngles event =
                new CompatEvents.ViewportEvent.ComputeCameraAngles(
                        self, partialTick, self.yRot(), self.xRot(), 0.0F);

        ClientActionProcessor.onViewRender(event);

        float pitch = event.getPitch();
        float roll = event.getRoll();
        if (pitch == self.xRot() && roll == 0.0F) return;

        if (pitch != self.xRot()) {
            setRotation(event.getYaw(), pitch);
        }
        if (roll != 0.0F) {
            applyRoll(self, roll);
        }
    }

    private void applyRoll(Camera camera, float rollDegrees) {
        float radians = (float) Math.toRadians(rollDegrees);
        Quaternionf rotation = new Quaternionf().rotateZ(radians);

        Vector3f up = this.up;
        Vector3f left = this.left;

        up.rotate(rotation);
        left.rotate(rotation);

        // The exported orientation quaternion has to agree with the rotated basis, otherwise
        // entities and particles placed relative to the camera keep the un-rolled orientation.
        Quaternionf exported = camera.rotation();
        exported.mul(rotation).normalize();
    }
}
