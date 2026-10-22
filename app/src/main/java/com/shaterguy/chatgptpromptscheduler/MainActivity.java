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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.view.View;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private static final String NON_PROJECT = "#nonproject";
    private String projectFilter = "";
    private int sortMode = 0;
    private ConfigStore store;
    private LinearLayout root;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ConfigStore(this);
        if (savedInstanceState != null) {
            projectFilter = savedInstanceState.getString("projectFilter", "");
            sortMode = savedInstanceState.getInt("sortMode", 0);
        }
        NotificationHelper.ensureChannels(this);
        requestNotificationPermission();
        render();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("projectFilter", projectFilter);
        state.putInt("sortMode", sortMode);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (root != null) render();
        ProfileRegistrySync.refresh(this, result -> { if (!isFinishing() && !isDestroyed() && root != null && result.updated) render(); });
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
        root.addView(Ui.body(this, "v" + BuildConfig.VERSION_NAME + " · 화면을 열지 않고 예약 프롬프트를 실행하는 Android 앱"));

        root.addView(Ui.section(this, "실행 준비 상태"));
        AlarmManager alarmManager = getSystemService(AlarmManager.class);
        boolean exact = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms();
        PowerManager power = getSystemService(PowerManager.class);
        boolean battery = power.isIgnoringBatteryOptimizations(getPackageName());
        root.addView(Ui.body(this, (exact ? "✓" : "✕") + " 정확한 알람 권한"));
        root.addView(Ui.body(this, (battery ? "✓" : "✕") + " 배터리 최적화 제외"));
        root.addView(Ui.button(this, "설정 및 관리", v ->
                startActivity(new Intent(this, SettingsActivity.class))));

        root.addView(Ui.section(this, "예약 작업"));
        Button add = Ui.button(this, "+ 예약 추가", v -> startActivity(new Intent(this, ScheduleEditorActivity.class)));
        root.addView(add);
        List<Schedule> schedules = store.loadSchedules();
        addListControls(schedules);
        long now = System.currentTimeMillis();
        List<Schedule> visible = ScheduleListOrder.visible(schedules, projectFilter, sortMode, now);
        if (visible.isEmpty()) root.addView(Ui.body(this,
                schedules.isEmpty() ? "등록된 예약이 없습니다." : "이 보기에 해당하는 예약이 없습니다."));
        for (Schedule schedule : visible) addScheduleCard(schedule, now);
        Ui.setContent(this, scroll);
        scroll.post(() -> {
            if (scrollView == scroll) scroll.scrollTo(0, previousScrollY);
        });
    }

    private void addListControls(List<Schedule> schedules) {
        ProjectCatalog catalog = new ProjectCatalog(this);
        Map<String, String> projects = new LinkedHashMap<>();
        for (ProjectUrlPolicy.ProjectRef ref : catalog.entries())
            projects.put(ref.projectId, catalog.displayName(ref));
        boolean hasNonProject = false;
        for (Schedule schedule : schedules) {
            ProjectUrlPolicy.ProjectRef ref = ScheduleListOrder.projectOf(schedule);
            if (ref != null) {
                if (!projects.containsKey(ref.projectId))
                    projects.put(ref.projectId, catalog.displayName(ref));
            } else hasNonProject = true;
        }
        ArrayList<String> keys = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        keys.add(""); labels.add("전체보기");
        for (Map.Entry<String, String> entry : projects.entrySet()) {
            keys.add(entry.getKey());
            int duplicates = 0;
            for (String name : projects.values()) if (name.equals(entry.getValue())) duplicates++;
            labels.add(duplicates > 1 ? entry.getValue() + " (" + entry.getKey() + ")" : entry.getValue());
        }
        if (hasNonProject) { keys.add(NON_PROJECT); labels.add("일반·기존 대화"); }
        if (!keys.contains(projectFilter)) projectFilter = "";

        root.addView(Ui.body(this, "프로젝트별 보기"));
        Spinner filter = new Spinner(this);
        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filter.setAdapter(filterAdapter);
        filter.setSelection(keys.indexOf(projectFilter), false);
        root.addView(filter);
        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = keys.get(position);
                if (!selected.equals(projectFilter)) { projectFilter = selected; render(); }
            }
        });

        root.addView(Ui.body(this, "정렬 기준"));
        Spinner ordering = new Spinner(this);
        ArrayAdapter<String> orderAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"최신 등록순", "예약 도래순"});
        orderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ordering.setAdapter(orderAdapter);
        ordering.setSelection(sortMode, false);
        root.addView(ordering);
        ordering.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (sortMode != position) { sortMode = position; render(); }
            }
        });
    }

    private void addScheduleCard(Schedule schedule, long now) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        card.setBackgroundResource(R.drawable.panel_background);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        root.addView(card, cardParams);
        card.addView(Ui.section(this, schedule.name));
        String targetSummary = "existing".equals(schedule.targetType)
                ? "existing · 기존 대화 모드 유지"
                : schedule.targetType + " · " + Schedule.normalizedExperience(schedule.targetType, schedule.experience);
        String reasoningSummary;
        if ("work".equals(schedule.experience)) {
            reasoningSummary = " · 모델 " + Schedule.displayWorkModel(schedule.experience, schedule.workModel)
                    + " · 추론 " + Schedule.displayReasoningEffort(schedule.experience, schedule.reasoningEffort);
        } else if ("chat".equals(schedule.experience) && !"existing".equals(schedule.targetType)) {
            reasoningSummary = " · 추론 " + Schedule.displayChatReasoning(schedule.experience, schedule.chatReasoning);
        } else {
            reasoningSummary = "";
        }
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
                Ui.button(this, "편집", v -> startActivity(new Intent(this, ScheduleEditorActivity.class)
                        .putExtra("scheduleId", schedule.id))),
                Ui.button(this, "지금 실행", v -> runNow(schedule.id)),
                Ui.button(this, "삭제", v -> confirmDelete(schedule))));
    }

    private void runNow(String scheduleId) {
        QueueStore queueStore = new QueueStore(this);
        QueueStore.EnqueueResult result;
        try {
            result = queueStore.enqueue(scheduleId, true);
        } catch (RuntimeException error) {
            toast("실행 대기열 저장 실패: " + error.getMessage());
            return;
        }
        if (!result.added) {
            toast("이미 실행 중이거나 대기 중인 예약입니다.");
            return;
        }
        Intent service = new Intent(this, ExecutionService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
            toast("실행 대기열에 추가했습니다.");
        } catch (RuntimeException error) {
            queueStore.finish(result.runId);
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

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4001);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
