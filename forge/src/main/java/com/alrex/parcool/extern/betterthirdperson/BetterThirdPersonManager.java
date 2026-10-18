package com.alrex.parcool.extern.betterthirdperson;

import com.alrex.parcool.common.action.impl.Dodge;
import com.alrex.parcool.extern.ModManager;
import io.socol.betterthirdperson.BetterThirdPerson;
import net.minecraft.client.Minecraft;

/**
 * Better Third Person integration (Forge 1.20.1, mod version 1.9.0).
 *
 * <p>Only polled from client-side camera code, so the class is never loaded on a dedicated server;
 * the {@code isInstalled()} guard keeps the BetterThirdPerson classes from being resolved when the
 * mod is absent.
 */
public class BetterThirdPersonManager extends ModManager {

    public BetterThirdPersonManager() {
        super("betterthirdperson");
    }

    private static BetterThirdPersonManager instance;

    public static BetterThirdPersonManager getInstance() {
        if (instance == null) instance = new BetterThirdPersonManager();
        return instance;
    }

    public Dodge.DodgeDirection handleCustomCameraRotationForDodge(Dodge.DodgeDirection direction) {
        // The dodge animation is built around the vanilla camera; with a decoupled camera it has to
        // run "front" so the movement still reads correctly.
        return isCameraDecoupled() ? Dodge.DodgeDirection.Front : direction;
    }

    public boolean isCameraDecoupled() {
        return isInstalled()
                && !Minecraft.getInstance().options.getCameraType().isFirstPerson()
                && BetterThirdPerson.getCameraManager().hasCustomCamera();
    }
}
