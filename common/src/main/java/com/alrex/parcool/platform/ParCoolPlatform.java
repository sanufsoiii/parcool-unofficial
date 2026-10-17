package com.alrex.parcool.platform;

import com.alrex.parcool.common.action.impl.Dodge;
import com.alrex.parcool.common.data.Parkourability;
import com.alrex.parcool.common.stamina.IParCoolStaminaHandler;
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

import javax.annotation.Nullable;

/**
 * Everything ParCool needs from a specific loader.
 *
 * <h2>Why this exists</h2>
 * <ul>
 *     <li>{@code net.minecraftforge.common.ForgeMod#SWIM_SPEED} is a Forge-added entity
 *     attribute ({@code forge:swim_speed}); vanilla Fabric has no equivalent, so FastSwim's
 *     server-side speed bonus has nothing to modify there.</li>
 *     <li>{@code Level#damageSources()} is a Forge-added accessor, so resolving a
 *     {@link DamageSource} from a {@link DamageType} key needs a loader specific call.</li>
 *     <li><b>Paraglider</b>, <b>EpicFight</b> and <b>BetterThirdPerson</b> ship Forge-only jars
 *     for 1.20.1, so their code lives in the {@code forge} module and is reached through this
 *     interface. Fabric ships no-op implementations below.</li>
 * </ul>
 *
 * <p>Implementations are discovered with {@link java.util.ServiceLoader}, so {@code common} stays
 * free of {@code net.fabricmc.*} and {@code net.minecraftforge.*} imports. See
 * {@code fabric/.../platform/FabricParCoolPlatform} and
 * {@code forge/.../platform/ForgeParCoolPlatform}.
 */
public interface ParCoolPlatform {

    /**
     * @return the player's {@code neoforgeswim_speed} attribute, or {@code null} on loaders that
     * do not have it.
     */
    @Nullable
    AttributeInstance getSwimSpeedAttribute(Player player);

    /**
     * 1.20.1 vanilla has no gravity attribute at all ({@code Attributes.GRAVITY} only appears in
     * 1.21): gravity is the hard-coded {@code LivingEntity.DEFAULT_BASE_GRAVITY}, and Forge turns it
     * into {@code forge:entity_gravity}. RideZipline needs the *effective* value, so the loader
     * supplies the attribute where one exists.
     *
     * @return the player's gravity attribute, or {@code null} if the loader has none.
     */
    @Nullable
    AttributeInstance getGravityAttribute(Player player);

    /**
     * The gravity actually pulling the player down: the loader's gravity attribute where there is
     * one, vanilla's fixed constant otherwise.
     */
    default double getGravity(Player player) {
        AttributeInstance attribute = getGravityAttribute(player);
        return attribute != null ? attribute.getValue() : net.minecraft.world.entity.LivingEntity.DEFAULT_BASE_GRAVITY;
    }

    /**
     * @return a damage source for the given damage type key, or {@code null} if it cannot be built.
     */
    @Nullable
    DamageSource createDamageSource(Player player, ResourceKey<DamageType> type);

    /**
     * Entity-aware block friction. NeoForge adds {@code BlockState#getFriction(Level, BlockPos, Entity)}
     * so that, for example, a sneaking player slides less on ice; vanilla only offers the
     * state-level {@code Block#getFriction()}.
     */
    float getFriction(BlockState state, Level level, BlockPos pos, @Nullable Entity entity);

    // ------------------------------------------------------------------
    // forced pose (NeoForge-only Player#getForcedPose / #setForcedPose)
    // ------------------------------------------------------------------

    @Nullable
    Pose getForcedPose(Player player);

    void setForcedPose(Player player, @Nullable Pose pose);

    // ------------------------------------------------------------------
    // BetterThirdPerson (NeoForge only)
    // ------------------------------------------------------------------

    boolean isBetterThirdPersonCameraDecoupled();

    // ------------------------------------------------------------------
    // Paraglider (NeoForge only)
    // ------------------------------------------------------------------

    IParCoolStaminaHandler newParagliderStaminaHandler(Player player);

    boolean isUsingParagliderStamina(Parkourability parkourability);

    boolean isFallingWithParaglider(Player player);

    // ------------------------------------------------------------------
    // EpicFight (NeoForge only)
    // ------------------------------------------------------------------

    IParCoolStaminaHandler newEpicFightStaminaHandler(Player player);

    boolean isUsingEpicFightStamina(Parkourability parkourability);

    // ------------------------------------------------------------------
    // shared
    // ------------------------------------------------------------------

    /**
     * Lets a third-party shoulder-camera mod redirect the Dodge camera animation.
     *
     * @return {@code Front} when the camera is decoupled, otherwise {@code direction} unchanged.
     */
    default Dodge.DodgeDirection handleDodgeCameraRotation(Dodge.DodgeDirection direction) {
        return direction;
    }
}
