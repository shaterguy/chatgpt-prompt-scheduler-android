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
    private static final int REQUEST_EXPORT = 2201;
    private final DateTimeFormatter fileFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.of("Asia/Seoul"));
    private OrchestrationRunLog logStore;
    private String pendingExportText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logStore = new OrchestrationRunLog(this);
        render();
    }

    private void render() {
        ScrollView scroll = Ui.scroll(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 24));
        root.addView(Ui.title(this, "오토런 실행 로그"));
        root.addView(Ui.body(this, "오토런 중계 전용 로그입니다. 최근 로그만 화면에 불러오며, 원문·프롬프트·URL·쿠키·토큰은 기록하지 않습니다."));
        root.addView(Ui.actionGrid(this,
                Ui.button(this, "새로고침", v -> render()),
                Ui.button(this, "로그 저장", v -> exportLogs()),
                Ui.button(this, "닫기", v -> finish())));

        List<String> lines = logStore.readRecentLines(500);
        if (lines.isEmpty()) {
            root.addView(Ui.body(this, "오토런 실행 로그가 없습니다."));
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
            pendingExportText = logStore.exportAll();
            if (pendingExportText.isEmpty()) {
                toast("저장할 오토런 로그가 없습니다.");
                pendingExportText = null;
                return;
            }
            String name = "chatgpt-prompt-scheduler-orchestration-logs-"
                    + fileFormatter.format(ZonedDateTime.now(ZoneId.of("Asia/Seoul"))) + ".jsonl";
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .setType("application/x-ndjson")
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
}
