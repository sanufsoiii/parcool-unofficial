package com.alrex.parcool.extern.shouldersurfing;

import com.alrex.parcool.common.action.impl.ClingToCliff;
import com.alrex.parcool.common.data.Parkourability;
import com.github.exopandora.shouldersurfing.api.callback.ITargetCameraOffsetCallback;
import com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing;
import com.github.exopandora.shouldersurfing.api.plugin.IShoulderSurfingPlugin;
import com.github.exopandora.shouldersurfing.api.plugin.IShoulderSurfingRegistrar;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Compatibility class for the "Shoulder Surfing" mod: it forces the camera back onto the player while
 * an action that owns the camera is running.
 *
 * <h2>What changed with the 1.21.3 line of ShoulderSurfing</h2>
 * Upstream ParCool registers an {@code ICameraCouplingCallback}, added in ShoulderSurfing 4.7.0. The
 * newest build CurseForge published for Minecraft 1.21.2/1.21.3 is 4.6.3, which predates it: there is
 * no coupling callback and {@code IShoulderSurfing#isCameraDecoupled()} does not exist yet either. The
 * two API points ParCool needs are, however, reachable in 4.6.3 through the free-look offset:
 * {@code ShoulderSurfingCamera#calcOffset} runs every registered
 * {@link ITargetCameraOffsetCallback#pre} and {@link ITargetCameraOffsetCallback#post}, and the value
 * {@code post} returns is stored as the camera's {@code targetOffset} - the very offset that decouples
 * the camera from the player. Returning {@link Vec3#ZERO} from {@code post} while ClingToCliff is doing
 * therefore does what {@code isForcingCameraCoupling} used to do: the camera stops drifting and follows
 * the player's head again.
 */
public class ShoulderSurfingDecoupledCamera implements ITargetCameraOffsetCallback, IShoulderSurfingPlugin {

    @Override
    public Vec3 post(IShoulderSurfing api, Vec3 targetOffset, Vec3 baseOffset) {
        return isForcingCameraCoupling(Minecraft.getInstance()) ? Vec3.ZERO : targetOffset;
    }

    /**
     * Whether an action that owns the camera is currently running.
     *
     * <p>Kept as its own method so the meaning survives even though 4.6.3 has no callback interface to
     * hang it on.
     */
    public boolean isForcingCameraCoupling(Minecraft mc) {
        if (mc.player == null) return false;
        var parkourability = Parkourability.get(mc.player);
        if (parkourability == null) return false;
        return parkourability.isDoingAny(ClingToCliff.class);
    }

    @Override
    public void register(IShoulderSurfingRegistrar registrar) {
        registrar.registerTargetCameraOffsetCallback(this);
    }
}
