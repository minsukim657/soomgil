package kr.carecabin.demo;

import java.util.Objects;

/** Decides whether this trip requests a temporary recirculation override; never controls HVAC. */
final class VentilationPolicy {
    enum Road { BEFORE, APPROACH, WORK_ZONE, PASSED, OPPOSITE, UNKNOWN }
    enum Work { ACTIVE, ENDED, UNKNOWN }
    enum Air { NORMAL, POOR, UNKNOWN }
    enum Mode { FRESH_AIR, RECIRCULATION, AUTOMATIC, UNKNOWN }
    enum Action { SWITCH, RESTORE, KEEP }
    enum Reason {
        APPROACHING_ACTIVE_WORK_WITH_POOR_AIR, WORK_ZONE_PASSED, AUTOMATIC_DISABLED,
        OVERRIDE_TIMEOUT, DRIVER_INTERVENED, DEFROST_PRIORITY, AUTOMATION_RELINQUISHED,
        INFORMATION_UNAVAILABLE, OPPOSITE_DIRECTION, WORK_ENDED, AIR_NORMAL,
        NOT_APPROACHING, ALREADY_RECIRCULATING, REQUEST_PENDING, APP_OVERRIDE_ACTIVE,
        EXTERNAL_MODE_CHANGE
    }

    record Observation(Road road, Work work, Air air, long elapsedSeconds,
                       boolean automaticEnabled, boolean driverIntervened, boolean defrost) {
        Observation {
            Objects.requireNonNull(road);
            Objects.requireNonNull(work);
            Objects.requireNonNull(air);
            if (elapsedSeconds < 0) throw new IllegalArgumentException("Negative elapsed time");
        }
    }

    /** KEEP has no target/request. Control layer echoes requestId only after actual application. */
    record Decision(Action action, Reason reason, Mode target, long requestId) {}

    // Simulated elapsed time, chosen to demonstrate timeout in a short replay.
    private static final long DEMO_OVERRIDE_LIMIT = 45;
    private long startedAt = -1;
    private long sequence;
    private long requestedAt;
    private boolean relinquished;
    private Mode previous;
    private Decision pending;

    /** One instance per trip. Calls, HVAC application and callbacks must be serialized by the caller. */
    Decision update(Observation input, Mode currentMode) {
        Objects.requireNonNull(input);
        Objects.requireNonNull(currentMode);
        if (input.driverIntervened() || input.defrost()) {
            relinquished = true;
            forgetOverride();
            return keep(input.defrost() ? Reason.DEFROST_PRIORITY : Reason.DRIVER_INTERVENED);
        }
        if (relinquished) return keep(Reason.AUTOMATION_RELINQUISHED);
        if (startedAt >= 0 && currentMode != Mode.RECIRCULATION && currentMode != Mode.UNKNOWN) {
            relinquished = true;
            forgetOverride();
            return keep(Reason.EXTERNAL_MODE_CHANGE);
        }
        if (startedAt >= 0) {
            if (pending != null) return keep(Reason.REQUEST_PENDING);
            if (!input.automaticEnabled()) return restore(Reason.AUTOMATIC_DISABLED);
            if (input.elapsedSeconds() - startedAt >= DEMO_OVERRIDE_LIMIT) {
                return restore(Reason.OVERRIDE_TIMEOUT);
            }
            // Passage is normalized from cached/matched geometry, independent of air grade/API health.
            if (input.road() == Road.PASSED) return restore(Reason.WORK_ZONE_PASSED);
            return keep(Reason.APP_OVERRIDE_ACTIVE);
        }

        Reason blocked = startBlocker(input, currentMode);
        if (blocked != null) {
            // Invalidate an unapplied SWITCH as soon as its preconditions disappear.
            pending = null;
            previous = null;
            return keep(blocked);
        }
        if (pending != null) return keep(Reason.REQUEST_PENDING);
        previous = currentMode;
        requestedAt = input.elapsedSeconds();
        pending = new Decision(Action.SWITCH, Reason.APPROACHING_ACTIVE_WORK_WITH_POOR_AIR,
                Mode.RECIRCULATION, ++sequence);
        return pending;
    }

    /** False means the response is stale/duplicate. A failed command may be retried on the next update. */
    boolean onApplied(long requestId, boolean applied) {
        if (!isPending(requestId)) return false;
        Decision completed = pending;
        pending = null;
        if (completed.action() == Action.SWITCH) {
            if (applied) startedAt = requestedAt;
            else previous = null;
        } else if (applied) {
            if (completed.reason() == Reason.OVERRIDE_TIMEOUT) relinquished = true;
            forgetOverride();
        }
        return true;
    }

    /** Check immediately before application, alongside live manual/defrost priority checks. */
    boolean isPending(long requestId) {
        return pending != null && pending.requestId() == requestId;
    }

    private Reason startBlocker(Observation input, Mode currentMode) {
        if (!input.automaticEnabled()) return Reason.AUTOMATIC_DISABLED;
        if (input.work() == Work.UNKNOWN || input.air() == Air.UNKNOWN
                || input.road() == Road.UNKNOWN || currentMode == Mode.UNKNOWN) {
            return Reason.INFORMATION_UNAVAILABLE;
        }
        if (input.road() == Road.OPPOSITE) return Reason.OPPOSITE_DIRECTION;
        if (input.work() == Work.ENDED) return Reason.WORK_ENDED;
        if (input.air() == Air.NORMAL) return Reason.AIR_NORMAL;
        if (input.road() != Road.APPROACH) return Reason.NOT_APPROACHING;
        if (currentMode == Mode.RECIRCULATION) return Reason.ALREADY_RECIRCULATING;
        return null;
    }

    private Decision restore(Reason reason) {
        pending = new Decision(Action.RESTORE, reason, previous, ++sequence);
        return pending;
    }

    private Decision keep(Reason reason) {
        return new Decision(Action.KEEP, reason, null, 0);
    }

    private void forgetOverride() {
        startedAt = -1;
        previous = null;
        pending = null;
    }
}
