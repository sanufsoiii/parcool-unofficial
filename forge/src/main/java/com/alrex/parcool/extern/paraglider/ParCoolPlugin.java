package com.alrex.parcool.extern.paraglider;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import tictim.paraglider.api.movement.MovementPlugin;
import tictim.paraglider.api.plugin.ParagliderPlugin;

/**
 * Publishes ParCool's actions to Paraglider as player states.
 *
 * <p>{@code @ParagliderPlugin} is picked up by Paraglider's own annotation scan; the class is never
 * instantiated by ParCool, which is why it has no constructor wiring here.
 */
@ParagliderPlugin
public class ParCoolPlugin implements MovementPlugin {
    @Override
    public void registerNewStates(@NotNull PlayerStateRegister register) {
        ParCoolPlayerStates.ENTRIES.forEach(it -> {
            register.register(it.stateID(), it.staminaDelta());
        });
    }

    @Override
    public void registerStateConnections(@NotNull PlayerStateConnectionRegister register) {
        ParCoolPlayerStates.ENTRIES.forEach(it -> {
            for (ResourceLocation parent : it.parentID()) {
                register.addBranch(parent, it.condition(), it.stateID(), it.priority());
            }
        });
    }
}
