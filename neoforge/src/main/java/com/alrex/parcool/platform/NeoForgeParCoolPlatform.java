package com.alrex.parcool.platform;

import com.alrex.parcool.common.action.impl.Dodge;
import com.alrex.parcool.common.data.Parkourability;
import com.alrex.parcool.common.stamina.IParCoolStaminaHandler;
import com.alrex.parcool.common.stamina.handlers.ParCoolStaminaHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForgeMod;

import javax.annotation.Nullable;

/**
 * NeoForge implementation of {@link ParCoolPlatform}: the loader that still owns every API the
 * optional integrations need, so everything here can use the real NeoForge calls.
 */
public class NeoForgeParCoolPlatform implements ParCoolPlatform {

    @Override
    @Nullable
    public AttributeInstance getSwimSpeedAttribute(Player player) {
        return player.getAttribute(NeoForgeMod.SWIM_SPEED);
    }

    @Override
    @Nullable
    public DamageSource createDamageSource(Player player, ResourceKey<DamageType> type) {
        return player.level().damageSources().source(type);
    }

    @Override
    public float getFriction(BlockState state, Level level, BlockPos pos, @Nullable Entity entity) {
        return state.getFriction(level, pos, entity);
    }

    @Override
    @Nullable
    public Pose getForcedPose(Player player) {
        return player.getForcedPose();
    }

    @Override
    public void setForcedPose(Player player, @Nullable Pose pose) {
        player.setForcedPose(pose);
    }

    /** Single instance: the Fabric implementation de-duplicates registrations per payload class,
     *  which only works if the same object is returned for the whole mod lifetime. */
    private static final com.alrex.parcool.platform.ParCoolNetwork NETWORK =
            new com.alrex.parcool.platform.NeoForgeParCoolNetwork();

    @Override
    public com.alrex.parcool.platform.ParCoolNetwork getNetwork() {
        return NETWORK;
    }

    @Override
    public boolean isBetterThirdPersonCameraDecoupled() {
        return com.alrex.parcool.extern.betterthirdperson.BetterThirdPersonManager.getInstance()
                .isCameraDecoupled();
    }

    @Override
    public IParCoolStaminaHandler newParagliderStaminaHandler(Player player) {
        return com.alrex.parcool.extern.paraglider.ParagliderManager.getInstance()
                .newParagliderStaminaHandlerFor(player);
    }

    @Override
    public boolean isUsingParagliderStamina(Parkourability parkourability) {
        return com.alrex.parcool.extern.paraglider.ParagliderManager.getInstance()
                .isUsingParagliderStamina(parkourability);
    }

    @Override
    public boolean isFallingWithParaglider(Player player) {
        return com.alrex.parcool.extern.paraglider.ParagliderManager.getInstance()
                .isFallingWithParaglider(player);
    }

    @Override
    public IParCoolStaminaHandler newEpicFightStaminaHandler(Player player) {
        return com.alrex.parcool.extern.epicfight.EpicFightManager.getInstance().newStaminaHandlerFor(player);
    }

    @Override
    public boolean isUsingEpicFightStamina(Parkourability parkourability) {
        return com.alrex.parcool.extern.epicfight.EpicFightManager.getInstance()
                .isUsingEpicFightStamina(parkourability);
    }

    @Override
    public Dodge.DodgeDirection handleDodgeCameraRotation(Dodge.DodgeDirection direction) {
        return com.alrex.parcool.extern.betterthirdperson.BetterThirdPersonManager.getInstance()
                .handleCustomCameraRotationForDodge(direction);
    }
}
