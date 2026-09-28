package com.alrex.parcool.common.action;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Supplier;

public class BehaviorEnforcer {
    /**
     * A jump-suppressing marker, plus how long it has been alive.
     *
     * <p>Upstream drops a marker as soon as its owner stops doing, but only when somebody asks
     * ({@link #cancelJump()}), and the owner is a method reference into the action. If the action ever
     * gets stuck reporting {@code isDoing() == true} the jump gate stays shut for good, which reads as
     * "every parkour feature is dead". The age gives the watchdog a way to cut that knot.
     */
    public static class RegisteredMarker {
        final Marker marker;
        int age = 0;

        RegisteredMarker(Marker marker) {
            this.marker = marker;
        }
    }

    /**
     * Hard cap for a jump-suppressing marker, in ticks (10 s).
     *
     * <p>Only the jump map is watched, because only its owners are provably short-lived: Dodge
     * ({@code MAX_TICK}), Roll ({@code getRollMaxTick()}), Slide (sliding-continuable tick) and Tap
     * ({@code 8}). The other maps belong to actions that are meant to last as long as the player
     * wants - ClingToCliff, HangDown and HideInBlock all have an indefinite {@code canContinue} - so
     * they are left to remove themselves.
     */
    public static final int STALE_JUMP_MARKER_TICK = 200;

    public static class ID implements Comparable<ID> {
        private static int idValue = 0;

        private static ID newID() {
            return new ID(idValue++);
        }

        private final int value;

        private ID(int value) {
            this.value = value;
        }

        @Override
        public int compareTo(ID o) {
            return Integer.compare(this.value, o.value);
        }
    }

    public static ID newID() {
        return ID.newID();
    }

    public interface Marker {
        boolean remain();
    }

    public static class Enforcer<T> {
        final Marker marker;
        final Supplier<T> behaviorSupplier;

        Enforcer(Marker marker, Supplier<T> supplier) {
            this.marker = marker;
            this.behaviorSupplier = supplier;
        }

        boolean remain() {
            return marker.remain();
        }

        T getBehavior() {
            return behaviorSupplier.get();
        }
    }

    private final TreeMap<ID, RegisteredMarker> jumpCancelMarks = new TreeMap<>();
    private final TreeMap<ID, Marker> descendFromEdgeCancelMarks = new TreeMap<>();
    private final ConcurrentSkipListMap<ID, Marker> sneakCancelMarks = new ConcurrentSkipListMap<>();
    private final TreeMap<ID, Marker> sprintCancelMarks = new TreeMap<>();
    private final TreeMap<ID, Marker> fallFlyingCancelMarks = new TreeMap<>();
    private final TreeMap<ID, Marker> showNameCancelMarks = new TreeMap<>();
    @Nullable
    private Enforcer<Vec3> movementEnforcer = null;
    @Nullable
    private Enforcer<Vec3> positionEnforcer = null;

    public void addMarkerCancellingJump(ID id, Marker marker) {
        jumpCancelMarks.put(id, new RegisteredMarker(marker));
    }

    public void addMarkerCancellingSneak(ID id, Marker marker) {
        sneakCancelMarks.put(id, marker);
    }

    public void addMarkerCancellingDescendFromEdge(ID id, Marker marker) {
        descendFromEdgeCancelMarks.put(id, marker);
    }

    public void addMarkerCancellingSprint(ID id, Marker marker) {
        sprintCancelMarks.put(id, marker);
    }

    public void addMarkerCancellingFallFlying(ID id, Marker marker) {
        fallFlyingCancelMarks.put(id, marker);
    }

    public void addMarkerCancellingShowName(ID id, Marker marker) {
        showNameCancelMarks.put(id, marker);
    }

    public void setMarkerEnforceMovePoint(Marker marker, Supplier<Vec3> movementSupplier) {
        movementEnforcer = new Enforcer<>(marker, movementSupplier);
    }

    public void setMarkerEnforcePosition(Marker marker, Supplier<Vec3> movementSupplier) {
        positionEnforcer = new Enforcer<>(marker, movementSupplier);
    }

    public boolean cancelJump() {
        jumpCancelMarks.values().removeIf(it -> !it.marker.remain());
        return !jumpCancelMarks.isEmpty();
    }

    /**
     * Ages the jump-suppressing markers and releases any that outlived their owner.
     *
     * <p>Called once a second for the local player. Returns the number of markers that had to be cut,
     * which is the signal that an action reported {@code isDoing()} long past the point where its own
     * {@code canContinue} should have ended it - the "all parkour is dead until /kill" freeze.
     */
    public int expireStaleJumpMarkers() {
        int expired = 0;
        var iterator = jumpCancelMarks.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            RegisteredMarker registered = entry.getValue();
            if (!registered.marker.remain()) {
                iterator.remove();
                continue;
            }
            if (++registered.age > STALE_JUMP_MARKER_TICK) {
                iterator.remove();
                expired++;
            }
        }
        return expired;
    }

    public boolean cancelSneak() {
        sneakCancelMarks.values().removeIf(it -> !it.remain());
        return !sneakCancelMarks.isEmpty();
    }

    public boolean cancelDescendFromEdge() {
        descendFromEdgeCancelMarks.values().removeIf(it -> !it.remain());
        return !descendFromEdgeCancelMarks.isEmpty();
    }

    public boolean cancelSprint() {
        sprintCancelMarks.values().removeIf(it -> !it.remain());
        return !sprintCancelMarks.isEmpty();
    }

    public boolean cancelFallFlying() {
        fallFlyingCancelMarks.values().removeIf(it -> !it.remain());
        return !fallFlyingCancelMarks.isEmpty();
    }

    public boolean cancelShowingName() {
        showNameCancelMarks.values().removeIf(it -> !it.remain());
        return !showNameCancelMarks.isEmpty();
    }

    @Nullable
    public Vec3 getEnforcedMovePoint() {
        if (movementEnforcer != null && remainsOrRelease(movementEnforcer)) {
            return movementEnforcer.getBehavior();
        }
        movementEnforcer = null;
        return null;
    }

    @Nullable
    public Vec3 getEnforcedPosition() {
        if (positionEnforcer != null && remainsOrRelease(positionEnforcer)) {
            return positionEnforcer.getBehavior();
        }
        positionEnforcer = null;
        return null;
    }

    /**
     * A stuck movement/position enforcer pins the player in place, so a throwing owner is treated as
     * "no longer valid" rather than being allowed to escape into the movement code every tick.
     */
    private boolean remainsOrRelease(Enforcer<?> enforcer) {
        try {
            return enforcer.remain();
        } catch (RuntimeException e) {
            com.alrex.parcool.ParCool.LOGGER.error("[parcool] behaviour enforcer threw, releasing it", e);
            return false;
        }
    }
}
