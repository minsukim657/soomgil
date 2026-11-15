package kr.carecabin.demo;

import android.app.Activity;
import android.content.pm.PackageManager;

import ai.pleos.playground.location.fused.FusedLocation;
import ai.pleos.playground.location.fused.listener.FusedLocationListener;
import ai.pleos.playground.location.fused.model.LocationData;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayDeque;

import static kr.carecabin.demo.VentilationPolicy.*;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(246, 247, 248);
    private static final int SURFACE = Color.WHITE;
    private static final int INSET = Color.rgb(243, 245, 247);
    private static final int ACCENT = Color.rgb(67, 132, 245);
    private static final int ACCENT_SOFT = Color.rgb(231, 239, 255);
    private static final int TEXT = Color.rgb(24, 27, 31);
    private static final int MUTED = Color.rgb(105, 112, 120);
    private static final int AMBER = Color.rgb(205, 112, 20);
    private static final long DEMO_STEP_DELAY_MS = 3000;
    private static final double[] DEMO_POSITIONS_METERS = {0, 150, 250, 320};
    private static final String DEMO_ROAD_ID = "demo-road";
    private static final double DEMO_HEADING = 90;
    private static final WorkZoneApproach WORK_ZONE = new WorkZoneApproach(100, 10, 30);
    private static final double LIVE_WORK_POINT_HALF_LENGTH_METERS = 15;
    private static final long LIVE_ENVIRONMENT_REFRESH_MS = 5 * 60 * 1000L;

    private static final double DEMO_LATITUDE = 37.5244505;
    private static final double DEMO_LONGITUDE = 126.8817115;
    private static final String[] API_SAMPLES = {"공사 진행", "공사 종료", "조회 실패"};

    private record Scenario(String title, Work work, Air approachAir, Air afterAir) {
        @Override public String toString() { return title; }
    }

    private static final Scenario[] SCENARIOS = {
            new Scenario("공사 진행 · 접근 시 대기 나쁨", Work.ACTIVE, Air.POOR, Air.NORMAL),
            new Scenario("공사 종료 · 같은 도로와 대기 등급", Work.ENDED, Air.POOR, Air.NORMAL),
            new Scenario("공사 정보 확인 불가", Work.UNKNOWN, Air.POOR, Air.NORMAL),
            new Scenario("공사 진행 · 대기 정보 확인 불가", Work.ACTIVE, Air.UNKNOWN, Air.NORMAL),
            new Scenario("공사 진행 · 접근 시 대기 보통", Work.ACTIVE, Air.NORMAL, Air.NORMAL),
            new Scenario("공사 진행 · 통과 후에도 대기 나쁨", Work.ACTIVE, Air.POOR, Air.POOR)
    };

    private static final String LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION";
    private static final int LOCATION_REQUEST = 410;
    private static final SeobuDemoRoute GPS_DEMO_ROUTE = new SeobuDemoRoute();
    private boolean gpsTrip;
    private long gpsStartedAt;
    private LocationData previousFix;
    private WorkZoneApproach.Position gpsPosition;
    private Road gpsRoad = Road.UNKNOWN;
    private VentilationInputBridge.RouteEvidence gpsRouteEvidence;
    private FusedLocation locationSdk;
    private TextView locationStatus;
    private EnvironmentReading liveReading;
    private boolean gpsStartInFlight;
    private final FusedLocationListener locationListener = new FusedLocationListener() {
        @Override
        public void onChanged(LocationData location) {
            runOnUiThread(() -> {
                if (locationSdk == null) return;
                if (location == null) {
                    locationStatus.setText("차량 위치를 아직 받지 못했어요.");
                    clearGpsPosition();
                    return;
                }
                locationStatus.setText(String.format(java.util.Locale.KOREA,
                        "위도 %.6f · 경도 %.6f\n진행 방향 %.1f° · 수신 시각 %tT",
                        location.getLatitude(), location.getLongitude(),
                        location.getHeading(), location.getTimestampMillis()));
                acceptGpsPosition(location);
                Log.d("SoomgilLocation", "Vehicle location update received");
            });
        }

        @Override
        public void onError(Exception error) {
            runOnUiThread(() -> {
                if (locationSdk != null) {
                    locationStatus.setText("차량 위치를 받을 수 없어요.");
                    clearGpsPosition();
                }
            });
            Log.w("SoomgilLocation", "Vehicle location callback failed", error);
        }
    };

    private PleosVehicleManager pleosVehicleManager;
    private VentilationPolicy policy;
    private final ArrayDeque<String> history = new ArrayDeque<>();
    private final SoomgilApiClient apiClient = new SoomgilApiClient();
    private int stage;
    private long elapsed;
    private Scenario trip;
    private boolean started;
    private boolean recirculating;
    private Mode vehicleMode = Mode.AUTOMATIC;
    private boolean driverIntervened;
    private boolean defrost;
    private float uiScale = 1f;

    private Spinner scenario;
    private Switch enabled;
    private TextView mode;
    private TextView status;
    private TextView route;
    private TextView workValue;
    private TextView airValue;
    private TextView log;
    private final Handler demoHandler = new Handler(Looper.getMainLooper());
    private final Runnable advanceDemo = this::advance;
    private static final long VEHICLE_STATE_POLL_MS = 1500;
    private boolean statePollInFlight;
    private boolean appControlInFlight;
    private boolean activityResumed;
    private final Runnable pollVehicleState = this::pollActualVehicleState;
    private final Runnable refreshLiveEnvironment = this::refreshLiveEnvironment;
    private Button next;
    private Button manual;
    private Button demist;
    private final VentilationInputBridge liveEnvironment =
            new VentilationInputBridge(WORK_ZONE, java.time.Clock.systemUTC());
    private Button fetchEnvironment;
    private Spinner apiSample;
    private TextView environmentResult;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initializes the Pleos Vehicle SDK entry point. This does not by itself
        // prove Vehicle Service connectivity or HVAC availability.
        pleosVehicleManager = new PleosVehicleManager(this);
        pleosVehicleManager.initialize();
        boolean wide = getResources().getConfiguration().screenWidthDp >= 600;
        uiScale = wide ? 1.45f : 1f;
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(wide ? 20 : 16), dp(wide ? 10 : 16), dp(wide ? 20 : 16), dp(wide ? 10 : 16));
        scroll.addView(root);
        setContentView(scroll);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
        TextView title = addText(header, "숨길", 27, TEXT, true);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        TextView demo = addText(header, "가상 주행", 13, ACCENT, true);
        demo.setBackground(surface(ACCENT_SOFT, 20));
        demo.setPadding(dp(14), dp(7), dp(14), dp(7));
        addText(root, "주행 경로에 맞춘 공조", 14, MUTED, false);

        if (!wide) buildEnvironmentPanel(root);

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.addView(columns, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout statusColumn = column();
        LinearLayout environmentColumn = column();
        LinearLayout controlsColumn = column();
        if (wide) {
            addWideColumn(columns, statusColumn, 1.2f, false, true);
            addWideColumn(columns, environmentColumn, 1.2f, true, true);
            addWideColumn(columns, controlsColumn, 0.8f, true, false);
            buildEnvironmentPanel(root);
        } else {
            columns.addView(statusColumn, new LinearLayout.LayoutParams(-1, -2));
            columns.addView(environmentColumn, new LinearLayout.LayoutParams(-1, -2));
            columns.addView(controlsColumn, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout dashboard = panel(statusColumn);
        addText(dashboard, "현재 차량 공조 상태", 15, MUTED, true);
        mode = addText(dashboard, "차량 기본 공조", 30, TEXT, true);
        mode.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        addText(dashboard, "동작 이유", 14, MUTED, true);
        status = addText(dashboard, "시연을 시작하면 공조 판단이 표시돼요.", 16, TEXT, false);

        LinearLayout environment = panel(environmentColumn);
        addText(environment, "경로의 환경 정보", 19, TEXT, true);
        route = addText(environment, "주행 시작 전", 15, MUTED, false);
        LinearLayout metrics = new LinearLayout(this);
        environment.addView(metrics, new LinearLayout.LayoutParams(-1, -2));
        workValue = addMetric(metrics, "도로 공사", false);
        airValue = addMetric(metrics, "인근 초미세먼지", true);

        LinearLayout settings = panel(controlsColumn);
        addText(settings, "시연 조작", 19, TEXT, true);
        addText(settings, "주행을 시작하면 공사 구간에 자동으로 접근하고 통과해요.", 14, MUTED, false);
        scenario = new Spinner(this);
        ArrayAdapter<Scenario> options = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, SCENARIOS);
        options.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        scenario.setAdapter(options);
        scenario.setContentDescription("시연 조건 선택");
        settings.addView(scenario, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        enabled = new Switch(this);
        enabled.setText("자동 공조 전환");
        enabled.setTextSize(16 * uiScale);
        enabled.setChecked(true);
        enabled.setMinHeight(dp(48));
        settings.addView(enabled, new LinearLayout.LayoutParams(-1, -2));
        Button start = addButton(settings, "이 조건으로 주행 시작",
                view -> startTrip(SCENARIOS[scenario.getSelectedItemPosition()]));
        start.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        start.setTextColor(Color.WHITE);

        next = addButton(settings, "다음 구간", view -> advance());
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        settings.addView(controls);
        manual = addButton(controls, "운전자 공조 조작", view -> {
            driverIntervened = true;
            vehicleMode = Mode.AUTOMATIC; // Simulated driver action, not a policy command.
            update();
        });
        demist = addButton(controls, "앞유리 김 서림 제거", view -> {
            defrost = true;
            vehicleMode = Mode.FRESH_AIR; // Simulated defrost priority.
            update();
        });

        Button hvacOnTest = addButton(settings, "실차 내기순환 ON 테스트", view ->
                confirmHvacTest(true));
        Button hvacOffTest = addButton(settings, "실차 내기순환 OFF 테스트", view ->
                confirmHvacTest(false));

        if (wide) {
            columns.post(() -> {
                int controlsHeight = settings.getMeasuredHeight();
                if (controlsHeight > 0) {
                    matchPanelHeight(dashboard, controlsHeight);
                    matchPanelHeight(environment, controlsHeight);
                }
            });
        }

        LinearLayout changes = panel(wide ? root : controlsColumn);
        addText(changes, "가상 주행 기록", 18, TEXT, true);
        log = addText(changes, "아직 변경이 없어요.", 15, MUTED, false);
        enabled.setOnCheckedChangeListener((button, checked) -> {
            if (started) update();
        });
        updateButtons();
        mode.setText("차량 공조 상태 확인 중");
    }

    /** Keep the dashboard synchronized with the vehicle, including driver-operated HVAC changes. */
    private void pollActualVehicleState() {
        if (isFinishing() || isDestroyed() || pleosVehicleManager == null) return;
        if (appControlInFlight || statePollInFlight) {
            if (activityResumed) demoHandler.postDelayed(pollVehicleState, VEHICLE_STATE_POLL_MS);
            return;
        }
        statePollInFlight = true;
        pleosVehicleManager.getRecirculationState((success, actual, detail) -> runOnUiThread(() -> {
            statePollInFlight = false;
            if (isFinishing() || isDestroyed()) return;
            // A poll that began before an app command must not overwrite the command's newer result.
            if (appControlInFlight) {
                if (activityResumed) demoHandler.postDelayed(pollVehicleState, VEHICLE_STATE_POLL_MS);
                return;
            }
            if (success && actual != null) {
                Mode liveMode = actual ? Mode.RECIRCULATION : Mode.AUTOMATIC;
                boolean changed = vehicleMode != liveMode;
                vehicleMode = liveMode;
                recirculating = actual;
                if (changed) {
                    if (started && !appControlInFlight) driverIntervened = true;
                    record("차량 공조 상태 변경 감지 · " + (actual ? "내기순환 중" : "기본 공조 중"));
                    if (started && policy != null) {
                        // Feed the live state into the policy so manual driver changes take priority.
                        update();
                    } else {
                        renderLiveVehicleMode();
                    }
                    if (!started) log.setText(String.join("\n", history));
                } else if (!started) {
                    renderLiveVehicleMode();
                }
            } else {
                Log.w("SoomgilPleos", "Live HVAC state polling failed: " + detail);
            }
            if (activityResumed) {
                demoHandler.removeCallbacks(pollVehicleState);
                demoHandler.postDelayed(pollVehicleState, VEHICLE_STATE_POLL_MS);
            }
        }));
    }

    private void renderLiveVehicleMode() {
        mode.setText(recirculating ? "내기순환 중" : "기본 공조 중");
        mode.setTextColor(recirculating ? ACCENT : TEXT);
        status.setText("실제 차량 공조 상태를 표시하고 있어요.");
    }

    private void confirmHvacTest(boolean enabled) {
        String action = enabled ? "내기 순환을 켜기" : "내기 순환을 끄기";
        new AlertDialog.Builder(this)
                .setTitle("실제 차량 공조 테스트")
                .setMessage("연결된 테스트 차량의 내기 순환을 " + action
                        + " 요청합니다. 차량이 안전하게 정차해 있고 테스트해도 되는지 확인하세요.")
                .setNegativeButton("취소", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("요청", (dialog, which) -> {
                    if (pleosVehicleManager != null)
                        pleosVehicleManager.setRecirculationForTest(enabled);
                })
                .show();
    }

    private void buildEnvironmentPanel(LinearLayout root) {
        LinearLayout card = panel(root);
        addText(card, "공사·주변 대기 관측", 18, TEXT, true);
        addText(card, "실제 조회 기준 · 서부간선도로 신정교~목동교", 14, MUTED, false);
        addButton(card, "실제 GPS·환경으로 주행 시작", view -> {
            if (checkSelfPermission(LOCATION_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{LOCATION_PERMISSION}, LOCATION_REQUEST);
            } else {
                startLocationTest();
            }
        });
        locationStatus = addText(card, "", 15, MUTED, false);
        fetchEnvironment = addButton(card, "실제 API에서 조회", view -> fetchEnvironment());
        fetchEnvironment.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        fetchEnvironment.setTextColor(Color.WHITE);
        apiSample = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, API_SAMPLES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        apiSample.setAdapter(adapter);
        apiSample.setContentDescription("공사 상태 샘플 선택");
        card.addView(apiSample, new LinearLayout.LayoutParams(-1, dp(50)));
        addButton(card, "샘플로 주행 시작", view -> startSampleTrip(apiSample.getSelectedItemPosition()));
        environmentResult = addText(card, "실제 API 조회 또는 샘플을 선택해 주세요.", 15, MUTED, false);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != LOCATION_REQUEST) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationTest();
        } else {
            locationStatus.setText("차량 위치 권한이 필요해요.");
        }
    }

    private void startLocationTest() {
        if (started && gpsTrip) {
            locationStatus.setText("실제 GPS 주행이 이미 진행 중이에요.");
            return;
        }
        if (gpsStartInFlight) return;
        gpsStartInFlight = true;
        locationStatus.setText("공사·대기 정보를 새로 확인하고 있어요.");
        apiClient.fetchAround(DEMO_LATITUDE, DEMO_LONGITUDE, new SoomgilApiClient.Callback() {
            @Override public void onSuccess(EnvironmentReading reading) {
                gpsStartInFlight = false;
                liveReading = reading;
                showLiveEnvironment(reading);
                startGpsTrip(reading);
            }

            @Override public void onFailure(String message) {
                gpsStartInFlight = false;
                EnvironmentReading failure = EnvironmentReading.failure(message);
                liveReading = failure;
                showLiveEnvironment(failure);
                locationStatus.setText("최신 환경 정보를 받지 못해 GPS 자동 공조를 시작하지 않았어요.");
            }
        });
    }

    private void startGpsTrip(EnvironmentReading reading) {
        if (reading == null || reading.hasFailed()
                || !Double.isFinite(reading.workLatitude) || !Double.isFinite(reading.workLongitude)) {
            locationStatus.setText("경로와 일치하는 공사 정보를 확인할 수 없어 자동화를 시작하지 않았어요.");
            return;
        }
        Observation fresh = liveEnvironment.observe(reading, null, 0, enabled.isChecked(), false, false);
        if (fresh.work() == Work.UNKNOWN || fresh.air() == Air.UNKNOWN) {
            locationStatus.setText("공사 상태 또는 최신 대기 정보를 확인할 수 없어 자동화를 시작하지 않았어요.");
            return;
        }
        SeobuDemoRoute.Match event = GPS_DEMO_ROUTE.match(reading.workLatitude, reading.workLongitude);
        if (event.offsetMeters() > 30 || event.distanceMeters() < GPS_DEMO_ROUTE.startMeters()
                || event.distanceMeters() > GPS_DEMO_ROUTE.endMeters()) {
            locationStatus.setText("공사 위치가 GPS 시연 구간과 일치하지 않아 자동화를 시작하지 않았어요.");
            return;
        }

        Scenario liveScenario = new Scenario("실시간 공사·대기 정보", fresh.work(), fresh.air(), fresh.air());
        if (locationSdk == null) {
            FusedLocation sdk = new FusedLocation(this);
            if (!sdk.isSupported()) {
                locationStatus.setText("이 기기에서는 Pleos 차량 위치 수신을 사용할 수 없어요.");
                return;
            }
            try {
                sdk.initialize();
                locationSdk = sdk;
                sdk.registerFusedLocationCallback(locationListener);
            } catch (RuntimeException error) {
                stopLocationTest();
                locationStatus.setText("차량 위치 수신을 시작하지 못했어요.");
                Log.w("SoomgilLocation", "Could not start vehicle location receiver", error);
                return;
            }
        }
        locationStatus.setText("실제 차량 위치를 기다리고 있어요.");
        startTrip(liveScenario, true);
    }

    private void stopLocationTest() {
        FusedLocation sdk = locationSdk;
        if (sdk == null) return;
        locationSdk = null;
        try {
            sdk.unregisterFusedLocationCallback(locationListener);
        } finally {
            sdk.release();
        }
    }

    private void fetchEnvironment() {
        fetchEnvironment.setEnabled(false);
        environmentResult.setText("ITS와 에어코리아를 조회하고 있어요…");
        apiClient.fetchAround(DEMO_LATITUDE, DEMO_LONGITUDE, new SoomgilApiClient.Callback() {
            @Override public void onSuccess(EnvironmentReading reading) {
                fetchEnvironment.setEnabled(true);
                showLiveEnvironment(reading);
            }

            @Override public void onFailure(String message) {
                fetchEnvironment.setEnabled(true);
                showLiveEnvironment(EnvironmentReading.failure(message));
            }
        });
    }

    private void refreshLiveEnvironment() {
        if (!activityResumed || !started || !gpsTrip) return;
        apiClient.fetchAround(DEMO_LATITUDE, DEMO_LONGITUDE, new SoomgilApiClient.Callback() {
            @Override public void onSuccess(EnvironmentReading reading) {
                if (isFinishing() || isDestroyed()) return;
                showLiveEnvironment(reading);
                record("실시간 공사·대기 정보 갱신");
                log.setText(String.join("\n", history));
                demoHandler.postDelayed(refreshLiveEnvironment, LIVE_ENVIRONMENT_REFRESH_MS);
            }

            @Override public void onFailure(String message) {
                if (isFinishing() || isDestroyed()) return;
                EnvironmentReading failure = EnvironmentReading.failure(message);
                showLiveEnvironment(failure);
                record("환경 정보 갱신 실패 · 자동화는 안전하게 대기");
                log.setText(String.join("\n", history));
                demoHandler.postDelayed(refreshLiveEnvironment, LIVE_ENVIRONMENT_REFRESH_MS);
            }
        });
    }

    private void scheduleLiveEnvironmentRefresh() {
        demoHandler.removeCallbacks(refreshLiveEnvironment);
        if (activityResumed && started && gpsTrip) {
            demoHandler.postDelayed(refreshLiveEnvironment, LIVE_ENVIRONMENT_REFRESH_MS);
        }
    }

    private void showLiveEnvironment(EnvironmentReading reading) {
        liveReading = reading;
        if (started && gpsTrip && gpsPosition != null) {
            gpsRouteEvidence = routeEvidenceFor(gpsPosition);
        }
        // Live route matching must provide evidence for the same ITS work zone.
        Observation input = liveEnvironment.observe(reading, null, 0,
                enabled.isChecked(), false, false);
        Decision decision = new VentilationPolicy().update(input, vehicleMode);
        String air = switch (input.air()) {
            case NORMAL -> "좋음/보통";
            case POOR -> "나쁨 이상";
            case UNKNOWN -> "확인 불가";
        };
        String control = decision.action() == Action.KEEP
                ? "공조 유지 · 공사 구간과 차량 경로 일치 확인 필요"
                : "공조 제어 조건 충족 · 시연 판단";
        String locationMatch = "";
        if (Double.isFinite(reading.workLatitude) && Double.isFinite(reading.workLongitude)) {
            SeobuDemoRoute.Match match = GPS_DEMO_ROUTE.match(reading.workLatitude, reading.workLongitude);
            boolean nearDemoZone = match.offsetMeters() <= 30
                    && match.distanceMeters() >= GPS_DEMO_ROUTE.startMeters()
                    && match.distanceMeters() <= GPS_DEMO_ROUTE.endMeters();
            locationMatch = nearDemoZone
                    ? (started && gpsTrip ? "\n공사 지점이 시연 경로와 일치해요. GPS 주행 중이에요." : "\n공사 지점이 시연 경로와 일치해요. GPS 주행을 시작하면 접근·통과 상태를 확인해요.")
                    : "\n공사 지점이 서부간선도로 시연 구간에서 벗어나 있어요.";
        }
        environmentResult.setText(reading.toDisplayText() + locationMatch
                + "\n판단에 사용한 대기 등급 · " + air
                + "\n" + (started && gpsTrip
                    ? "실제 GPS 위치에 따라 공조 상태를 판단하고 있어요."
                    : control));
        if (started && gpsTrip) update();
    }

    private void startSampleTrip(int selected) {
        EnvironmentReading reading = switch (selected) {
            case 0 -> EnvironmentSamples.inProgress();
            case 1 -> EnvironmentSamples.ended();
            default -> EnvironmentSamples.queryFailed();
        };
        environmentResult.setText("시연 샘플\n" + reading.toDisplayText());
        Work work = switch (reading.workStatus) {
            case IN_PROGRESS -> Work.ACTIVE;
            case ENDED -> Work.ENDED;
            case UNKNOWN -> Work.UNKNOWN;
        };
        Air air = switch (reading.pm25Grade) {
            case "1", "2" -> Air.NORMAL;
            case "3", "4" -> Air.POOR;
            default -> Air.UNKNOWN;
        };
        startTrip(new Scenario(reading.workStatusLabel(), work, air, air));
    }

    private void clearGpsPosition() {
        previousFix = null;
        gpsPosition = null;
        gpsRoad = Road.UNKNOWN;
        gpsRouteEvidence = null;
        if (started && gpsTrip) update();
    }

    private void acceptGpsPosition(LocationData fix) {
        if (!started || !gpsTrip) return;
        if (!Double.isFinite(fix.getLatitude()) || !Double.isFinite(fix.getLongitude())) {
            clearGpsPosition();
            return;
        }

        elapsed = (android.os.SystemClock.elapsedRealtime() - gpsStartedAt) / 1000;
        if (previousFix != null && fix.getTimestampMillis() <= previousFix.getTimestampMillis()) return;
        SeobuDemoRoute.Match projection = GPS_DEMO_ROUTE.match(fix.getLatitude(), fix.getLongitude());
        if (projection.offsetMeters() > 30
                || projection.distanceMeters() < GPS_DEMO_ROUTE.startMeters()
                || projection.distanceMeters() > GPS_DEMO_ROUTE.endMeters()) {
            clearGpsPosition();
            return;
        }
        if (previousFix == null) {
            previousFix = fix;
            update();
            return;
        }
        float[] movement = new float[2];
        android.location.Location.distanceBetween(previousFix.getLatitude(), previousFix.getLongitude(),
                fix.getLatitude(), fix.getLongitude(), movement);
        if (movement[0] < 10) return;
        previousFix = fix;

        WorkZoneApproach.Position position = new WorkZoneApproach.Position(
                DEMO_ROAD_ID, projection.distanceMeters(), movement[1]);
        gpsPosition = position;
        gpsRouteEvidence = routeEvidenceFor(position);
        Observation live = liveEnvironment.observe(liveReading, gpsRouteEvidence, elapsed,
                enabled.isChecked(), driverIntervened, defrost);
        gpsRoad = live.road();
        Log.d("SoomgilLocation", "GPS demo heading=" + movement[1] + " road=" + gpsRoad);
        update();
    }

    private VentilationInputBridge.RouteEvidence routeEvidenceFor(WorkZoneApproach.Position position) {
        if (liveReading == null || liveReading.hasFailed()
                || !Double.isFinite(liveReading.workLatitude)
                || !Double.isFinite(liveReading.workLongitude)) return null;
        SeobuDemoRoute.Match event = GPS_DEMO_ROUTE.match(
                liveReading.workLatitude, liveReading.workLongitude);
        if (event.offsetMeters() > 30 || event.distanceMeters() < GPS_DEMO_ROUTE.startMeters()
                || event.distanceMeters() > GPS_DEMO_ROUTE.endMeters()) return null;
        Work work = switch (liveReading.workStatus) {
            case IN_PROGRESS -> Work.ACTIVE;
            case ENDED -> Work.ENDED;
            case UNKNOWN -> Work.UNKNOWN;
        };
        double zoneStart = Math.max(GPS_DEMO_ROUTE.startMeters(),
                event.distanceMeters() - LIVE_WORK_POINT_HALF_LENGTH_METERS);
        double zoneEnd = Math.min(GPS_DEMO_ROUTE.endMeters(),
                event.distanceMeters() + LIVE_WORK_POINT_HALF_LENGTH_METERS);
        WorkZoneApproach.Zone zone = new WorkZoneApproach.Zone(
                DEMO_ROAD_ID, zoneStart, zoneEnd, event.headingDegrees(), work);
        WorkZoneApproach.Snapshot snapshot = new WorkZoneApproach.Snapshot(true, position, zone);
        return new VentilationInputBridge.RouteEvidence(
                liveReading.roadName, liveReading.segment, snapshot);
    }

    private void startTrip(Scenario selected) {
        startTrip(selected, false);
    }

    private void startTrip(Scenario selected, boolean useGps) {
        demoHandler.removeCallbacks(advanceDemo);
        if (!useGps && locationSdk != null) stopLocationTest();
        started = false;
        history.clear();
        record("주행 전 실제 차량 공조 상태를 확인하고 있어요.");
        log.setText(String.join("\n", history));
        if (pleosVehicleManager == null) {
            record("Pleos 연결이 없어 기존 공조 상태를 확인할 수 있어 주행을 시작하지 않았어요.");
            log.setText(String.join("\n", history));
            return;
        }
        pleosVehicleManager.getRecirculationState((success, actual, detail) -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (!success || actual == null) {
                if (useGps) stopLocationTest();
                record("기존 공조 상태 확인 실패 · 안전을 위해 자동 주행 제어를 시작하지 않았어요. " + detail);
                log.setText(String.join("\n", history));
                status.setText("차량 공조 상태를 확인할 수 없어 주행 제어를 보류해요.");
                return;
            }
            gpsTrip = useGps;
            gpsStartedAt = android.os.SystemClock.elapsedRealtime();
            previousFix = null;
            gpsPosition = null;
            gpsRoad = Road.UNKNOWN;
            gpsRouteEvidence = null;
            trip = selected;
            policy = new VentilationPolicy();
            history.clear();
            stage = 0;
            elapsed = 0;
            started = true;
            recirculating = actual;
            vehicleMode = actual ? Mode.RECIRCULATION : Mode.AUTOMATIC;
            driverIntervened = false;
            defrost = false;
            record(actual
                    ? "출발 전 차량 내기순환 ON 확인 · 앱은 이 상태를 기본값으로 존중"
                    : "출발 전 차량 내기순환 OFF 확인 · 앱이 전환한 경우 위험 해소 후 OFF 복원");
            // Refresh the environment card after GPS mode becomes active.
            if (gpsTrip && liveReading != null) showLiveEnvironment(liveReading);
            else update();
            if (!gpsTrip) demoHandler.postDelayed(advanceDemo, DEMO_STEP_DELAY_MS);
            else {
                scheduleLiveEnvironmentRefresh();
                if (locationSdk != null) locationSdk.getLastKnownLocation(locationListener);
            }
        }));
    }

    private void advance() {
        stage++;
        elapsed = stage * 10L;
        update();
        if (stage < DEMO_POSITIONS_METERS.length - 1) {
            if (!gpsTrip) demoHandler.postDelayed(advanceDemo, DEMO_STEP_DELAY_MS);
        }
    }

    private void update() {
        Road road;
        Work work;
        Air air;
        Observation input;
        if (gpsTrip) {
            input = liveEnvironment.observe(liveReading, gpsRouteEvidence, elapsed,
                    enabled.isChecked(), driverIntervened, defrost);
            road = input.road();
            work = input.work();
            air = input.air();
        } else {
            WorkZoneApproach.Position position = new WorkZoneApproach.Position(
                    DEMO_ROAD_ID, DEMO_POSITIONS_METERS[stage], DEMO_HEADING);
            WorkZoneApproach.Zone zone = new WorkZoneApproach.Zone(
                    DEMO_ROAD_ID, 200, 300, DEMO_HEADING, trip.work());
            road = WORK_ZONE.classify(new WorkZoneApproach.Snapshot(true, position, zone)).road();
            air = road == Road.PASSED ? trip.afterAir() : trip.approachAir();
            work = trip.work();
            input = new Observation(road, work, air, elapsed,
                    enabled.isChecked(), driverIntervened, defrost);
        }
        Decision decision = policy.update(input, vehicleMode);
        switch (decision.action()) {
            case SWITCH, RESTORE -> {
                if (policy.isPending(decision.requestId())) {
                    final long requestId = decision.requestId();
                    final Action requestedAction = decision.action();
                    final Mode target = decision.target();
                    boolean requestedRecirculation = target == Mode.RECIRCULATION;
                    if (pleosVehicleManager == null) {
                        policy.onApplied(requestId, false);
                        record("Pleos 연결 없음 · 공조 변경을 적용하지 못했어요.");
                    } else {
                        appControlInFlight = true;
                        pleosVehicleManager.setRecirculation(requestedRecirculation,
                                (requested, verified, actual, detail) -> runOnUiThread(() -> {
                                    appControlInFlight = false;
                                    if (policy == null || !policy.isPending(requestId)) return;
                                    policy.onApplied(requestId, verified);
                                    if (verified) {
                                        vehicleMode = actual ? Mode.RECIRCULATION : Mode.AUTOMATIC;
                                        recirculating = actual;
                                        record(requestedAction == Action.SWITCH
                                                ? "대기질 나쁨 감지 · 실제 차량 내기순환 ON 확인"
                                                : "공사 구간 통과/자동화 종료 · 실제 차량 공조 상태 복원 확인");
                                    } else {
                                        record("공조 변경 확인 실패 · " + detail);
                                    }
                                    renderDashboard(road, trip.work(), air);
                                    log.setText(history.isEmpty() ? "아직 변경이 없어요." : String.join("\n", history));
                                    updateButtons();
                                }));
                    }
                }
            }
            case KEEP -> { }
        }
        recirculating = vehicleMode == Mode.RECIRCULATION;

        renderDashboard(road, work, air);
        log.setText(history.isEmpty() ? "아직 변경이 없어요." : String.join("\n", history));
        updateButtons();
    }

    private void renderDashboard(Road road, Work work, Air air) {
        mode.setText(recirculating ? "내기순환 중" : "기본 공조 중");
        mode.setTextColor(recirculating ? ACCENT : TEXT);
        if (defrost || driverIntervened) {
            status.setText(priorityReason());
        } else if (!enabled.isChecked()) {
            status.setText("자동 전환이 꺼져 있어요.");
        } else if (road == Road.PASSED && air == Air.POOR) {
            status.setText(recirculating
                    ? "공사 구간은 지났지만 대기가 여전히 나빠 내기순환을 유지해요."
                    : "공사 구간은 지났지만 대기가 여전히 나빠요. 내기순환 전환 상태를 확인해 주세요.");
        } else if (work == Work.ENDED) {
            status.setText(recirculating ? "공사 종료 정보가 확인됐어요. 대기질 위험 해소 여부를 확인해요."
                    : "공사 종료 정보가 확인됐어요. 차량 기본 공조를 유지해요.");
        } else if (work == Work.UNKNOWN || air == Air.UNKNOWN) {
            status.setText(recirculating ? "환경 정보를 확인할 수 없어 새로운 전환 없이 현재 공조를 유지해요."
                    : "환경 정보를 확인할 수 없어 차량 기본 공조를 유지해요.");
        } else if (road == Road.PASSED) {
            status.setText(recirculating
                    ? "공사 구간과 대기질 위험이 해소될 때까지 내기순환을 유지해요."
                    : "공사 구간을 지났고 대기질도 보통이라 기본 공조로 복원했어요.");
        } else if (recirculating) {
            status.setText(road == Road.APPROACH ? "차량 내기순환 상태를 확인했어요. 실제 차량 상태를 유지해요."
                    : "실제 차량 내기순환 상태를 유지하고 있어요.");
        } else if (air == Air.NORMAL) {
            status.setText("인근 초미세먼지가 ‘보통’이라 차량 기본 공조를 유지해요.");
        } else {
            status.setText("등록 경로에서 공사 구간에 접근하고 있어요.");
        }
        String workLabel = switch (work) {
            case ACTIVE -> "진행";
            case ENDED -> "종료";
            case UNKNOWN -> "확인 불가";
        };
        String airLabel = switch (air) {
            case NORMAL -> "보통";
            case POOR -> "나쁨";
            case UNKNOWN -> "확인 불가";
        };
        String roadLabel = switch (road) {
            case BEFORE -> "출발";
            case APPROACH -> "공사 구간 진입 전";
            case WORK_ZONE -> "공사 구간 통과 중";
            case PASSED -> "구간 통과 후";
            case OPPOSITE -> "공사 구간 반대 방향";
            case UNKNOWN -> "위치 확인 불가";
        };
        route.setText("주행 단계 · " + roadLabel);
        workValue.setText(workLabel);
        workValue.setTextColor(work == Work.ACTIVE ? AMBER : TEXT);
        airValue.setText(airLabel);
        airValue.setTextColor(air == Air.POOR ? AMBER : TEXT);
    }

    private String priorityReason() {
        if (defrost) return "앞유리 김 서림 제거를 우선해요.";
        return "운전자의 공조 조작을 우선해요.";
    }

    private void record(String message) {
        history.addLast("+" + elapsed + "초  " + message);
    }

    private void updateButtons() {
        manual.setEnabled(started && !driverIntervened && !defrost);
        demist.setEnabled(started && !defrost);
        next.setEnabled(started && !gpsTrip && stage < DEMO_POSITIONS_METERS.length - 1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        demoHandler.removeCallbacks(pollVehicleState);
        demoHandler.post(pollVehicleState);
        scheduleLiveEnvironmentRefresh();
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        demoHandler.removeCallbacks(pollVehicleState);
        demoHandler.removeCallbacks(refreshLiveEnvironment);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopLocationTest();
        demoHandler.removeCallbacks(advanceDemo);
        demoHandler.removeCallbacks(refreshLiveEnvironment);
        apiClient.shutdown();
        if (pleosVehicleManager != null) pleosVehicleManager.release();
        super.onDestroy();
    }

    private LinearLayout panel(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(11), dp(14), dp(11));
        box.setBackground(surface(SURFACE, 12));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.topMargin = dp(10);
        parent.addView(box, layout);
        return box;
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private void matchPanelHeight(LinearLayout panel, int height) {
        ViewGroup.LayoutParams layout = panel.getLayoutParams();
        layout.height = height;
        panel.setLayoutParams(layout);
    }

    private void addWideColumn(LinearLayout parent, LinearLayout column, float weight,
                               boolean marginStart, boolean marginEnd) {
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, -2, weight);
        if (marginStart) layout.setMarginStart(dp(6));
        if (marginEnd) layout.setMarginEnd(dp(6));
        parent.addView(column, layout);
    }

    private TextView addMetric(LinearLayout parent, String label, boolean second) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setBackground(surface(INSET, 10));
        tile.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, -2, 1);
        layout.topMargin = dp(8);
        if (second) layout.setMarginStart(dp(10));
        parent.addView(tile, layout);
        addText(tile, label, 14, MUTED, false);
        return addText(tile, "—", 20, TEXT, true);
    }

    private GradientDrawable surface(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        background.setStroke(dp(1), Color.rgb(228, 231, 234));
        return background;
    }

    private TextView addText(LinearLayout parent, String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size * uiScale);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        text.setPadding(0, dp(4), 0, dp(4));
        parent.addView(text);
        return text;
    }

    private Button addButton(LinearLayout parent, String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16 * uiScale);
        button.setMinHeight(dp(52));
        button.setOnClickListener(listener);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(235, 238, 242)));
        button.setTextColor(TEXT);
        if (parent.getOrientation() == LinearLayout.HORIZONTAL) {
            parent.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        } else {
            parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
        }
        return button;
    }

    private int dp(int value) {
        return Math.round(value * uiScale * getResources().getDisplayMetrics().density);
    }
}

