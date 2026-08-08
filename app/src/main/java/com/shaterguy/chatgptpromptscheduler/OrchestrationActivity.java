package com.shaterguy.chatgptpromptscheduler;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public final class OrchestrationActivity extends Activity {
    private OrchestrationStore store;
    private EditText projectName;
    private EditText chatUrl;
    private EditText workUrl;
    private EditText jobId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new OrchestrationStore(this);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (projectName != null) render();
    }

    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 24));
        root.addView(Ui.title(this, "오토런 중계 · Protocol 3.0"));
        root.addView(Ui.body(this, "예약 실행과 분리된 선택 기능입니다. 예약 시간이 오면 오토런 WebView를 즉시 닫고 예약 실행이 끝난 뒤 이어갑니다."));

        root.addView(Ui.section(this, "연결 설정"));
        projectName = field("프로젝트 이름(선택)", store.projectName(), false);
        chatUrl = field("일반 Chat 대화 URL", store.chatUrl(), true);
        workUrl = field("Work 대화 URL", store.workUrl(), true);
        jobId = field("Job ID", store.jobId(), false);
        root.addView(projectName);
        root.addView(chatUrl);
        root.addView(workUrl);
        root.addView(jobId);

        root.addView(Ui.section(this, "현재 상태"));
        root.addView(Ui.body(this, "상태: " + store.status()));
        root.addView(Ui.body(this, "대화/단계: " + store.sideLabel() + " · " + store.phase()));
        if (!store.lastSignal().isEmpty()) root.addView(Ui.body(this, "마지막 신호: " + store.lastSignal()));
        if (!store.error().isEmpty()) root.addView(Ui.body(this, "확인 필요: " + store.error()));

        root.addView(Ui.section(this, "중계 제어"));
        root.addView(Ui.actionGrid(this,
                Ui.button(this, "새로 시작", v -> startNew()),
                Ui.button(this, "재개", v -> resumeRelay()),
                Ui.button(this, "일시정지", v -> pauseRelay()),
                Ui.button(this, "중지", v -> stopRelay())));
        root.addView(Ui.body(this, "새로 시작은 [AUTOMATION_START Job ID]부터 시작합니다. 재개는 저장된 미전송/응답 대기 상태를 그대로 복구합니다."));
        android.widget.ScrollView scroll = Ui.scroll(this);
        scroll.addView(root);
        Ui.setContent(this, scroll);
    }

    private EditText field(String hint, String value, boolean url) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setSingleLine(true);
        input.setMinHeight(Ui.dp(this, 52));
        input.setInputType(url ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        return input;
    }

    private void saveFields() {
        store.saveConfig(projectName.getText().toString(), chatUrl.getText().toString(), workUrl.getText().toString(), jobId.getText().toString());
    }

    private void startNew() {
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
        render();
    }

    private void resumeRelay() {
        if (!chatUrl.getText().toString().trim().equals(store.runChatUrl())
                || !workUrl.getText().toString().trim().equals(store.runWorkUrl())
                || !jobId.getText().toString().trim().equals(store.runJobId())) {
            toast("실행 설정이 변경되었습니다. 기존 값으로 되돌리거나 새 Job ID로 새로 시작해 주세요.");
            return;
        }
        String error = store.runtimeConfigError();
        if (!error.isEmpty()) { toast(error); return; }
        if (store.pendingPrompt().isEmpty()) { toast("먼저 새로 시작을 눌러 주세요."); return; }
        store.resume();
        if (startRelayService()) toast("저장된 상태에서 중계를 재개했습니다.");
        render();
    }

    private void pauseRelay() {
        store.pause("사용자가 일시정지했습니다.");
        stopService(new Intent(this, OrchestrationService.class));
        toast("오토런 중계를 일시정지했습니다.");
        render();
    }

    private void stopRelay() {
        store.stop();
        stopService(new Intent(this, OrchestrationService.class));
        toast("오토런 중계를 중지했습니다.");
        render();
    }

    private boolean startRelayService() {
        try {
            Intent service = new Intent(this, OrchestrationService.class).setAction(OrchestrationService.ACTION_RUN);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
            return true;
        } catch (RuntimeException error) {
            store.pause("중계 서비스 시작 실패: " + error.getMessage());
            toast("중계 서비스 시작 실패: " + error.getMessage());
            return false;
        }
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
