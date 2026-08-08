package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.URI;
import java.util.HashSet;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;

/** Protocol 3.0 state is deliberately isolated from schedule and queue persistence. */
public final class OrchestrationStore {
    public static final String SIDE_CHAT = "CHAT";
    public static final String SIDE_WORK = "WORK";
    public static final String PHASE_SUBMIT = "SUBMIT";
    public static final String PHASE_SUBMITTING = "SUBMITTING";
    public static final String PHASE_WAIT = "WAIT_RESPONSE";

    private static final String PREFS = "orchestration_protocol_3";
    private static final Pattern JOB_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private final SharedPreferences preferences;

    public OrchestrationStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveConfig(String projectName, String chatUrl, String workUrl, String jobId) {
        commit(preferences.edit()
                .putString("projectName", clean(projectName))
                .putString("chatUrl", clean(chatUrl))
                .putString("workUrl", clean(workUrl))
                .putString("jobId", clean(jobId)));
    }

    public String configError() {
        return configError(chatUrl(), workUrl(), jobId());
    }

    public String runtimeConfigError() {
        return configError(runChatUrl(), runWorkUrl(), runJobId());
    }

    public static String configError(String chatUrl, String workUrl, String jobId) {
        String cleanJob = clean(jobId);
        String cleanChat = clean(chatUrl);
        String cleanWork = clean(workUrl);
        if (!JOB_ID.matcher(cleanJob).matches()) return "Job ID는 영문/숫자로 시작하고 영문·숫자·._-만 사용할 수 있습니다.";
        if (!isAllowedRelayUrl(cleanChat))
            return "일반 Chat URL은 대화 ID(/c/...)가 포함된 https://chatgpt.com 주소여야 합니다.";
        if (!isAllowedRelayUrl(cleanWork))
            return "Work URL은 대화 ID(/c/...)가 포함된 https://chatgpt.com 주소여야 합니다.";
        if (TargetParser.conversationId(cleanChat).equals(TargetParser.conversationId(cleanWork)))
            return "일반 Chat과 Work는 서로 다른 대화여야 합니다.";
        return "";
    }

    public void begin() {
        long now = System.currentTimeMillis();
        Set<String> usedJobIds = new HashSet<>(preferences.getStringSet("usedJobIds", Collections.emptySet()));
        usedJobIds.add(jobId());
        commit(preferences.edit()
                .putBoolean("active", true)
                .putBoolean("paused", false)
                .putString("side", SIDE_CHAT)
                .putString("phase", PHASE_SUBMIT)
                .putString("pendingPrompt", "[AUTOMATION_START " + jobId() + "]")
                .putString("runChatUrl", chatUrl())
                .putString("runWorkUrl", workUrl())
                .putString("runJobId", jobId())
                .putString("lastStartedJobId", jobId())
                .putStringSet("usedJobIds", usedJobIds)
                .putString("lastSignal", "")
                .putString("lastStep", "")
                .putString("lastRound", "")
                .putString("candidateFingerprint", "")
                .putInt("candidateStability", 0)
                .putBoolean("terminal", false)
                .putString("status", "일반 Chat 시작 신호 전송 준비")
                .putString("error", "")
                .putLong("epoch", epoch() + 1L)
                .putLong("phaseStartedAt", now)
                .putInt("pollCount", 0));
    }

    public void markWaiting() {
        commit(preferences.edit().putString("phase", PHASE_WAIT).putString("status", sideLabel() + " 응답 신호 대기")
                .putLong("phaseStartedAt", System.currentTimeMillis()).putInt("pollCount", 0)
                .putString("candidateFingerprint", "").putInt("candidateStability", 0));
    }

    public void markSubmitting() {
        commit(preferences.edit().putString("phase", PHASE_SUBMITTING)
                .putString("status", sideLabel() + " 전송 커밋 중")
                .putLong("phaseStartedAt", System.currentTimeMillis()));
    }

    public void transition(OrchestrationSignal signal) {
        String nextSide;
        String prompt;
        if (signal.type == OrchestrationSignal.Type.SEND_WORK) {
            nextSide = SIDE_WORK;
            prompt = "[AUTOMATION_WORK_STEP " + signal.jobId + " " + signal.step + " " + signal.round + "]";
        } else if (signal.type == OrchestrationSignal.Type.SEND_CHAT) {
            nextSide = SIDE_CHAT;
            prompt = "[AUTOMATION_CHAT_REVIEW " + signal.jobId + " " + signal.step + " " + signal.round + "]";
        } else {
            throw new IllegalArgumentException("전환 신호가 아닙니다.");
        }
        commit(preferences.edit()
                .putString("lastSignal", signal.raw)
                .putString("lastStep", signal.step)
                .putString("lastRound", signal.round)
                .putString("side", nextSide)
                .putString("pendingPrompt", prompt)
                .putString("phase", PHASE_SUBMIT)
                .putString("status", sideLabel(nextSide) + " 다음 턴 준비")
                .putString("error", "")
                .putString("candidateFingerprint", "")
                .putInt("candidateStability", 0)
                .putLong("phaseStartedAt", System.currentTimeMillis())
                .putInt("pollCount", 0));
    }

    public void incrementPoll() {
        preferences.edit().putInt("pollCount", pollCount() + 1).apply();
    }

    public int observeCandidate(String fingerprint) {
        String cleanFingerprint = clean(fingerprint);
        int stability = cleanFingerprint.equals(candidateFingerprint()) ? candidateStability() + 1 : 1;
        preferences.edit().putString("candidateFingerprint", cleanFingerprint)
                .putInt("candidateStability", stability).apply();
        return stability;
    }

    public void setStatus(String status) {
        preferences.edit().putString("status", clean(status)).apply();
    }

    public void pause(String reason) {
        commit(preferences.edit().putBoolean("paused", true).putString("status", "일시정지")
                .putString("error", clean(reason)));
    }

    public boolean resume() {
        if (terminal()) return false;
        commit(preferences.edit().putBoolean("active", true).putBoolean("paused", false)
                .putString("status", sideLabel() + " 중계 재개").putString("error", "")
                .putInt("pollCount", 0).putLong("phaseStartedAt", System.currentTimeMillis())
                .putString("candidateFingerprint", "").putInt("candidateStability", 0));
        return true;
    }

    public void finish(OrchestrationSignal signal) {
        String status = switch (signal.type) {
            case DONE -> "완료";
            case PAUSE -> "상대 Chat 요청으로 일시정지";
            case ABORTED -> "중단됨";
            default -> throw new IllegalArgumentException("종료 신호가 아닙니다.");
        };
        boolean paused = signal.type == OrchestrationSignal.Type.PAUSE;
        commit(preferences.edit().putString("lastSignal", signal.raw).putBoolean("active", paused)
                .putBoolean("paused", paused).putBoolean("terminal", isTerminalSignal(signal.type))
                .putString("status", status).putString("error", ""));
    }

    public void stop() {
        commit(preferences.edit().putBoolean("active", false).putBoolean("paused", false)
                .putString("status", "사용자가 중지함").putString("error", ""));
    }

    public String projectName() { return preferences.getString("projectName", ""); }
    public String chatUrl() { return preferences.getString("chatUrl", ""); }
    public String workUrl() { return preferences.getString("workUrl", ""); }
    public String jobId() { return preferences.getString("jobId", ""); }
    public String runChatUrl() { return preferences.getString("runChatUrl", ""); }
    public String runWorkUrl() { return preferences.getString("runWorkUrl", ""); }
    public String runJobId() { return preferences.getString("runJobId", ""); }
    public String lastStartedJobId() { return preferences.getString("lastStartedJobId", ""); }
    public boolean active() { return preferences.getBoolean("active", false); }
    public boolean paused() { return preferences.getBoolean("paused", false); }
    public boolean terminal() {
        if (preferences.getBoolean("terminal", false)) return true;
        // Preserve the terminal guard for state created by v0.1.14 before this flag existed.
        OrchestrationSignal last = OrchestrationSignal.parse(lastSignal(), runJobId());
        return last != null && isTerminalSignal(last.type);
    }
    public String side() { return preferences.getString("side", SIDE_CHAT); }
    public String phase() { return preferences.getString("phase", PHASE_SUBMIT); }
    public String pendingPrompt() { return preferences.getString("pendingPrompt", ""); }
    public String lastSignal() { return preferences.getString("lastSignal", ""); }
    public String lastStep() { return preferences.getString("lastStep", ""); }
    public String lastRound() { return preferences.getString("lastRound", ""); }
    public String status() { return preferences.getString("status", "설정 전"); }
    public String error() { return preferences.getString("error", ""); }
    public int pollCount() { return preferences.getInt("pollCount", 0); }
    public String candidateFingerprint() { return preferences.getString("candidateFingerprint", ""); }
    public int candidateStability() { return preferences.getInt("candidateStability", 0); }
    public long phaseStartedAt() { return preferences.getLong("phaseStartedAt", 0L); }
    public long epoch() { return preferences.getLong("epoch", 0L); }
    public String targetUrl() { return SIDE_WORK.equals(side()) ? runWorkUrl() : runChatUrl(); }
    public String sideLabel() { return sideLabel(side()); }

    public static boolean isTerminalSignal(OrchestrationSignal.Type type) {
        return type == OrchestrationSignal.Type.DONE || type == OrchestrationSignal.Type.ABORTED;
    }

    public static boolean isAllowedRelayUrl(String url) {
        if (!TargetParser.isSupported(url) || TargetParser.conversationId(url) == null) return false;
        URI uri = URI.create(url);
        return uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    public String newRunError(String candidateJobId) {
        String candidate = clean(candidateJobId);
        Set<String> usedJobIds = new HashSet<>(preferences.getStringSet("usedJobIds", Collections.emptySet()));
        if (!lastStartedJobId().isEmpty()) usedJobIds.add(lastStartedJobId());
        if (!candidate.isEmpty() && usedJobIds.contains(candidate))
            return "이미 중계에 사용한 Job ID입니다. 새 Job ID로 시작해 주세요.";
        return "";
    }

    private static String sideLabel(String side) { return SIDE_WORK.equals(side) ? "Work" : "일반 Chat"; }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static void commit(SharedPreferences.Editor editor) {
        if (!editor.commit()) throw new IllegalStateException("오토런 중계 상태를 저장하지 못했습니다.");
    }
}
