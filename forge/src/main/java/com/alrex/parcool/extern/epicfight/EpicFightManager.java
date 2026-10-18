package com.alrex.parcool.extern.epicfight;

import com.alrex.parcool.common.data.Parkourability;
import com.alrex.parcool.common.stamina.IParCoolStaminaHandler;
import com.alrex.parcool.common.stamina.StaminaType;
import com.alrex.parcool.common.stamina.handlers.ParCoolStaminaHandler;
import com.alrex.parcool.extern.ModManager;
import net.minecraft.world.entity.player.Player;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

import javax.annotation.Nullable;

/**
 * EpicFight integration (Forge 1.20.1, mod version 20.14.17).
 *
 * <p>EpicFight owns a stamina bar of its own while its mode is active, so ParCool hands its stamina
 * over to it: the HUD shows EpicFight's values and consumption is forwarded through
 * {@link EpicFightStaminaHandler#processOnServer}.
 */
public class EpicFightManager extends ModManager {

    public EpicFightManager() {
        super("epicfight");
    }

    private static EpicFightManager instance;

    public static EpicFightManager getInstance() {
        if (instance == null) instance = new EpicFightManager();
        return instance;
    }

    /**
     * True only when EpicFight's own classes are actually loadable, not merely advertised.
     *
     * <p>{@link #isInstalled()} only reports that the mod id shows up in the loader's list. If the jar
     * is absent, partial or built for another version, every class reference below is a
     * {@code NoClassDefFoundError} on first use - so the guard is a loadability check, and every
     * EpicFight entry point consults it before touching an EpicFight type.
     */
    public static boolean isEpicFightUsable() {
        try {
            Class.forName("yesman.epicfight.world.capabilities.EpicFightCapabilities", false,
                    EpicFightManager.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public IParCoolStaminaHandler newStaminaHandlerFor(Player player) {
        if (isEpicFightUsable() && isUsingEpicFightStamina(Parkourability.get(player))) {
            return new EpicFightStaminaHandler();
        }
        return new ParCoolStaminaHandler();
    }

    @Nullable
    PlayerPatch<?> getPlayerPatch(Player player) {
        if (!isInstalled() || !isEpicFightUsable()) {
            return null;
        }
        return EpicFightCapabilities.getEntityPatch(player, PlayerPatch.class);
    }

    public boolean isUsingEpicFightStamina(Parkourability parkourability) {
        if (!isInstalled() || !isEpicFightUsable()) return false;
        var forcedStamina = parkourability.getServerLimitation().getForcedStamina();
        if (forcedStamina == StaminaType.EPIC_FIGHT) return true;
        return forcedStamina == StaminaType.NONE && parkourability.getClientInfo().getRequestedStamina() == StaminaType.EPIC_FIGHT;
    }
}
