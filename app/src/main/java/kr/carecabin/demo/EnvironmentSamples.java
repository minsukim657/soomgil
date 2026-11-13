package kr.carecabin.demo;

/** Stable screen fixtures for the three roadwork outcomes. */
final class EnvironmentSamples {
    private EnvironmentSamples() {}

    static EnvironmentReading inProgress() {
        return new EnvironmentReading(EnvironmentReading.WorkStatus.IN_PROGRESS,
                "국도2호선", "강진읍 인근 공사 지점", "종점방향",
                "2026-09-02 00:00", "2026-12-31 00:00",
                "강진읍", "3", "나쁨", "2026-11-09 17:00", 9.1, null);
    }

    static EnvironmentReading ended() {
        return new EnvironmentReading(EnvironmentReading.WorkStatus.ENDED,
                "국도2호선", "강진읍 인근 공사 지점", "종점방향",
                "2026-09-02 00:00", "2026-11-08 00:00",
                "강진읍", "3", "나쁨", "2026-11-09 17:00", 9.1, null);
    }

    static EnvironmentReading queryFailed() {
        return EnvironmentReading.failure("ITS 또는 에어코리아 응답을 확인할 수 없습니다.\n"
                + "키 설정, 네트워크, API 권한을 확인해 주세요.");
    }
}

