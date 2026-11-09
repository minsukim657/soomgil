package kr.carecabin.demo;

import java.util.Objects;

import static kr.carecabin.demo.VentilationPolicy.*;

/** Optional normalization boundary for fixtures/future map matching; not a GPS or API client. */
final class WorkZoneApproach {
    // Distances increase in the target road direction; headings use degrees, north = 0.
    record Position(String roadId, double distanceMeters, double headingDegrees) {}
    record Zone(String roadId, double startMeters, double endMeters, double headingDegrees, Work work) {}
    record Snapshot(boolean available, Position position, Zone zone) {}
    record Result(Road road, Work work) {}

    /** Inject a fixture today; connect existing location/work providers here once their API is agreed. */
    interface Source {
        Snapshot read() throws Exception;
    }

    private final double approachMeters;
    private final double exitMarginMeters;
    private final double headingToleranceDegrees;

    WorkZoneApproach(double approachMeters, double exitMarginMeters, double headingToleranceDegrees) {
        if (!Double.isFinite(approachMeters) || approachMeters < 0
                || !Double.isFinite(exitMarginMeters) || exitMarginMeters < 0
                || !Double.isFinite(headingToleranceDegrees)
                || headingToleranceDegrees < 0 || headingToleranceDegrees >= 90) {
            throw new IllegalArgumentException("Invalid geometry thresholds");
        }
        this.approachMeters = approachMeters;
        this.exitMarginMeters = exitMarginMeters;
        this.headingToleranceDegrees = headingToleranceDegrees;
    }

    Result read(Source source) {
        Objects.requireNonNull(source);
        try {
            return classify(source.read());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return unknown();
        } catch (Exception unavailable) {
            return unknown();
        }
    }

    Result classify(Snapshot snapshot) {
        if (snapshot == null || !snapshot.available() || snapshot.position() == null
                || snapshot.zone() == null) return unknown();
        Position position = snapshot.position();
        Zone zone = snapshot.zone();
        if (position.roadId() == null || zone.roadId() == null || zone.work() == null
                || !Double.isFinite(position.distanceMeters()) || !Double.isFinite(position.headingDegrees())
                || !Double.isFinite(zone.startMeters()) || !Double.isFinite(zone.endMeters())
                || !Double.isFinite(zone.headingDegrees()) || zone.startMeters() > zone.endMeters()) {
            return unknown();
        }
        if (!position.roadId().equals(zone.roadId())) return unknown();
        double positionHeading = (position.headingDegrees() % 360 + 360) % 360;
        double zoneHeading = (zone.headingDegrees() % 360 + 360) % 360;
        double delta = Math.abs(positionHeading - zoneHeading);
        delta = Math.min(delta, 360 - delta);
        if (delta >= 180 - headingToleranceDegrees) return new Result(Road.OPPOSITE, zone.work());
        if (delta > headingToleranceDegrees) return unknown();
        double distance = position.distanceMeters();
        Road road;
        if (distance < zone.startMeters() - approachMeters) road = Road.BEFORE;
        else if (distance < zone.startMeters()) road = Road.APPROACH;
        else if (distance <= zone.endMeters() + exitMarginMeters) road = Road.WORK_ZONE;
        else road = Road.PASSED;
        return new Result(road, zone.work());
    }

    private Result unknown() { return new Result(Road.UNKNOWN, Work.UNKNOWN); }
}
