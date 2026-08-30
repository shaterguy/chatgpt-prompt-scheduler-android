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
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ScheduleEditorActivity extends Activity {
    private ConfigStore store;
    private ProjectCatalog projectCatalog;
    private RequestProfileRegistry profileRegistry;
    private Schedule schedule;
    private EditText name;
    private Spinner targetType;
    private Spinner projectTarget;
    private LinearLayout projectTargetSection;
    private List<ProjectChoice> projectChoices = new ArrayList<>();
    private EditText targetUrl;
    private LinearLayout targetUrlSection;
    private Spinner experience;
    private LinearLayout experienceSection;
    private Spinner workModel;
    private Spinner reasoningEffort;
    private LinearLayout workReasoningSection;
    private Spinner chatReasoning;
    private LinearLayout chatReasoningSection;
    private List<String> workModelValues = new ArrayList<>();
    private List<String> workReasoningValues = new ArrayList<>();
    private List<String> chatReasoningValues = new ArrayList<>();
    private String lastWorkModelValue = "";
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
        projectCatalog = new ProjectCatalog(this);
        profileRegistry = new RequestProfileRegistry(this);
        String id = getIntent().getStringExtra("scheduleId");
        schedule = id == null ? new Schedule() : store.findSchedule(id);
        if (schedule == null) schedule = new Schedule();
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (projectTarget != null) reloadProjectChoices();
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

        projectTargetSection = section(root);
        projectTargetSection.addView(Ui.body(this, "프로젝트"));
        projectTarget = new Spinner(this);
        projectTargetSection.addView(projectTarget);
        projectTargetSection.addView(Ui.body(this,
                "프로젝트는 메인 화면의 ‘로그인/세션’에서 해당 프로젝트를 직접 열면 자동 등록됩니다."));
        reloadProjectChoices();

        targetUrlSection = section(root);
        targetUrlSection.addView(Ui.body(this, "기존 대화 URL"));
        targetUrl = new EditText(this);
        targetUrl.setSingleLine(true);
        targetUrl.setHint("https://chatgpt.com/.../c/<conversation-id>");
        targetUrl.setText("existing".equals(schedule.targetType) ? schedule.targetUrl : "");
        targetUrlSection.addView(targetUrl);

        experienceSection = section(root);
        experience = spinner(experienceSection, "실행 모드", new String[]{"chat", "work"},
                "work".equals(schedule.experience) ? "work" : "chat");

        chatReasoningSection = section(root);
        chatReasoningSection.addView(Ui.body(this, "일반 Chat 추론 정도"));
        chatReasoning = new Spinner(this);
        chatReasoningSection.addView(chatReasoning);
        reloadChatReasoningChoices();

        workReasoningSection = section(root);
        workReasoningSection.addView(Ui.body(this, "Work 모델"));
        workModel = new Spinner(this);
        workReasoningSection.addView(workModel);
        workReasoningSection.addView(Ui.body(this, "Work 추론 강도"));
        reasoningEffort = new Spinner(this);
        workReasoningSection.addView(reasoningEffort);
        reloadWorkModelChoices();
        lastWorkModelValue = selectedProfileValue(workModel, workModelValues);
        reloadWorkReasoningChoices(Schedule.normalizedReasoningEffort(schedule.experience, schedule.reasoningEffort));

        targetType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updateTargetOptionVisibility(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { updateTargetOptionVisibility(); }
        });
        experience.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updateTargetOptionVisibility(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { updateTargetOptionVisibility(); }
        });
        workModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String currentModel = selectedProfileValue(workModel, workModelValues);
                if (currentModel.equals(lastWorkModelValue)) return;
                lastWorkModelValue = currentModel;
                reloadWorkReasoningChoices("inherit");
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        prompt = edit(root, "프롬프트", schedule.prompt, true);
        recurrence = spinner(root, "반복", new String[]{"once", "daily", "weekly", "interval"}, schedule.recurrence);

        timesSection = section(root);
        times = edit(timesSection, "실행 시각 · 쉼표 구분 (예: 08:00,17:00)", String.join(",", schedule.times), false);
        weekdaysSection = section(root);
        weekdays = edit(weekdaysSection, "주간 요일 · 1=월∼7=일 (예: 1,2,3,4,5)", joinInts(schedule.weekdays), false);
        intervalSection = section(root);
        intervalMinutes = edit(intervalSection, "분 간격 · 이전 실행 완료 후 (15∼10080)",
                String.valueOf(Schedule.normalizedIntervalMinutes(schedule.intervalMinutes)), false);

        recurrence.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updateRecurrenceOptionVisibility(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { updateRecurrenceOptionVisibility(); }
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

    private void reloadChatReasoningChoices() {
        chatReasoningValues = new ArrayList<>();
        chatReasoningValues.add("keep");
        for (String value : profileRegistry.chatReasonings()) if (!chatReasoningValues.contains(value)) chatReasoningValues.add(value);
        String current = Schedule.normalizedChatReasoning(schedule.experience, schedule.chatReasoning);
        if (!"keep".equals(current) && !chatReasoningValues.contains(current)) chatReasoningValues.add(current);
        ArrayList<String> labels = new ArrayList<>();
        for (String value : chatReasoningValues) labels.add("keep".equals(value)
                ? "현재 Chat 설정 유지"
                : labelForRegistration(value, profileRegistry.find(RequestProfileEngine.Mode.CHAT, "", value) != null));
        chatReasoning.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        selectValue(chatReasoning, chatReasoningValues, current);
    }

    private void reloadWorkModelChoices() {
        workModelValues = new ArrayList<>();
        workModelValues.add("inherit");
        for (String value : profileRegistry.workModels()) if (!workModelValues.contains(value)) workModelValues.add(value);
        String current = Schedule.normalizedWorkModel(schedule.experience, schedule.workModel);
        if (!"inherit".equals(current) && !workModelValues.contains(current)) workModelValues.add(current);
        ArrayList<String> labels = new ArrayList<>();
        for (String value : workModelValues) labels.add("inherit".equals(value)
                ? "현재 설정 유지"
                : labelForRegistration(value, !profileRegistry.workReasoningsForModel(value).isEmpty()));
        workModel.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        selectValue(workModel, workModelValues, current);
    }

    private void reloadWorkReasoningChoices(String requested) {
        if (reasoningEffort == null || workModel == null) return;
        String model = selectedProfileValue(workModel, workModelValues);
        workReasoningValues = new ArrayList<>();
        workReasoningValues.add("inherit");
        if (!"inherit".equals(model)) {
            for (String value : profileRegistry.workReasoningsForModel(model)) {
                if (!workReasoningValues.contains(value)) workReasoningValues.add(value);
            }
        }
        String current = Schedule.normalizedReasoningEffort("work", requested);
        if (!"inherit".equals(current) && !workReasoningValues.contains(current)) workReasoningValues.add(current);
        ArrayList<String> labels = new ArrayList<>();
        for (String value : workReasoningValues) {
            boolean registered = "inherit".equals(value)
                    || profileRegistry.find(RequestProfileEngine.Mode.WORK, model, value) != null;
            labels.add("inherit".equals(value) ? "현재 설정 유지" : labelForRegistration(value, registered));
        }
        reasoningEffort.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        selectValue(reasoningEffort, workReasoningValues, current);
    }

    private String labelForRegistration(String value, boolean registered) {
        return registered ? value : value + " · 등록되지 않음";
    }

    private static void selectValue(Spinner spinner, List<String> values, String value) {
        spinner.setSelection(Math.max(0, values.indexOf(value)));
    }

    private static String selectedProfileValue(Spinner spinner, List<String> values) {
        int index = spinner == null ? -1 : spinner.getSelectedItemPosition();
        return index >= 0 && index < values.size() ? values.get(index) : "";
    }

    private void reloadProjectChoices() {
        if (projectTarget == null || projectCatalog == null) return;
        ArrayList<ProjectChoice> next = new ArrayList<>();
        for (ProjectUrlPolicy.ProjectRef ref : projectCatalog.entries()) {
            next.add(new ProjectChoice(projectCatalog.displayName(ref), ref.canonicalUrl));
        }
        if ("project".equals(schedule.targetType) && isTargetValidForType("project", schedule.targetUrl)) {
            String currentId = TargetParser.projectId(schedule.targetUrl);
            boolean found = false;
            for (ProjectChoice choice : next) {
                if (currentId != null && currentId.equals(TargetParser.projectId(choice.url))) {
                    found = true;
                    break;
                }
            }
            if (!found) next.add(0, new ProjectChoice("현재 저장 프로젝트 · " + currentId, schedule.targetUrl));
        }
        projectChoices = next;
        ArrayList<String> labels = new ArrayList<>();
        for (ProjectChoice choice : projectChoices) labels.add(choice.label);
        if (labels.isEmpty()) labels.add("등록된 프로젝트 없음");
        projectTarget.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        projectTarget.setEnabled(!projectChoices.isEmpty());
        if (!projectChoices.isEmpty() && "project".equals(schedule.targetType)) {
            String currentId = TargetParser.projectId(schedule.targetUrl);
            for (int i = 0; i < projectChoices.size(); i++) {
                if (currentId != null && currentId.equals(TargetParser.projectId(projectChoices.get(i).url))) {
                    projectTarget.setSelection(i);
                    break;
                }
            }
        }
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
        projectTargetSection.setVisibility(showsProjectSelection(type) ? View.VISIBLE : View.GONE);
        targetUrlSection.setVisibility(usesManualTargetUrl(type) ? View.VISIBLE : View.GONE);
        experienceSection.setVisibility(showsExperience(type) ? View.VISIBLE : View.GONE);
        chatReasoningSection.setVisibility(showsChatReasoning(type, mode) ? View.VISIBLE : View.GONE);
        workReasoningSection.setVisibility(showsReasoningEffort(type, mode) ? View.VISIBLE : View.GONE);
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

    static boolean usesManualTargetUrl(String targetType) { return "existing".equals(targetType); }
    static boolean showsProjectSelection(String targetType) { return "project".equals(targetType); }
    static boolean showsExperience(String targetType) { return !"existing".equals(targetType); }
    static boolean showsReasoningEffort(String targetType, String experience) {
        return showsExperience(targetType) && "work".equals(experience);
    }
    static boolean showsChatReasoning(String targetType, String experience) {
        return showsExperience(targetType) && "chat".equals(experience);
    }
    static boolean showsClockTimes(String recurrence) { return !"interval".equals(recurrence); }
    static boolean showsWeekdays(String recurrence) { return "weekly".equals(recurrence); }
    static boolean showsIntervalMinutes(String recurrence) { return "interval".equals(recurrence); }

    static boolean isTargetValidForType(String targetType, String url) {
        if ("general".equals(targetType)) return true;
        if (!TargetParser.isSupported(url)) return false;
        if ("project".equals(targetType)) {
            return TargetParser.projectId(url) != null && TargetParser.conversationId(url) == null;
        }
        if ("existing".equals(targetType)) return TargetParser.conversationId(url) != null;
        return false;
    }

    private String selectedProjectUrl() {
        if (projectTarget == null || projectChoices.isEmpty()) return "";
        int index = projectTarget.getSelectedItemPosition();
        return index < 0 || index >= projectChoices.size() ? "" : projectChoices.get(index).url;
    }

    private String selected(Spinner spinner) {
        return spinner == null || spinner.getSelectedItem() == null
                ? "" : String.valueOf(spinner.getSelectedItem());
    }

    static String reasoningEffortValue(String label) {
        return "현재 설정 유지".equals(label) ? "inherit" : label;
    }

    static String chatReasoningValue(String label) {
        return "현재 Chat 설정 유지".equals(label) ? "keep" : label;
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

    private void save() {
        String type = selected(targetType);
        String url = switch (type) {
            case "general" -> "https://chatgpt.com/";
            case "project" -> selectedProjectUrl();
            case "existing" -> targetUrl.getText().toString().trim();
            default -> "";
        };
        if ("project".equals(type) && url.isEmpty()) {
            toast("등록된 프로젝트가 없습니다. 메인 화면의 로그인/세션에서 프로젝트를 직접 연 뒤 다시 선택하세요.");
            return;
        }
        if (!isTargetValidForType(type, url)) {
            if ("existing".equals(type)) toast("기존 대화 URL에는 /c/<conversation-id>가 필요합니다.");
            else toast("올바른 ChatGPT 대상을 선택하세요.");
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
            if (parsedInterval < Schedule.MIN_INTERVAL_MINUTES
                    || parsedInterval > Schedule.MAX_INTERVAL_MINUTES) {
                toast("분 간격은 15∼10080 사이로 입력하세요.");
                return;
            }
        }

        schedule.name = name.getText().toString().trim().isEmpty()
                ? "예약" : name.getText().toString().trim();
        schedule.targetType = type;
        schedule.targetUrl = url;
        schedule.experience = Schedule.normalizedExperience(type, selected(experience));
        schedule.workModel = Schedule.normalizedWorkModel(
                schedule.experience, selectedProfileValue(workModel, workModelValues));
        schedule.reasoningEffort = Schedule.normalizedReasoningEffort(
                schedule.experience, selectedProfileValue(reasoningEffort, workReasoningValues));
        schedule.chatReasoning = Schedule.normalizedChatReasoning(
                schedule.experience, selectedProfileValue(chatReasoning, chatReasoningValues));
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
        try {
            schedule.retryCount = Math.max(0, Math.min(5,
                    Integer.parseInt(retryCount.getText().toString().trim())));
        } catch (NumberFormatException ignored) {
            schedule.retryCount = 2;
        }
        schedule.enabled = enabled.isChecked();
        profileRegistry.attach(schedule);
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
            if (value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d") && !values.contains(value)) {
                values.add(value);
            }
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

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private static final class ProjectChoice {
        final String label;
        final String url;
        ProjectChoice(String label, String url) {
            this.label = label == null || label.isBlank() ? "프로젝트" : label;
            this.url = url == null ? "" : url;
        }
    }
}
