package com.shaterguy.chatgptpromptscheduler;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ScheduleEditorActivity extends Activity {
    private static final String[] REASONING_EFFORT_VALUES =
            new String[]{"inherit", "light", "medium", "high", "xhigh", "max", "ultra"};
    private static final String[] REASONING_EFFORT_LABELS =
            new String[]{"inherit", "light", "medium", "high", "xhigh", "max", "울트라"};
    private static final String[] CHAT_REASONING_VALUES =
            new String[]{"keep", "instant", "medium", "high", "xhigh", "pro"};
    private static final String[] CHAT_REASONING_LABELS =
            new String[]{"현재 Chat 설정 유지", "Instant", "Medium", "High", "Extra High", "Pro"};

    private ConfigStore store;
    private Schedule schedule;
    private EditText name;
    private Spinner targetType;
    private EditText targetUrl;
    private TextView targetUrlLabel;
    private LinearLayout targetUrlSection;
    private Spinner experience;
    private LinearLayout experienceSection;
    private Spinner workModel;
    private Spinner reasoningEffort;
    private LinearLayout workReasoningSection;
    private Spinner chatReasoning;
    private LinearLayout chatReasoningSection;
    private EditText prompt;
    private Spinner recurrence;
    private EditText times;
    private LinearLayout timesSection;
    private EditText weekdays;
    private LinearLayout weekdaysSection;
    private EditText intervalMinutes;
    private LinearLayout intervalSection;
    private EditText retryCount;
    private Switch enabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ConfigStore(this);
        String id = getIntent().getStringExtra("scheduleId");
        schedule = id == null ? new Schedule() : store.findSchedule(id);
        if (schedule == null) schedule = new Schedule();
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = Ui.scroll(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 24));
        scroll.addView(root);
        root.addView(Ui.title(this, "예약 편집"));

        name = edit(root, "예약 이름", schedule.name, false);
        targetType = spinner(root, "대상 유형", new String[]{"general", "project", "existing"}, schedule.targetType);

        targetUrlSection = section(root);
        targetUrlLabel = Ui.body(this, "대상 URL");
        targetUrlSection.addView(targetUrlLabel);
        targetUrl = new EditText(this);
        targetUrl.setSingleLine(true);
        targetUrl.setText(schedule.targetUrl);
        targetUrlSection.addView(targetUrl);

        experienceSection = section(root);
        experience = spinner(experienceSection, "실행 모드", new String[]{"chat", "work"},
                "work".equals(schedule.experience) ? "work" : "chat");

        chatReasoningSection = section(root);
        chatReasoning = mappedSpinner(chatReasoningSection,
                "일반 Chat 추론 정도",
                CHAT_REASONING_VALUES,
                CHAT_REASONING_LABELS,
                Schedule.normalizedChatReasoning(schedule.experience, schedule.chatReasoning));

        workReasoningSection = section(root);
        workModel = spinner(workReasoningSection,
                "Work 모델 · inherit=웹 현재 설정 유지",
                new String[]{"inherit", "sol", "terra", "luna"},
                Schedule.normalizedWorkModel(schedule.experience, schedule.workModel));
        reasoningEffort = mappedSpinner(workReasoningSection,
                "Work 추론 강도 · inherit=웹 현재 설정 유지",
                REASONING_EFFORT_VALUES,
                REASONING_EFFORT_LABELS,
                Schedule.normalizedReasoningEffort(schedule.experience, schedule.reasoningEffort));

        targetType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateTargetOptionVisibility();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateTargetOptionVisibility();
            }
        });

        experience.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateTargetOptionVisibility();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateTargetOptionVisibility();
            }
        });

        prompt = edit(root, "프롬프트", schedule.prompt, true);
        recurrence = spinner(root, "반복", new String[]{"once", "daily", "weekly", "interval"}, schedule.recurrence);

        timesSection = section(root);
        times = edit(timesSection, "실행 시각 · 쉼표 구분 (예: 08:00,17:00)", String.join(",", schedule.times), false);

        weekdaysSection = section(root);
        weekdays = edit(weekdaysSection, "주간 요일 · 1=월∼7=일 (예: 1,2,3,4,5)", joinInts(schedule.weekdays), false);

        intervalSection = section(root);
        intervalMinutes = edit(intervalSection,
                "분 간격 · 이전 실행 완료 후 (15∼10080)",
                String.valueOf(Schedule.normalizedIntervalMinutes(schedule.intervalMinutes)), false);

        recurrence.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateRecurrenceOptionVisibility();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateRecurrenceOptionVisibility();
            }
        });

        retryCount = edit(root, "웹 엔진 재시도 횟수 (0∼5)", String.valueOf(schedule.retryCount), false);
        enabled = new Switch(this);
        enabled.setText("예약 활성화");
        enabled.setChecked(schedule.enabled);
        root.addView(enabled);

        root.addView(Ui.actionGrid(this,
                Ui.button(this, "저장", v -> save()),
                Ui.button(this, "취소", v -> finish())));

        updateTargetOptionVisibility();
        updateRecurrenceOptionVisibility();
        Ui.setContent(this, scroll);
    }

    private LinearLayout section(LinearLayout root) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        root.addView(section);
        return section;
    }

    private void updateTargetOptionVisibility() {
        String type = selected(targetType);
        String mode = selected(experience);
        boolean showUrl = requiresTargetUrl(type);
        targetUrlSection.setVisibility(showUrl ? View.VISIBLE : View.GONE);
        experienceSection.setVisibility(showsExperience(type) ? View.VISIBLE : View.GONE);
        chatReasoningSection.setVisibility(showsChatReasoning(type, mode) ? View.VISIBLE : View.GONE);
        workReasoningSection.setVisibility(showsReasoningEffort(type, mode) ? View.VISIBLE : View.GONE);
        if (showUrl) {
            boolean existing = "existing".equals(type);
            targetUrlLabel.setText(existing ? "기존 대화 URL" : "프로젝트 URL");
            targetUrl.setHint(existing
                    ? "https://chatgpt.com/.../c/<conversation-id>"
                    : "https://chatgpt.com/g/<project-id>");
        }
    }

    private void updateRecurrenceOptionVisibility() {
        String value = selected(recurrence);
        timesSection.setVisibility(showsClockTimes(value) ? View.VISIBLE : View.GONE);
        weekdaysSection.setVisibility(showsWeekdays(value) ? View.VISIBLE : View.GONE);
        intervalSection.setVisibility(showsIntervalMinutes(value) ? View.VISIBLE : View.GONE);
    }

    static boolean requiresTargetUrl(String targetType) {
        return "project".equals(targetType) || "existing".equals(targetType);
    }

    static boolean showsExperience(String targetType) {
        return !"existing".equals(targetType);
    }

    static boolean showsReasoningEffort(String targetType, String experience) {
        return showsExperience(targetType) && "work".equals(experience);
    }

    static boolean showsChatReasoning(String targetType, String experience) {
        return showsExperience(targetType) && "chat".equals(experience);
    }

    static boolean showsClockTimes(String recurrence) {
        return !"interval".equals(recurrence);
    }

    static boolean showsWeekdays(String recurrence) {
        return "weekly".equals(recurrence);
    }

    static boolean showsIntervalMinutes(String recurrence) {
        return "interval".equals(recurrence);
    }

    static boolean isTargetValidForType(String targetType, String url) {
        if ("general".equals(targetType)) return true;
        if (!TargetParser.isSupported(url)) return false;
        if ("project".equals(targetType)) {
            return TargetParser.projectId(url) != null && TargetParser.conversationId(url) == null;
        }
        if ("existing".equals(targetType)) return TargetParser.conversationId(url) != null;
        return false;
    }

    private String selected(Spinner spinner) {
        return spinner == null || spinner.getSelectedItem() == null ? "" : String.valueOf(spinner.getSelectedItem());
    }

    static String reasoningEffortValue(String label) {
        return "울트라".equals(label) ? "ultra" : label;
    }

    private EditText edit(LinearLayout root, String label, String value, boolean multiline) {
        root.addView(Ui.body(this, label));
        EditText edit = new EditText(this);
        edit.setText(value);
        if (multiline) {
            edit.setMinLines(7);
            edit.setGravity(android.view.Gravity.TOP);
        } else edit.setSingleLine(true);
        root.addView(edit);
        return edit;
    }

    private Spinner spinner(LinearLayout root, String label, String[] values, String selected) {
        root.addView(Ui.body(this, label));
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
        spinner.setSelection(Math.max(0, Arrays.asList(values).indexOf(selected)));
        root.addView(spinner);
        return spinner;
    }

    private Spinner mappedSpinner(LinearLayout root, String label, String[] values,
                                  String[] labels, String selectedValue) {
        root.addView(Ui.body(this, label));
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        spinner.setSelection(Math.max(0, Arrays.asList(values).indexOf(selectedValue)));
        root.addView(spinner);
        return spinner;
    }

    private void save() {
        String type = selected(targetType);
        String url = "general".equals(type) ? "https://chatgpt.com/" : targetUrl.getText().toString().trim();
        if (!isTargetValidForType(type, url)) {
            if ("project".equals(type)) {
                toast("프로젝트 새 대화는 대화방이 아닌 프로젝트 홈 URL을 입력하세요.");
            } else if ("existing".equals(type)) {
                toast("기존 대화 URL에는 /c/<conversation-id>가 필요합니다.");
            } else {
                toast("chatgpt.com의 올바른 HTTPS URL을 입력하세요.");
            }
            return;
        }
        if (prompt.getText().toString().trim().isEmpty()) {
            toast("프롬프트를 입력하세요.");
            return;
        }

        String recurrenceValue = selected(recurrence);
        List<String> parsedTimes = new ArrayList<>();
        if (showsClockTimes(recurrenceValue)) {
            parsedTimes = parseTimes(times.getText().toString());
            if (parsedTimes.isEmpty()) {
                toast("HH:mm 형식의 실행 시각을 하나 이상 입력하세요.");
                return;
            }
        }

        int parsedInterval = schedule.intervalMinutes;
        if (showsIntervalMinutes(recurrenceValue)) {
            try {
                parsedInterval = Integer.parseInt(intervalMinutes.getText().toString().trim());
            } catch (NumberFormatException ignored) {
                toast("분 간격을 숫자로 입력하세요.");
                return;
            }
            if (parsedInterval < Schedule.MIN_INTERVAL_MINUTES || parsedInterval > Schedule.MAX_INTERVAL_MINUTES) {
                toast("분 간격은 15∼10080 사이로 입력하세요.");
                return;
            }
        }

        schedule.name = name.getText().toString().trim().isEmpty() ? "예약" : name.getText().toString().trim();
        schedule.targetType = type;
        schedule.targetUrl = url;
        schedule.experience = Schedule.normalizedExperience(type, selected(experience));
        schedule.workModel = Schedule.normalizedWorkModel(
                schedule.experience, selected(workModel));
        schedule.reasoningEffort = Schedule.normalizedReasoningEffort(
                schedule.experience, reasoningEffortValue(selected(reasoningEffort)));
        schedule.chatReasoning = Schedule.normalizedChatReasoning(
                schedule.experience, selected(chatReasoning));
        schedule.prompt = prompt.getText().toString();
        schedule.recurrence = recurrenceValue;
        schedule.intervalMinutes = Schedule.normalizedIntervalMinutes(parsedInterval);
        if (showsClockTimes(recurrenceValue)) {
            schedule.times.clear();
            schedule.times.addAll(parsedTimes);
        }
        schedule.weekdays.clear();
        if (showsWeekdays(recurrenceValue)) {
            schedule.weekdays.addAll(parseWeekdays(weekdays.getText().toString()));
            if (schedule.weekdays.isEmpty()) {
                toast("매주 반복은 요일을 하나 이상 입력하세요.");
                return;
            }
        } else {
            schedule.weekdays.addAll(Arrays.asList(1, 2, 3, 4, 5, 6, 7));
        }
        try { schedule.retryCount = Math.max(0, Math.min(5, Integer.parseInt(retryCount.getText().toString().trim()))); }
        catch (NumberFormatException ignored) { schedule.retryCount = 2; }
        schedule.enabled = enabled.isChecked();
        store.saveSchedule(schedule);
        AlarmEngine.cancel(this, schedule.id);
        if (schedule.enabled) AlarmEngine.scheduleNext(this, schedule, System.currentTimeMillis());
        toast("예약을 저장했습니다.");
        finish();
    }

    static List<String> parseTimes(String raw) {
        List<String> values = new ArrayList<>();
        for (String part : raw.split("[,\\s]+")) {
            String value = part.trim();
            if (value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d") && !values.contains(value)) values.add(value);
        }
        values.sort(String::compareTo);
        return values;
    }

    static List<Integer> parseWeekdays(String raw) {
        List<Integer> values = new ArrayList<>();
        for (String part : raw.split("[,\\s]+")) {
            try {
                int value = Integer.parseInt(part.trim());
                if (value >= 1 && value <= 7 && !values.contains(value)) values.add(value);
            } catch (NumberFormatException ignored) {}
        }
        return values;
    }

    private String joinInts(List<Integer> values) {
        StringBuilder builder = new StringBuilder();
        for (Integer value : values) {
            if (builder.length() > 0) builder.append(',');
            builder.append(value);
        }
        return builder.toString();
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
