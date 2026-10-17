package com.alrex.parcool.common.network.payload;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.data.ParCoolDataKeys;
import com.alrex.parcool.common.data.Parkourability;
import com.alrex.parcool.common.data.ReadonlyStamina;
import com.alrex.parcool.common.network.MessageType;
import com.alrex.parcool.common.network.ParCoolMessage;
import com.alrex.parcool.common.network.NetworkRegistries;
import com.alrex.parcool.common.network.ServerPayloadGuard;
import com.alrex.parcool.common.network.StaminaSynchronizationBroadcaster;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public record StaminaPayload(UUID playerID, ReadonlyStamina stamina) implements ParCoolMessage {
    public static final MessageType<StaminaPayload> S2C
            = NetworkRegistries.s2c("payload.stamina", StaminaPayload::read);
    public static final MessageType<StaminaPayload> C2S
            = NetworkRegistries.c2s("payload.stamina", StaminaPayload::read);

    public void write(FriendlyByteBuf buf) {
        // The UUID used to travel as two VAR_LONGs on 1.21.1; FriendlyByteBuf#writeUUID is the same
        // varlong pair, so the wire contents are unchanged.
        buf.writeUUID(playerID());
        stamina().writeTo(buf);
    }

    public static StaminaPayload read(FriendlyByteBuf buf) {
        return new StaminaPayload(buf.readUUID(), ReadonlyStamina.readFrom(buf));
    }

    /** Client side: apply the server-broadcast stamina of the named player. */
    public void processPlayer(NetworkManager.PacketContext context) {
        Player player = context.getPlayer().level().getPlayerByUUID(this.playerID);
        if (player == null || player.isLocalPlayer()) return;
        ParCoolDataKeys.setStamina(player, this.stamina);
    }

    /** Server side: only the sender's own reported stamina is accepted, and only within the server's
     *  own limits. The client is authoritative for the *value* (it is the one that spends stamina), but
     *  not for the bounds: a reported max of {@code Integer.MAX_VALUE} would make the bar unspillable
     *  and a reported max below the server's minimum would make it unplayable. */
    public void processSelf(Player sender) {
        if (!ServerPayloadGuard.isSelf(sender, this.playerID)) return;
        int serverMax = Parkourability.get(sender).getActionInfo().getMaxStaminaLimit();
        int max = ServerPayloadGuard.clampReportedStamina(this.stamina.max(), serverMax, 0);
        int value = ServerPayloadGuard.clampReportedStamina(this.stamina.value(), max, 0);
        ParCoolDataKeys.setStamina(sender, new ReadonlyStamina(this.stamina.isExhausted() && value > 0, value, max));
    }

    public static void handleClient(StaminaPayload payload, NetworkManager.PacketContext context) {
        context.queue(() -> payload.processPlayer(context));
    }

    public static void handleServer(StaminaPayload payload, NetworkManager.PacketContext context) {
        context.queue(() -> {
            Player sender = context.getPlayer();
            // Upstream wrote the reported value into the slot of whoever playerID named, so a client
            // could set any player's server-side stamina.
            if (!ServerPayloadGuard.isSelf(sender, payload.playerID())) {
                ParCool.LOGGER.debug("Rejected stamina report for foreign player {}", payload.playerID);
                return;
            }
            payload.processSelf(sender);
            StaminaSynchronizationBroadcaster.add(payload.playerID, payload.stamina);
        });
    }
}
