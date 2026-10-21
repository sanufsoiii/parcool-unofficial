package com.alrex.parcool.platform;

import com.alrex.parcool.ParCool;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client-only helper for Fabric that restores shared key mappings directly in {@link KeyMapping#MAP}.
 *
 * <h2>Why this exists</h2>
 * On Fabric, vanilla Minecraft's {@code KeyMapping.MAP} stores only ONE mapping per physical key.
 * When {@link KeyMapping#resetMapping()} runs, ParCool bindings (which default to right click, Space,
 * Ctrl, etc.) can overwrite vanilla's {@code key.use}, {@code key.jump}, {@code key.sprint}, etc.
 *
 * <p>This helper runs right after {@code resetMapping()} and ensures that whenever a physical key is
 * shared between ParCool and vanilla (or another mod), the non-ParCool mapping retains ownership in
 * {@code KeyMapping.MAP} so vanilla interactions (block placing, eating, jumping) continue to function.
 *
 * <p>Isolated in its own client-only class so dedicated servers never load {@link KeyMapping}.
 */
public final class FabricClientKeyBindingHelper {

    private FabricClientKeyBindingHelper() {
    }

    public static void restoreVanillaBindings() {
        Map<InputConstants.Key, KeyMapping> bindingMap = KeyMapping.MAP;
        Map<String, KeyMapping> allBindings = KeyMapping.ALL;
        if (bindingMap == null || allBindings == null) {
            return;
        }

        InputConstants.Key rightClickKey = InputConstants.Type.MOUSE.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        KeyMapping currentRightClickOwner = bindingMap.get(rightClickKey);
        ParCool.LOGGER.debug("restoreVanillaBindings invoked! MAP size: {}, ALL size: {}, rightClick owner: {}",
                bindingMap.size(), allBindings.size(), currentRightClickOwner != null ? currentRightClickOwner.getName() : "null");

        // Collect physical keys that are currently owned by a ParCool binding.
        List<InputConstants.Key> parcoolKeys = new ArrayList<>();
        for (Map.Entry<InputConstants.Key, KeyMapping> entry : bindingMap.entrySet()) {
            if (isParCool(entry.getValue())) {
                parcoolKeys.add(entry.getKey());
            }
        }

        int restored = 0;
        for (InputConstants.Key key : parcoolKeys) {
            KeyMapping bestCandidate = null;

            for (KeyMapping candidate : allBindings.values()) {
                if (isParCool(candidate)) continue;
                if (!candidate.key.equals(key)) continue;

                // Prefer vanilla key.use or other gameplay-critical mappings if multiple candidates exist
                if (bestCandidate == null || "key.use".equals(candidate.getName())) {
                    bestCandidate = candidate;
                    if ("key.use".equals(candidate.getName())) {
                        break;
                    }
                }
            }

            if (bestCandidate != null) {
                bindingMap.put(key, bestCandidate);
                restored++;
                ParCool.LOGGER.info("[parcool] Restored key mapping for '{}' -> '{}' (was eclipsed by ParCool)",
                        key, bestCandidate.getName());
            }
        }

        if (restored > 0) {
            ParCool.LOGGER.info("[parcool] Successfully restored {} shared key mapping(s) to vanilla/modded owners", restored);
        }
    }

    private static boolean isParCool(KeyMapping mapping) {
        return mapping != null && mapping.getName() != null && mapping.getName().startsWith("key.parcool.");
    }
}
