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
        private final float partialTick;

        public RenderEvent(GuiGraphics s, float partialTick) {
            this.graphics = s;
            this.partialTick = partialTick;
        }

        public GuiGraphics getGuiGraphics() {
            return graphics;
        }

        /** The frame's partial tick. 1.20.1 passes it as a plain float (the {@code DeltaTracker}
         *  wrapper only exists from 1.20.5 on). */
        public float getPartialTick() {
            return partialTick;
        }

    }
}
