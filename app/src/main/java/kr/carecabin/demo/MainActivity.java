package kr.carecabin.demo;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
    private static final long[] STEP_TIMES = {0, 10, 20, 30};

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

    private VentilationPolicy policy;
    private final ArrayDeque<String> history = new ArrayDeque<>();
    private int stage;
    private long elapsed;
    private Scenario trip;
    private boolean started;
    private boolean recirculating;
    private Mode vehicleMode = Mode.AUTOMATIC;
    private boolean driverIntervened;
    private boolean defrost;
    private boolean timedOut;

    private Spinner scenario;
    private Switch enabled;
    private TextView mode;
    private TextView status;
    private TextView route;
    private TextView workValue;
    private TextView airValue;
    private TextView log;
    private Button next;
    private Button manual;
    private Button demist;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        addText(dashboard, "현재 가상 공조", 15, MUTED, true);
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
        addText(settings, "가상 공사·대기·차량 상태를 바꿔봐요.", 14, MUTED, false);
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
        Button start = addButton(settings, "이 조건으로 주행 시작", view -> startTrip());
        start.setBackgroundTintList(ColorStateList.valueOf(MINT));
        start.setTextColor(BACKGROUND);

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

        LinearLayout changes = panel(side);
        addText(changes, "가상 주행 기록", 19, Color.WHITE, true);
        log = addText(changes, "아직 변경이 없어요.", 15, MUTED, false);
        enabled.setOnCheckedChangeListener((button, checked) -> {
            if (started) update();
        });
        updateButtons();
    }

    private void startTrip() {
        trip = SCENARIOS[scenario.getSelectedItemPosition()];
        policy = new VentilationPolicy();
        history.clear();
        stage = 0;
        elapsed = 0;
        started = true;
        recirculating = false;
        vehicleMode = Mode.AUTOMATIC;
        driverIntervened = false;
        defrost = false;
        timedOut = false;
        update();
    }

    private void advance() {
        if (stage < STEP_TIMES.length - 1) {
            stage++;
            elapsed = STEP_TIMES[stage];
        } else {
            elapsed = 60;
            timedOut = true;
        }
        update();
    }

    private void update() {
        Air air = stage == 3 ? trip.afterAir() : trip.approachAir();
        Decision decision = policy.update(new Observation(Road.values()[stage], trip.work(), air, elapsed,
                enabled.isChecked(), driverIntervened, defrost), vehicleMode);
        switch (decision.action()) {
            case SWITCH, RESTORE -> {
                // Demo-only successful application. Real integration must wait for the vehicle callback.
                if (policy.isPending(decision.requestId())) {
                    vehicleMode = decision.target();
                    policy.onApplied(decision.requestId(), true);
                    timedOut = decision.reason() == Reason.OVERRIDE_TIMEOUT;
                    record(decision.action() == Action.SWITCH
                            ? "전방 공사 진행 · 인근 초미세먼지 나쁨 · 진입 전 내기순환 시작"
                            : decision.reason() == Reason.AUTOMATIC_DISABLED
                            ? "자동 전환 해제 · 이전 설정 복원"
                            : timedOut ? "시연용 작동 시간 경과 · 이전 설정 복원"
                            : "구간 통과 · 이전 공조 설정 복원");
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
        mode.setText(recirculating ? "내기순환 중" : "차량 기본 공조");
        mode.setTextColor(recirculating ? MINT : Color.WHITE);
        if (defrost || driverIntervened || timedOut) {
            status.setText(priorityReason());
        } else if (!enabled.isChecked()) {
            status.setText("자동 전환이 꺼져 있어요.");
        } else if (work == Work.ENDED) {
            status.setText(recirculating ? "공사 종료 정보가 확인됐어요. 구간 통과 후 이전 설정으로 복원해요."
                    : "공사 종료 정보가 확인됐어요. 차량 기본 공조를 유지해요.");
        } else if (work == Work.UNKNOWN || air == Air.UNKNOWN) {
            status.setText(recirculating ? "환경 정보를 확인할 수 없어 새로운 전환 없이 현재 공조를 유지해요."
                    : "환경 정보를 확인할 수 없어 차량 기본 공조를 유지해요.");
        } else if (stage == 3) {
            status.setText("공사 구간을 지났어요. 차량 기본 공조로 운행해요.");
        } else if (recirculating) {
            status.setText(stage == 1 ? "전방 공사와 인근 초미세먼지 정보를 확인해 내기로 전환했어요."
                    : "공사 구간을 지나는 동안 내기순환 중이에요.");
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
        if (driverIntervened) return "운전자의 공조 조작을 우선해요.";
        return "시연용 작동 시간이 지나 자동 제어를 종료했어요.";
    }

    private void record(String message) {
        history.addLast("+" + elapsed + "초  " + message);
    }

    private void updateButtons() {
        next.setEnabled(started && (stage < 3 || recirculating));
        next.setText(stage == 3 && recirculating ? "시간 경과 시연" : "다음 구간");
        manual.setEnabled(started && !driverIntervened && !defrost);
        demist.setEnabled(started && !defrost);
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
