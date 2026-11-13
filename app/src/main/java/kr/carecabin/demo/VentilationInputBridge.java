package kr.carecabin.demo;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import static kr.carecabin.demo.VentilationPolicy.*;

/** Joins the roadwork/air observation with an independently verified matched route. */
final class VentilationInputBridge {
    /** Provided only after the location team has matched this exact roadwork to the route. */
    record RouteEvidence(String roadName, String segment, WorkZoneApproach.Snapshot snapshot) {}

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter OBSERVED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Duration MAX_AIR_AGE = Duration.ofHours(2);
    private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(10);

    private final WorkZoneApproach classifier;
    private final Clock clock;

    VentilationInputBridge(WorkZoneApproach classifier, Clock clock) {
        this.classifier = Objects.requireNonNull(classifier);
        this.clock = Objects.requireNonNull(clock);
    }

    Observation observe(EnvironmentReading reading, RouteEvidence route, long elapsedSeconds,
                        boolean automaticEnabled, boolean driverIntervened, boolean defrost) {
        Work work = workOf(reading);
        Air air = airOf(reading);
        Road road = Road.UNKNOWN;
        if (isMatching(reading, route)) {
            WorkZoneApproach.Snapshot snapshot = route.snapshot();
            WorkZoneApproach.Zone zone = snapshot.zone();
            // The construction API is the sole source of truth for ACTIVE/ENDED/UNKNOWN.
            WorkZoneApproach.Zone normalizedZone = new WorkZoneApproach.Zone(
                    zone.roadId(), zone.startMeters(), zone.endMeters(), zone.headingDegrees(), work);
            road = classifier.classify(new WorkZoneApproach.Snapshot(
                    snapshot.available(), snapshot.position(), normalizedZone)).road();
        }
        return new Observation(road, work, air, elapsedSeconds,
                automaticEnabled, driverIntervened, defrost);
    }

    private boolean isMatching(EnvironmentReading reading, RouteEvidence route) {
        return reading != null && !reading.hasFailed() && route != null
                && route.snapshot() != null && route.snapshot().available()
                && route.snapshot().position() != null && route.snapshot().zone() != null
                && usable(reading.roadName) && usable(reading.segment)
                && reading.roadName.trim().equals(route.roadName())
                && reading.segment.trim().equals(route.segment());
    }

    private boolean usable(String value) {
        return value != null && !value.isBlank() && !value.equals("확인 불가")
                && !value.contains("미제공");
    }

    private Work workOf(EnvironmentReading reading) {
        if (reading == null || reading.hasFailed() || reading.workStatus == null) return Work.UNKNOWN;
        return switch (reading.workStatus) {
            case IN_PROGRESS -> Work.ACTIVE;
            case ENDED -> Work.ENDED;
            case UNKNOWN -> Work.UNKNOWN;
        };
    }

    private Air airOf(EnvironmentReading reading) {
        if (reading == null || reading.hasFailed() || reading.pm25Grade == null
                || reading.observedAt == null) return Air.UNKNOWN;
        try {
            LocalDateTime sample = LocalDateTime.parse(reading.observedAt.trim(), OBSERVED_AT);
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), KOREA);
            Duration age = Duration.between(sample, now);
            if (age.compareTo(MAX_AIR_AGE) > 0 || age.compareTo(MAX_FUTURE_SKEW.negated()) < 0)
                return Air.UNKNOWN;
        } catch (RuntimeException invalidTime) {
            return Air.UNKNOWN;
        }
        return switch (reading.pm25Grade.trim()) {
            case "1", "2" -> Air.NORMAL;
            case "3", "4" -> Air.POOR;
            default -> Air.UNKNOWN;
        };
    }
}
