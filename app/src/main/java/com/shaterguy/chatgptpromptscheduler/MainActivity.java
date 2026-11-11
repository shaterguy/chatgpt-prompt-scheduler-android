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
        sortKey = getPreferences(MODE_PRIVATE).getString("scheduleSort", ScheduleListPolicy.NEWEST);
        if (!ScheduleListPolicy.DUE.equals(sortKey)) sortKey = ScheduleListPolicy.NEWEST;
        if (savedInstanceState != null) {
            filterKey = savedInstanceState.getString("scheduleFilter", ScheduleListPolicy.ALL);
        }
        NotificationHelper.ensureChannels(this);
        requestNotificationPermission();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (root != null) render();
        if (store.loadSchedules().stream().anyMatch(schedule -> schedule.enabled && schedule.excludesHolidays())) {
            KoreanHolidaySync.refresh(this, changed -> {
                if (!changed) return;
                AlarmEngine.reconcileHolidayAlarms(this);
                if (!isFinishing() && !isDestroyed() && root != null) render();
            });
        }
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
        root = Ui.content(this);
        scroll.addView(root);
        root.addView(Ui.title(this, "예약"));
        Ui.navigation(this, root, "schedules");

        List<Schedule> schedules = store.loadSchedules();
        int enabledCount = 0;
        for (Schedule schedule : schedules) if (schedule.enabled) enabledCount++;
        root.addView(Ui.body(this, "전체 " + schedules.size() + "개 · 활성 " + enabledCount + "개"));
        root.addView(Ui.primaryButton(this, "예약 추가", v ->
                startActivity(new Intent(this, ScheduleEditorActivity.class))));

        ProjectCatalog catalog = new ProjectCatalog(this);
        Map<String, String> projectNames = new LinkedHashMap<>();
        for (ProjectUrlPolicy.ProjectRef project : catalog.entries()) {
            projectNames.put(project.projectId, catalog.displayName(project));
        }
        List<ScheduleListPolicy.Filter> filters = ScheduleListPolicy.filters(schedules, projectNames);
        if (!ScheduleListPolicy.hasFilter(filters, filterKey)) filterKey = ScheduleListPolicy.ALL;

        LinearLayout filterCard = Ui.card(this, root);
        LinearLayout filterBox = new LinearLayout(this);
        filterBox.setOrientation(LinearLayout.VERTICAL);
        filterBox.addView(Ui.label(this, "보기"));
        ArrayList<String> labels = new ArrayList<>();
        int selectedIndex = 0;
        for (int i = 0; i < filters.size(); i++) {
            ScheduleListPolicy.Filter filter = filters.get(i);
            labels.add(filter.label);
            if (filter.key.equals(filterKey)) selectedIndex = i;
        }
        Spinner filterSpinner = new Spinner(this);
        Ui.selector(this, filterSpinner, "프로젝트별 예약 보기");
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
        filterBox.addView(filterSpinner);

        LinearLayout sortBox = new LinearLayout(this);
        sortBox.setOrientation(LinearLayout.VERTICAL);
        sortBox.addView(Ui.label(this, "정렬"));
        Spinner sortSpinner = new Spinner(this);
        Ui.selector(this, sortSpinner, "예약 정렬");
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
                    getPreferences(MODE_PRIVATE).edit().putString("scheduleSort", selected).apply();
                    render(true);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        sortBox.addView(sortSpinner);
        filterCard.addView(Ui.actionGrid(this, filterBox, sortBox));

        long now = System.currentTimeMillis();
        List<Schedule> visible = ScheduleListPolicy.visible(schedules, filterKey, sortKey, now);
        root.addView(Ui.label(this, visible.size() + "개 예약 표시"));
        if (visible.isEmpty()) {
            LinearLayout empty = Ui.card(this, root);
            empty.addView(Ui.emphasis(this, schedules.isEmpty() ? "첫 예약을 만들어 보세요" : "해당 프로젝트에 예약이 없습니다"));
            empty.addView(Ui.body(this, schedules.isEmpty()
                    ? "프롬프트와 실행 시각을 지정하면 여기에서 관리할 수 있습니다."
                    : "위의 보기에서 다른 프로젝트나 전체 예약을 선택하세요."));
        }
        for (Schedule schedule : visible) addScheduleCard(schedule, now);
        Ui.setContent(this, scroll);
        scroll.post(() -> {
            if (scrollView == scroll) scroll.scrollTo(0, previousScrollY);
        });
    }

    private void addScheduleCard(Schedule schedule, long now) {
        LinearLayout card = Ui.card(this, root);
        LinearLayout heading = Ui.row(this);
        android.widget.TextView name = Ui.emphasis(this, schedule.name);
        Ui.addWeighted(heading, name);
        Switch toggle = new Switch(this);
        toggle.setText(schedule.enabled ? "활성" : "중지");
        toggle.setMinHeight(Ui.dp(this, 48));
        toggle.setMinimumWidth(Ui.dp(this, 88));
        toggle.setContentDescription(schedule.name + ", 예약 활성화");
        toggle.setChecked(schedule.enabled);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            schedule.enabled = checked;
            store.saveSchedule(schedule);
            AlarmEngine.cancel(this, schedule.id);
            if (checked) AlarmEngine.scheduleNext(this, schedule, System.currentTimeMillis());
            button.setText(checked ? "활성" : "중지");
            root.post(() -> { if (!isFinishing() && !isDestroyed()) render(); });
        });
        heading.addView(toggle);
        card.addView(heading);
        card.addView(Ui.emphasis(this, Recurrence.describeNext(schedule, now)));

        String target = UiDisplayPolicy.label(schedule.targetType);
        if ("project".equals(schedule.targetType)) {
            ProjectCatalog catalog = new ProjectCatalog(this);
            String projectId = TargetParser.projectId(schedule.targetUrl);
            for (ProjectUrlPolicy.ProjectRef project : catalog.entries()) {
                if (project.projectId.equals(projectId)) { target += " · " + catalog.displayName(project); break; }
            }
        }
        String mode = "existing".equals(schedule.targetType)
                ? "기존 대화 설정 유지" : UiDisplayPolicy.label(schedule.experience);
        card.addView(Ui.body(this, target + " · " + mode));
        if ("work".equals(schedule.experience) && !"existing".equals(schedule.targetType)) {
            card.addView(Ui.body(this, Schedule.displayWorkModel(schedule.experience, schedule.workModel)
                    + " · 추론 " + Schedule.displayReasoningEffort(schedule.experience, schedule.reasoningEffort)));
        } else if ("chat".equals(schedule.experience) && !"existing".equals(schedule.targetType)) {
            card.addView(Ui.body(this, "추론 " + Schedule.displayChatReasoning(schedule.experience, schedule.chatReasoning)));
        }
        card.addView(Ui.label(this, "마지막 결과 · " + UiDisplayPolicy.status(schedule.lastStatus)));
        android.widget.Button edit = Ui.button(this, "편집", v ->
                startActivity(new Intent(this, ScheduleEditorActivity.class).putExtra("scheduleId", schedule.id)));
        edit.setContentDescription(schedule.name + ", 예약 편집");
        android.widget.Button run = Ui.button(this, "지금 실행", v -> runNow(schedule.id));
        run.setContentDescription(schedule.name + ", 지금 실행");
        card.addView(Ui.actionGrid(this, edit, run));
        android.widget.Button more = Ui.button(this, "더보기", v -> {
            android.widget.PopupMenu menu = new android.widget.PopupMenu(this, v);
            menu.getMenu().add("예약 삭제");
            menu.setOnMenuItemClickListener(item -> { confirmDelete(schedule); return true; });
            menu.show();
        });
        more.setContentDescription(schedule.name + ", 더보기, 예약 삭제");
        card.addView(more);
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
