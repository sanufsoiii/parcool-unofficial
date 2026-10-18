package com.alrex.parcool.common.item.zipline;

import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.common.block.zipline.ZiplineHookBlock;
import com.alrex.parcool.common.block.zipline.ZiplineHookTileEntity;
import com.alrex.parcool.common.block.zipline.ZiplineInfo;
import com.alrex.parcool.common.zipline.Zipline;
import com.alrex.parcool.common.zipline.ZiplineType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class ZiplineRopeItem extends Item {
    public static class RopeColor implements ItemColor {
        @Override
        public int getColor(@Nonnull ItemStack itemStack, int i) {
            return i > 0 ? -1 : ZiplineRopeItem.getColor(itemStack);
        }
    }

    public ZiplineRopeItem(Properties p_i48487_1_) {
        super(p_i48487_1_);
    }

    /**
     * 1.20.1 stores the rope state in the stack's NBT instead of 1.21's data components; the keys are
     * the ones upstream uses on this Minecraft version ({@code Tile_X}/{@code Tile_Y}/{@code Tile_Z},
     * {@code color}, {@code zipline_type}), so a world upgraded from the original Forge ParCool keeps
     * its ropes' bound hook and colour.
     *
     * <p>The alpha is set here, unlike upstream's {@code 0x4C7FE6}: {@code ItemColor#getColor} returns
     * ARGB, so an upstream-dyed rope renders fully transparent.
     */
    public static final int DEFAULT_COLOR = 0xFF4C7FE6;

    private static final String TAG_POS_X = "Tile_X";
    private static final String TAG_POS_Y = "Tile_Y";
    private static final String TAG_POS_Z = "Tile_Z";
    private static final String TAG_COLOR = "color";
    private static final String TAG_ZIPLINE_TYPE = "zipline_type";

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, @Nonnull List<Component> lines, @Nonnull TooltipFlag tooltipFlag) {
        BlockPos pos = getBlockPosition(stack);

        if (pos != null) {
            lines.add(Component.translatable("parcool.gui.text.zipline.bind_pos", pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.YELLOW));
        } else {
            lines.add(Component.translatable("parcool.gui.text.zipline.not_bound").withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.empty());
        lines.add(Component.translatable("parcool.gui.text.zipline.tension", getZiplineType(stack).getTranslationName()).withStyle(ChatFormatting.GRAY));
        if (hasCustomColor(stack)) {
            lines.add(Component.translatable("parcool.gui.text.zipline.colored").withStyle(ChatFormatting.BLUE));
        }
    }

    @Nonnull
    @Override
    public InteractionResult useOn(@Nonnull UseOnContext context) {
        ItemStack stack = context.getItemInHand();

        // First Point is already registered
        if (hasBlockPosition(stack)) {
            // Second Point is Found
            if (context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof ZiplineHookBlock) {
                BlockPos start = getBlockPosition(stack);
                if (start == null) return InteractionResult.FAIL;

                BlockPos end = context.getClickedPos();
                if (start.equals(end)) return InteractionResult.PASS;
                double horizontalDistSqr = Mth.square(start.getX() - end.getX()) + Mth.square(start.getZ() - end.getZ());
                if (horizontalDistSqr > Zipline.MAXIMUM_HORIZONTAL_DISTANCE * Zipline.MAXIMUM_HORIZONTAL_DISTANCE) {
                    if (context.getLevel().isClientSide()) {
                        Player player = context.getPlayer();
                        if (player != null) {
                            player.displayClientMessage(Component.translatable("parcool.message.zipline.too_far"), true);
                        }
                    }
                    return InteractionResult.FAIL;
                } else {
                    double verticalDist = Math.abs(end.getY() - start.getY());
                    if (verticalDist * Mth.fastInvSqrt(horizontalDistSqr) > 1. || verticalDist > Zipline.MAXIMUM_VERTICAL_DISTANCE) {
                        if (context.getLevel().isClientSide()) {
                            Player player = context.getPlayer();
                            if (player != null) {
                                player.displayClientMessage(Component.translatable("parcool.message.zipline.too_steep"), true);
                            }
                        }
                        return InteractionResult.FAIL;
                    }
                }

                BlockEntity startEntity = context.getLevel().getBlockEntity(start);
                BlockEntity endEntity = context.getLevel().getBlockEntity(end);
                if (startEntity instanceof ZiplineHookTileEntity startZipEntity && endEntity instanceof ZiplineHookTileEntity endZipEntity) {
                    if (getZiplineType(stack).getZipline(startZipEntity.getActualZiplinePoint(null), endZipEntity.getActualZiplinePoint(null)).conflictsWithSomething(context.getLevel())) {
                        Player player = context.getPlayer();
                        if (player != null) {
                            player.displayClientMessage(Component.translatable("parcool.message.zipline.obstacle_detected"), true);
                        }
                        return InteractionResult.FAIL;
                    }
                    if (!context.getLevel().isClientSide()) {
                        if (!startZipEntity.connectTo(endZipEntity, new ZiplineInfo(getZiplineType(stack), getColor(stack)))) {
                            Player player = context.getPlayer();
                            if (player != null) {
                                player.displayClientMessage(Component.translatable("parcool.message.zipline.already_exist"), true);
                            }
                            return InteractionResult.FAIL;
                        }
                        stack.shrink(1);
                    }
                    Player player = context.getPlayer();
                    if (player != null) {
                        player.playSound(SoundEvents.ZIPLINE_SET.get(), 1, 1);
                    }
                    removeBlockPosition(stack);
                    return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
                } else {
                    removeBlockPosition(stack);
                    if (context.getLevel().isClientSide()) {
                        Player player = context.getPlayer();
                        if (player != null) {
                            player.displayClientMessage(Component.translatable("parcool.message.zipline.point_not_found"), true);
                        }
                    }
                    return InteractionResult.FAIL;
                }
            }
            // Remove position info
            if (context.isSecondaryUseActive()) {
                if (context.getLevel().isClientSide()) {
                    Player player = context.getPlayer();
                    if (player != null) {
                        player.displayClientMessage(Component.translatable("parcool.message.zipline.reset_point"), true);
                    }
                }
                removeBlockPosition(stack);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        } else {
            BlockPos pos = context.getClickedPos();
            if (context.getLevel().getBlockState(pos).getBlock() instanceof ZiplineHookBlock) {
                setBlockPosition(stack, pos);
                if (context.getLevel().isClientSide()) {
                    Player player = context.getPlayer();
                    if (player != null) {
                        player.displayClientMessage(Component.translatable("parcool.message.zipline.set_point", pos.toShortString()), true);
                    }
                }
                return InteractionResult.SUCCESS;
            } else if (context.isSecondaryUseActive()) {
                changeZiplineType(stack);
                if (context.getLevel().isClientSide()) {
                    Player player = context.getPlayer();
                    if (player != null) {
                        player.displayClientMessage(Component.translatable("parcool.message.zipline.change_tension", getZiplineType(stack).getTranslationName()), true);
                    }
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
    }

    public static void setBlockPosition(ItemStack stack, BlockPos pos) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_POS_X, pos.getX());
        tag.putInt(TAG_POS_Y, pos.getY());
        tag.putInt(TAG_POS_Z, pos.getZ());
    }

    public static void removeBlockPosition(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(TAG_POS_X);
        tag.remove(TAG_POS_Y);
        tag.remove(TAG_POS_Z);
    }

    public static boolean hasBlockPosition(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_POS_X) && tag.contains(TAG_POS_Y) && tag.contains(TAG_POS_Z);
    }

    @Nullable
    public static BlockPos getBlockPosition(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_POS_X) || !tag.contains(TAG_POS_Y) || !tag.contains(TAG_POS_Z)) {
            return null;
        }
        return new BlockPos(tag.getInt(TAG_POS_X), tag.getInt(TAG_POS_Y), tag.getInt(TAG_POS_Z));
    }

    public static void setColor(ItemStack stack, int color) {
        CompoundTag tag = stack.getOrCreateTag();
        if (color != DEFAULT_COLOR) {
            tag.putInt(TAG_COLOR, color);
        } else {
            tag.remove(TAG_COLOR);
        }
    }

    public static int getColor(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_COLOR)) {
            return DEFAULT_COLOR;
        }
        return tag.getInt(TAG_COLOR);
    }

    public static boolean hasCustomColor(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_COLOR) && tag.getInt(TAG_COLOR) != DEFAULT_COLOR;
    }

    public static ZiplineType getZiplineType(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_ZIPLINE_TYPE)) {
            return ZiplineType.STANDARD;
        }
        // Read back defensively: the byte is written by this mod but a hand-edited or downgraded stack
        // can hold any value, and an ordinal outside the enum would be an ArrayIndexOutOfBoundsException
        // from the tooltip renderer.
        return ZiplineType.byOrdinal(tag.getByte(TAG_ZIPLINE_TYPE));
    }

    public static void changeZiplineType(ItemStack stack) {
        setZiplineType(stack, ZiplineType.values()[(getZiplineType(stack).ordinal() + 1) % ZiplineType.values().length]);
    }

    public static void setZiplineType(ItemStack stack, ZiplineType type) {
        stack.getOrCreateTag().putByte(TAG_ZIPLINE_TYPE, (byte) type.ordinal());
    }
}
