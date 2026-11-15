package kr.carecabin.demo;

import static kr.carecabin.demo.VentilationPolicy.*;

public final class VentilationPolicyTest {
    private static int checks;

    public static void main(String[] args) {
        VentilationPolicy p = new VentilationPolicy();
        keep(p, Road.BEFORE, Work.ACTIVE, Air.NORMAL, Mode.AUTOMATIC);
        applied(expect(p, Road.APPROACH, Work.ACTIVE, Air.NORMAL, Mode.AUTOMATIC,
                Action.SWITCH, Reason.CONSTRUCTION), p, Mode.RECIRCULATION);
        applied(expect(p, Road.PASSED, Work.ACTIVE, Air.NORMAL, Mode.RECIRCULATION,
                Action.RESTORE, Reason.RISKS_CLEARED), p, Mode.AUTOMATIC);

        p = new VentilationPolicy();
        keep(p, Road.BEFORE, Work.ACTIVE, Air.NORMAL, Mode.RECIRCULATION);
        keep(p, Road.APPROACH, Work.ACTIVE, Air.NORMAL, Mode.RECIRCULATION);
        keep(p, Road.PASSED, Work.ACTIVE, Air.NORMAL, Mode.RECIRCULATION);
        keep(p, Road.PASSED, Work.ACTIVE, Air.NORMAL, Mode.AUTOMATIC);
        applied(expect(p, Road.APPROACH, Work.ACTIVE, Air.NORMAL, Mode.AUTOMATIC,
                Action.SWITCH, Reason.CONSTRUCTION), p, Mode.RECIRCULATION);
        applied(expect(p, Road.PASSED, Work.ACTIVE, Air.NORMAL, Mode.RECIRCULATION,
                Action.RESTORE, Reason.RISKS_CLEARED), p, Mode.AUTOMATIC);

        p = new VentilationPolicy();
        applied(expect(p, Road.UNKNOWN, Work.ENDED, Air.POOR, Mode.FRESH_AIR,
                Action.SWITCH, Reason.POOR_AIR), p, Mode.RECIRCULATION);
        applied(expect(p, Road.UNKNOWN, Work.ENDED, Air.NORMAL, Mode.RECIRCULATION,
                Action.RESTORE, Reason.RISKS_CLEARED), p, Mode.FRESH_AIR);

        p = new VentilationPolicy();
        applied(expect(p, Road.APPROACH, Work.ACTIVE, Air.NORMAL, Mode.AUTOMATIC,
                Action.SWITCH, Reason.CONSTRUCTION), p, Mode.RECIRCULATION);
        keep(p, Road.PASSED, Work.ACTIVE, Air.POOR, Mode.RECIRCULATION);
        applied(expect(p, Road.PASSED, Work.ACTIVE, Air.POOR, Mode.AUTOMATIC,
                Action.SWITCH, Reason.POOR_AIR), p, Mode.RECIRCULATION);
        applied(expect(p, Road.PASSED, Work.ACTIVE, Air.NORMAL, Mode.RECIRCULATION,
                Action.RESTORE, Reason.RISKS_CLEARED), p, Mode.AUTOMATIC);

        p = new VentilationPolicy();
        keep(p, Road.BEFORE, Work.ACTIVE, Air.NORMAL, Mode.RECIRCULATION);
        expect(p, Road.UNKNOWN, Work.ACTIVE, Air.NORMAL, Mode.AUTOMATIC,
                Action.KEEP, Reason.INFORMATION_UNAVAILABLE);
        keep(p, Road.PASSED, Work.ACTIVE, Air.NORMAL, Mode.AUTOMATIC);
        applied(expect(p, Road.APPROACH, Work.ACTIVE, Air.UNKNOWN, Mode.AUTOMATIC,
                Action.SWITCH, Reason.CONSTRUCTION), p, Mode.RECIRCULATION);
        expect(p, Road.UNKNOWN, Work.ACTIVE, Air.UNKNOWN, Mode.RECIRCULATION,
                Action.KEEP, Reason.INFORMATION_UNAVAILABLE);
        keep(p, Road.UNKNOWN, Work.ACTIVE, Air.UNKNOWN, Mode.AUTOMATIC);
        applied(expect(p, Road.UNKNOWN, Work.ACTIVE, Air.POOR, Mode.AUTOMATIC,
                Action.SWITCH, Reason.POOR_AIR), p, Mode.RECIRCULATION);
        applied(expect(p, Road.PASSED, Work.ACTIVE, Air.NORMAL, Mode.RECIRCULATION,
                Action.RESTORE, Reason.RISKS_CLEARED), p, Mode.AUTOMATIC);

        p = new VentilationPolicy();
        check(p.update(new Observation(Road.APPROACH, Work.ACTIVE, Air.POOR, 1, false, true, false),
                Mode.AUTOMATIC).reason() == Reason.AUTOMATIC_DISABLED, "manual when automation is off");
        check(p.update(new Observation(Road.APPROACH, Work.ACTIVE, Air.POOR, 1, true, false, true),
                Mode.AUTOMATIC).reason() == Reason.DEFROST_PRIORITY, "defrost takes priority");
        Decision request = expect(p, Road.APPROACH, Work.ACTIVE, Air.NORMAL, Mode.AUTOMATIC,
                Action.SWITCH, Reason.CONSTRUCTION);
        expect(p, Road.PASSED, Work.ACTIVE, Air.NORMAL, Mode.AUTOMATIC, Action.KEEP, Reason.REQUEST_PENDING);
        applied(request, p, Mode.RECIRCULATION);
        applied(expect(p, Road.PASSED, Work.ACTIVE, Air.NORMAL, Mode.RECIRCULATION,
                Action.RESTORE, Reason.RISKS_CLEARED), p, Mode.AUTOMATIC);

        System.out.println("PASS: " + checks + " policy assertions");
    }

    private static Decision expect(VentilationPolicy p, Road road, Work work, Air air,
                                   Mode mode, Action action, Reason reason) {
        Decision decision = p.update(new Observation(road, work, air, 1, true, false, false), mode);
        check(decision.action() == action, "Expected " + action + " got " + decision);
        check(decision.reason() == reason, "Expected " + reason + " got " + decision);
        return decision;
    }

    private static void keep(VentilationPolicy p, Road road, Work work, Air air, Mode mode) {
        Decision decision = p.update(new Observation(road, work, air, 1, true, false, false), mode);
        check(decision.action() == Action.KEEP, "Expected KEEP got " + decision);
    }

    private static void applied(Decision decision, VentilationPolicy p, Mode target) {
        check(decision.target() == target, "Expected target " + target + " got " + decision);
        check(p.onApplied(decision.requestId(), true), "Vehicle command acknowledged");
    }

    private static void check(boolean passed, String message) {
        checks++;
        if (!passed) throw new AssertionError(message);
    }
}
