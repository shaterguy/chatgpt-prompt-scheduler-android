package com.shaterguy.chatgptpromptscheduler;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.graphics.Typeface;
import android.view.View;
import android.view.autofill.AutofillValue;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

public final class OrchestrationActivity extends Activity {
    public static final String EXTRA_NEW_JOB = "orchestration.newJob";
    private static final String STATE_PROJECT_URL = "orchestration.projectUrl";
    private static final String STATE_REQUIREMENT = "orchestration.requirement";
    private static final String STATE_WORK_MODEL = "orchestration.workModel";
    private static final String STATE_REASONING = "orchestration.reasoning";
    private static final String[] MODEL_VALUES = {"inherit", "sol", "terra", "luna"};
    private static final String[] MODEL_LABELS = {"inherit (현재값 유지)", "sol", "terra", "luna"};
    private static final String[] REASONING_VALUES = {"inherit", "light", "medium", "high", "xhigh", "max", "ultra"};
    private static final String[] REASONING_LABELS = {"inherit (현재값 유지)", "light", "medium", "high", "xhigh", "max", "ultra"};

    private OrchestrationStore store;
    private OrchestrationRunLog runLog;
    private EditText projectUrl;
    private EditText requirement;
    private Spinner workModel;
    private Spinner reasoningEffort;
    private TextView statusSummary;
    private TextView currentStatus;
    private TextView lastReceive;
    private TextView lastDelivery;
    private TextView nextExpected;
    private TextView errorStatus;
    private Bundle restoredState;
    private boolean newJobMode;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() { refreshStatus(); refreshHandler.postDelayed(this, 1000L); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new OrchestrationStore(this);
        runLog = new OrchestrationRunLog(this);
        newJobMode = getIntent().getBooleanExtra(EXTRA_NEW_JOB, false);
        if (!newJobMode) runLog.record(store, "UI_OPEN", "source=activity");
        restoredState = savedInstanceState;
        createViews();
        restoredState = null;
        if (!newJobMode) refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Activity navigation is not a relay event. Only read the durable service state.
        refreshHandler.removeCallbacks(refreshRunnable);
        if (!newJobMode) {
            refreshStatus();
            refreshHandler.postDelayed(refreshRunnable, 1000L);
        }
    }

    @Override
    protected void onPause() {
        refreshHandler.removeCallbacks(refreshRunnable);
        saveProjectDefault();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (projectUrl != null) outState.putString(STATE_PROJECT_URL, projectUrl.getText().toString());
        if (requirement != null) outState.putString(STATE_REQUIREMENT, requirement.getText().toString());
        if (workModel != null) outState.putString(STATE_WORK_MODEL,
                MODEL_VALUES[workModel.getSelectedItemPosition()]);
        if (reasoningEffort != null) outState.putString(STATE_REASONING,
                REASONING_VALUES[reasoningEffort.getSelectedItemPosition()]);
        super.onSaveInstanceState(outState);
    }

    private void createViews() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        suppressCredentialCapture(root);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 24));
        root.addView(Ui.title(this, newJobMode ? "새 오토런 작업" : "오토런 · Protocol 3.3"));
        root.addView(Ui.button(this, "작업 목록", v -> finish()));
        if (showsCurrentJobState(newJobMode)) createCurrentJobViews(root); else createNewJobViews(root);

        android.widget.ScrollView scroll = Ui.scroll(this);
        suppressCredentialCapture(scroll);
        scroll.addView(root);
        Ui.setContent(this, scroll);
    }

    private void createNewJobViews(LinearLayout root) {
        NewJobFormDefaults defaults = newJobFormDefaults(store.defaultProjectUrl());
        root.addView(Ui.body(this, "프로젝트 주소만 기본값으로 불러옵니다. 요구사항과 Work 설정은 새 작업마다 독립된 빈 상태로 시작합니다."));
        root.addView(Ui.section(this, "새 Job 설정"));
        projectUrl = field("ChatGPT 프로젝트 주소 · https://chatgpt.com/g/<project-id>",
                defaults.projectUrl, true, STATE_PROJECT_URL);
        root.addView(projectUrl);
        root.addView(Ui.body(this, "프로젝트 기본값 변경은 이미 실행 중인 Job에 영향을 주지 않습니다."));
        root.addView(Ui.section(this, "Work 모델"));
        workModel = spinner(MODEL_LABELS,
                restoredSelection(STATE_WORK_MODEL, MODEL_VALUES, defaults.workModel));
        root.addView(workModel);
        root.addView(Ui.section(this, "Work 추론 정도"));
        reasoningEffort = spinner(REASONING_LABELS,
                restoredSelection(STATE_REASONING, REASONING_VALUES, defaults.reasoningEffort));
        root.addView(reasoningEffort);
        root.addView(Ui.section(this, "오토런 요구사항"));
        requirement = field("(오토런)\n확정된 작업 요구사항", defaults.requirement, false, STATE_REQUIREMENT);
        requirement.setSingleLine(false);
        requirement.setMinLines(8);
        requirement.setMaxLines(30);
        requirement.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        requirement.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        root.addView(requirement);
        root.addView(Ui.section(this, "시작"));
        root.addView(Ui.button(this, "오토런 시작", v -> startNew()));
        root.addView(Ui.body(this, "새 Job을 시작하기 전에는 이전 Job의 현재 동작·신호·오류·로그를 이 화면에 불러오지 않습니다."));
    }

    private void createCurrentJobViews(LinearLayout root) {
        root.addView(Ui.body(this, "현재 Job의 영속 실행 상태와 중계 제어만 표시합니다. 새 Job 입력은 작업 목록의 ‘새 작업’에서 시작합니다."));
        root.addView(Ui.section(this, "현재 동작"));
        statusSummary = Ui.body(this, "");
        statusSummary.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusSummary.setTextSize(Ui.isTablet(this) ? 18 : 16);
        root.addView(statusSummary);
        currentStatus = Ui.body(this, "");
        root.addView(currentStatus);
        root.addView(Ui.section(this, "마지막 수신"));
        lastReceive = Ui.body(this, "");
        root.addView(lastReceive);
        root.addView(Ui.section(this, "마지막 전달"));
        lastDelivery = Ui.body(this, "");
        root.addView(lastDelivery);
        root.addView(Ui.section(this, "다음 기대"));
        nextExpected = Ui.body(this, "");
        root.addView(nextExpected);
        root.addView(Ui.section(this, "오류"));
        errorStatus = Ui.body(this, "");
        root.addView(errorStatus);

        root.addView(Ui.section(this, "중계 제어"));
        root.addView(Ui.actionGrid(this,
                Ui.button(this, "재개", v -> resumeRelay()),
                Ui.button(this, "일시정지", v -> pauseRelay()),
                Ui.button(this, "중지", v -> stopRelay()),
                Ui.button(this, "실행 로그", v -> openLogs(OrchestrationLogsActivity.KIND_EXECUTION)),
                Ui.button(this, "디버그 로그", v -> openLogs(OrchestrationLogsActivity.KIND_DEBUG))));
        root.addView(Ui.body(this, "‘재개’는 두 대화방의 실제 상태를 다시 확인합니다. 사용자 조치 대기 중에는 조치를 마쳤다는 제어 신호를 일반 Chat에 보내고 재검증 응답부터 계속 감시합니다."));
    }

    private void refreshStatus() {
        if (currentStatus == null) return;
        statusSummary.setText("현재 상태: " + store.statusSummary());
        String stepRound = store.currentStep().isEmpty() ? "-" : store.currentStep() + " / " + store.currentRound();
        currentStatus.setText("모니터링 대화방: " + OrchestrationStore.sideLabel(store.monitoringSide())
                + "\n현재 동작: " + store.status()
                + "\nJob ID: " + emptyAsDash(store.runJobId())
                + "\n프로젝트: " + emptyAsDash(store.runProjectUrl())
                + "\n일반 Chat: " + connectionLabel(store.runChatUrl())
                + "\nWork: " + connectionLabel(store.runWorkUrl())
                + "\nWork 모델/추론: " + store.runWorkModel() + " / " + store.runReasoningEffort()
                + "\nBootstrap: " + store.bootstrapState()
                + "\n다음 전달 대상: " + OrchestrationStore.sideLabel(store.deliveryTarget())
                + " · " + deliveryLabel(store.deliveryState())
                + "\n현재 Step/Round: " + stepRound
                + "\n예약 실행 선점: " + (store.schedulePreempted() ? "예 · 중계 일시 양보" : "아니요"));

        String signal = store.lastAcceptedSignal().isEmpty() ? "-" : store.lastAcceptedSignal();
        String source = store.lastSignalSource().isEmpty() ? "-" : OrchestrationStore.sideLabel(store.lastSignalSource());
        lastReceive.setText("발생 대화방: " + source + "\n수신 신호: " + signal
                + "\n수신 시각: " + time(store.lastSignalAt()));

        String lastTarget = store.lastDeliveryTarget().isEmpty() ? "-" : OrchestrationStore.sideLabel(store.lastDeliveryTarget());
        lastDelivery.setText("전달 대상: " + lastTarget
                + "\n실제 전달 프롬프트: " + emptyAsDash(store.lastDeliveredPrompt())
                + "\n전달 상태: " + (store.lastDeliveryState().isEmpty() ? "-" : deliveryLabel(store.lastDeliveryState()))
                + "\n전달 시각: " + time(store.lastDeliveryAt()));

        nextExpected.setText(emptyAsDash(store.expectedSignal()));
        errorStatus.setText(store.error().isEmpty() ? "오류 없음"
                : "코드: " + emptyAsDash(store.lastErrorCode()) + "\n내용: " + store.error()
                + "\n시각: " + time(store.errorAt()));
    }

    private EditText field(String hint, String value, boolean url, String stateKey) {
        EditText input = new NonCredentialEditText(this);
        input.setHint(hint);
        input.setText(restoredValue(stateKey, value));
        input.setSingleLine(true);
        input.setMinHeight(Ui.dp(this, 52));
        input.setInputType(url ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        return input;
    }

    private String restoredValue(String key, String storedValue) {
        if (restoredState == null || !restoredState.containsKey(key)) return storedValue;
        String value = restoredState.getString(key);
        return value == null ? "" : value;
    }

    private int restoredSelection(String key, String[] values, String defaultValue) {
        if (restoredState == null || !restoredState.containsKey(key)) return indexOf(values, defaultValue);
        return indexOf(values, restoredState.getString(key, defaultValue));
    }

    private Spinner spinner(String[] labels, int selected) {
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        spinner.setSelection(Math.max(0, selected));
        return spinner;
    }

    private void saveProjectDefault() {
        if (!newJobMode || projectUrl == null) return;
        store.saveAutomaticProjectDefault(projectUrl.getText().toString());
    }

    private void startNew() {
        warnNotifications();
        if (store.active() && !store.terminal() && !store.runJobId().isEmpty()) {
            toast("현재 Job이 실행 중입니다. 먼저 일시정지 또는 중지해 주세요. Job ID: " + store.runJobId());
            return;
        }
        String nextProject = projectUrl.getText().toString().trim();
        String nextRequirement = requirement.getText().toString();
        String error = OrchestrationStore.automaticConfigError(nextProject, nextRequirement);
        if (!error.isEmpty()) { toast(error); return; }
        stopService(new Intent(this, OrchestrationService.class));
        saveProjectDefault();
        String generated = store.beginAutomatic(nextProject,
                MODEL_VALUES[workModel.getSelectedItemPosition()],
                REASONING_VALUES[reasoningEffort.getSelectedItemPosition()], nextRequirement);
        runLog.record(store, "UI_START", "source=automatic_bootstrap");
        if (startRelayService()) toast("오토런을 시작했습니다. Job ID: " + generated);
        getIntent().removeExtra(EXTRA_NEW_JOB);
        newJobMode = false;
        recreate();
    }

    private void resumeRelay() {
        warnNotifications();
        if (store.runJobId().isEmpty()) {
            toast("복구할 영속 오토런 상태가 없습니다. 새 Job으로 시작해 주세요.");
            refreshStatus();
            return;
        }
        boolean fullRelay = !store.runChatUrl().isEmpty() && !store.runWorkUrl().isEmpty();
        ResumePath path = resumePath(store.waitingForUser(), fullRelay);
        boolean resumed = switch (path) {
            case USER_ACTION_RESOLVED -> store.resolveUserAction();
            case RECONCILE -> store.beginReconciliation();
            case BOOTSTRAP -> store.resume();
        };
        if (!resumed) {
            toast(path == ResumePath.USER_ACTION_RESOLVED
                    ? store.userActionBlockReason() : store.resumeBlockReason());
            refreshStatus();
            return;
        }
        runLog.record(store, "UI_RESUME", "source=manual;path=" + path.name());
        if (path == ResumePath.USER_ACTION_RESOLVED)
            runLog.record(store, "UI_USER_RESOLVED", "source=resume");
        if (path == ResumePath.RECONCILE)
            runLog.record(store, "RESUME_RECONCILE_STARTED", "source=manual");
        if (startRelayService()) {
            toast(switch (path) {
                case USER_ACTION_RESOLVED -> "사용자 조치 완료 신호를 일반 Chat에 보내 재검증부터 재개합니다.";
                case RECONCILE -> "두 대화방의 실제 상태를 확인해 오토런 중계를 재구성합니다.";
                case BOOTSTRAP -> "마지막으로 확인된 bootstrap 상태에서 관찰 전용으로 재개합니다.";
            });
        }
        refreshStatus();
    }

    private void pauseRelay() {
        store.pause("사용자가 일시정지했습니다.");
        runLog.record(store, "UI_PAUSE", "source=manual");
        stopService(new Intent(this, OrchestrationService.class));
        toast("오토런 중계를 일시정지했습니다.");
        refreshStatus();
    }

    private void stopRelay() {
        store.stop();
        runLog.record(store, "UI_STOP", "source=manual");
        stopService(new Intent(this, OrchestrationService.class));
        toast("오토런 중계를 중지했습니다.");
        refreshStatus();
    }

    private boolean startRelayService() {
        try {
            Intent service = new Intent(this, OrchestrationService.class).setAction(OrchestrationService.ACTION_RUN);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
            return true;
        } catch (RuntimeException error) {
            store.fail("SERVICE_START_FAILED", "중계 서비스를 시작하지 못했습니다.");
            if (NotificationHelper.orchestrationAlertsEnabled(this))
                NotificationHelper.orchestrationError(this, store.monitoringSide(), store.runJobId(),
                        store.currentStep(), store.currentRound(), "중계 서비스를 시작하지 못했습니다.");
            toast("중계 서비스 시작 실패");
            return false;
        }
    }

    private void openLogs(String kind) {
        Intent intent = new Intent(this, OrchestrationLogsActivity.class)
                .putExtra(OrchestrationLogsActivity.EXTRA_LOG_KIND, kind);
        if (!store.runJobId().isEmpty()) intent.putExtra(OrchestrationLogsActivity.EXTRA_JOB_ID, store.runJobId());
        startActivity(intent);
    }

    private static String deliveryLabel(String state) {
        return switch (state) {
            case OrchestrationStore.DELIVERY_PENDING -> "대기";
            case OrchestrationStore.DELIVERY_PREPARING -> "준비";
            case OrchestrationStore.DELIVERY_SUBMITTING -> "제출 중";
            case OrchestrationStore.DELIVERY_SUBMITTED -> "제출 완료 · DOM 확인 중";
            case OrchestrationStore.DELIVERY_WAITING_RESPONSE -> "제출 확인 완료 · 응답 대기";
            case OrchestrationStore.DELIVERY_AMBIGUOUS -> "결과 불명확";
            case OrchestrationStore.DELIVERY_FAILED -> "실패";
            default -> state;
        };
    }

    private static String time(long value) {
        return value <= 0L ? "-" : DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
                .format(new Date(value));
    }

    private static String emptyAsDash(String value) { return value == null || value.isEmpty() ? "-" : value; }
    private static void suppressCredentialCapture(View view) {
        view.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view.setImportantForContentCapture(View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS);
        }
    }

    private void warnNotifications() {
        if (!NotificationHelper.orchestrationAlertsEnabled(this)) {
            toast("오토런은 계속 진행되지만 오류·완료 알림이 꺼져 있습니다.");
        }
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i;
        return 0;
    }

    private static String connectionLabel(String url) {
        return url == null || url.isEmpty() ? "준비 전" : "연결됨 · " + url;
    }

    public static Intent newJobIntent(Context context) {
        return new Intent(context, OrchestrationActivity.class).putExtra(EXTRA_NEW_JOB, true);
    }

    static boolean showsCurrentJobState(boolean newJobMode) { return !newJobMode; }

    enum ResumePath { USER_ACTION_RESOLVED, RECONCILE, BOOTSTRAP }

    static ResumePath resumePath(boolean waitingForUser, boolean fullRelay) {
        if (waitingForUser) return ResumePath.USER_ACTION_RESOLVED;
        return fullRelay ? ResumePath.RECONCILE : ResumePath.BOOTSTRAP;
    }

    static NewJobFormDefaults newJobFormDefaults(String defaultProjectUrl) {
        return new NewJobFormDefaults(defaultProjectUrl == null ? "" : defaultProjectUrl,
                "inherit", "inherit", "");
    }

    static final class NewJobFormDefaults {
        final String projectUrl;
        final String workModel;
        final String reasoningEffort;
        final String requirement;

        NewJobFormDefaults(String projectUrl, String workModel, String reasoningEffort, String requirement) {
            this.projectUrl = projectUrl;
            this.workModel = workModel;
            this.reasoningEffort = reasoningEffort;
            this.requirement = requirement;
        }
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    private static final class NonCredentialEditText extends EditText {
        NonCredentialEditText(Context context) {
            super(context);
            setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            setAutofillHints((String[]) null);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setImportantForContentCapture(View.IMPORTANT_FOR_CONTENT_CAPTURE_NO);
            }
        }

        @Override
        public int getAutofillType() {
            return View.AUTOFILL_TYPE_NONE;
        }

        @Override
        public android.view.autofill.AutofillValue getAutofillValue() {
            return null;
        }

        @Override
        public void autofill(AutofillValue value) {
            // URL, Job ID, and project inputs are never credential/autofill targets.
        }
    }
}
