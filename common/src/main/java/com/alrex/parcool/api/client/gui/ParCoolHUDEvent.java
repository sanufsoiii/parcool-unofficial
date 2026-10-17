package com.alrex.parcool.api.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import com.alrex.parcool.api.event.CancellableEvent;

public class ParCoolHUDEvent {

    private boolean canceled = false;

    public boolean isCanceled() {
        return this.canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }


    public static class RenderEvent extends ParCoolHUDEvent implements CancellableEvent {
        private final GuiGraphics graphics;
        // 1.20.6's ClientGuiEvent.RENDER_HUD passes a plain float partial tick (DeltaTracker only
        // exists from 1.21), so the event carries that float.
        private final float partialTick;

        public RenderEvent(GuiGraphics s, float partialTick) {
            this.graphics = s;
            this.partialTick = partialTick;
        }

        public GuiGraphics getGuiGraphics() {
            return graphics;
        }

        public float getPartialTick() {
            return partialTick;
        }

    }
}
