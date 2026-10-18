package com.alrex.parcool.extern.paraglider;

import com.alrex.parcool.api.client.gui.ParCoolHUDEvent;
import com.alrex.parcool.api.event.ParCoolEventBus;
import com.alrex.parcool.api.unstable.animation.ParCoolAnimationInfoEvent;
import com.alrex.parcool.common.data.Parkourability;
import com.alrex.parcool.extern.AdditionalMods;
import net.minecraft.client.Minecraft;

/**
 * Subscribes to ParCool's own event bus for the two places where Paraglider has to take over.
 *
 * <p>Upstream was a pair of {@code @SubscribeEvent} listeners on the Forge bus and was never
 * registered through the mod-agnostic path (only ShoulderSurfing was), so both hooks were dead.
 * They are announced through {@code ParCoolEventBus} here, and
 * {@code ParCoolPlatform#initClientIntegrations()} subscribes them on Forge.
 */
public class EventConsumerForParaglider {

    private EventConsumerForParaglider() {
    }

    public static void register() {
        ParCoolEventBus.hudEvents().register(EventConsumerForParaglider::onHUDRender);
        ParCoolEventBus.animationInfoEvents().register(EventConsumerForParaglider::onUpdateAnimateInfo);
    }

    /**
     * Paraglider draws its own stamina wheel, so ParCool's bar would be a second, disagreeing one.
     */
    public static void onHUDRender(ParCoolHUDEvent event) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        var parkourability = Parkourability.get(player);
        if (parkourability == null) return;
        if (AdditionalMods.isUsingParagliderStamina(parkourability)) {
            event.setCanceled(true);
        }
    }

    public static void onUpdateAnimateInfo(ParCoolAnimationInfoEvent event) {
        if (AdditionalMods.isFallingWithParaglider(event.getPlayer())) {
            event.getOption().cancelAnimation();
        }
    }
}
