package com.alrex.parcool.common.block.zipline;

import com.alrex.parcool.common.entity.zipline.ZiplineRopeEntity;
import com.alrex.parcool.common.item.Items;
import com.alrex.parcool.common.item.zipline.ZiplineRopeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class ZiplineHookTileEntity extends BlockEntity {

    private final TreeMap<BlockPos, ZiplineInfo> connections = new TreeMap<>();

    //OnlyIn Logical Server
    private final TreeMap<BlockPos, ZiplineRopeEntity> connectionEntities = new TreeMap<>();

    public ZiplineHookTileEntity(BlockEntityType<?> p_155228_, BlockPos p_155229_, net.minecraft.world.level.block.state.BlockState p_155230_) {
        super(p_155228_, p_155229_, p_155230_);
    }

    public Set<BlockPos> getConnectionPoints() {
        return connections.keySet();
    }

    private TreeMap<BlockPos, ZiplineInfo> getConnectionInfo() {
        return connections;
    }

    public List<ItemStack> removeAllConnection() {
        if (level == null) return Collections.EMPTY_LIST;
        getConnectionPoints().stream()
                .filter(level::isLoaded)
                .map(level::getBlockEntity)
                .map(it -> it instanceof ZiplineHookTileEntity ? (ZiplineHookTileEntity) it : null)
                .filter(Objects::nonNull)
                .forEach(it -> it.onPairHookRegistrationRemoved(this));
        List<ItemStack> itemStacks = Collections.EMPTY_LIST;
        if (!level.isClientSide()) {
            connectionEntities.values().forEach((it) -> it.remove(Entity.RemovalReason.DISCARDED));
            itemStacks = getConnectionInfo().values().stream().map(it -> {
                ItemStack stack = new ItemStack(Items.ZIPLINE_ROPE::get);
                ZiplineRopeItem.setColor(stack, ARGB.color(0xFF, it.getColor()));
                return stack;
            }).collect(Collectors.toList());
        }
        connectionEntities.clear();
        getConnectionInfo().clear();
        setChanged();
        return itemStacks;
    }

    private void onPairHookRegistrationRemoved(ZiplineHookTileEntity removedPair) {
        getConnectionPoints().remove(removedPair.getBlockPos());
        connectionEntities.remove(removedPair.getBlockPos());
        setChanged();
    }

    private void onPairHookUnloaded(ZiplineHookTileEntity removedPair) {
        connectionEntities.remove(removedPair.getBlockPos());
    }

    /**
     * Was {@code BlockEntity#onChunkUnloaded()}, a NeoForge addition that vanilla 1.21.1 does not
     * call. Invoked from {@code mixin.common.LevelChunkMixin} just before the chunk drops its block
     * entities, so the paired-hook bookkeeping still happens on chunk unload.
     */
    public void parcool$onChunkUnloaded() {
        if (level != null) {
            getConnectionPoints().stream()
                    .filter(level::isLoaded)
                    .map(level::getBlockEntity)
                    .map(it -> it instanceof ZiplineHookTileEntity ? (ZiplineHookTileEntity) it : null)
                    .filter(Objects::nonNull)
                    .forEach(it -> it.onPairHookUnloaded(this));
            if (!level.isClientSide()) {
                connectionEntities.values().forEach((it) -> it.remove(Entity.RemovalReason.DISCARDED));
            }
            connectionEntities.clear();
        }
    }

    public Vec3 getActualZiplinePoint(@Nullable BlockPos connected) {
        if (level == null)
            new Vec3(getBlockPos().getX() + 0.5, getBlockPos().getY() + 0.5, getBlockPos().getZ() + 0.5);
        BlockState state = level.getBlockState(this.getBlockPos());
        Block block = state.getBlock();
        if (block instanceof ZiplineHookBlock) {
            return ((ZiplineHookBlock) block).getActualZiplinePoint(this.getBlockPos(), state);
        }
        return new Vec3(getBlockPos().getX() + 0.5, getBlockPos().getY() + 0.5, getBlockPos().getZ() + 0.5);
    }

    public boolean connectTo(ZiplineHookTileEntity target, ZiplineInfo info) {
        if (this == target) return false;

        if (level != null && !level.isClientSide()) {
            if (this.getConnectionPoints().stream().anyMatch(target.getBlockPos()::equals)) {
                return false;
            }
            ZiplineRopeEntity ropeEntity = spawnRope(level, target, info);
            if (ropeEntity != null) {
                this.getConnectionInfo().put(target.getBlockPos(), info);
                this.setChanged();
                target.getConnectionInfo().put(this.getBlockPos(), info);
                target.setChanged();

                return true;
            }
        }
        return false;
    }

    @Nullable
    private ZiplineRopeEntity spawnRope(Level level, ZiplineHookTileEntity target, ZiplineInfo info) {
        if (level.isClientSide()) return null;
        if (target.connectionEntities.containsKey(this.getBlockPos())) return null;

        ZiplineRopeEntity entity = new ZiplineRopeEntity(level, getBlockPos(), target.getBlockPos(), info);
        boolean result = level.addFreshEntity(entity);
        if (result) {
            this.connectionEntities.put(target.getBlockPos(), entity);
            target.connectionEntities.put(this.getBlockPos(), entity);
        }
        return result ? entity : null;
    }

    /**
     * 1.21.6 replaced the {@code CompoundTag} save hooks with {@link ValueOutput}, so the connection
     * list is written through {@code childrenList}. The on-disk keys are unchanged, so a world written
     * by 1.21.1 still loads: the reader accepts both the relative ({@code rX}/{@code rY}/{@code rZ})
     * and the absolute ({@code X}/{@code Y}/{@code Z}) form, exactly as before.
     */
    private void saveTo(ValueOutput output) {
        ValueOutput.ValueOutputList connections = output.childrenList("Connection");
        BlockPos pos = getBlockPos();
        for (Map.Entry<BlockPos, ZiplineInfo> infoEntry : getConnectionInfo().entrySet()) {
            ValueOutput entry = connections.addChild();
            entry.putInt("rX", infoEntry.getKey().getX() - pos.getX());
            entry.putInt("rY", infoEntry.getKey().getY() - pos.getY());
            entry.putInt("rZ", infoEntry.getKey().getZ() - pos.getZ());
            entry.store("Info", ZiplineInfo.CODEC, infoEntry.getValue());
        }
    }

    private void restoreFrom(ValueInput input) {
        ValueInput.ValueInputList connections = input.childrenListOrEmpty("Connection");
        if (connections.isEmpty()) return;
        getConnectionInfo().clear();
        for (ValueInput entry : connections) {
            BlockPos pos;
            if (entry.getIntOr("rX", Integer.MIN_VALUE) != Integer.MIN_VALUE
                    || entry.getIntOr("rY", Integer.MIN_VALUE) != Integer.MIN_VALUE
                    || entry.getIntOr("rZ", Integer.MIN_VALUE) != Integer.MIN_VALUE) {
                pos = getBlockPos().offset(
                        entry.getIntOr("rX", 0),
                        entry.getIntOr("rY", 0),
                        entry.getIntOr("rZ", 0));
            } else {
                pos = new BlockPos(entry.getIntOr("X", 0), entry.getIntOr("Y", 0), entry.getIntOr("Z", 0));
            }
            entry.read("Info", ZiplineInfo.CODEC)
                    .ifPresent(info -> getConnectionInfo().put(pos, info));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        saveTo(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        restoreFrom(input);
    }

    /**
     * Was {@code Block#onRemove} on {@link ZiplineHookBlock}, which 1.21.6 removed: the removal
     * side effects now hang off the block entity instead. Called from {@code LevelChunk#setBlockState}
     * on the server right before the block entity is dropped, which is exactly the old {@code onRemove}
     * window, so the linked ropes are still detached and their items returned to the world.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        var itemStacks = removeAllConnection();
        itemStacks.forEach(it -> Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), it));
    }

    /**
     * {@code getUpdateTag}/{@code handleUpdateTag} are vanilla again in 1.21.6, and both route through
     * {@link #saveAdditional} / {@link #loadAdditional}, so the sync needs no hook of its own - the
     * connections travel to the client through the same ValueInput/ValueOutput pair as on disk.
     */

    public static void tick(Level level, BlockPos pos, BlockState state, BlockEntity entity) {
        if (!(entity instanceof ZiplineHookTileEntity self)) return;

        if (level != null && !level.isClientSide()) {
            self.connectionEntities.values().removeIf(it -> !it.isAlive());
            if (self.connectionEntities.size() < self.getConnectionPoints().size()) {
                List<ZiplineHookTileEntity> tileEntities = self.getConnectionPoints()
                        .stream()
                        .filter(it -> !self.connectionEntities.containsKey(it))
                        .filter(level::isLoaded)
                        .map(level::getBlockEntity)
                        .map(it -> it instanceof ZiplineHookTileEntity ? (ZiplineHookTileEntity) it : null)
                        .filter(Objects::nonNull)
                        .toList();
                tileEntities.forEach(it -> {
                    if (it.getConnectionPoints().contains(self.getBlockPos())) {
                        self.spawnRope(level, it, self.getConnectionInfo().get(it.getBlockPos()));
                    } else {
                        self.getConnectionPoints().remove(it.getBlockPos());
                        self.setChanged();
                    }
                });
            }
        }
    }
}
