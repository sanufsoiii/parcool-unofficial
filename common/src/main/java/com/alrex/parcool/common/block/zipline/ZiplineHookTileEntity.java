package com.alrex.parcool.common.block.zipline;

import com.alrex.parcool.common.entity.zipline.ZiplineRopeEntity;
import com.alrex.parcool.common.item.Items;
import com.alrex.parcool.common.item.zipline.ZiplineRopeItem;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.ARGB;
import net.minecraft.core.HolderLookup;
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

    private void saveTo(CompoundTag nbt) {
        var connections = new ListTag();
        for (Map.Entry<BlockPos, ZiplineInfo> infoEntry : getConnectionInfo().entrySet()) {
            var entryTag = new CompoundTag();
            var pos = getBlockPos();
            entryTag.putInt("rX", infoEntry.getKey().getX() - pos.getX());
            entryTag.putInt("rY", infoEntry.getKey().getY() - pos.getY());
            entryTag.putInt("rZ", infoEntry.getKey().getZ() - pos.getZ());
            entryTag.put("Info", infoEntry.getValue().save());
            connections.add(entryTag);
        }
        nbt.put("Connection", connections);
    }

    private void restoreFrom(CompoundTag nbt) {
        Tag connections = nbt.get("Connection");
        if (!(connections instanceof ListTag listConnections)) {
            return;
        }
        getConnectionInfo().clear();

        for (Tag entry : listConnections) {
            if (!(entry instanceof CompoundTag cTag))
                continue;

            // 1.21.5: CompoundTag#getInt returns Optional<Integer>; getIntOr(key, default) replaces
            // the "contains() ? getInt() : 0" pattern the reader used to spell out.
            BlockPos pos;
            if (cTag.contains("rX") && cTag.contains("rY") && cTag.contains("rZ")) {
                pos = getBlockPos().offset(
                        cTag.getIntOr("rX", 0),
                        cTag.getIntOr("rY", 0),
                        cTag.getIntOr("rZ", 0)
                );
            } else if (cTag.contains("X") && cTag.contains("Y") && cTag.contains("Z")) {
                pos = new BlockPos(cTag.getIntOr("X", 0), cTag.getIntOr("Y", 0), cTag.getIntOr("Z", 0));
            } else
                continue;
            ZiplineInfo info = ZiplineInfo.load(cTag.get("Info"));
            getConnectionInfo().put(pos, info);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveTo(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        restoreFrom(tag);
    }

    @Nonnull
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var nbt = super.getUpdateTag(registries);
        saveTo(nbt);
        return nbt;
    }

    /**
     * Was {@code handleUpdateTag(CompoundTag, HolderLookup.Provider)}, a NeoForge addition. Vanilla
     * routes both the disk load and the client sync through {@link #loadAdditional}, so the restore
     * hook lives there.
     */

    /**
     * Hands back the rope items when the hook block is removed. 1.21.5 deleted
     * {@code BlockBehaviour#onRemove} - the vanilla removal hook this used to be reached through -
     * and {@code LevelChunk#setBlockState} now calls this instead, on the outgoing block entity and
     * just before it is dropped, and only on the server (which is the same guard the old
     * {@code !world.isClientSide()} check gave). The base implementation only does something for a
     * {@code Container}, which this block entity is not, but it is called anyway so a future
     * supertype change cannot silently drop something.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level == null || this.level.isClientSide()) return;
        List<ItemStack> itemStacks = removeAllConnection();
        itemStacks.forEach(it -> Containers.dropItemStack(this.level, pos.getX(), pos.getY(), pos.getZ(), it));
    }

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
