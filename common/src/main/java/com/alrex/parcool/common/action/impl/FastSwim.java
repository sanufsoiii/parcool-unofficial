package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.client.animation.impl.FastSwimAnimator;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.data.client.Animation;
import com.alrex.parcool.common.data.Parkourability;
import com.alrex.parcool.common.info.ActionInfo;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.platform.PlatformServices;

import java.nio.ByteBuffer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;

public class FastSwim extends Action {
    // 1.20.1 identifies modifiers by UUID; a ResourceLocation-keyed modifier is 1.21 API.
    private static final java.util.UUID FAST_SWIM_MODIFIER_ID = java.util.UUID.fromString("2c2f6cab-7a5f-4d22-8b3f-4c3d0f1b6b02");
    private static final String FAST_SWIM_MODIFIER_NAME = ParCool.MOD_ID + ".modifier.speed.fastswim";
    private double speedModifier = 0;
    private boolean toggleStatus;

    public double getSpeedModifier(ActionInfo info) {
        return Math.min(
                info.getClientSetting().get(ParCoolConfig.Client.Doubles.FastSwimSpeedModifier),
                info.getServerLimitation().get(ParCoolConfig.Server.Doubles.MaxFastSwimSpeedModifier)
        );
    }

    @Override
    public boolean canStart(Player player, Parkourability parkourability, ByteBuffer startInfo) {
        return canContinue(player, parkourability);
    }

    @Override
    public boolean canContinue(Player player, Parkourability parkourability) {
        return (player.isInWaterOrBubble()
                && player.getVehicle() == null
                && !player.isFallFlying()
                && player.isSprinting()
                && player.isSwimming()
                && !parkourability.get(FastRun.class).isDoing()
                && ((ParCoolConfig.Client.getInstance().FastRunControl.get() == FastRun.ControlType.PressKey && KeyBindings.isDown(KeyBindings.getKeyFastRunning()))
                || (ParCoolConfig.Client.getInstance().FastRunControl.get() == FastRun.ControlType.Toggle && toggleStatus)
                || ParCoolConfig.Client.getInstance().FastRunControl.get() == FastRun.ControlType.Auto)
        );
    }

    @Override
    public void onClientTick(Player player, Parkourability parkourability) {
        if (player.isLocalPlayer()) {
            if (ParCoolConfig.Client.getInstance().FastRunControl.get() == FastRun.ControlType.Toggle
                    && parkourability.getAdditionalProperties().getSprintingTick() > 3
                    && player.isInWaterOrBubble()
                    && player.isSwimming()
            ) {
                if (KeyRecorder.keyFastRunning.isPressed())
                    toggleStatus = !toggleStatus;
            } else {
                toggleStatus = false;
            }
        }
    }

    @Override
    public void onWorkingTickInClient(Player player, Parkourability parkourability) {
        Animation animation = Animation.get(player);
        if (animation != null && !animation.hasAnimator()) {
            animation.setAnimator(new FastSwimAnimator());
        }
    }

    @Override
    public void onStartInServer(Player player, Parkourability parkourability, ByteBuffer startData) {
        speedModifier = parkourability.get(FastSwim.class).getSpeedModifier(parkourability.getActionInfo());
    }

    @Override
    public void onServerTick(Player player, Parkourability parkourability) {
        // null on loaders without the NeoForge swim-speed attribute -> FastSwim speed bonus is a no-op there
        AttributeInstance attr = PlatformServices.get().getSwimSpeedAttribute(player);
        if (attr == null) return;
        if (attr.getModifier(FAST_SWIM_MODIFIER_ID) != null) attr.removeModifier(FAST_SWIM_MODIFIER_ID);
        if (isDoing()) {
            player.setSprinting(true);
            attr.addTransientModifier(new AttributeModifier(
                    FAST_SWIM_MODIFIER_ID,
                    FAST_SWIM_MODIFIER_NAME,
                    speedModifier / 8d,
                    AttributeModifier.Operation.ADDITION
            ));
        }
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.OnWorking;
    }
}
