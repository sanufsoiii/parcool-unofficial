package com.alrex.parcool.common.info;

import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.data.client.LocalStamina;
import com.alrex.parcool.common.stamina.StaminaType;
import com.alrex.parcool.config.ParCoolConfig;
import net.minecraft.client.player.LocalPlayer;

public class ActionInfo {
    public ActionInfo() {
    }

    public ClientSetting getClientSetting() {
        return clientSetting;
    }

    public void setClientSetting(ClientSetting clientSetting) {
        this.clientSetting = clientSetting;
	}

    private ClientSetting clientSetting = ClientSetting.UNSYNCED_INSTANCE;

    public ServerLimitation getServerLimitation() {
        return serverLimitation;
	}

    public void setServerLimitation(ServerLimitation serverLimitation) {
        this.serverLimitation = serverLimitation;
    }

    private ServerLimitation serverLimitation = ServerLimitation.UNSYNCED_INSTANCE;

	/**
	 * The server is the floor for every numeric limit and the veto for every action: the client decides
	 * what it wants, {@link ServerLimitation} decides what is allowed. Reading only the client setting
	 * here would let a client with {@code staminaConsumptionOf = 0} undercut the server's minimum, and
	 * a client with a permissive {@code getPossibilityOf} could re-enable an action the server banned -
	 * both of which is what the AND and the Math.max in {@link #getStaminaConsumptionOf} exist for.
	 */
	public boolean can(Class<? extends Action> action) {
        return getClientSetting().get(ParCoolConfig.Client.Booleans.ParCoolIsActive)
                && getClientSetting().getPossibilityOf(action)
                && getServerLimitation().isPermitted(action);
	}

    public StaminaType getStaminaType() {
        var forcedStamina = getServerLimitation().getForcedStamina();
        if (forcedStamina == StaminaType.NONE) {
            var requestedStamina = getClientSetting().getRequestedStamina();
            if (requestedStamina == StaminaType.NONE) {
                return isInfiniteStaminaPermitted() ? StaminaType.NONE : StaminaType.PARCOOL;
            }
            return requestedStamina;
        }
        return forcedStamina;
    }

	/**
	 * Server-side consumption is a *floor*, not a default: Math.max (rather than the client's value)
	 * is what stops a client that reports 0 from making an action free against a server that charges
	 * for it. Reported values are additionally clamped in {@code ServerPayloadGuard}.
	 */
	public int getStaminaConsumptionOf(Class<? extends Action> action) {
        return Math.max(
                getClientSetting().getStaminaConsumptionOf(action),
                getServerLimitation().getStaminaConsumptionOf(action)
        );
	}

    public int getStaminaRecoveryLimit() {
        return getServerLimitation().get(ParCoolConfig.Server.Integers.MaxStaminaRecovery);
	}

    public int getMaxStaminaLimit() {
        return getServerLimitation().get(ParCoolConfig.Server.Integers.MaxStaminaLimit);
	}

    public boolean isStaminaInfinite(LocalStamina stamina, LocalPlayer player) {
        return stamina.isInfinite(player);
	}

	public boolean isInfiniteStaminaPermitted() {
        return serverLimitation.get(ParCoolConfig.Server.Booleans.AllowInfiniteStamina);
	}

    public void updateStaminaType(LocalStamina stamina, LocalPlayer player) {
        stamina.changeType(player, getStaminaType());
    }
}
