package com.alrex.parcool.platform;

import com.alrex.parcool.common.action.impl.Dodge;
import com.alrex.parcool.common.data.Parkourability;
import com.alrex.parcool.common.stamina.IParCoolStaminaHandler;
import com.alrex.parcool.common.stamina.handlers.ParCoolStaminaHandler;
import com.alrex.parcool.compat.IPlayerForcedPose;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Fabric implementation of {@link ParCoolPlatform}.
 *
 * <p>Paraglider, EpicFight and BetterThirdPerson have no 1.21.1 Fabric build, so their hooks report
 * "absent" and ParCool falls back to its own stamina and camera behaviour — exactly as it does when
 * the mod is not installed at all. The swim speed attribute is NeoForge-only, so FastSwim's
 * server-side speed bonus is a no-op here.
 */
public class FabricParCoolPlatform implements ParCoolPlatform {

    @Override
    @Nullable
    public AttributeInstance getSwimSpeedAttribute(Player player) {
        return null;
    }

    @Override
    @Nullable
    public DamageSource createDamageSource(Player player, ResourceKey<DamageType> type) {
        // Vanilla 1.21.1 has no Level#damageSources(); DamageSource takes the DamageType directly.
        Holder<DamageType> holder = player.level().registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(type);
        return new DamageSource(holder);
    }

    @Override
    public float getFriction(BlockState state, Level level, BlockPos pos, @Nullable Entity entity) {
        return state.getBlock().getFriction();
    }

    @Override
    @Nullable
    public Pose getForcedPose(Player player) {
        return ((IPlayerForcedPose) player).parcool$getForcedPose();
    }

    @Override
    public void setForcedPose(Player player, @Nullable Pose pose) {
        // Vanilla 1.21.1 has no forced-pose concept; the mixin re-adds NeoForge's one, without which
        // Crawl/Slide cannot hold Pose.SWIMMING and Entity#isVisuallyCrawling() never reports true.
        ((IPlayerForcedPose) player).parcool$setForcedPose(pose);
    }

    /** Single instance: the Fabric implementation de-duplicates registrations per payload class,
     *  which only works if the same object is returned for the whole mod lifetime. */
    private static final com.alrex.parcool.platform.ParCoolNetwork NETWORK =
            new com.alrex.parcool.platform.FabricParCoolNetwork();

    @Override
    public com.alrex.parcool.platform.ParCoolNetwork getNetwork() {
        return NETWORK;
    }

    @Override
    public boolean isBetterThirdPersonCameraDecoupled() {
        return false;
    }

    @Override
    public IParCoolStaminaHandler newParagliderStaminaHandler(Player player) {
        return new ParCoolStaminaHandler();
    }

    @Override
    public boolean isUsingParagliderStamina(Parkourability parkourability) {
        return false;
    }

    @Override
    public boolean isFallingWithParaglider(Player player) {
        return false;
    }

    @Override
    public IParCoolStaminaHandler newEpicFightStaminaHandler(Player player) {
        return new ParCoolStaminaHandler();
    }

    @Override
    public boolean isUsingEpicFightStamina(Parkourability parkourability) {
        return false;
    }

    @Override
    public Dodge.DodgeDirection handleDodgeCameraRotation(Dodge.DodgeDirection direction) {
        return direction;
    }
}
