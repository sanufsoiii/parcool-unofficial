package com.alrex.parcool.api.unstable.action;

import com.alrex.parcool.common.action.Action;
import net.minecraft.world.entity.player.Player;
import com.alrex.parcool.api.event.CancellableEvent;

public class ParCoolActionEvent {

    /**
     * Cancellation flag. Upstream only the {@code ICancellableEvent} subclasses carried it and the
     * call sites always held the concrete cancellable type, so the flag can live on the base
     * without changing observable behaviour. Implement {@link CancellableEvent} on the subclasses
     * that are allowed to be cancelled.
     */
    private boolean canceled = false;

    public boolean isCanceled() {
        return this.canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    private final Player player;
    private final Action action;

    public Player getPlayer() {
        return player;
    }

    public Action getAction() {
        return action;
    }

    public ParCoolActionEvent(Player player, Action action) {
        this.player = player;
        this.action = action;
    }

    @Deprecated
    public static class TryToStartEvent extends ParCoolActionEvent implements CancellableEvent {
        public TryToStartEvent(Player player, Action action) {
            super(player, action);
        }
    }

    @Deprecated
    public static class TryToContinueEvent extends ParCoolActionEvent implements CancellableEvent {
        public TryToContinueEvent(Player player, Action action) {
            super(player, action);
        }
    }

    @Deprecated
    public static class StartEvent extends ParCoolActionEvent {
        public StartEvent(Player player, Action action) {
            super(player, action);
        }
    }

    @Deprecated
    public static class StopEvent extends ParCoolActionEvent {
        public StopEvent(Player player, Action action) {
            super(player, action);
        }
    }
    // ======

    /**
     * @deprecated superseded by {@link TryToStartEvent}, which is what new code should listen to.
     *
     * <p>Both are posted for the same start attempt, so a listener on
     * {@link com.alrex.parcool.api.event.ParCoolEventBus#actionEvents} sees two events per try, as it
     * did upstream. That duplication is deliberate and kept for compatibility: removing the deprecated
     * post would silently stop every add-on that still subscribes to it, which is a worse break than
     * the double notification. Cancel either one and the attempt is refused - the start path treats a
     * cancellation from either event as a veto.
     */
    @Deprecated
    public static class TryToStart extends ParCoolActionEvent implements CancellableEvent {
        public TryToStart(Player player, Action action) {
            super(player, action);
        }
    }

    public static class TryToContinue extends ParCoolActionEvent implements CancellableEvent {
        public TryToContinue(Player player, Action action) {
            super(player, action);
        }
    }

    public static class Start extends ParCoolActionEvent {
        private Start(Player player, Action action) {
            super(player, action);
        }

        public static class Pre extends Start {
            public Pre(Player player, Action action) {
                super(player, action);
            }
        }

        public static class Post extends Start {
            public Post(Player player, Action action) {
                super(player, action);
            }
        }
    }

    public static class Finish extends ParCoolActionEvent {
        private Finish(Player player, Action action) {
            super(player, action);
        }

        public static class Pre extends Finish {
            public Pre(Player player, Action action) {
                super(player, action);
            }
        }

        public static class Post extends Finish {
            public Post(Player player, Action action) {
                super(player, action);
            }
        }
    }

    public static class Tick extends ParCoolActionEvent {
        private Tick(Player player, Action action) {
            super(player, action);
        }

        public static class Pre extends Tick {
            public Pre(Player player, Action action) {
                super(player, action);
            }
        }

        public static class Post extends Tick {
            public Post(Player player, Action action) {
                super(player, action);
            }
        }
    }

}
