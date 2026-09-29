package com.alrex.parcool.common.handlers;

import com.alrex.parcool.common.action.impl.HideInBlock;
import com.alrex.parcool.common.data.Parkourability;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class PlayerVisibilityHandler {
    /**
     * Was {@code LivingEvent.LivingVisibilityEvent} (which multiplied the visibility score by 0.1).
     * NeoForge's variant has no Architectury counterpart, so the factor is applied by
     * {@code mixin.common.PlayerInteractionVisibilityMixin}, which injects into
     * {@code LivingEntity#getVisibilityPercent} - the method NeoForge fired the event from, and the
     * closer of the two hooks 1.21.8 still has. 1.21.1 used
     * {@code Player#canInteractWithEntity(Entity, double)}, which is still present here but is the
     * entity tracker's range predicate rather than the visibility score, so it fires on a different
     * schedule and would hide the player where it does not matter.
     */
    public static boolean isHiddenFrom(Entity viewer, Player target) {
        Parkourability parkourability = Parkourability.get(target);
        if (parkourability == null) return false;
        return parkourability.get(HideInBlock.class).isDoing();
    }
}
