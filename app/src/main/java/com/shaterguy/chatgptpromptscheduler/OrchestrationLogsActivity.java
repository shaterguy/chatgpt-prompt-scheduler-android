package com.shaterguy.chatgptpromptscheduler;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** On-demand viewer/exporter for redacted Protocol 3.x telemetry. */
public final class OrchestrationLogsActivity extends Activity {
    public static final String EXTRA_JOB_ID = "orchestration.jobId";
    public static final String EXTRA_LOG_KIND = "orchestration.logKind";
    public static final String KIND_EXECUTION = "execution";
    public static final String KIND_DEBUG = "debug";
    private static final int REQUEST_EXPORT = 2201;
    private final DateTimeFormatter fileFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.of("Asia/Seoul"));
    private OrchestrationRunLog logStore;
    private String pendingExportText;
    private String selectedJobId;
    private String selectedKind;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logStore = new OrchestrationRunLog(this);
        selectedJobId = safeJobId(getIntent().getStringExtra(EXTRA_JOB_ID));
        selectedKind = KIND_EXECUTION.equals(getIntent().getStringExtra(EXTRA_LOG_KIND))
                ? KIND_EXECUTION : KIND_DEBUG;
        render();
    }

    private void render() {
        ScrollView scroll = Ui.scroll(this);
        // Log pages may be shorter than the viewport. Let their content keep its natural height;
        // otherwise the trailing filler cell in the three-button grid can consume the viewport
        // and visually push a short execution log below the screen on some Android layouts.
        scroll.setFillViewport(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 24));
        boolean execution = KIND_EXECUTION.equals(selectedKind);
        String title = execution ? "작업 실행 로그" : "작업 디버그 로그";
        root.addView(Ui.title(this, title));
        root.addView(Ui.body(this, (selectedJobId.isEmpty() ? "전체 오토런" : "Job ID: " + selectedJobId)
                + (execution
                ? "\n사용자에게 의미 있는 시작·대화 생성·신호·상태·완료 항목만 표시합니다."
                : "\n진단용 redacted JSONL입니다. 원문·프롬프트·URL·쿠키·토큰은 기록하지 않습니다.")));
        root.addView(Ui.actionGrid(this,
                Ui.button(this, "새로고침", v -> render()),
                Ui.button(this, "로그 저장", v -> exportLogs()),
                Ui.button(this, "닫기", v -> finish())));

        List<String> lines = execution
                ? logStore.readExecutionLines(selectedJobId, 500)
                : selectedJobId.isEmpty() ? logStore.readRecentLines(500) : logStore.readJobLines(selectedJobId, 500);
        if (lines.isEmpty()) {
            root.addView(Ui.body(this, title + "가 없습니다."));
        } else {
            TextView output = Ui.body(this, String.join("\n", lines));
            output.setTypeface(Typeface.MONOSPACE);
            output.setTextIsSelectable(true);
            output.setTextSize(Ui.isTablet(this) ? 13 : 11);
            output.setBackgroundResource(R.drawable.panel_background);
            output.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
            root.addView(output, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        scroll.addView(root);
        Ui.setContent(this, scroll);
    }

    private void exportLogs() {
        try {
            boolean execution = KIND_EXECUTION.equals(selectedKind);
            pendingExportText = execution ? logStore.exportExecution(selectedJobId)
                    : selectedJobId.isEmpty() ? logStore.exportAll() : logStore.exportJob(selectedJobId);
            if (pendingExportText.isEmpty()) {
                toast("저장할 오토런 로그가 없습니다.");
                pendingExportText = null;
                return;
            }
            String extension = execution ? ".txt" : ".jsonl";
            String name = "chatgpt-prompt-scheduler-" + (execution ? "execution" : "debug") + "-"
                    + (selectedJobId.isEmpty() ? "all" : selectedJobId) + "-"
                    + fileFormatter.format(ZonedDateTime.now(ZoneId.of("Asia/Seoul"))) + extension;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .setType(execution ? "text/plain" : "application/x-ndjson")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_TITLE, name);
            startActivityForResult(intent, REQUEST_EXPORT);
        } catch (Exception error) {
            pendingExportText = null;
            toast("오토런 로그 구성 실패");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT || resultCode != RESULT_OK || data == null || data.getData() == null) {
            pendingExportText = null;
            return;
        }
        String text = pendingExportText;
        pendingExportText = null;
        if (text == null) return;
        try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
            if (output == null) throw new IllegalStateException("output unavailable");
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.flush();
            toast("오토런 로그를 저장했습니다.");
        } catch (Exception error) {
            toast("오토런 로그 저장 실패");
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private static String safeJobId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,64}")) return "";
        return value;
    }
}
