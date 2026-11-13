# Pleos Vehicle SDK integration — first step

This copy preserves the original Soomgil app code and its ITS/AirKorea local.properties
BuildConfig fields. It adds only the official Pleos Maven repository and Vehicle SDK
dependency (`ai.pleos.playground:Vehicle:2.3.2`).

## Important boundary

- This change only makes the Vehicle SDK artifact available to the app at build time.
- It does NOT yet call HVAC or FusedLocation APIs.
- Do not add `CONTROL_CAR_CLIMATE` or start HVAC SET calls until the Pleos project
  feature status/permission has been approved for this app. Declaring a permission
  alone does not grant access.
- The next implementation step is to verify the installed Pleos Vehicle Service/API
  compatibility, then add the minimum approved HVAC permissions and typed API calls.
- Existing ITS_API_KEY and AIRKOREA_API_KEY values remain in your local.properties;
  do not share or commit that file.

## Build check

Open this project in Android Studio and run Gradle Sync. If dependency resolution fails,
check network access to the Pleos Maven repository and confirm the SDK version allowed
for your Pleos project.

## Lifecycle integration added

`PleosVehicleManager` now owns `Vehicle(context)`, calls `initialize()` from
`MainActivity.onCreate()`, and calls `release()` from `onDestroy()`.

This is intentionally only the documented SDK entry-point lifecycle. It does NOT
claim Vehicle Service connection, HVAC support, or successful climate control.
The public API reference lists `checkHvacRecirculationCapabilities`,
`getHvacRecirculationState`, and `setHvacRecirculation`, but the rendered reference
pages currently expose method names without the Java parameter/callback signatures.
Those signatures must be confirmed from the SDK artifact/sample before adding typed
calls; no speculative HVAC call has been inserted.

## HVAC API signature diagnostic

This revision performs read-only Java reflection over the installed Vehicle SDK to
log the HVAC domain getter and public recirculation-related method signatures.
It does not invoke any HVAC capability/get/set method and does not alter vehicle
settings. Use Logcat filter `SoomgilPleos` and send the lines beginning with
`Vehicle HVAC`, `HVAC domain`, or `HVAC recirculation API`. These exact signatures
will be used for the typed integration in the next revision.

## Read-only HVAC query step

The runtime signatures supplied from Logcat confirmed `getHVAC()`,
`checkHvacRecirculationCapabilities(Function1, Function1)`, and
`getHvacRecirculationState(SeatArea, Function1, Function1)`. This revision uses
those exact runtime signatures reflectively to query supported seat areas and
current recirculation state. It does not call `setHvacRecirculation` and does not
change vehicle settings. Logcat filter: `SoomgilPleos`.

## SeatArea type inspection

The runtime log shows `SeatArea.getEnumConstants()` returned no values. This
revision logs the SeatArea type, public fields, methods, nested classes, and
constructors so the SDK's actual SeatArea representation can be identified.
It does not issue HVAC queries or change vehicle settings.

## SeatArea construction and read query

The runtime log confirms SeatArea is a value object constructed with a vararg
`SeatValue...`, not an enum. This revision inspects the SeatValue enum constants,
constructs a SeatArea from those values, then performs the documented
capability and current-state queries. It does not invoke the HVAC setter.

## 2026-11-13 통합 공조 정책 업데이트

현재 앱 소스에는 Pleos HVAC 내기순환 상태 조회와 SET 요청이 연결되어 있습니다. SET 콜백 이후 상태가 늦게 반영되는 현상을 고려해 400ms 간격 최대 5회 재조회합니다. 화면은 1.5초 주기의 실제 내기순환 Boolean 조회를 따릅니다.

공조 정책은 출발 전 실제 내기순환 ON을 운전자 기본 상태로 존중하고, 앱이 OFF 상태에서 켠 경우에만 앱 소유 전환으로 기록합니다. 앱 소유 전환은 대기질이 계속 POOR이면 공사 구간 통과 뒤에도 유지하며, 공사 위험(해당 전환 시 ACTIVE였던 경우)과 대기질 위험이 모두 해소되어야 이전 OFF 상태로 복원합니다. 운전자 직접 조작/김 서림 우선은 자동 제어권을 포기합니다. SET/RESTORE가 대기 중인 동안 조건이 바뀌면 해당 요청을 취소하고 늦게 도착한 콜백은 무시합니다.

정책 회귀 테스트는 `sh scripts/test-ventilation.sh`에서 83 assertions 통과했습니다. Android Gradle APK 빌드와 Pleos 에뮬레이터 실기동 검증은 이 소스 패키지 생성 환경에서 수행하지 않았습니다.

**남은 제한:** ITS/AirKorea 실제 조회 결과는 화면 표시용이며, 현재 자동 주행 정책 입력은 여전히 시연 시나리오입니다. 실제 GPS/내비 경로와 공사 구간의 도로·방향 맵매칭, 실제 관측값의 최신성 검증, Pleos 운전자 조작 이벤트의 직접 구독은 아직 연결되지 않았습니다. 내기순환 Boolean 외 전체 HVAC 모드를 읽거나 복원하지 않습니다.
