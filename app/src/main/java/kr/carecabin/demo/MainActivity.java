package kr.carecabin.demo;

import android.app.Activity;
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
    private static final int BACKGROUND = Color.rgb(8, 19, 27);
    private static final int SURFACE = Color.rgb(18, 35, 45);
    private static final int INSET = Color.rgb(13, 27, 36);
    private static final int MINT = Color.rgb(125, 236, 197);
    private static final int MUTED = Color.rgb(181, 202, 212);
    private static final int AMBER = Color.rgb(255, 206, 133);
    private static final String[] STAGES = {"출발", "공사 구간 진입 전", "공사 구간 통과 중", "구간 통과 후"};
    private static final long DEMO_STEP_DELAY_MS = 3000;
    private static final long[] STEP_TIMES = {0, 10, 20, 30};
    private static final double DEMO_LATITUDE = 34.69806;
    private static final double DEMO_LONGITUDE = 126.69637;
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
    private Button manual;
    private Button demist;
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
        boolean wide = getResources().getConfiguration().screenWidthDp >= 760;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(wide ? 28 : 16), dp(20), dp(wide ? 28 : 16), dp(20));
        scroll.addView(root);
        setContentView(scroll);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
        TextView title = addText(header, "숨길", 32, Color.WHITE, true);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        TextView demo = addText(header, "가상 주행", 14, MINT, true);
        demo.setBackground(surface(SURFACE, 20));
        demo.setPadding(dp(14), dp(7), dp(14), dp(7));
        addText(root, "주행 경로에 맞춘 공조", 16, MUTED, false);
        buildEnvironmentPanel(root);

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.addView(columns, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout overview = new LinearLayout(this);
        overview.setOrientation(LinearLayout.VERTICAL);
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        columns.addView(overview, wide ? new LinearLayout.LayoutParams(0, -2, 3)
                : new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams sideLayout = wide ? new LinearLayout.LayoutParams(0, -2, 2)
                : new LinearLayout.LayoutParams(-1, -2);
        if (wide) sideLayout.setMarginStart(dp(16));
        columns.addView(side, sideLayout);

        LinearLayout dashboard = panel(overview);
        addText(dashboard, "현재 차량 공조 상태", 15, MUTED, true);
        mode = addText(dashboard, "차량 기본 공조", 34, Color.WHITE, true);
        mode.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        addText(dashboard, "동작 이유", 14, MUTED, true);
        status = addText(dashboard, "시연을 시작하면 공조 판단이 표시돼요.", 17, Color.WHITE, false);

        LinearLayout environment = panel(overview);
        addText(environment, "경로의 환경 정보", 20, Color.WHITE, true);
        route = addText(environment, "주행 시작 전", 15, MUTED, false);
        LinearLayout metrics = new LinearLayout(this);
        environment.addView(metrics, new LinearLayout.LayoutParams(-1, -2));
        workValue = addMetric(metrics, "도로 공사", false);
        airValue = addMetric(metrics, "인근 초미세먼지", true);

        LinearLayout settings = panel(side);
        addText(settings, "시연 조작", 20, Color.WHITE, true);
        addText(settings, "주행을 시작하면 공사 구간에 자동으로 접근하고 통과해요.", 14, MUTED, false);
        scenario = new Spinner(this);
        ArrayAdapter<Scenario> options = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, SCENARIOS);
        options.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        scenario.setAdapter(options);
        scenario.setContentDescription("시연 조건 선택");
        settings.addView(scenario, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        enabled = new Switch(this);
        enabled.setText("자동 공조 전환");
        enabled.setChecked(true);
        enabled.setMinHeight(dp(48));
        settings.addView(enabled, new LinearLayout.LayoutParams(-1, -2));
        Button start = addButton(settings, "이 조건으로 주행 시작", view -> startTrip(SCENARIOS[scenario.getSelectedItemPosition()]));
        start.setBackgroundTintList(ColorStateList.valueOf(MINT));
        start.setTextColor(BACKGROUND);

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

        LinearLayout changes = panel(side);
        addText(changes, "가상 주행 기록", 19, Color.WHITE, true);
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
        mode.setTextColor(recirculating ? MINT : Color.WHITE);
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
        addText(card, "공사·주변 대기 관측", 20, Color.WHITE, true);
        addText(card, "시연 좌표 · 34.69806, 126.69637", 14, MUTED, false);
        fetchEnvironment = addButton(card, "실제 API에서 조회", view -> fetchEnvironment());
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

    private void fetchEnvironment() {
        fetchEnvironment.setEnabled(false);
        environmentResult.setText("ITS와 에어코리아를 조회하고 있어요…");
        apiClient.fetchAround(DEMO_LATITUDE, DEMO_LONGITUDE, new SoomgilApiClient.Callback() {
            @Override public void onSuccess(EnvironmentReading reading) {
                fetchEnvironment.setEnabled(true);
                environmentResult.setText(reading.toDisplayText());
            }

            @Override public void onFailure(String message) {
                fetchEnvironment.setEnabled(true);
                environmentResult.setText(EnvironmentReading.failure(message).toDisplayText());
            }
        });
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

    private void startTrip(Scenario selected) {
        demoHandler.removeCallbacks(advanceDemo);
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
                record("기존 공조 상태 확인 실패 · 안전을 위해 자동 주행 제어를 시작하지 않았어요. " + detail);
                log.setText(String.join("\n", history));
                status.setText("차량 공조 상태를 확인할 수 없어 주행 제어를 보류해요.");
                return;
            }
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
            update();
            demoHandler.postDelayed(advanceDemo, DEMO_STEP_DELAY_MS);
        }));
    }

    private void advance() {
        stage++;
        elapsed = STEP_TIMES[stage];
        update();
        if (stage < STEP_TIMES.length - 1) {
            demoHandler.postDelayed(advanceDemo, DEMO_STEP_DELAY_MS);
        }
    }

    private void update() {
        Air air = stage == 3 ? trip.afterAir() : trip.approachAir();
        Decision decision = policy.update(new Observation(Road.values()[stage], trip.work(), air, elapsed,
                enabled.isChecked(), driverIntervened, defrost), vehicleMode);
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
                                    renderDashboard(trip.work(), air);
                                    log.setText(history.isEmpty() ? "아직 변경이 없어요." : String.join("\n", history));
                                    updateButtons();
                                }));
                    }
                }
            }
            case KEEP -> { }
        }
        recirculating = vehicleMode == Mode.RECIRCULATION;

        renderDashboard(trip.work(), air);
        log.setText(history.isEmpty() ? "아직 변경이 없어요." : String.join("\n", history));
        updateButtons();
    }

    private void renderDashboard(Work work, Air air) {
        mode.setText(recirculating ? "내기순환 중" : "기본 공조 중");
        mode.setTextColor(recirculating ? MINT : Color.WHITE);
        if (defrost || driverIntervened) {
            status.setText(priorityReason());
        } else if (!enabled.isChecked()) {
            status.setText("자동 전환이 꺼져 있어요.");
        } else if (stage == 3 && air == Air.POOR) {
            status.setText(recirculating
                    ? "공사 구간은 지났지만 대기가 여전히 나빠 내기순환을 유지해요."
                    : "공사 구간은 지났지만 대기가 여전히 나빠요. 내기순환 전환 상태를 확인해 주세요.");
        } else if (work == Work.ENDED) {
            status.setText(recirculating ? "공사 종료 정보가 확인됐어요. 대기질 위험 해소 여부를 확인해요."
                    : "공사 종료 정보가 확인됐어요. 차량 기본 공조를 유지해요.");
        } else if (work == Work.UNKNOWN || air == Air.UNKNOWN) {
            status.setText(recirculating ? "환경 정보를 확인할 수 없어 새로운 전환 없이 현재 공조를 유지해요."
                    : "환경 정보를 확인할 수 없어 차량 기본 공조를 유지해요.");
        } else if (stage == 3) {
            status.setText(recirculating
                    ? "공사 구간과 대기질 위험이 해소될 때까지 내기순환을 유지해요."
                    : "공사 구간을 지났고 대기질도 보통이라 기본 공조로 복원했어요.");
        } else if (recirculating) {
            status.setText(stage == 1 ? "차량 내기순환 상태를 확인했어요. 실제 차량 상태를 유지해요."
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
        route.setText("주행 단계 · " + STAGES[stage]);
        workValue.setText(workLabel);
        workValue.setTextColor(work == Work.ACTIVE ? AMBER : Color.WHITE);
        airValue.setText(airLabel);
        airValue.setTextColor(air == Air.POOR ? AMBER : Color.WHITE);
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        demoHandler.removeCallbacks(pollVehicleState);
        demoHandler.post(pollVehicleState);
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        demoHandler.removeCallbacks(pollVehicleState);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        demoHandler.removeCallbacks(advanceDemo);
        apiClient.shutdown();
        if (pleosVehicleManager != null) pleosVehicleManager.release();
        super.onDestroy();
    }

    private LinearLayout panel(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setBackground(surface(SURFACE, 16));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.topMargin = dp(14);
        parent.addView(box, layout);
        return box;
    }

    private TextView addMetric(LinearLayout parent, String label, boolean second) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setBackground(surface(INSET, 12));
        tile.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, -2, 1);
        layout.topMargin = dp(8);
        if (second) layout.setMarginStart(dp(10));
        parent.addView(tile, layout);
        addText(tile, label, 14, MUTED, false);
        return addText(tile, "—", 20, Color.WHITE, true);
    }

    private GradientDrawable surface(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        return background;
    }

    private TextView addText(LinearLayout parent, String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
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
        button.setMinHeight(dp(48));
        button.setOnClickListener(listener);
        if (parent.getOrientation() == LinearLayout.HORIZONTAL) {
            parent.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        } else {
            parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
        }
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

