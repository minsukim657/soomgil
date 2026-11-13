package kr.carecabin.demo;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static kr.carecabin.demo.VentilationPolicy.*;
import static kr.carecabin.demo.WorkZoneApproach.*;

/** Contract tests for two provider boundaries; no Android SDK needed. */
public final class VentilationInputBridgeTest {
    private static int checks;
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-13T06:30:00Z"), ZoneOffset.UTC);
    private static final VentilationInputBridge BRIDGE = new VentilationInputBridge(
            new WorkZoneApproach(100, 10, 45), NOW);

    public static void main(String[] args) {
        sameRouteDifferentConstructionStatus();
        directionAndMissingEvidence();
        pm25AndFreshness();
        restoreAndPriorities();
        System.out.println("PASS: " + checks + " bridge assertions");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static EnvironmentReading reading(EnvironmentReading.WorkStatus status, String grade, String time) {
        return new EnvironmentReading(status, "국도2호선", "강진읍 공사", "종점 방향",
                "2026-11-01 00:00", "2026-12-31 00:00", "강진읍", grade, grade, time, 1, null);
    }

    private static VentilationInputBridge.RouteEvidence route(double positionMeters, double heading) {
        return new VentilationInputBridge.RouteEvidence("국도2호선", "강진읍 공사",
                new Snapshot(true, new Position("road-2", positionMeters, heading),
                        new Zone("road-2", 100, 200, 0, Work.UNKNOWN)));
    }

    private static Observation observe(EnvironmentReading reading, VentilationInputBridge.RouteEvidence route) {
        return BRIDGE.observe(reading, route, 10, true, false, false);
    }

    private static void sameRouteDifferentConstructionStatus() {
        for (EnvironmentReading.WorkStatus status : EnvironmentReading.WorkStatus.values()) {
            Observation input = observe(reading(status, "3", "2026-11-13 15:00"), route(50, 0));
            Decision result = new VentilationPolicy().update(input, Mode.AUTOMATIC);
            check(input.road() == Road.APPROACH, "Matched road approaches for all work statuses");
            check(input.air() == Air.POOR, "Air must stay identical");
            check(result.action() == (status == EnvironmentReading.WorkStatus.IN_PROGRESS
                    ? Action.SWITCH : Action.KEEP), "Only active construction starts an override");
        }
    }

    private static void directionAndMissingEvidence() {
        EnvironmentReading live = reading(EnvironmentReading.WorkStatus.IN_PROGRESS, "3", "2026-11-13 15:00");
        check(observe(live, route(50, 180)).road() == Road.OPPOSITE, "Opposite direction recognized");
        check(new VentilationPolicy().update(observe(live, route(50, 180)), Mode.AUTOMATIC).action()
                == Action.KEEP, "Opposite direction never switches");
        check(observe(live, route(211, 0)).road() == Road.PASSED, "Passed recognized");
        check(observe(live, null).road() == Road.UNKNOWN, "No location match is unknown");
        VentilationInputBridge.RouteEvidence wrong = new VentilationInputBridge.RouteEvidence(
                "other-road", "강진읍 공사", route(50, 0).snapshot());
        check(observe(live, wrong).road() == Road.UNKNOWN, "Wrong construction identity rejected");
        check(observe(EnvironmentReading.failure("offline"), route(50, 0)).work() == Work.UNKNOWN,
                "Failed API is unknown");
        check(new VentilationPolicy().update(observe(live, null), Mode.AUTOMATIC).action()
                == Action.KEEP, "Unmatched location never starts a command");
    }

    private static void pm25AndFreshness() {
        for (String grade : new String[] {"1", "2"}) {
            check(observe(reading(EnvironmentReading.WorkStatus.IN_PROGRESS, grade, "2026-11-13 15:00"),
                    route(50, 0)).air() == Air.NORMAL, "Good/moderate -> normal");
        }
        for (String grade : new String[] {"3", "4"}) {
            check(observe(reading(EnvironmentReading.WorkStatus.IN_PROGRESS, grade, "2026-11-13 15:00"),
                    route(50, 0)).air() == Air.POOR, "Poor/very poor -> poor");
        }
        check(observe(reading(EnvironmentReading.WorkStatus.IN_PROGRESS, "3", "2026-11-13 12:00"),
                route(50, 0)).air() == Air.UNKNOWN, "Stale air is unknown");
        check(observe(reading(EnvironmentReading.WorkStatus.IN_PROGRESS, "3", "invalid"),
                route(50, 0)).air() == Air.UNKNOWN, "Invalid time is unknown");
        check(observe(reading(EnvironmentReading.WorkStatus.IN_PROGRESS, "-", "2026-11-13 15:00"),
                route(50, 0)).air() == Air.UNKNOWN, "Missing grade is unknown");
    }

    private static void restoreAndPriorities() {
        EnvironmentReading live = reading(EnvironmentReading.WorkStatus.IN_PROGRESS, "3", "2026-11-13 15:00");
        VentilationPolicy policy = new VentilationPolicy();
        Decision switched = policy.update(observe(live, route(50, 0)), Mode.FRESH_AIR);
        check(switched.action() == Action.SWITCH, "First request switches");
        check(policy.onApplied(switched.requestId(), true), "Ownership is confirmed only on application");
        Decision restored = policy.update(observe(live, route(211, 0)), Mode.RECIRCULATION);
        check(restored.action() == Action.RESTORE && restored.target() == Mode.FRESH_AIR,
                "Passage restores previous setting");
        VentilationPolicy other = new VentilationPolicy();
        Observation defrost = BRIDGE.observe(live, route(50, 0), 10, true, false, true);
        check(other.update(defrost, Mode.AUTOMATIC).action() == Action.KEEP,
                "Defrost has priority");
        VentilationPolicy another = new VentilationPolicy();
        Observation driver = BRIDGE.observe(live, route(50, 0), 10, true, true, false);
        check(another.update(driver, Mode.AUTOMATIC).action() == Action.KEEP,
                "Driver has priority");
    }
}
