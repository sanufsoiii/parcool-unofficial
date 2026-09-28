package com.alrex.parcool.client.action;

import com.alrex.parcool.client.stamina.StaminaOps;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.data.ParCoolDataKeys;
import com.alrex.parcool.common.data.Parkourability;
import com.alrex.parcool.common.data.client.Animation;
import com.alrex.parcool.common.data.client.LocalStamina;
import com.alrex.parcool.common.event.CompatEvents;
import com.alrex.parcool.config.ParCoolConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The client-only half of {@link com.alrex.parcool.common.action.ActionProcessor}.
 *
 * <h2>Why it is a separate class</h2>
 * {@code ActionProcessor} is instantiated on a dedicated server (it is the single
 * {@code TickEvent.PLAYER_POST} listener) and NeoForge's {@code RuntimeDistCleaner} aborts the server
 * as soon as a class loaded there calls a member on a {@code @OnlyIn(Dist.CLIENT)} receiver. Every
 * call that needs {@code Minecraft}, {@code LocalPlayer} or the animation/stamina caches therefore
 * lives here, and {@code ActionProcessor} only calls into it with server-safe parameter types.
 */
public final class ClientActionProcessor {

    /** Cooldown between two stamina reports to the server, in ticks. */
    private static int staminaSyncCoolTimeTick = 5;

    private ClientActionProcessor() {
    }

    public static void preprocess(Player player, Parkourability parkourability) {
        if (!(player instanceof AbstractClientPlayer clientPlayer)) return;
        Animation animation = Animation.get(clientPlayer);
        if (animation == null) return;
        animation.tick(clientPlayer, parkourability);
    }

    public static void postProcess(Player player, Parkourability parkourability) {
        if (!player.isLocalPlayer()) return;
        LocalPlayer localPlayer = (LocalPlayer) player;
        if (!parkourability.limitationIsNotSynced()) {
            LocalStamina.get(localPlayer).onTick(localPlayer);
            if (--staminaSyncCoolTimeTick <= 0) {
                StaminaOps.sync(localPlayer, ParCoolDataKeys.getStamina(localPlayer));
                staminaSyncCoolTimeTick = 5;
            }
        }

        boolean penalise = ParCoolConfig.Client.Booleans.EnableStaminaExhaustionPenalty.get()
                && LocalStamina.get(localPlayer).imposeExhaustionPenalty(localPlayer);
        if (penalise) {
            player.setSprinting(false);
            AttributeInstance attribute = localPlayer.getAttribute(Attributes.MOVEMENT_SPEED);
            if (!attribute.hasModifier(STAMINA_DEPLETED_SLOWNESS_MODIFIER_ID)) {
                attribute.addTransientModifier(STAMINA_DEPLETED_SLOWNESS_MODIFIER);
            }
        } else {
            AttributeInstance attribute = localPlayer.getAttribute(Attributes.MOVEMENT_SPEED);
            attribute.removeModifier(STAMINA_DEPLETED_SLOWNESS_MODIFIER_ID);
        }
    }

    public static void consumeStamina(Player player, int value) {
        if (!player.isLocalPlayer()) return;
        LocalPlayer localPlayer = (LocalPlayer) player;
        LocalStamina.get(localPlayer).consume(localPlayer, value);
    }

    public static void onRenderFrame(CompatEvents.RenderFrameEvent.Pre event) {
        Player clientPlayer = Minecraft.getInstance().player;
        if (clientPlayer == null) return;
        for (Player player : clientPlayer.level().players()) {
            Parkourability parkourability = Parkourability.get(player);
            // continue, not return: one player without ParCool data must not stop the render update of
            // every other player in the world.
            if (parkourability == null) continue;
            List<Action> actions = parkourability.getList();
            for (Action action : actions) {
                action.onRenderTick(event, player, parkourability);
            }
            Animation animation = Animation.get(player);
            if (animation == null) continue;
            // Animator#getTick() only advances through Animation#tick, which upstream reached solely
            // through the local player's TickEvent.PLAYER_POST. Every animator that is started by
            // onStartInOtherClient - Dodge, Roll, ClimbUp, Tap, Vault, VerticalWallRun, JumpFromBar,
            // Dive - therefore froze at getTick() == 0 on every *other* client, shouldRemoved() never
            // fired, and the remote player was stuck in the first frame of the action forever. Ticking
            // here is what makes an action play out to completion for everyone who can see it.
            if (player instanceof AbstractClientPlayer avatarPlayer) {
                animation.tick(avatarPlayer, parkourability);
            }
            animation.onRenderTick(event, player, parkourability);
        }
    }

    public static void onViewRender(CompatEvents.ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;
        Animation animation = Animation.get(player);
        if (animation == null) return;
        animation.cameraSetup(event, player, parkourability);
    }

    private static final Identifier STAMINA_DEPLETED_SLOWNESS_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(com.alrex.parcool.ParCool.MOD_ID, "exhausted.speed");

    private static final AttributeModifier STAMINA_DEPLETED_SLOWNESS_MODIFIER = new AttributeModifier(
            STAMINA_DEPLETED_SLOWNESS_MODIFIER_ID,
            -0.05,
            AttributeModifier.Operation.ADD_VALUE
    );
}
