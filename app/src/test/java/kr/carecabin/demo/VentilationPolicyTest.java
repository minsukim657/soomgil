package kr.carecabin.demo;

import static kr.carecabin.demo.VentilationPolicy.*;

/** Dependency-free regression tests for vehicle-state ownership and combined risk handling. */
public final class VentilationPolicyTest {
    private static int checks;

    private static Observation obs(Road road, Work work, Air air, long t) {
        return new Observation(road, work, air, t, true, false, false);
    }
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static Decision expect(VentilationPolicy p, Observation o, Mode m, Action a, Reason r) {
        Decision d = p.update(o, m);
        check(d.action() == a, "Expected " + a + " got " + d);
        check(d.reason() == r, "Expected " + r + " got " + d);
        return d;
    }
    private static VentilationPolicy activeOverride(Mode initial) {
        VentilationPolicy p = new VentilationPolicy();
        Decision d = expect(p, obs(Road.APPROACH, Work.ACTIVE, Air.POOR, 1), initial,
                Action.SWITCH, Reason.POOR_AIR_AT_APPROACH);
        check(p.onApplied(d.requestId(), true), "switch acknowledged");
        return p;
    }

    public static void main(String[] args) {
        startingVehicleStateIsRespected();
        overlappingRisksMustAllClear();
        driverAndDefrostTakePriority();
        failuresAndUnknownDataAreSafe();
        System.out.println("PASS: " + checks + " assertions");
    }

    private static void startingVehicleStateIsRespected() {
        VentilationPolicy p = new VentilationPolicy();
        expect(p, obs(Road.APPROACH, Work.ACTIVE, Air.POOR, 10), Mode.RECIRCULATION,
                Action.KEEP, Reason.ALREADY_RECIRCULATING);
        p = activeOverride(Mode.AUTOMATIC);
        Decision restore = expect(p, obs(Road.PASSED, Work.ACTIVE, Air.NORMAL, 30),
                Mode.RECIRCULATION, Action.RESTORE, Reason.WORK_ZONE_PASSED);
        check(restore.target() == Mode.AUTOMATIC, "restore starting automatic mode");
        check(p.onApplied(restore.requestId(), true), "restore acknowledged");
        expect(p, obs(Road.PASSED, Work.ACTIVE, Air.NORMAL, 31), Mode.AUTOMATIC,
                Action.KEEP, Reason.AIR_NORMAL);
    }

    private static void overlappingRisksMustAllClear() {
        VentilationPolicy p = activeOverride(Mode.AUTOMATIC);
        expect(p, obs(Road.PASSED, Work.ACTIVE, Air.POOR, 30), Mode.RECIRCULATION,
                Action.KEEP, Reason.AIR_STILL_POOR);
        expect(p, obs(Road.PASSED, Work.UNKNOWN, Air.UNKNOWN, 31), Mode.RECIRCULATION,
                Action.KEEP, Reason.INFORMATION_UNAVAILABLE);
        Decision restore = expect(p, obs(Road.PASSED, Work.ACTIVE, Air.NORMAL, 32),
                Mode.RECIRCULATION, Action.RESTORE, Reason.WORK_ZONE_PASSED);
        check(restore.target() == Mode.AUTOMATIC, "restore only after air and work risks clear");

        p = activeOverride(Mode.FRESH_AIR);
        expect(p, obs(Road.WORK_ZONE, Work.ACTIVE, Air.NORMAL, 20), Mode.RECIRCULATION,
                Action.KEEP, Reason.WORK_RISK_STILL_ACTIVE);
        expect(p, obs(Road.PASSED, Work.ENDED, Air.POOR, 30), Mode.RECIRCULATION,
                Action.KEEP, Reason.AIR_STILL_POOR);
        restore = expect(p, obs(Road.PASSED, Work.ENDED, Air.NORMAL, 40),
                Mode.RECIRCULATION, Action.RESTORE, Reason.WORK_ENDED);
        check(restore.target() == Mode.FRESH_AIR, "restore original fresh-air mode");

        p = activeOverride(Mode.AUTOMATIC);
        expect(p, obs(Road.WORK_ZONE, Work.ACTIVE, Air.POOR, 1000), Mode.RECIRCULATION,
                Action.KEEP, Reason.AIR_STILL_POOR);
        // No short demo timeout may turn recirculation off while a hazard persists.
        expect(p, obs(Road.WORK_ZONE, Work.ACTIVE, Air.POOR, 10000), Mode.RECIRCULATION,
                Action.KEEP, Reason.AIR_STILL_POOR);
    }

    private static void driverAndDefrostTakePriority() {
        VentilationPolicy p = activeOverride(Mode.AUTOMATIC);
        expect(p, new Observation(Road.PASSED, Work.ACTIVE, Air.NORMAL, 30,
                true, true, false), Mode.RECIRCULATION, Action.KEEP, Reason.DRIVER_INTERVENED);
        expect(p, obs(Road.PASSED, Work.ACTIVE, Air.NORMAL, 31), Mode.AUTOMATIC,
                Action.KEEP, Reason.AUTOMATION_RELINQUISHED);

        p = activeOverride(Mode.AUTOMATIC);
        expect(p, new Observation(Road.PASSED, Work.ACTIVE, Air.NORMAL, 30,
                true, false, true), Mode.RECIRCULATION, Action.KEEP, Reason.DEFROST_PRIORITY);

        p = activeOverride(Mode.AUTOMATIC);
        expect(p, obs(Road.WORK_ZONE, Work.ACTIVE, Air.POOR, 20), Mode.FRESH_AIR,
                Action.KEEP, Reason.EXTERNAL_MODE_CHANGE);
    }

    private static void failuresAndUnknownDataAreSafe() {
        VentilationPolicy p = new VentilationPolicy();
        Decision poorAirOnly = expect(p, obs(Road.APPROACH, Work.UNKNOWN, Air.POOR, 10), Mode.AUTOMATIC,
                Action.SWITCH, Reason.POOR_AIR_AT_APPROACH);
        check(p.onApplied(poorAirOnly.requestId(), true), "poor-air-only switch acknowledged");
        Decision poorAirRestore = expect(p, obs(Road.APPROACH, Work.UNKNOWN, Air.NORMAL, 11), Mode.RECIRCULATION,
                Action.RESTORE, Reason.WORK_ZONE_PASSED);
        check(p.onApplied(poorAirRestore.requestId(), true), "poor-air-only restore acknowledged");
        p = new VentilationPolicy();
        expect(p, obs(Road.APPROACH, Work.ACTIVE, Air.UNKNOWN, 10), Mode.AUTOMATIC,
                Action.KEEP, Reason.INFORMATION_UNAVAILABLE);
        expect(p, obs(Road.OPPOSITE, Work.ACTIVE, Air.POOR, 10), Mode.AUTOMATIC,
                Action.KEEP, Reason.OPPOSITE_DIRECTION);

        Decision d = p.update(obs(Road.APPROACH, Work.ACTIVE, Air.POOR, 10), Mode.AUTOMATIC);
        check(d.action() == Action.SWITCH, "eligible approach requests switch");
        check(p.onApplied(d.requestId(), false), "failed switch acknowledged");
        Decision retry = p.update(obs(Road.APPROACH, Work.ACTIVE, Air.POOR, 11), Mode.AUTOMATIC);
        check(retry.action() == Action.SWITCH && retry.requestId() != d.requestId(), "failed request can retry");
        check(!p.onApplied(d.requestId(), true), "stale callback ignored");

        p = new VentilationPolicy();
        Decision cancelledSwitch = p.update(obs(Road.APPROACH, Work.ACTIVE, Air.POOR, 1), Mode.AUTOMATIC);
        expect(p, obs(Road.APPROACH, Work.ACTIVE, Air.NORMAL, 2), Mode.AUTOMATIC,
                Action.KEEP, Reason.AIR_NORMAL);
        check(!p.onApplied(cancelledSwitch.requestId(), true), "switch cancelled when air risk clears before apply");

        p = activeOverride(Mode.AUTOMATIC);
        Decision pendingRestore = p.update(obs(Road.PASSED, Work.ACTIVE, Air.NORMAL, 30), Mode.RECIRCULATION);
        expect(p, obs(Road.PASSED, Work.ACTIVE, Air.POOR, 31), Mode.RECIRCULATION,
                Action.KEEP, Reason.AIR_STILL_POOR);
        check(!p.onApplied(pendingRestore.requestId(), true), "restore cancelled when poor air returns before apply");

        p = activeOverride(Mode.AUTOMATIC);
        expect(p, new Observation(Road.PASSED, Work.ACTIVE, Air.POOR, 30,
                false, false, false), Mode.RECIRCULATION, Action.KEEP, Reason.AUTOMATIC_DISABLED);
    }
}
