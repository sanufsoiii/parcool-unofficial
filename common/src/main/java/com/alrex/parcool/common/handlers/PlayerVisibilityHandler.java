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
     * only visibility hook 1.21.10 still has.
     */
    public static boolean isHiddenFrom(Entity viewer, Player target) {
        Parkourability parkourability = Parkourability.get(target);
        if (parkourability == null) return false;
        return parkourability.get(HideInBlock.class).isDoing();
    }
}
