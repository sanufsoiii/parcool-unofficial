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
        if (!isInstalled() || Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return false;
        }
        try {
            return ShoulderSurfing.getInstance().isShoulderSurfing()
                    && ShoulderSurfing.getInstance().isCameraDecoupled();
        } catch (NoSuchMethodError | NoClassDefFoundError error) {
            // The integration is written against the 4.11.0 API, and the ShoulderSurfing releases
            // published for 1.20.2-1.20.4 are the older 4.2.x line, whose IShoulderSurfing has no
            // isCameraDecoupled. Degrade to "not decoupled" - the answer an absent ShoulderSurfing
            // gives - instead of letting the first dodge throw NoSuchMethodError into the client.
            // ShoulderSurfing's own plugin loader catches Throwable, so the coupling plugin fails
            // soft on its side too.
            return false;
        }
    }
}
