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

    private void render() {
        ScrollView scroll = Ui.scroll(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 24));
        scroll.addView(root);
        root.addView(Ui.title(this, "실행 기록"));
        root.addView(Ui.body(this, "실패 카드의 ‘이 실패 풀로그 내려받기’에서 WebView·페이지 이동·입력기·전송 검증 진단을 JSON으로 저장할 수 있습니다."));
        root.addView(Ui.actionGrid(this,
                Ui.button(this, "새로고침", v -> render()),
                Ui.button(this, "전체 풀로그 내려받기", v -> exportAll()),
                Ui.button(this, "전체 삭제", v -> new AlertDialog.Builder(this).setTitle("기록 삭제").setMessage("모든 실행 기록을 삭제합니다.")
                        .setNegativeButton("취소", null).setPositiveButton("삭제", (dialog, which) -> { store.clear(); render(); }).show()),
                Ui.button(this, "닫기", v -> finish())));
        JSONArray logs = store.read();
        if (logs.length() == 0) root.addView(Ui.body(this, "실행 기록이 없습니다."));
        for (int i = 0; i < logs.length(); i++) {
            JSONObject item = logs.optJSONObject(i);
            if (item == null) continue;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
            card.setBackgroundResource(R.drawable.panel_background);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
            root.addView(card, params);
            long finished = item.optLong("finishedAt", 0L);
            String status = item.optString("status", "UNKNOWN");
            card.addView(Ui.section(this, item.optString("scheduleName", "예약") + " · " + status));
            card.addView(Ui.body(this, finished == 0 ? "시각 확인 불가" : formatter.format(Instant.ofEpochMilli(finished))));
            card.addView(Ui.body(this, "Run ID: " + item.optString("runId", "")));
            card.addView(Ui.body(this, item.optString("detail", "")));
            card.addView(Ui.body(this, item.optString("targetUrl", "")));
            JSONArray events = item.optJSONArray("events");
            card.addView(Ui.body(this, "진단 이벤트: " + (events == null ? 0 : events.length()) + "개"));
            if (!item.optBoolean("success", "VERIFIED".equals(status))) {
                String runId = item.optString("runId", "");
                card.addView(Ui.button(this, "이 실패 풀로그 내려받기", v -> exportOne(runId)));
            }
        }
        Ui.setContent(this, scroll);
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
