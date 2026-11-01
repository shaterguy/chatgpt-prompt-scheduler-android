package com.shaterguy.chatgptpromptscheduler;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class LogsActivity extends Activity {
    private static final int REQUEST_EXPORT_LOG = 2101;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss").withZone(ZoneId.of("Asia/Seoul"));
    private final DateTimeFormatter fileFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("Asia/Seoul"));
    private RunLogStore store;
    private String pendingExportText;
    private boolean autoExportHandled;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new RunLogStore(this);
        render();
        String exportRunId = getIntent().getStringExtra("exportRunId");
        if (!autoExportHandled && exportRunId != null && !exportRunId.isBlank()) {
            autoExportHandled = true;
            getWindow().getDecorView().post(() -> exportOne(exportRunId));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store != null) render();
    }

    private void render() {
        int previousScrollY = scrollView == null ? 0 : scrollView.getScrollY();
        ScrollView scroll = Ui.scroll(this);
        scrollView = scroll;
        LinearLayout root = Ui.content(this);
        scroll.addView(root);
        root.addView(Ui.title(this, "실행 기록"));
        Ui.navigation(this, root, "logs");
        JSONArray logs = store.read();
        root.addView(Ui.body(this, "최근 실행 " + logs.length() + "건"));
        root.addView(Ui.actionGrid(this,
                Ui.button(this, "새로고침", v -> render()),
                Ui.button(this, "전체 풀로그 내려받기", v -> exportAll())));
        LinearLayout management = Ui.disclosure(this, root, "기록 관리");
        management.addView(Ui.button(this, "전체 삭제", v ->
                new AlertDialog.Builder(this).setTitle("기록 삭제").setMessage("모든 실행 기록을 삭제합니다.")
                        .setNegativeButton("취소", null).setPositiveButton("삭제",
                                (dialog, which) -> { store.clear(); render(); }).show()));
        if (logs.length() == 0) {
            LinearLayout empty = Ui.card(this, root);
            empty.addView(Ui.emphasis(this, "아직 실행 기록이 없습니다"));
            empty.addView(Ui.body(this, "예약을 실행하면 완료 여부와 진단 내용을 확인할 수 있습니다."));
        }
        for (int i = 0; i < logs.length(); i++) {
            JSONObject item = logs.optJSONObject(i);
            if (item == null) continue;
            LinearLayout card = Ui.card(this, root);
            long finished = item.optLong("finishedAt", 0L);
            String status = item.optString("status", "UNKNOWN");
            String scheduleName = item.optString("scheduleName", "예약");
            card.addView(Ui.emphasis(this, scheduleName));
            card.addView(Ui.body(this, UiDisplayPolicy.status(status) + " · "
                    + (finished == 0 ? "시각 확인 불가" : formatter.format(Instant.ofEpochMilli(finished)))));
            android.widget.TextView summary = Ui.body(this, item.optString("detail", ""));
            summary.setMaxLines(3);
            summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(summary);
            LinearLayout detail = Ui.disclosure(this, card, "실행 상세");
            detail.addView(Ui.body(this, "상태 원문: " + status));
            detail.addView(Ui.body(this, "Run ID: " + item.optString("runId", "")));
            detail.addView(Ui.body(this, item.optString("detail", "")));
            detail.addView(Ui.body(this, item.optString("targetUrl", "")));
            String conversationUrl = item.optString("conversationUrl", "");
            if (!conversationUrl.isBlank()) detail.addView(Ui.body(this, "대화 주소: " + conversationUrl));
            JSONArray events = item.optJSONArray("events");
            detail.addView(Ui.body(this, "진단 이벤트: " + (events == null ? 0 : events.length()) + "개"));
            if (!item.optBoolean("success", "VERIFIED".equals(status))) {
                String runId = item.optString("runId", "");
                android.widget.Button export = Ui.button(this, "이 실패 풀로그 내려받기", v -> exportOne(runId));
                export.setContentDescription(scheduleName + ", 실패 풀로그 내려받기");
                card.addView(export);
            }
        }
        Ui.setContent(this, scroll);
        scroll.post(() -> {
            if (scrollView == scroll) scroll.scrollTo(0, previousScrollY);
        });
    }

    private void exportAll() {
        try {
            String name = "chatgpt-prompt-scheduler-full-logs-" + fileFormatter.format(Instant.now()) + ".json";
            beginExport(store.exportAll(), name);
        } catch (Exception error) {
            toast("전체 풀로그 구성 실패: " + error.getMessage());
        }
    }

    private void exportOne(String runId) {
        try {
            JSONObject payload = store.exportOne(runId);
            if (payload == null) {
                toast("해당 실행 로그를 찾지 못했습니다.");
                return;
            }
            String safeRun = runId.replaceAll("[^A-Za-z0-9_-]", "");
            if (safeRun.length() > 20) safeRun = safeRun.substring(0, 20);
            String name = "chatgpt-prompt-scheduler-failure-" + fileFormatter.format(Instant.now()) + "-" + safeRun + ".json";
            beginExport(payload, name);
        } catch (Exception error) {
            toast("실패 풀로그 구성 실패: " + error.getMessage());
        }
    }

    private void beginExport(JSONObject payload, String fileName) throws Exception {
        pendingExportText = payload.toString(2);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .setType("application/json")
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, REQUEST_EXPORT_LOG);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT_LOG || resultCode != RESULT_OK || data == null || data.getData() == null) {
            pendingExportText = null;
            return;
        }
        Uri uri = data.getData();
        String text = pendingExportText;
        pendingExportText = null;
        if (text == null) return;
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IllegalStateException("출력 스트림을 열지 못했습니다.");
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.flush();
            toast("풀로그 JSON을 저장했습니다.");
        } catch (Exception error) {
            toast("풀로그 저장 실패: " + error.getMessage());
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
