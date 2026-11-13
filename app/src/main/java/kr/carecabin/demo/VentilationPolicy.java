package kr.carecabin.demo;

import java.util.Objects;

/** Trip-scoped policy for a temporary recirculation override. */
final class VentilationPolicy {
    enum Road { BEFORE, APPROACH, WORK_ZONE, PASSED, OPPOSITE, UNKNOWN }
    enum Work { ACTIVE, ENDED, UNKNOWN }
    enum Air { NORMAL, POOR, UNKNOWN }
    enum Mode { FRESH_AIR, RECIRCULATION, AUTOMATIC, UNKNOWN }
    enum Action { SWITCH, RESTORE, KEEP }
    enum Reason {
        POOR_AIR_AT_APPROACH, WORK_ZONE_PASSED, AUTOMATIC_DISABLED,
        DRIVER_INTERVENED, DEFROST_PRIORITY, AUTOMATION_RELINQUISHED,
        INFORMATION_UNAVAILABLE, OPPOSITE_DIRECTION, WORK_ENDED, AIR_NORMAL,
        NOT_APPROACHING, ALREADY_RECIRCULATING, REQUEST_PENDING, APP_OVERRIDE_ACTIVE,
        EXTERNAL_MODE_CHANGE, AIR_STILL_POOR, WORK_RISK_STILL_ACTIVE
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

    record Decision(Action action, Reason reason, Mode target, long requestId) {}

    private long sequence;
    private boolean appOverrideActive;
    private boolean constructionRiskOwned;
    private boolean pendingConstructionRisk;
    private boolean relinquished;
    private Mode previous;
    private Decision pending;

    /** Caller serializes observations and HVAC callbacks. */
    Decision update(Observation input, Mode currentMode) {
        Objects.requireNonNull(input);
        Objects.requireNonNull(currentMode);

        if (input.driverIntervened() || input.defrost()) {
            relinquished = true;
            forgetOverride();
            return keep(input.defrost() ? Reason.DEFROST_PRIORITY : Reason.DRIVER_INTERVENED);
        }
        if (relinquished) return keep(Reason.AUTOMATION_RELINQUISHED);

        if (pending != null) {
            if (pending.action() == Action.SWITCH && !switchStillAllowed(input, currentMode)) {
                pending = null;
                previous = null;
                pendingConstructionRisk = false;
            } else if (pending.action() == Action.RESTORE && !restoreStillAllowed(input)) {
                pending = null;
            }
        }

        if (appOverrideActive && currentMode != Mode.RECIRCULATION && currentMode != Mode.UNKNOWN) {
            relinquished = true;
            forgetOverride();
            return keep(Reason.EXTERNAL_MODE_CHANGE);
        }

        if (appOverrideActive) {
            if (pending != null) return keep(Reason.REQUEST_PENDING);
            if (!input.automaticEnabled()) {
                // Turning automation off relinquishes ownership; do not blindly expose occupants
                // while either environmental risk is still present.
                relinquished = true;
                forgetOverride();
                return keep(Reason.AUTOMATIC_DISABLED);
            }
            if (input.air() == Air.POOR) return keep(Reason.AIR_STILL_POOR);
            if (input.air() == Air.UNKNOWN) return keep(Reason.INFORMATION_UNAVAILABLE);
            if (constructionRiskOwned && !workRiskCleared(input))
                return keep(Reason.WORK_RISK_STILL_ACTIVE);
            return restore(input.work() == Work.ENDED ? Reason.WORK_ENDED : Reason.WORK_ZONE_PASSED);
        }

        if (pending != null) return keep(Reason.REQUEST_PENDING);
        Reason blocked = startBlocker(input, currentMode);
        if (blocked != null) return keep(blocked);

        previous = currentMode;
        pendingConstructionRisk = input.work() == Work.ACTIVE;
        pending = new Decision(Action.SWITCH, Reason.POOR_AIR_AT_APPROACH,
                Mode.RECIRCULATION, ++sequence);
        return pending;
    }


    private boolean switchStillAllowed(Observation input, Mode currentMode) {
        return input.automaticEnabled() && input.road() == Road.APPROACH
                && input.road() != Road.OPPOSITE && input.air() == Air.POOR
                && currentMode != Mode.RECIRCULATION && currentMode != Mode.UNKNOWN
                && !input.driverIntervened() && !input.defrost();
    }

    private boolean restoreStillAllowed(Observation input) {
        return input.automaticEnabled() && input.air() == Air.NORMAL
                && (!constructionRiskOwned || workRiskCleared(input))
                && !input.driverIntervened() && !input.defrost();
    }

    private boolean workRiskCleared(Observation input) {
        // A passed/ended work zone clears the construction risk. Unknown road/work data
        // never authorizes restoration; require explicit passage or an ended-work signal.
        return input.road() == Road.PASSED || input.work() == Work.ENDED;
    }

    boolean onApplied(long requestId, boolean applied) {
        if (!isPending(requestId)) return false;
        Decision completed = pending;
        pending = null;
        if (completed.action() == Action.SWITCH) {
            if (applied) {
                appOverrideActive = true;
                constructionRiskOwned = pendingConstructionRisk;
            } else {
                previous = null;
                pendingConstructionRisk = false;
            }
        } else if (applied) {
            forgetOverride();
        }
        return true;
    }

    boolean isPending(long requestId) {
        return pending != null && pending.requestId() == requestId;
    }

    private Reason startBlocker(Observation input, Mode currentMode) {
        if (!input.automaticEnabled()) return Reason.AUTOMATIC_DISABLED;
        if (currentMode == Mode.UNKNOWN || input.air() == Air.UNKNOWN || input.road() == Road.UNKNOWN)
            return Reason.INFORMATION_UNAVAILABLE;
        if (input.road() == Road.OPPOSITE) return Reason.OPPOSITE_DIRECTION;
        if (input.air() == Air.NORMAL) return Reason.AIR_NORMAL;
        if (input.road() != Road.APPROACH) return Reason.NOT_APPROACHING;
        if (currentMode == Mode.RECIRCULATION) return Reason.ALREADY_RECIRCULATING;
        // Poor air is sufficient to request recirculation at the approach point;
        // active construction is tracked separately as an additional risk.
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
        appOverrideActive = false;
        constructionRiskOwned = false;
        pendingConstructionRisk = false;
        previous = null;
        pending = null;
    }
}
