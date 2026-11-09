package kr.carecabin.demo;

import static kr.carecabin.demo.VentilationPolicy.*;
import static kr.carecabin.demo.WorkZoneApproach.*;

/** Dependency-free JVM regression suite: run with scripts/test-ventilation.sh. */
public final class VentilationPolicyTest {
    private static int checks;

    private static Observation observation(Road road, Work work, Air air, long seconds) {
        return new Observation(road, work, air, seconds, true, false, false);
    }

    private static Observation approach() { return observation(Road.APPROACH, Work.ACTIVE, Air.POOR, 10); }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static Decision expect(VentilationPolicy policy, Observation input, Mode mode,
                                   Action action, Reason reason) {
        Decision result = policy.update(input, mode);
        check(result.action() == action, "Expected " + action + ": " + result);
        check(result.reason() == reason, "Expected " + reason + ": " + result);
        if (action == Action.KEEP) check(result.target() == null && result.requestId() == 0, "KEEP has no command");
        else check(result.target() != null && result.requestId() > 0, "Command has target and ID");
        return result;
    }

    private static VentilationPolicy active(Mode previous) {
        VentilationPolicy policy = new VentilationPolicy();
        Decision start = expect(policy, approach(), previous, Action.SWITCH,
                Reason.APPROACHING_ACTIVE_WORK_WITH_POOR_AIR);
        check(policy.onApplied(start.requestId(), true), "Application confirmed");
        return policy;
    }

    public static void main(String[] args) {
        constructionAndDirection();
        ownershipAndRestoration();
        priorityAndFailures();
        geometryAndSources();
        System.out.println("PASS: " + checks + " assertions");
    }

    private static void constructionAndDirection() {
        WorkZoneApproach classifier = new WorkZoneApproach(100, 10, 45);
        Position samePosition = new Position("road", 50, 0);
        for (Work work : Work.values()) {
            Result result = classifier.classify(new Snapshot(true, samePosition, new Zone("road", 100, 200, 0, work)));
            expect(new VentilationPolicy(), observation(result.road(), result.work(), Air.POOR, 10), Mode.AUTOMATIC,
                    work == Work.ACTIVE ? Action.SWITCH : Action.KEEP,
                    work == Work.ACTIVE ? Reason.APPROACHING_ACTIVE_WORK_WITH_POOR_AIR
                            : work == Work.ENDED ? Reason.WORK_ENDED : Reason.INFORMATION_UNAVAILABLE);
        }
        expect(new VentilationPolicy(), observation(Road.OPPOSITE, Work.ACTIVE, Air.POOR, 10),
                Mode.AUTOMATIC, Action.KEEP, Reason.OPPOSITE_DIRECTION);
        expect(new VentilationPolicy(), observation(Road.APPROACH, Work.ACTIVE, Air.NORMAL, 10),
                Mode.AUTOMATIC, Action.KEEP, Reason.AIR_NORMAL);
        for (Road road : new Road[] {Road.BEFORE, Road.WORK_ZONE, Road.PASSED}) {
            expect(new VentilationPolicy(), observation(road, Work.ACTIVE, Air.POOR, 10),
                    Mode.AUTOMATIC, Action.KEEP, Reason.NOT_APPROACHING);
        }
        expect(new VentilationPolicy(), approach(), Mode.RECIRCULATION, Action.KEEP, Reason.ALREADY_RECIRCULATING);
        expect(new VentilationPolicy(), approach(), Mode.UNKNOWN, Action.KEEP, Reason.INFORMATION_UNAVAILABLE);
    }

    private static void ownershipAndRestoration() {
        for (Mode original : new Mode[] {Mode.AUTOMATIC, Mode.FRESH_AIR}) {
            VentilationPolicy policy = active(original);
            expect(policy, observation(Road.WORK_ZONE, Work.ACTIVE, Air.POOR, 20), Mode.RECIRCULATION,
                    Action.KEEP, Reason.APP_OVERRIDE_ACTIVE);
            Decision restore = expect(policy, observation(Road.PASSED, Work.ACTIVE, Air.POOR, 30), Mode.RECIRCULATION,
                    Action.RESTORE, Reason.WORK_ZONE_PASSED);
            check(restore.target() == original, "Restore exact previous mode");
            expect(policy, observation(Road.PASSED, Work.ACTIVE, Air.POOR, 31), Mode.RECIRCULATION,
                    Action.KEEP, Reason.REQUEST_PENDING);
            check(policy.onApplied(restore.requestId(), false), "Restore failure acknowledged");
            restore = expect(policy, observation(Road.PASSED, Work.UNKNOWN, Air.UNKNOWN, 32), Mode.RECIRCULATION,
                    Action.RESTORE, Reason.WORK_ZONE_PASSED);
            policy.onApplied(restore.requestId(), true);
            check(!policy.onApplied(restore.requestId(), true), "Duplicate acknowledgement ignored");
            expect(policy, observation(Road.PASSED, Work.ACTIVE, Air.POOR, 33), original, Action.KEEP, Reason.NOT_APPROACHING);
        }
        VentilationPolicy policy = new VentilationPolicy();
        expect(policy, approach(), Mode.RECIRCULATION, Action.KEEP, Reason.ALREADY_RECIRCULATING);
        expect(policy, observation(Road.PASSED, Work.ACTIVE, Air.POOR, 30), Mode.RECIRCULATION,
                Action.KEEP, Reason.NOT_APPROACHING);
        policy = active(Mode.AUTOMATIC);
        expect(policy, observation(Road.WORK_ZONE, Work.ENDED, Air.NORMAL, 20), Mode.RECIRCULATION,
                Action.KEEP, Reason.APP_OVERRIDE_ACTIVE);
        expect(policy, observation(Road.PASSED, Work.ENDED, Air.NORMAL, 30), Mode.RECIRCULATION,
                Action.RESTORE, Reason.WORK_ZONE_PASSED);
        policy = active(Mode.AUTOMATIC);
        Decision timeout = expect(policy, observation(Road.WORK_ZONE, Work.ACTIVE, Air.POOR, 55), Mode.RECIRCULATION,
                Action.RESTORE, Reason.OVERRIDE_TIMEOUT);
        policy.onApplied(timeout.requestId(), true);
        expect(policy, approach(), Mode.AUTOMATIC, Action.KEEP, Reason.AUTOMATION_RELINQUISHED);
        policy = active(Mode.AUTOMATIC);
        expect(policy, new Observation(Road.WORK_ZONE, Work.ACTIVE, Air.POOR, 20, false, false, false),
                Mode.RECIRCULATION, Action.RESTORE, Reason.AUTOMATIC_DISABLED);
    }

    private static void priorityAndFailures() {
        for (boolean defrost : new boolean[] {false, true}) {
            for (boolean confirmed : new boolean[] {false, true}) {
                VentilationPolicy policy = new VentilationPolicy();
                Decision start = policy.update(approach(), Mode.AUTOMATIC);
                if (confirmed) policy.onApplied(start.requestId(), true);
                expect(policy, new Observation(Road.PASSED, Work.ACTIVE, Air.POOR, 60, false, !defrost, defrost),
                        confirmed ? Mode.RECIRCULATION : Mode.AUTOMATIC, Action.KEEP,
                        defrost ? Reason.DEFROST_PRIORITY : Reason.DRIVER_INTERVENED);
                check(!policy.isPending(start.requestId()), "Priority cancels pending command");
                check(!policy.onApplied(start.requestId(), true), "Cancelled ack ignored");
                expect(policy, approach(), Mode.AUTOMATIC, Action.KEEP, Reason.AUTOMATION_RELINQUISHED);
            }
        }
        VentilationPolicy policy = active(Mode.AUTOMATIC);
        expect(policy, observation(Road.WORK_ZONE, Work.ACTIVE, Air.POOR, 20), Mode.FRESH_AIR,
                Action.KEEP, Reason.EXTERNAL_MODE_CHANGE);
        expect(policy, approach(), Mode.FRESH_AIR, Action.KEEP, Reason.AUTOMATION_RELINQUISHED);
        for (Observation invalid : new Observation[] {
                observation(Road.APPROACH, Work.UNKNOWN, Air.POOR, 11),
                observation(Road.APPROACH, Work.ACTIVE, Air.UNKNOWN, 11),
                observation(Road.UNKNOWN, Work.ACTIVE, Air.POOR, 11),
                observation(Road.APPROACH, Work.ENDED, Air.POOR, 11),
                observation(Road.OPPOSITE, Work.ACTIVE, Air.POOR, 11)}) {
            policy = new VentilationPolicy();
            Decision start = policy.update(approach(), Mode.AUTOMATIC);
            check(policy.update(invalid, Mode.AUTOMATIC).action() == Action.KEEP, "Invalid data never starts command");
            check(!policy.isPending(start.requestId()), "Precondition lost cancels switch");
            check(!policy.onApplied(start.requestId(), true), "Stale success ignored");
        }
        policy = new VentilationPolicy();
        Decision start = policy.update(approach(), Mode.AUTOMATIC);
        expect(policy, approach(), Mode.AUTOMATIC, Action.KEEP, Reason.REQUEST_PENDING);
        check(!policy.onApplied(start.requestId() + 1, true), "Unknown request ignored");
        policy.onApplied(start.requestId(), false);
        Decision retry = expect(policy, approach(), Mode.AUTOMATIC, Action.SWITCH,
                Reason.APPROACHING_ACTIVE_WORK_WITH_POOR_AIR);
        check(retry.requestId() != start.requestId(), "Retry has new ID");
        policy = active(Mode.AUTOMATIC);
        expect(policy, observation(Road.UNKNOWN, Work.UNKNOWN, Air.UNKNOWN, 20), Mode.RECIRCULATION,
                Action.KEEP, Reason.APP_OVERRIDE_ACTIVE);
        expect(policy, observation(Road.PASSED, Work.UNKNOWN, Air.UNKNOWN, 30), Mode.RECIRCULATION,
                Action.RESTORE, Reason.WORK_ZONE_PASSED);
        policy = active(Mode.AUTOMATIC);
        Decision restore = policy.update(observation(Road.PASSED, Work.ACTIVE, Air.POOR, 30), Mode.RECIRCULATION);
        expect(policy, new Observation(Road.PASSED, Work.ACTIVE, Air.POOR, 31, true, true, false),
                Mode.RECIRCULATION, Action.KEEP, Reason.DRIVER_INTERVENED);
        check(!policy.onApplied(restore.requestId(), true), "Priority also cancels restore");
    }

    private static void geometryAndSources() {
        WorkZoneApproach classifier = new WorkZoneApproach(100, 10, 45);
        Zone zone = new Zone("road", 100, 200, 0, Work.ACTIVE);
        double[] distances = {-1, 0, 99, 100, 200, 210, 211};
        Road[] roads = {Road.BEFORE, Road.APPROACH, Road.APPROACH, Road.WORK_ZONE,
                Road.WORK_ZONE, Road.WORK_ZONE, Road.PASSED};
        for (int i = 0; i < distances.length; i++) {
            check(classifier.classify(new Snapshot(true, new Position("road", distances[i], 0), zone)).road()
                    == roads[i], "Distance boundary " + distances[i]);
        }
        for (double heading : new double[] {-1, 359, 719, -721}) {
            check(classifier.classify(new Snapshot(true, new Position("road", 50, heading), zone)).road()
                    == Road.APPROACH, "Wrapped heading " + heading);
        }
        check(classifier.classify(new Snapshot(true, new Position("road", 50, 180), zone)).road()
                == Road.OPPOSITE, "Opposite heading");
        check(classifier.classify(new Snapshot(true, new Position("road", 50, 90), zone)).road()
                == Road.UNKNOWN, "Perpendicular heading uncertain");
        check(classifier.classify(new Snapshot(true, new Position("other", 50, 0), zone)).road()
                == Road.UNKNOWN, "Different road");
        check(classifier.classify(new Snapshot(true, new Position("road", Double.NaN, 0), zone)).road()
                == Road.UNKNOWN, "Invalid position");
        check(classifier.classify(new Snapshot(false, null, null)).work() == Work.UNKNOWN, "Unavailable response");
        check(classifier.read(() -> { throw new java.io.IOException("fixture failure"); }).road()
                == Road.UNKNOWN, "Source failure normalized");
        check(classifier.read(() -> new Snapshot(true, new Position("road", 50, 0), zone)).road()
                == Road.APPROACH, "Fixture source injection");
        check(classifier.read(() -> { throw new InterruptedException(); }).road() == Road.UNKNOWN,
                "Interrupted source normalized");
        check(Thread.interrupted(), "Interrupt flag preserved");
    }
}
