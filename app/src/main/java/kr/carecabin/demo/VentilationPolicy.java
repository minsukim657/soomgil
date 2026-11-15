package kr.carecabin.demo;

import java.util.Objects;

/** Keeps the driver's preference while applying temporary, independent risk overrides. */
final class VentilationPolicy {
    enum Road { GENERAL, BEFORE, APPROACH, WORK_ZONE, PASSED, OPPOSITE, UNKNOWN }
    enum Work { NONE, ACTIVE, ENDED, UNKNOWN }
    enum Air {
        NORMAL, POOR, VERY_POOR, UNKNOWN;

        boolean isPoor() {
            return this == POOR || this == VERY_POOR;
        }
    }
    enum Mode { FRESH_AIR, RECIRCULATION, AUTOMATIC, UNKNOWN }
    enum Action { SWITCH, RESTORE, KEEP }
    enum Reason {
        CONSTRUCTION, POOR_AIR, RISKS_CLEARED, AUTOMATIC_DISABLED, DEFROST_PRIORITY,
        INFORMATION_UNAVAILABLE, REQUEST_PENDING, ALREADY_RECIRCULATING, NO_RISK
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
    private Mode preference = Mode.UNKNOWN;
    private Mode lastObserved = Mode.UNKNOWN;
    private boolean overrideActive;
    private Decision pending;

    static boolean constructionRisk(Road road, Work work) {
        return work == Work.ACTIVE && (road == Road.APPROACH || road == Road.WORK_ZONE);
    }

    static boolean informationUnavailable(Road road, Work work, Air air) {
        return air == Air.UNKNOWN || work == Work.UNKNOWN || (work == Work.ACTIVE && road == Road.UNKNOWN);
    }

    Decision update(Observation input, Mode currentMode) {
        Objects.requireNonNull(input);
        Objects.requireNonNull(currentMode);
        if (pending != null) return keep(Reason.REQUEST_PENDING);
        if (currentMode == Mode.UNKNOWN) return keep(Reason.INFORMATION_UNAVAILABLE);

        boolean construction = constructionRisk(input.road(), input.work());
        boolean poorAir = input.air().isPoor();
        boolean risk = construction || poorAir;
        boolean unavailable = informationUnavailable(input.road(), input.work(), input.air());
        boolean manual = input.driverIntervened()
                || (lastObserved != Mode.UNKNOWN && lastObserved != currentMode);
        lastObserved = currentMode;
        if (preference == Mode.UNKNOWN
                || (manual && (!input.automaticEnabled() || !risk) && !input.defrost())) {
            preference = currentMode;
            overrideActive = false;
        }

        if (input.defrost()) return keep(Reason.DEFROST_PRIORITY);
        if (!input.automaticEnabled()) return keep(Reason.AUTOMATIC_DISABLED);

        if (risk) {
            if (currentMode == Mode.RECIRCULATION) return keep(Reason.ALREADY_RECIRCULATING);
            return request(Action.SWITCH, construction ? Reason.CONSTRUCTION : Reason.POOR_AIR,
                    Mode.RECIRCULATION);
        }
        if (unavailable) return keep(Reason.INFORMATION_UNAVAILABLE);
        if (overrideActive && currentMode != preference) {
            return request(Action.RESTORE, Reason.RISKS_CLEARED, preference);
        }
        overrideActive = false;
        return keep(Reason.NO_RISK);
    }

    boolean onApplied(long requestId, boolean applied) {
        if (!isPending(requestId)) return false;
        Decision completed = pending;
        pending = null;
        if (applied) {
            lastObserved = completed.target();
            overrideActive = completed.action() == Action.SWITCH;
        }
        return true;
    }

    boolean isPending(long requestId) {
        return pending != null && pending.requestId() == requestId;
    }

    private Decision request(Action action, Reason reason, Mode target) {
        pending = new Decision(action, reason, target, ++sequence);
        return pending;
    }

    private Decision keep(Reason reason) {
        return new Decision(Action.KEEP, reason, null, 0);
    }
}
