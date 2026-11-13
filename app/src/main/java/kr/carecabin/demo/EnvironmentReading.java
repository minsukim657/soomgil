package kr.carecabin.demo;

/** Values passed from the roadwork and AirKorea APIs to the screen. */
final class EnvironmentReading {
    enum WorkStatus { IN_PROGRESS, ENDED, UNKNOWN }

    final WorkStatus workStatus;
    final String roadName;
    final String segment;
    final String direction;
    final String validFrom;
    final String validUntil;
    final String stationName;
    final String pm25Grade;
    final String pm25GradeLabel;
    final String observedAt;
    final double stationDistanceKm;
    final String failureMessage;

    EnvironmentReading(WorkStatus workStatus, String roadName, String segment,
                       String direction, String validFrom, String validUntil,
                       String stationName, String pm25Grade, String pm25GradeLabel,
                       String observedAt, double stationDistanceKm, String failureMessage) {
        this.workStatus = workStatus;
        this.roadName = roadName;
        this.segment = segment;
        this.direction = direction;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.stationName = stationName;
        this.pm25Grade = pm25Grade;
        this.pm25GradeLabel = pm25GradeLabel;
        this.observedAt = observedAt;
        this.stationDistanceKm = stationDistanceKm;
        this.failureMessage = failureMessage;
    }

    static EnvironmentReading failure(String message) {
        return new EnvironmentReading(WorkStatus.UNKNOWN, "확인 불가", "확인 불가",
                "확인 불가", "확인 불가", "확인 불가", "확인 불가",
                "-", "확인 불가", "확인 불가", -1, message);
    }

    boolean hasFailed() {
        return failureMessage != null;
    }

    String workStatusLabel() {
        return switch (workStatus) {
            case IN_PROGRESS -> "공사 진행";
            case ENDED -> "공사 종료";
            case UNKNOWN -> "공사 상태 확인 불가";
        };
    }

    String toDisplayText() {
        if (hasFailed()) return "조회 실패\n" + failureMessage;
        String distance = stationDistanceKm >= 0
                ? String.format(java.util.Locale.KOREA, " · 직선거리 %.1fkm", stationDistanceKm)
                : "";
        return workStatusLabel() + "\n"
                + "도로·구간: " + roadName + " · " + segment + "\n"
                + "방향: " + direction + "\n"
                + "유효 시각: " + validFrom + " ~ " + validUntil + "\n"
                + "측정소: " + stationName + distance + "\n"
                + "초미세먼지 등급: " + pm25GradeLabel + " (" + pm25Grade + ")\n"
                + "관측 시각: " + observedAt;
    }
}

