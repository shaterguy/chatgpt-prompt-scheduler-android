package com.shaterguy.chatgptpromptscheduler;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int REQUEST_EXPORT = 1001;
    private static final int REQUEST_IMPORT = 1002;
    private ConfigStore store;
    private LinearLayout root;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ConfigStore(this);
        NotificationHelper.ensureChannels(this);
        requestNotificationPermission();
        render();
        recoverOrchestrationService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (root != null) render();
    }

    private void render() {
        int previousScrollY = scrollView == null ? 0 : scrollView.getScrollY();
        ScrollView scroll = Ui.scroll(this);
        scrollView = scroll;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 24));
        scroll.addView(root);
        root.addView(Ui.title(this, "ChatGPT Prompt Scheduler"));
        root.addView(Ui.body(this, "v0.1.16 · 화면을 열지 않고 예약 프롬프트를 실행하는 Android 앱"));

        root.addView(Ui.section(this, "선택 기능 · 오토런 중계"));
        root.addView(Ui.body(this, "예약 실행과 분리된 Protocol 3.x 중계입니다. 예약 작업이 항상 우선합니다."));
        root.addView(Ui.button(this, "오토런 중계 열기", v -> startActivity(new Intent(this, OrchestrationActivity.class))));

        root.addView(Ui.section(this, "실행 준비 상태"));
        AlarmManager alarmManager = getSystemService(AlarmManager.class);
        boolean exact = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms();
        PowerManager power = getSystemService(PowerManager.class);
        boolean battery = power.isIgnoringBatteryOptimizations(getPackageName());
        root.addView(Ui.body(this, (exact ? "✓" : "✕") + " 정확한 알람 권한"));
        root.addView(Ui.body(this, (battery ? "✓" : "✕") + " 배터리 최적화 제외"));
        root.addView(Ui.body(this, "로그인 세션은 아래 ‘ChatGPT 로그인/세션’ 화면에서 확인합니다."));

        root.addView(Ui.actionGrid(this,
                Ui.button(this, "알람 권한", v -> requestExactAlarm()),
                Ui.button(this, "절전 제외", v -> requestBatteryExemption()),
                Ui.button(this, "로그인/세션", v -> startActivity(new Intent(this, LoginActivity.class))),
                Ui.button(this, "설정", v -> startActivity(new Intent(this, SettingsActivity.class)))));

        root.addView(Ui.section(this, "예약 작업"));
        Button add = Ui.button(this, "+ 예약 추가", v -> startActivity(new Intent(this, ScheduleEditorActivity.class)));
        root.addView(add);
        List<Schedule> schedules = store.loadSchedules();
        if (schedules.isEmpty()) root.addView(Ui.body(this, "등록된 예약이 없습니다."));
        long now = System.currentTimeMillis();
        for (Schedule schedule : schedules) addScheduleCard(schedule, now);

        root.addView(Ui.section(this, "백업 및 진단"));
        root.addView(Ui.actionGrid(this,
                Ui.button(this, "설정 내보내기", v -> exportConfig()),
                Ui.button(this, "설정 가져오기", v -> importConfig()),
                Ui.button(this, "실행 기록", v -> startActivity(new Intent(this, LogsActivity.class))),
                Ui.button(this, "알람 재구축", v -> { AlarmEngine.rebuildAll(this); toast("알람을 다시 등록했습니다."); })));
        Ui.setContent(this, scroll);
        scroll.post(() -> {
            if (scrollView == scroll) scroll.scrollTo(0, previousScrollY);
        });
    }

    /** Process/force-stop recovery after the user directly reopens the app; never mutates relay state. */
    private void recoverOrchestrationService() {
        OrchestrationStore relay = new OrchestrationStore(this);
        String delivery = relay.deliveryState();
        if (!relay.active() || relay.paused() || relay.terminal() || relay.waitingForUser()
                || OrchestrationStore.DELIVERY_AMBIGUOUS.equals(delivery)
                || OrchestrationStore.DELIVERY_FAILED.equals(delivery)) return;
        Intent service = new Intent(this, OrchestrationService.class).setAction(OrchestrationService.ACTION_RUN);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
            new OrchestrationRunLog(this).record(relay, "APP_RECOVERY", "source=activity");
        } catch (RuntimeException ignored) {
            relay.fail("SERVICE_RECOVERY_FAILED", "앱 재실행 후 오토런 중계 서비스를 복구하지 못했습니다.");
            new OrchestrationRunLog(this).record(relay, "APP_RECOVERY_FAILED", "source=activity");
            if (NotificationHelper.orchestrationAlertsEnabled(this)) {
                NotificationHelper.orchestrationError(this, relay.monitoringSide(), relay.runJobId(),
                        relay.currentStep(), relay.currentRound(), "앱 재실행 후 중계 서비스를 복구하지 못했습니다.");
            }
        }
    }

    private void addScheduleCard(Schedule schedule, long now) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        card.setBackgroundResource(R.drawable.panel_background);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        root.addView(card, cardParams);
        card.addView(Ui.section(this, schedule.name));
        String targetSummary = "existing".equals(schedule.targetType)
                ? "existing · 기존 대화 모드 유지"
                : schedule.targetType + " · " + Schedule.normalizedExperience(schedule.targetType, schedule.experience);
        String reasoningSummary = "work".equals(schedule.experience)
                ? " · 모델 " + Schedule.normalizedWorkModel(schedule.experience, schedule.workModel)
                        + " · 추론 " + Schedule.displayReasoningEffort(schedule.experience, schedule.reasoningEffort)
                : "";
        card.addView(Ui.body(this, targetSummary + reasoningSummary + " · " + Recurrence.describeNext(schedule, now)));
        card.addView(Ui.body(this, "마지막 상태: " + schedule.lastStatus));
        Switch toggle = new Switch(this);
        toggle.setText(schedule.enabled ? "활성" : "비활성");
        toggle.setChecked(schedule.enabled);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            schedule.enabled = checked;
            store.saveSchedule(schedule);
            AlarmEngine.cancel(this, schedule.id);
            if (checked) AlarmEngine.scheduleNext(this, schedule, System.currentTimeMillis());
            button.setText(checked ? "활성" : "비활성");
        });
        card.addView(Ui.actionGrid(this, toggle,
                Ui.button(this, "편집", v -> startActivity(new Intent(this, ScheduleEditorActivity.class).putExtra("scheduleId", schedule.id))),
                Ui.button(this, "지금 실행", v -> runNow(schedule.id)),
                Ui.button(this, "삭제", v -> confirmDelete(schedule))));
    }

    private void runNow(String scheduleId) {
        QueueStore queueStore = new QueueStore(this);
        QueueStore.EnqueueResult result;
        AutomationRuntimeGate.setScheduleActive(true);
        try {
            result = queueStore.enqueue(scheduleId, true);
        } catch (RuntimeException error) {
            AutomationRuntimeGate.setScheduleActive(false);
            toast("실행 대기열 저장 실패: " + error.getMessage());
            return;
        }
        if (!result.added) {
            AutomationRuntimeGate.setScheduleActive(queueStore.hasActive());
            toast("이미 실행 중이거나 대기 중인 예약입니다.");
            return;
        }

        Intent service = new Intent(this, ExecutionService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
            toast("실행 대기열에 추가했습니다.");
        } catch (RuntimeException error) {
            queueStore.finish(result.runId);
            AutomationRuntimeGate.setScheduleActive(queueStore.hasActive());
            toast("실행 서비스 시작 실패: " + error.getMessage());
        }
    }

    private void confirmDelete(Schedule schedule) {
        new AlertDialog.Builder(this).setTitle("예약 삭제").setMessage(schedule.name + " 예약을 삭제합니다.")
                .setNegativeButton("취소", null).setPositiveButton("삭제", (dialog, which) -> {
                    AlarmEngine.cancel(this, schedule.id);
                    store.deleteSchedule(schedule.id);
                    render();
                }).show();
    }

    private void exportConfig() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").putExtra(Intent.EXTRA_TITLE, "chatgpt-prompt-scheduler-settings.json");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void importConfig() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT) {
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                output.write(store.exportPortable().toString(2).getBytes(StandardCharsets.UTF_8));
                toast("설정 JSON을 저장했습니다.");
            } catch (Exception error) { toast("내보내기 실패: " + error.getMessage()); }
        } else if (requestCode == REQUEST_IMPORT) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
                StringBuilder text = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) text.append(line).append('\n');
                JSONObject incoming = new JSONObject(text.toString());
                int count = incoming.optJSONArray("schedules") == null ? 0 : incoming.optJSONArray("schedules").length();
                new AlertDialog.Builder(this).setTitle("설정 가져오기")
                        .setMessage("현재 예약과 설정을 교체하고 " + count + "개 예약을 가져옵니다. 로그인 쿠키와 실행 기록은 변경하지 않습니다.")
                        .setNegativeButton("취소", null).setPositiveButton("교체", (dialog, which) -> {
                            try {
                                int imported = store.importPortable(incoming);
                                AlarmEngine.rebuildAll(this);
                                toast(imported + "개 예약을 가져왔습니다.");
                                render();
                            } catch (Exception error) { toast("가져오기 실패: " + error.getMessage()); }
                        }).show();
            } catch (Exception error) { toast("파일 읽기 실패: " + error.getMessage()); }
        }
    }

    private void requestExactAlarm() {
        if (Build.VERSION.SDK_INT >= 31) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } else toast("이 Android 버전에서는 별도 권한이 필요하지 않습니다.");
    }

    private void requestBatteryExemption() {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
        try { startActivity(intent); } catch (Exception error) { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4001);
        }
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
