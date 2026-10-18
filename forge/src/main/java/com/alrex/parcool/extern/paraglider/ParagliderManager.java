package com.alrex.parcool.extern.paraglider;

import com.alrex.parcool.common.data.Parkourability;
import com.alrex.parcool.common.stamina.IParCoolStaminaHandler;
import com.alrex.parcool.common.stamina.StaminaType;
import com.alrex.parcool.common.stamina.handlers.ParCoolStaminaHandler;
import com.alrex.parcool.extern.ModManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import tictim.paraglider.api.item.Paraglider;
import tictim.paraglider.api.stamina.Stamina;

import javax.annotation.Nullable;

/**
 * Paraglider integration (Forge 1.20.1, mod version 20.1.3).
 *
 * <p>Two halves: while Paraglider's stamina is the selected one, {@link ParagliderStaminaHandler}
 * mirrors its bar; and {@link ParCoolPlugin} publishes ParCool's actions as Paraglider player
 * states, so gliding on a rope or rolling costs the same stamina Paraglider charges for its own
 * movement.
 */
public class ParagliderManager extends ModManager {

    public ParagliderManager() {
        super("paraglider");
    }

    private static ParagliderManager instance;

    public static ParagliderManager getInstance() {
        if (instance == null) instance = new ParagliderManager();
        return instance;
    }

    /**
     * True only when Paraglider's own classes resolve, not merely when the mod id is present: a jar
     * built for another Minecraft version would otherwise throw {@code NoClassDefFoundError} from
     * the first {@link Stamina#get(Player)} call.
     */
    public static boolean isParagliderUsable() {
        try {
            Class.forName("tictim.paraglider.api.stamina.Stamina", false,
                    ParagliderManager.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    @Override
    public void initInClient() {
        if (isInstalled() && isParagliderUsable()) {
            EventConsumerForParaglider.register();
        }
    }

    public IParCoolStaminaHandler newParagliderStaminaHandlerFor(Player player) {
        if (isUsingParagliderStamina(Parkourability.get(player))) return new ParagliderStaminaHandler();
        return new ParCoolStaminaHandler();
    }

    public boolean isUsingParagliderStamina(Parkourability parkourability) {
        if (!isInstalled() || !isParagliderUsable()) return false;
        var forcedStamina = parkourability.getServerLimitation().getForcedStamina();
        if (forcedStamina == StaminaType.PARAGLIDER) return true;
        return forcedStamina == StaminaType.NONE && parkourability.getClientInfo().getRequestedStamina() == StaminaType.PARAGLIDER;
    }

    public boolean isFallingWithParaglider(Player player) {
        if (!isInstalled() || !isParagliderUsable()) return false;
        // Paraglider marks the held glider as "paragliding" while it is deployed; the main hand is
        // checked first, exactly as upstream did.
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.getItem() instanceof Paraglider paragliderItem && paragliderItem.isParagliding(stack)) {
                return true;
            }
        }
        return false;
    }
}
