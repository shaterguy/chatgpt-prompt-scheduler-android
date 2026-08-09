package com.shaterguy.chatgptpromptscheduler;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Autorun landing page: a new-job entry point plus durable, per-job execution history. */
public final class OrchestrationHistoryActivity extends Activity {
    private OrchestrationStore current;
    private OrchestrationHistoryStore history;
    private OrchestrationRunLog logs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = new OrchestrationStore(this);
        history = new OrchestrationHistoryStore(this);
        logs = new OrchestrationRunLog(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        history.sync(current);
        render();
    }

    private void render() {
        ScrollView scroll = Ui.scroll(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 24));
        root.addView(Ui.title(this, "오토런 작업"));
        root.addView(Ui.body(this, "새 작업을 시작하거나 이전 작업의 수행 항목, 실행 로그, 디버그 로그를 확인합니다."));
        root.addView(Ui.actionGrid(this,
                Ui.button(this, "새 작업", v -> startActivity(OrchestrationActivity.newJobIntent(this))),
                Ui.button(this, "새로고침", v -> { history.sync(current); render(); })));

        JSONArray jobs = history.read();
        root.addView(Ui.section(this, "작업 이력 · " + jobs.length() + "개"));
        if (jobs.length() == 0) {
            root.addView(Ui.body(this, "아직 오토런 작업 이력이 없습니다."));
        } else {
            for (int index = 0; index < jobs.length(); index++) {
                JSONObject job = jobs.optJSONObject(index);
                if (job != null) root.addView(jobCard(job));
            }
        }
        scroll.addView(root);
        Ui.setContent(this, scroll);
    }

    private LinearLayout jobCard(JSONObject job) {
        String jobId = job.optString("jobId", "-");
        boolean isCurrent = jobId.equals(current.runJobId());
        String role = jobRole(isCurrent, job.optBoolean("active"), job.optBoolean("terminal"),
                history.hasWorkspace(jobId));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.panel_background);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, Ui.dp(this, 5), 0, Ui.dp(this, 8));
        card.setLayoutParams(cardParams);

        TextView title = Ui.body(this, job.optString("statusSummary", "상태 확인 필요")
                + " · " + role + "\n" + jobId);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextSize(Ui.isTablet(this) ? 18 : 16);
        card.addView(title);

        String requirement = compact(job.optString("requirement"), 320);
        String sequence = job.optString("step").isEmpty() ? "-"
                : job.optString("step") + " / " + job.optString("round");
        card.addView(Ui.body(this,
                "요구사항: " + empty(requirement)
                        + "\n프로젝트: " + empty(job.optString("projectUrl"))
                        + "\nWork 모델/추론: " + job.optString("workModel", "inherit")
                        + " / " + job.optString("reasoningEffort", "inherit")
                        + "\nStep/Round: " + sequence
                        + "\n일반 Chat: " + connected(job.optString("chatUrl"))
                        + " · Work: " + connected(job.optString("workUrl"))
                        + "\n마지막 갱신: " + time(job.optLong("updatedAt"))));

        List<String> performed = logs.readExecutionLines(jobId, 4);
        card.addView(Ui.section(this, "수행 항목"));
        card.addView(Ui.body(this, performed.isEmpty() ? "아직 기록된 수행 항목이 없습니다."
                : String.join("\n", performed)));

        if (!job.optString("error").isEmpty()) {
            card.addView(Ui.body(this, "오류: " + job.optString("lastErrorCode")
                    + " · " + job.optString("error")));
        }

        LinearLayout actions = Ui.actionGrid(this,
                Ui.button(this, isCurrent ? "현재 작업 열기" : "작업 열기",
                        v -> startActivity(OrchestrationActivity.jobIntent(this, jobId))),
                Ui.button(this, "실행 로그", v -> openLogs(jobId, OrchestrationLogsActivity.KIND_EXECUTION)),
                Ui.button(this, "디버그 로그", v -> openLogs(jobId, OrchestrationLogsActivity.KIND_DEBUG)));
        card.addView(actions);
        return card;
    }

    private void openLogs(String jobId, String kind) {
        startActivity(new Intent(this, OrchestrationLogsActivity.class)
                .putExtra(OrchestrationLogsActivity.EXTRA_JOB_ID, jobId)
                .putExtra(OrchestrationLogsActivity.EXTRA_LOG_KIND, kind));
    }

    private static String connected(String url) { return url == null || url.isEmpty() ? "준비 전" : "연결됨"; }
    static String jobRole(boolean current, boolean active, boolean terminal, boolean hasWorkspace) {
        if (current && active && !terminal) return "현재 실행";
        if (current && terminal) return "현재 선택 · 종료";
        if (current && hasWorkspace) return "현재 선택 · 재개 가능";
        if (current) return "현재 선택 · 기록 전용";
        if (terminal) return "종료";
        return hasWorkspace ? "재개 가능" : "기록 전용";
    }
    private static String empty(String value) { return value == null || value.isEmpty() ? "-" : value; }
    private static String compact(String value, int max) {
        String clean = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }
    private static String time(long value) {
        return value <= 0L ? "-" : DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(value));
    }
}
