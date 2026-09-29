package com.alrex.parcool.utilities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class EntityUtil {
	public static void addVelocity(Entity entity, Vec3 vec) {
		entity.setDeltaMovement(entity.getDeltaMovement().add(vec));
	}

	/**
	 * Replaces {@code Entity#isInWaterOrBubble()}, which 1.21.5 removed.
	 *
	 * <p>1.21.1 answered {@code isInWater() || getBubbleColumn()}, where {@code getBubbleColumn} was a
	 * flag the entity carried while {@code BubbleColumnBlock#entityInside} was running. That flag no
	 * exists on 1.21.5 and later - the column is applied imperatively every tick from
	 * {@code entityInside} and nothing stores it - so the block test below is the equivalent query.
	 * {@code entityInside} fires for the block the entity occupies and for the one directly below it
	 * (the "above the column" case), which is exactly the two positions checked here.
	 */
	public static boolean isInWaterOrBubble(Entity entity) {
		if (entity.isInWater()) return true;
		BlockPos pos = entity.blockPosition();
		BlockPos below = pos.below();
		return entity.level().getBlockState(pos).is(Blocks.BUBBLE_COLUMN)
				|| entity.level().getBlockState(below).is(Blocks.BUBBLE_COLUMN);
	}
}
