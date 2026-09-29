package com.alrex.parcool.mixin.common;

import com.alrex.parcool.common.data.ParCoolData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Persists the persistent {@link com.alrex.parcool.common.data.DataKey}s into the player save file.
 * <p>
 * This is the loader-agnostic stand-in for NeoForge's
 * {@code AttachmentType#serialize(Codec)}, which stored attachment data in the entity's own NBT.
 * Only keys registered as persistent are touched, so action/render state stays transient exactly as
 * before.
 */
@Mixin(Player.class)
public abstract class ParCoolDataPersistenceMixin {

    // 1.21.10's player save hooks are ValueOutput / ValueInput based. ParCool's persistent slots are
    // small and flat, so they are written as one NBT compound under a single key - which keeps the
    // on-disk key ({@code parcool}) and the round trip identical to 1.21.1. The key itself lives in
    // ParCoolData#ROOT_TAG; a second copy of it here was never read by anything.
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void parcool$saveData(ValueOutput output, CallbackInfo ci) {
        ParCoolData.saveToOutput((Player) (Object) this, output);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void parcool$loadData(ValueInput input, CallbackInfo ci) {
        ParCoolData.loadFromInput((Player) (Object) this, input);
    }
}
