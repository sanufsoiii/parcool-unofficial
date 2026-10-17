package com.alrex.parcool.registry;

import com.alrex.parcool.ParCool;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * ParCool's two player attributes, registered on NeoForge only.
 *
 * <p>Why this cannot live in {@code :common}: {@code BuiltInRegistries.ATTRIBUTE} is writable only
 * while {@code Bootstrap#bootStrap} runs, and NeoForge closes the built-in registries before the mod
 * constructors are invoked - a direct {@code Registry.registerForHolder} from the common entry point
 * therefore kills the client and the server with
 * {@code IllegalStateException: Registry is already frozen (trying to add key parcool:max_stamina)},
 * which is exactly how this port used to crash on Prism. NeoForge's own {@code DeferredRegister} hooks
 * {@code RegisterEvent}, which is still inside the writable window, so it is the only thing that works
 * here - and it is what upstream ParCool's NeoForge build uses. Architectury's {@code DeferredRegister}
 * is not an option: it rejects {@link Registries#ATTRIBUTE} outright
 * ({@code RegistrarManager._get} asserts on it).
 *
 * <p>{@code common/api/Attributes} therefore only <i>resolves</i> the holders on NeoForge, which it
 * does lazily on the first {@code Player#createAttributes} - long after this ran.
 *
 * <p>NeoForge 21.1's {@code DeferredRegister#create} does not attach itself to any bus, so the mod's
 * own bus is handed in explicitly; the {@code @Mod} constructor receives it through the
 * {@link net.neoforged.fml.ModContainer} that FML injects.
 */
public final class NeoForgeAttributes {

    private static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, ParCool.MOD_ID);

    public static void register(IEventBus modEventBus) {
        ATTRIBUTES.register("max_stamina",
                () -> new RangedAttribute("parcool.max_stamina", 2000, 10, 10000).setSyncable(true));
        ATTRIBUTES.register("stamina_recovery",
                () -> new RangedAttribute("parcool.stamina_recovery", 20, 0, 10000).setSyncable(true));
        ATTRIBUTES.register(modEventBus);
    }

    private NeoForgeAttributes() {
    }
}
