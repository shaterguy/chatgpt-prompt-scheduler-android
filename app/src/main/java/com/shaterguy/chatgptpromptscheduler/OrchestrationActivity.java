package com.shaterguy.chatgptpromptscheduler;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

public final class OrchestrationActivity extends Activity {
    private static final String STATE_PROJECT_NAME = "orchestration.projectName";
    private static final String STATE_CHAT_URL = "orchestration.chatUrl";
    private static final String STATE_WORK_URL = "orchestration.workUrl";
    private static final String STATE_JOB_ID = "orchestration.jobId";

    private OrchestrationStore store;
    private EditText projectName;
    private EditText chatUrl;
    private EditText workUrl;
    private EditText jobId;
    private TextView currentStatus;
    private TextView lastReceive;
    private TextView lastDelivery;
    private TextView nextExpected;
    private TextView errorStatus;
    private Button resolvedButton;
    private Bundle restoredState;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() { refreshStatus(); refreshHandler.postDelayed(this, 1000L); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new OrchestrationStore(this);
        restoredState = savedInstanceState;
        createViews();
        restoredState = null;
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Activity navigation is not a relay event. Only read the durable service state.
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshStatus();
        refreshHandler.postDelayed(refreshRunnable, 1000L);
    }

    @Override
    protected void onPause() {
        refreshHandler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (projectName != null) outState.putString(STATE_PROJECT_NAME, projectName.getText().toString());
        if (chatUrl != null) outState.putString(STATE_CHAT_URL, chatUrl.getText().toString());
        if (workUrl != null) outState.putString(STATE_WORK_URL, workUrl.getText().toString());
        if (jobId != null) outState.putString(STATE_JOB_ID, jobId.getText().toString());
        super.onSaveInstanceState(outState);
    }

    private void createViews() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 24));
        root.addView(Ui.title(this, "오토런 중계 · Protocol 3.x"));
        root.addView(Ui.body(this, "예약 실행과 분리된 선택 기능입니다. 예약 실행이 항상 우선하며 화면 이동은 중계 상태를 바꾸지 않습니다."));

        root.addView(Ui.section(this, "연결 설정"));
        projectName = field("프로젝트 이름(선택)", store.projectName(), false, STATE_PROJECT_NAME);
        chatUrl = field("일반 Chat 대화 URL", store.chatUrl(), true, STATE_CHAT_URL);
        workUrl = field("Work 대화 URL", store.workUrl(), true, STATE_WORK_URL);
        jobId = field("Job ID", store.jobId(), false, STATE_JOB_ID);
        root.addView(projectName);
        root.addView(chatUrl);
        root.addView(workUrl);
        root.addView(jobId);

        root.addView(Ui.section(this, "현재 동작"));
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
        resolvedButton = Ui.button(this, "처리 완료", v -> resolveUserAction());
        root.addView(Ui.actionGrid(this,
                Ui.button(this, "새로 시작", v -> startNew()),
                Ui.button(this, "재개", v -> resumeRelay()),
                Ui.button(this, "일시정지", v -> pauseRelay()),
                Ui.button(this, "중지", v -> stopRelay()),
                resolvedButton));
        root.addView(Ui.body(this, "‘처리 완료’는 성공 확정이 아닙니다. 일반 Chat에 재검증을 요청하고 검증 응답을 다시 감시합니다."));
        android.widget.ScrollView scroll = Ui.scroll(this);
        scroll.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        scroll.addView(root);
        Ui.setContent(this, scroll);
    }

    private void refreshStatus() {
        if (currentStatus == null) return;
        String stepRound = store.currentStep().isEmpty() ? "-" : store.currentStep() + " / " + store.currentRound();
        currentStatus.setText("모니터링 대화방: " + OrchestrationStore.sideLabel(store.monitoringSide())
                + "\n현재 동작: " + store.status()
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
        resolvedButton.setEnabled(store.waitingForUser());
    }

    private EditText field(String hint, String value, boolean url, String stateKey) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(restoredValue(stateKey, value));
        input.setSingleLine(true);
        input.setMinHeight(Ui.dp(this, 52));
        input.setInputType(url ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        return input;
    }

    private String restoredValue(String key, String storedValue) {
        if (restoredState == null || !restoredState.containsKey(key)) return storedValue;
        String value = restoredState.getString(key);
        return value == null ? "" : value;
    }

    private void saveFields() {
        store.saveConfig(projectName.getText().toString(), chatUrl.getText().toString(),
                workUrl.getText().toString(), jobId.getText().toString());
    }

    private void startNew() {
        if (!ensureNotifications()) return;
        String nextChatUrl = chatUrl.getText().toString().trim();
        String nextWorkUrl = workUrl.getText().toString().trim();
        String nextJobId = jobId.getText().toString().trim();
        String error = OrchestrationStore.configError(nextChatUrl, nextWorkUrl, nextJobId);
        if (!error.isEmpty()) { toast(error); return; }
        error = store.newRunError(nextJobId);
        if (!error.isEmpty()) { toast(error); return; }
        stopService(new Intent(this, OrchestrationService.class));
        saveFields();
        store.begin();
        if (startRelayService()) toast("오토런 중계를 시작했습니다.");
        refreshStatus();
    }

    private void resumeRelay() {
        if (!ensureNotifications()) return;
        if (!chatUrl.getText().toString().trim().equals(store.runChatUrl())
                || !workUrl.getText().toString().trim().equals(store.runWorkUrl())
                || !jobId.getText().toString().trim().equals(store.runJobId())) {
            toast("실행 설정이 변경되었습니다. 기존 값으로 되돌리거나 새 Job ID로 새로 시작해 주세요.");
            return;
        }
        if (store.waitingForUser()) { toast("사용자 조치 후 ‘처리 완료’를 눌러 재검증해 주세요."); return; }
        String error = store.runtimeConfigError();
        if (!error.isEmpty()) { toast(error); return; }
        if (store.pendingPrompt().isEmpty()) { toast("먼저 새로 시작을 눌러 주세요."); return; }
        if (!store.resume()) {
            toast("완료·중단 또는 결과 불명확 상태입니다. 화면 상태를 확인해 주세요.");
            return;
        }
        if (startRelayService()) toast("저장된 상태에서 중계를 재개했습니다.");
        refreshStatus();
    }

    private void resolveUserAction() {
        if (!ensureNotifications()) return;
        if (!store.resolveUserAction()) { toast("현재 사용자 조치 대기 상태가 아닙니다."); return; }
        if (startRelayService()) toast("일반 Chat에 사용자 조치 재검증을 요청합니다.");
        refreshStatus();
    }

    private void pauseRelay() {
        store.pause("사용자가 일시정지했습니다.");
        stopService(new Intent(this, OrchestrationService.class));
        toast("오토런 중계를 일시정지했습니다.");
        refreshStatus();
    }

    private void stopRelay() {
        store.stop();
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
    private boolean ensureNotifications() {
        if (NotificationHelper.orchestrationAlertsEnabled(this)) return true;
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 403);
            toast("오토런 오류 알림 권한을 허용한 뒤 다시 눌러 주세요.");
            return false;
        }
        toast("오토런 오류 알림 권한과 ‘오토런 오류 및 사용자 조치’ 채널을 켜 주세요.");
        try {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } catch (RuntimeException ignored) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:" + getPackageName())));
        }
        return false;
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
