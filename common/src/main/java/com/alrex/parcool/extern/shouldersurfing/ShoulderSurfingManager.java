package com.alrex.parcool.extern.shouldersurfing;

import com.alrex.parcool.common.action.impl.Dodge;
import com.alrex.parcool.common.action.impl.Dodge.DodgeDirection;
import com.alrex.parcool.extern.ModManager;
import com.github.exopandora.shouldersurfing.api.client.ShoulderSurfing;
import net.minecraft.client.Minecraft;

public class ShoulderSurfingManager extends ModManager {
    public ShoulderSurfingManager() {
        super("shouldersurfing");
    }

    public Dodge.DodgeDirection handleCustomCameraRotationForDodge(Dodge.DodgeDirection direction) {
        return isCameraDecoupled() ? DodgeDirection.Front : direction;
    }

    public Boolean isCameraDecoupled() {
        // 4.6.3, the newest ShoulderSurfing for Minecraft 1.21.2/1.21.3, has no
        // IShoulderSurfing#isCameraDecoupled() - that arrived together with ICameraCouplingCallback in
        // 4.7.0. What it does have is the free-look target offset on IShoulderSurfingCamera, which is
        // exactly the value those callbacks are allowed to zero out: while it is non-zero the camera is
        // not following the player, i.e. it is decoupled.
        return isInstalled()
                && !Minecraft.getInstance().options.getCameraType().isFirstPerson()
                && ShoulderSurfing.getInstance().isShoulderSurfing()
                && ShoulderSurfing.getInstance().getCamera().getTargetOffset().lengthSqr() > 0.0;
    }
}
