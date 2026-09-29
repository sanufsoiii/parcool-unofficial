package com.alrex.parcool.common.handlers;

import com.alrex.parcool.common.data.Parkourability;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;

/**
 * Carries the two synced documents across a death.
 *
 * <p>Deliberately only {@code ClientSetting} and {@code ServerLimitation} are copied. The stamina
 * itself is not: {@code ReadonlyStamina} is persisted per player through the NBT hooks and
 * {@code ParCoolData} restores it on respawn, while the action state must not survive a death - the
 * actions are re-evaluated by {@code ActionProcessor} as soon as the clone ticks. This matches
 * upstream, which also only carried the two documents across a clone.
 */
public class PlayerCloneHandler {
	public static void onClone(Player original, Player clone, boolean wasDeath) {
        if (wasDeath && clone instanceof ServerPlayer) {
            Parkourability pFrom = Parkourability.get(original);
            Parkourability pTo = Parkourability.get(clone);
            if (pFrom != null && pTo != null) {
                pTo.CopyFrom(pFrom);
            }
        }
    }
}
