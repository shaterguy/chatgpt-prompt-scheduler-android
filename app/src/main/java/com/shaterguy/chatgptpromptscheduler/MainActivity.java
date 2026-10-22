package com.shaterguy.chatgptpromptscheduler;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MainActivity extends Activity {
    private ConfigStore store;
    private LinearLayout root;
    private ScrollView scrollView;
    private String filterKey = ScheduleListPolicy.ALL;
    private String sortKey = ScheduleListPolicy.NEWEST;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ConfigStore(this);
        if (savedInstanceState != null) {
            filterKey = savedInstanceState.getString("scheduleFilter", ScheduleListPolicy.ALL);
            sortKey = savedInstanceState.getString("scheduleSort", ScheduleListPolicy.NEWEST);
        }
        NotificationHelper.ensureChannels(this);
        requestNotificationPermission();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (root != null) render();
        ProfileRegistrySync.refresh(this, result -> { if (!isFinishing() && !isDestroyed() && root != null && result.updated) render(); });
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("scheduleFilter", filterKey);
        state.putString("scheduleSort", sortKey);
        super.onSaveInstanceState(state);
    }

    private void render() { render(false); }

    private void render(boolean selectionChanged) {
        int previousScrollY = selectionChanged || scrollView == null ? 0 : scrollView.getScrollY();
        ScrollView scroll = Ui.scroll(this);
        scrollView = scroll;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 24));
        scroll.addView(root);
        root.addView(Ui.title(this, "ChatGPT Prompt Scheduler"));
        root.addView(Ui.body(this, "v" + BuildConfig.VERSION_NAME + " · 화면을 열지 않고 예약 프롬프트를 실행하는 Android 앱"));
        root.addView(Ui.button(this, "설정", v -> startActivity(new Intent(this, SettingsActivity.class))));

        root.addView(Ui.section(this, "예약 작업"));
        root.addView(Ui.button(this, "+ 예약 추가", v -> startActivity(new Intent(this, ScheduleEditorActivity.class))));

        List<Schedule> schedules = store.loadSchedules();
        ProjectCatalog catalog = new ProjectCatalog(this);
        Map<String, String> projectNames = new LinkedHashMap<>();
        for (ProjectUrlPolicy.ProjectRef project : catalog.entries()) {
            projectNames.put(project.projectId, catalog.displayName(project));
        }
        List<ScheduleListPolicy.Filter> filters = ScheduleListPolicy.filters(schedules, projectNames);
        if (!ScheduleListPolicy.hasFilter(filters, filterKey)) filterKey = ScheduleListPolicy.ALL;
        root.addView(Ui.body(this, "보기"));
        ArrayList<String> labels = new ArrayList<>();
        int selectedIndex = 0;
        for (int i = 0; i < filters.size(); i++) {
            ScheduleListPolicy.Filter filter = filters.get(i);
            labels.add(filter.label);
            if (filter.key.equals(filterKey)) selectedIndex = i;
        }
        Spinner filterSpinner = new Spinner(this);
        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterSpinner.setAdapter(filterAdapter);
        filterSpinner.setSelection(selectedIndex, false);
        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                String selected = filters.get(position).key;
                if (!selected.equals(filterKey)) {
                    filterKey = selected;
                    render(true);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        root.addView(filterSpinner);

        root.addView(Ui.body(this, "정렬"));
        Spinner sortSpinner = new Spinner(this);
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, List.of("최신 등록순", "예약 도래순"));
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(sortAdapter);
        sortSpinner.setSelection(ScheduleListPolicy.DUE.equals(sortKey) ? 1 : 0, false);
        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                String selected = position == 1 ? ScheduleListPolicy.DUE : ScheduleListPolicy.NEWEST;
                if (!selected.equals(sortKey)) {
                    sortKey = selected;
                    render(true);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        root.addView(sortSpinner);

        long now = System.currentTimeMillis();
        List<Schedule> visible = ScheduleListPolicy.visible(schedules, filterKey, sortKey, now);
        root.addView(Ui.body(this, "표시 " + visible.size() + "개 / 전체 " + schedules.size() + "개"));
        if (visible.isEmpty()) {
            root.addView(Ui.body(this, schedules.isEmpty()
                    ? "등록된 예약이 없습니다." : "현재 보기 조건에 해당하는 예약이 없습니다."));
        }
        for (Schedule schedule : visible) addScheduleCard(schedule, now);

        Ui.setContent(this, scroll);
        scroll.post(() -> {
            if (scrollView == scroll) scroll.scrollTo(0, previousScrollY);
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
            // Refresh due order after AlarmEngine has persisted its next-run target.
            root.post(() -> { if (!isFinishing() && !isDestroyed()) render(); });
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
