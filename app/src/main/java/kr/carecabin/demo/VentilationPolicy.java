package kr.carecabin.demo;

/** Decides whether this trip requests a temporary recirculation override. */
final class VentilationPolicy {
    enum Road { BEFORE, APPROACH, WORK_ZONE, PASSED }
    enum Work { ACTIVE, ENDED, UNKNOWN }
    enum Air { NORMAL, POOR, UNKNOWN }
    enum Action { NONE, START_RECIRCULATION, RESTORE_PREVIOUS, RELEASE_TO_VEHICLE }

    record Observation(Road road, Work work, Air air, long elapsedSeconds,
                       boolean automaticEnabled, boolean driverIntervened, boolean defrost) {}

    // Simulated elapsed time, chosen to demonstrate timeout in a short replay.
    private static final long DEMO_OVERRIDE_LIMIT = 45;
    private long startedAt = -1;
    private boolean relinquished;

    Action update(Observation input) {
        if (input.driverIntervened() || input.defrost()) {
            relinquished = true;
            return end(Action.RELEASE_TO_VEHICLE);
        }
        if (!input.automaticEnabled()) {
            return end(Action.RESTORE_PREVIOUS);
        }
        if (relinquished) {
            return Action.NONE;
        }
        if (startedAt >= 0) {
            if (input.elapsedSeconds() - startedAt >= DEMO_OVERRIDE_LIMIT) {
                relinquished = true;
                return end(Action.RELEASE_TO_VEHICLE);
            }
            if ((input.work() == Work.ENDED || input.road() == Road.PASSED) && input.air() == Air.NORMAL) {
                return end(Action.RESTORE_PREVIOUS);
            }
            return Action.NONE;
        }
        if (input.work() == Work.ACTIVE && input.air() == Air.POOR && input.road() == Road.APPROACH) {
            startedAt = input.elapsedSeconds();
            return Action.START_RECIRCULATION;
        }
        return Action.NONE;
    }

    private Action end(Action action) {
        if (startedAt < 0) {
            return Action.NONE;
        }
        startedAt = -1;
        return action;
    }
}
