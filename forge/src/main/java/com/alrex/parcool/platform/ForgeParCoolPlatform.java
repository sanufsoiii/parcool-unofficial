package com.alrex.parcool.platform;

import com.alrex.parcool.common.action.impl.Dodge;
import com.alrex.parcool.common.data.Parkourability;
import com.alrex.parcool.common.stamina.IParCoolStaminaHandler;
import com.alrex.parcool.extern.betterthirdperson.BetterThirdPersonManager;
import com.alrex.parcool.extern.epicfight.EpicFightManager;
import com.alrex.parcool.extern.paraglider.ParagliderManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeMod;

import javax.annotation.Nullable;

/**
 * Forge implementation of {@link ParCoolPlatform}.
 *
 * <p>Forge 1.20.1 owns every API the optional integrations need, so unlike the Fabric side nothing
 * here has to report "not available": the two Forge-added attributes are read straight off the
 * player, the Forge-only forced pose is the vanilla-visible one, and the integrations in
 * {@code com.alrex.parcool.extern} are real implementations.
 */
public class ForgeParCoolPlatform implements ParCoolPlatform {

    @Override
    @Nullable
    public AttributeInstance getSwimSpeedAttribute(Player player) {
        return player.getAttribute(ForgeMod.SWIM_SPEED.get());
    }

    @Override
    @Nullable
    public AttributeInstance getGravityAttribute(Player player) {
        return player.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
    }

    @Override
    public float getFriction(BlockState state, Level level, BlockPos pos, @Nullable Entity entity) {
        // Forge's entity-aware variant (IForgeBlockState), fetched through the state so a modded
        // block can answer per entity.
        return state.getFriction(level, pos, entity);
    }

    @Override
    @Nullable
    public Pose getForcedPose(Player player) {
        return player.getForcedPose();
    }

    @Override
    public void setForcedPose(Player player, @Nullable Pose pose) {
        // Forge's own Player#setForcedPose: it makes the pose override win over the vanilla
        // clearance check, which is what Crawl/Slide need to hold Pose.SWIMMING.
        player.setForcedPose(pose);
    }

    @Override
    public void initClientIntegrations() {
        // Subscribes the Paraglider HUD/animation hooks; without this call the manager exists but
        // nobody listens, because the mod-agnostic AdditionalMods path only owns ShoulderSurfing.
        ParagliderManager.getInstance().initInClient();
    }

    @Override
    public boolean isBetterThirdPersonCameraDecoupled() {
        return BetterThirdPersonManager.getInstance().isCameraDecoupled();
    }

    @Override
    public IParCoolStaminaHandler newParagliderStaminaHandler(Player player) {
        return ParagliderManager.getInstance().newParagliderStaminaHandlerFor(player);
    }

    @Override
    public boolean isUsingParagliderStamina(Parkourability parkourability) {
        return ParagliderManager.getInstance().isUsingParagliderStamina(parkourability);
    }

    @Override
    public boolean isFallingWithParaglider(Player player) {
        return ParagliderManager.getInstance().isFallingWithParaglider(player);
    }

    @Override
    public IParCoolStaminaHandler newEpicFightStaminaHandler(Player player) {
        return EpicFightManager.getInstance().newStaminaHandlerFor(player);
    }

    @Override
    public boolean isUsingEpicFightStamina(Parkourability parkourability) {
        return EpicFightManager.getInstance().isUsingEpicFightStamina(parkourability);
    }

    @Override
    public Dodge.DodgeDirection handleDodgeCameraRotation(Dodge.DodgeDirection direction) {
        return BetterThirdPersonManager.getInstance().handleCustomCameraRotationForDodge(direction);
    }
}
