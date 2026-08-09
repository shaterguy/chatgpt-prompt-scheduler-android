package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Durable Protocol 3.x state, deliberately isolated from schedule and queue persistence. */
public final class OrchestrationStore {
    public static final String SIDE_CHAT = "CHAT";
    public static final String SIDE_WORK = "WORK";

    public static final String DELIVERY_PENDING = "PENDING";
    public static final String DELIVERY_PREPARING = "PREPARING";
    public static final String DELIVERY_SUBMITTING = "SUBMITTING";
    public static final String DELIVERY_SUBMITTED = "SUBMITTED";
    public static final String DELIVERY_WAITING_RESPONSE = "WAITING_RESPONSE";
    public static final String DELIVERY_AMBIGUOUS = "AMBIGUOUS";
    public static final String DELIVERY_FAILED = "FAILED";

    public static final String RECONCILIATION_NONE = "NONE";
    public static final String RECONCILIATION_SCAN_ROOMS = "SCAN_ROOMS";
    public static final String RECONCILIATION_CONFIRM_ROOMS = "CONFIRM_ROOMS";
    public static final String RECONCILIATION_SOURCE_FRESHNESS = "SOURCE_FRESHNESS";
    public static final String RECONCILIATION_WAITING_IDLE = "WAITING_IDLE";
    public static final String RECONCILIATION_TARGET_SCAN = "TARGET_SCAN";

    public static final String STOP_GENERATION_NONE = "NONE";
    public static final String STOP_GENERATION_SUBMITTING = "SUBMITTING";
    public static final String STOP_GENERATION_CLICKED = "CLICKED";
    public static final String STOP_GENERATION_CONFIRMED = "CONFIRMED";
    public static final String STOP_GENERATION_AMBIGUOUS = "AMBIGUOUS";

    // Backward compatibility for callers/tests from v0.1.14.
    public static final String PHASE_SUBMIT = "SUBMIT";
    public static final String PHASE_SUBMITTING = "SUBMITTING";
    public static final String PHASE_WAIT = "WAIT_RESPONSE";

    private static final int SCHEMA_VERSION = 5;
    private static final String PREFS = "orchestration_protocol_3";
    private static final Pattern JOB_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private final SharedPreferences preferences;

    public OrchestrationStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        migrateLegacyState();
    }

    public void saveConfig(String projectName, String chatUrl, String workUrl, String jobId) {
        commit(preferences.edit().putString("projectName", clean(projectName))
                .putString("chatUrl", clean(chatUrl)).putString("workUrl", clean(workUrl))
                .putString("jobId", clean(jobId)));
    }

    public String configError() { return configError(chatUrl(), workUrl(), jobId()); }
    public String runtimeConfigError() { return configError(runChatUrl(), runWorkUrl(), runJobId()); }

    public static String configError(String chatUrl, String workUrl, String jobId) {
        String cleanJob = clean(jobId);
        String cleanChat = clean(chatUrl);
        String cleanWork = clean(workUrl);
        if (!JOB_ID.matcher(cleanJob).matches()) return "Job ID는 영문/숫자로 시작하고 영문·숫자·._-만 사용할 수 있습니다.";
        if (!isAllowedRelayUrl(cleanChat)) return "일반 Chat URL은 대화 ID(/c/...)가 포함된 https://chatgpt.com 주소여야 합니다.";
        if (!isAllowedRelayUrl(cleanWork)) return "Work URL은 대화 ID(/c/...)가 포함된 https://chatgpt.com 주소여야 합니다.";
        if (TargetParser.conversationId(cleanChat).equals(TargetParser.conversationId(cleanWork)))
            return "일반 Chat과 Work는 서로 다른 대화여야 합니다.";
        return "";
    }

    public void begin() {
        long now = System.currentTimeMillis();
        Set<String> usedJobIds = new HashSet<>(preferences.getStringSet("usedJobIds", Collections.emptySet()));
        usedJobIds.add(jobId());
        commit(preferences.edit().putInt("schemaVersion", SCHEMA_VERSION)
                .putBoolean("active", true).putBoolean("paused", false).putBoolean("terminal", false)
                .putString("monitoringSide", SIDE_CHAT).putString("deliveryTarget", SIDE_CHAT)
                .putString("deliveryState", DELIVERY_PENDING)
                .putString("pendingPrompt", "[AUTOMATION_START " + jobId() + "]")
                .putString("stampedPrompt", "")
                .putString("lastDeliveryTarget", "").putString("lastDeliveredPrompt", "")
                .putString("lastDeliveryState", "").putLong("deliveryPreparedAt", 0L)
                .putLong("deliveryAttemptAt", 0L).putLong("lastDeliveryAt", 0L)
                .putString("runChatUrl", chatUrl()).putString("runWorkUrl", workUrl())
                .putString("runJobId", jobId()).putString("lastStartedJobId", jobId())
                .putStringSet("usedJobIds", usedJobIds).putString("lastSignalSource", "")
                .putString("lastAcceptedSignal", "").putLong("lastSignalAt", 0L)
                .putString("currentStep", "").putString("currentRound", "")
                .putString("expectedSignal", "전송 완료 후 결정")
                .putString("candidateFingerprint", "").putInt("candidateStability", 0)
                .putString("status", "일반 Chat으로 시작 프롬프트 전송 대기")
                .putString("lastErrorCode", "").putString("error", "").putLong("errorAt", 0L)
                .putBoolean("schedulePreempted", false).putBoolean("waitingForUser", false)
                .putBoolean("reconciling", false).putString("reconciliationPhase", RECONCILIATION_NONE)
                .putString("reconciliationSide", SIDE_CHAT)
                .putString("actionId", "").putLong("epoch", epoch() + 1L)
                .putLong("phaseStartedAt", now).putLong("pollCountLong", 0L).putInt("pollCount", 0)
                .putLong("responseEpoch", 0L).putLong("responseStartElapsed", 0L)
                .putString("responseStartBootIdentity", "").putLong("lastSignalResponseEpoch", -1L)
                .putLong("continuationEpoch", 0L).putBoolean("softYieldDue", false)
                .putString("stopGenerationState", STOP_GENERATION_NONE).putLong("stopGenerationEpoch", 0L));
    }

    public void markPreparing() {
        long now = System.currentTimeMillis();
        commit(preferences.edit().putString("deliveryState", DELIVERY_PREPARING)
                .putLong("deliveryPreparedAt", now).putLong("phaseStartedAt", now)
                .putString("status", sideLabel(deliveryTarget()) + "로 프롬프트 전송 준비 완료"));
    }

    public void resetPreparing() {
        commit(preferences.edit().putString("deliveryState", DELIVERY_PENDING)
                .putString("status", sideLabel(deliveryTarget()) + "로 프롬프트 전송 준비 복구"));
    }

    /** Must be committed synchronously immediately before the click-capable script is evaluated. */
    public void markSubmitting() {
        long now = System.currentTimeMillis();
        commit(preferences.edit().putString("deliveryState", DELIVERY_SUBMITTING)
                .putLong("deliveryAttemptAt", now).putLong("phaseStartedAt", now)
                .putString("status", sideLabel(deliveryTarget()) + "로 프롬프트 제출 중"));
    }

    public void markSubmitted() {
        long now = System.currentTimeMillis();
        commit(preferences.edit().putString("deliveryState", DELIVERY_SUBMITTED)
                .putString("lastDeliveryTarget", deliveryTarget())
                .putString("lastDeliveredPrompt", stampedPrompt())
                .putString("lastDeliveryState", DELIVERY_SUBMITTED)
                .putLong("lastDeliveryAt", now)
                .putString("status", sideLabel(deliveryTarget()) + " 프롬프트 제출 결과 확인 중")
                .putLong("phaseStartedAt", now));
    }

    /** Called only after the exact user turn is observed in the target conversation DOM. */
    public void markWaiting() {
        long now = System.currentTimeMillis();
        String target = deliveryTarget();
        commit(preferences.edit().putString("deliveryState", DELIVERY_WAITING_RESPONSE)
                .putString("monitoringSide", target).putString("expectedSignal", expectedFor(target, currentStep(), currentRound()))
                .putString("lastDeliveryTarget", target).putString("lastDeliveredPrompt", stampedPrompt())
                .putString("lastDeliveryState", DELIVERY_WAITING_RESPONSE).putLong("lastDeliveryAt", now)
                .putString("status", sideLabel(target) + " 응답 대기 중")
                .putLong("phaseStartedAt", now).putLong("pollCountLong", 0L).putInt("pollCount", 0)
                .putString("candidateFingerprint", "").putInt("candidateStability", 0));
        resetResponseTiming("WAITING_RESPONSE");
    }

    public void transition(OrchestrationSignal signal, String sourceSide) {
        String target;
        String prompt;
        if (signal.type == OrchestrationSignal.Type.SEND_WORK) {
            target = SIDE_WORK;
            prompt = promptFor(signal);
        } else if (signal.type == OrchestrationSignal.Type.SEND_CHAT) {
            target = SIDE_CHAT;
            prompt = promptFor(signal);
        } else {
            throw new IllegalArgumentException("전환 신호가 아닙니다.");
        }
        long now = System.currentTimeMillis();
        commit(preferences.edit().putString("lastSignalSource", sourceSide)
                .putString("lastAcceptedSignal", signal.raw).putLong("lastSignalAt", now)
                .putString("currentStep", signal.step).putString("currentRound", signal.round)
                .putString("deliveryTarget", target).putString("pendingPrompt", prompt)
                .putString("stampedPrompt", "")
                .putString("deliveryState", DELIVERY_PENDING).putString("expectedSignal", "전송 완료 후 결정")
                .putLong("deliveryPreparedAt", 0L).putLong("deliveryAttemptAt", 0L)
                .putString("status", sideLabel(target) + "로 프롬프트 전송 대기")
                .putString("lastErrorCode", "").putString("error", "").putLong("errorAt", 0L)
                .putString("candidateFingerprint", "").putInt("candidateStability", 0)
                .putLong("phaseStartedAt", now).putLong("pollCountLong", 0L).putInt("pollCount", 0));
        resetResponseTiming("SIGNAL_TRANSITION");
    }

    /** Prepares a same-side continuation without changing Step/Round or conversation target. */
    public void continueSame(OrchestrationSignal signal, String sourceSide) {
        if (signal == null || signal.type != OrchestrationSignal.Type.CONTINUE_SAME)
            throw new IllegalArgumentException("SAME-SIDE 신호가 아닙니다.");
        prepareSameSideDelivery(sourceSide, signal.raw);
    }

    /** Creates the short same-side trigger used only after confirmed Hard Fallback recovery. */
    public void prepareSameSideFallback(String sourceSide) {
        prepareSameSideDelivery(sourceSide, "");
    }

    private void prepareSameSideDelivery(String sourceSide, String acceptedSignal) {
        if (!SIDE_CHAT.equals(sourceSide) && !SIDE_WORK.equals(sourceSide))
            throw new IllegalArgumentException("SAME-SIDE source side가 올바르지 않습니다.");
        if (currentStep().isEmpty() || currentRound().isEmpty())
            throw new IllegalStateException("SAME-SIDE continuation의 Step/Round가 없습니다.");
        long now = System.currentTimeMillis();
        long nextContinuation = continuationEpoch() == Long.MAX_VALUE
                ? Long.MAX_VALUE : continuationEpoch() + 1L;
        SharedPreferences.Editor edit = preferences.edit()
                .putString("lastSignalSource", sourceSide)
                .putString("deliveryTarget", sourceSide)
                .putString("pendingPrompt", sameSidePrompt(runJobId(), currentStep(), currentRound()))
                .putString("stampedPrompt", "")
                .putString("deliveryState", DELIVERY_PENDING)
                .putString("expectedSignal", expectedFor(sourceSide, currentStep(), currentRound()))
                .putLong("deliveryPreparedAt", 0L).putLong("deliveryAttemptAt", 0L)
                .putString("status", sideLabel(sourceSide) + " SAME-SIDE continuation 전송 대기")
                .putString("lastErrorCode", "").putString("error", "").putLong("errorAt", 0L)
                .putString("candidateFingerprint", "").putInt("candidateStability", 0)
                .putLong("phaseStartedAt", now).putLong("pollCountLong", 0L).putInt("pollCount", 0)
                .putLong("continuationEpoch", nextContinuation);
        if (!acceptedSignal.isEmpty()) {
            edit.putString("lastAcceptedSignal", acceptedSignal)
                    .putLong("lastSignalAt", now)
                    .putLong("lastSignalResponseEpoch", responseEpoch());
        }
        commit(edit);
        resetResponseTiming(acceptedSignal.isEmpty() ? "HARD_FALLBACK_CONTINUATION" : "CONTINUE_SAME");
    }

    public void waitForUser(OrchestrationSignal signal, String sourceSide) {
        long now = System.currentTimeMillis();
        commit(preferences.edit().putString("lastSignalSource", sourceSide)
                .putString("lastAcceptedSignal", signal.raw).putLong("lastSignalAt", now)
                .putString("currentStep", signal.step).putString("currentRound", signal.round)
                .putBoolean("waitingForUser", true).putString("actionId", signal.actionId)
                .putBoolean("paused", true).putBoolean("terminal", false)
                .putString("expectedSignal", "사용자 처리 완료 후 일반 Chat 재검증")
                .putString("status", "사용자 조치 대기").putString("lastErrorCode", "")
                .putString("error", "").putLong("errorAt", 0L)
                .putBoolean("reconciling", false).putString("reconciliationPhase", RECONCILIATION_NONE));
    }

    public boolean resolveUserAction() {
        if (!waitingForUser() || actionId().isEmpty() || terminal()) return false;
        long now = System.currentTimeMillis();
        String prompt = userResolvedPrompt(runJobId(), actionId());
        commit(preferences.edit().putBoolean("active", true).putBoolean("paused", false)
                .putBoolean("waitingForUser", false).putString("deliveryTarget", SIDE_CHAT)
                .putString("pendingPrompt", prompt).putString("stampedPrompt", "")
                .putString("deliveryState", DELIVERY_PENDING)
                .putLong("deliveryPreparedAt", 0L).putLong("deliveryAttemptAt", 0L)
                .putString("expectedSignal", "전송 완료 후 일반 Chat의 재검증 결과")
                .putString("status", "일반 Chat으로 사용자 조치 재검증 요청 전송 대기")
                .putString("lastErrorCode", "").putString("error", "").putLong("errorAt", 0L)
                .putLong("phaseStartedAt", now).putLong("epoch", epoch() + 1L)
                .putString("candidateFingerprint", "").putInt("candidateStability", 0)
                .putLong("pollCountLong", 0L).putInt("pollCount", 0));
        resetResponseTiming("USER_ACTION_RESOLVED");
        return true;
    }

    public void incrementPoll() {
        long next = pollCountLong() == Long.MAX_VALUE ? Long.MAX_VALUE : pollCountLong() + 1L;
        preferences.edit().putLong("pollCountLong", next).putInt("pollCount", (int) Math.min(next, Integer.MAX_VALUE)).apply();
    }

    public int observeCandidate(String fingerprint) {
        String cleanFingerprint = clean(fingerprint);
        int stability = cleanFingerprint.equals(candidateFingerprint()) ? candidateStability() + 1 : 1;
        preferences.edit().putString("candidateFingerprint", cleanFingerprint).putInt("candidateStability", stability).apply();
        return stability;
    }

    public void setStatus(String status) { preferences.edit().putString("status", clean(status)).apply(); }
    public void setSchedulePreempted(boolean preempted) {
        preferences.edit().putBoolean("schedulePreempted", preempted).apply();
    }

    public void pause(String reason) {
        commit(preferences.edit().putBoolean("paused", true).putString("status", "사용자가 중계를 일시정지함")
                .putString("error", clean(reason)).putBoolean("reconciling", false)
                .putString("reconciliationPhase", RECONCILIATION_NONE));
    }

    public void fail(String code, String reason) {
        String recovery = deliveryState();
        commit(preferences.edit().putBoolean("paused", true).putString("recoveryDeliveryState", recovery)
                .putString("deliveryState", DELIVERY_FAILED).putString("status", "오류로 중계 일시정지")
                .putString("lastErrorCode", clean(code)).putString("error", clean(reason))
                .putLong("errorAt", System.currentTimeMillis())
                .putBoolean("reconciling", false).putString("reconciliationPhase", RECONCILIATION_NONE));
    }

    public void ambiguous(String reason) {
        commit(preferences.edit().putBoolean("paused", true).putString("deliveryState", DELIVERY_AMBIGUOUS)
                .putString("status", "전송 결과 불명확 · 사용자 확인 필요")
                .putString("lastErrorCode", "DELIVERY_AMBIGUOUS").putString("error", clean(reason))
                .putLong("errorAt", System.currentTimeMillis())
                .putBoolean("reconciling", false).putString("reconciliationPhase", RECONCILIATION_NONE));
    }

    public boolean resume() {
        if (terminal() || waitingForUser()) return false;
        String restored = deliveryState();
        if (DELIVERY_FAILED.equals(restored)) {
            restored = preferences.getString("recoveryDeliveryState", DELIVERY_WAITING_RESPONSE);
        }
        if (DELIVERY_AMBIGUOUS.equals(restored)) {
            // Recovery is observation-only. The service enters recoverSubmission() and never clicks.
            restored = DELIVERY_SUBMITTING;
        }
        commit(preferences.edit().putBoolean("active", true).putBoolean("paused", false)
                .putString("deliveryState", restored).putString("status", currentActionFor(restored))
                .putString("lastErrorCode", "").putString("error", "").putLong("errorAt", 0L)
                .putLong("pollCountLong", 0L).putInt("pollCount", 0)
                .putLong("phaseStartedAt", System.currentTimeMillis())
                .putString("candidateFingerprint", "").putInt("candidateStability", 0));
        return true;
    }

    /**
     * Starts Resume as a DOM reconciliation transaction. Existing delivery fields remain hints
     * until both conversation rooms have been scanned and a new atomic state is committed.
     */
    public boolean beginReconciliation() {
        if (runJobId().isEmpty() || runChatUrl().isEmpty() || runWorkUrl().isEmpty()) return false;
        commit(preferences.edit().putBoolean("active", true).putBoolean("paused", false)
                .putBoolean("reconciling", true).putString("reconciliationPhase", RECONCILIATION_SCAN_ROOMS)
                .putString("reconciliationSide", SIDE_CHAT)
                .putString("status", "재개 상태 재구성 중 · 두 대화방 확인")
                .putString("lastErrorCode", "").putString("error", "").putLong("errorAt", 0L)
                .putLong("phaseStartedAt", System.currentTimeMillis())
                .putString("candidateFingerprint", "").putInt("candidateStability", 0)
                .putLong("epoch", epoch() + 1L));
        return true;
    }

    /** Discards in-memory reconciliation evidence after schedule preemption or process recovery. */
    public void restartReconciliation() {
        if (!reconciling()) return;
        commit(preferences.edit().putBoolean("active", true).putBoolean("paused", false)
                .putString("reconciliationPhase", RECONCILIATION_SCAN_ROOMS)
                .putString("reconciliationSide", SIDE_CHAT)
                .putString("status", "재개 상태 재구성 중 · 두 대화방을 처음부터 다시 확인")
                .putLong("phaseStartedAt", System.currentTimeMillis())
                .putLong("epoch", epoch() + 1L));
    }

    public void setReconciliationSide(String side, String status) {
        if (!SIDE_CHAT.equals(side) && !SIDE_WORK.equals(side))
            throw new IllegalArgumentException("재구성 대화방이 올바르지 않습니다.");
        commit(preferences.edit().putString("reconciliationSide", side)
                .putString("reconciliationPhase", RECONCILIATION_SCAN_ROOMS)
                .putString("status", clean(status)));
    }

    /** Begins the second, stable-idle confirmation pass after discovery selected a route. */
    public void beginReconciliationConfirmation() {
        commit(preferences.edit().putString("reconciliationSide", SIDE_CHAT)
                .putString("reconciliationPhase", RECONCILIATION_CONFIRM_ROOMS)
                .putString("status", "재개 후보 안정성 확인 중 · 일반 Chat 재확인")
                .putLong("phaseStartedAt", System.currentTimeMillis())
                .putLong("epoch", epoch() + 1L));
    }

    public void setReconciliationConfirmationSide(String side, String status) {
        if (!SIDE_CHAT.equals(side) && !SIDE_WORK.equals(side))
            throw new IllegalArgumentException("재구성 확인 대화방이 올바르지 않습니다.");
        commit(preferences.edit().putString("reconciliationSide", side)
                .putString("reconciliationPhase", RECONCILIATION_CONFIRM_ROOMS)
                .putString("status", clean(status))
                .putLong("epoch", epoch() + 1L));
    }

    public void setReconciliationSourceFreshness(String side, String status) {
        if (!SIDE_CHAT.equals(side) && !SIDE_WORK.equals(side))
            throw new IllegalArgumentException("재구성 원본 대화방이 올바르지 않습니다.");
        commit(preferences.edit().putString("reconciliationSide", side)
                .putString("reconciliationPhase", RECONCILIATION_SOURCE_FRESHNESS)
                .putString("status", clean(status))
                .putLong("phaseStartedAt", System.currentTimeMillis())
                .putLong("epoch", epoch() + 1L));
    }

    public void setReconciliationWaiting(String status) {
        commit(preferences.edit().putString("reconciliationPhase", RECONCILIATION_WAITING_IDLE)
                .putString("reconciliationSide", SIDE_CHAT).putString("status", clean(status)));
    }

    public void setReconciliationTarget(String side, String status) {
        if (!SIDE_CHAT.equals(side) && !SIDE_WORK.equals(side))
            throw new IllegalArgumentException("재구성 전달 대상이 올바르지 않습니다.");
        commit(preferences.edit().putString("reconciliationSide", side)
                .putString("reconciliationPhase", RECONCILIATION_TARGET_SCAN)
                .putString("status", clean(status)));
    }

    /**
     * Rebuilds delivery as observation-only when the exact target user turn already exists in
     * the DOM. This creates no submit side effect and enters normal response monitoring directly.
     */
    public void rebuildForExistingPrompt(OrchestrationSignal signal, String sourceSide) {
        if (signal == null || (signal.type != OrchestrationSignal.Type.SEND_WORK
                && signal.type != OrchestrationSignal.Type.SEND_CHAT
                && signal.type != OrchestrationSignal.Type.CONTINUE_SAME))
            throw new IllegalArgumentException("관찰 복구 가능한 재구성 신호가 아닙니다.");
        String target = signal.type == OrchestrationSignal.Type.SEND_WORK ? SIDE_WORK
                : signal.type == OrchestrationSignal.Type.SEND_CHAT ? SIDE_CHAT : sourceSide;
        String prompt = promptFor(signal);
        long now = System.currentTimeMillis();
        long nextContinuation = signal.type == OrchestrationSignal.Type.CONTINUE_SAME
                ? continuationEpoch() == Long.MAX_VALUE ? Long.MAX_VALUE : continuationEpoch() + 1L
                : continuationEpoch();
        commit(preferences.edit().putBoolean("active", true).putBoolean("paused", false)
                .putBoolean("terminal", false).putBoolean("waitingForUser", false)
                .putBoolean("reconciling", false).putString("reconciliationPhase", RECONCILIATION_NONE)
                .putString("monitoringSide", target).putString("lastSignalSource", sourceSide)
                .putString("lastAcceptedSignal", signal.raw).putLong("lastSignalAt", now)
                .putString("currentStep", signal.step).putString("currentRound", signal.round)
                .putString("deliveryTarget", target).putString("pendingPrompt", prompt)
                .putString("stampedPrompt", prompt).putString("deliveryState", DELIVERY_WAITING_RESPONSE)
                .putString("expectedSignal", expectedFor(target, signal.step, signal.round))
                .putString("lastDeliveryTarget", target).putString("lastDeliveredPrompt", prompt)
                .putString("lastDeliveryState", DELIVERY_WAITING_RESPONSE).putLong("lastDeliveryAt", now)
                .putLong("deliveryPreparedAt", 0L).putLong("deliveryAttemptAt", 0L)
                .putString("status", sideLabel(target) + " 기존 프롬프트 확인 · 응답 대기 중")
                .putString("lastErrorCode", "").putString("error", "").putLong("errorAt", 0L)
                .putLong("phaseStartedAt", now).putLong("pollCountLong", 0L).putInt("pollCount", 0)
                .putString("candidateFingerprint", "").putInt("candidateStability", 0)
                .putLong("epoch", epoch() + 1L).putLong("responseStartElapsed", 0L)
                .putString("responseStartBootIdentity", "").putBoolean("softYieldDue", false)
                .putString("stopGenerationState", STOP_GENERATION_NONE).putLong("stopGenerationEpoch", 0L)
                .putLong("continuationEpoch", nextContinuation)
                .putLong("lastSignalResponseEpoch", signal.type == OrchestrationSignal.Type.CONTINUE_SAME
                        ? responseEpoch() : -1L));
    }

    /** Atomically replaces stale local delivery position with the selected DOM-derived route. */
    public void rebuildForReconciliation(OrchestrationSignal signal, String sourceSide) {
        if (signal == null || (signal.type != OrchestrationSignal.Type.SEND_WORK
                && signal.type != OrchestrationSignal.Type.SEND_CHAT
                && signal.type != OrchestrationSignal.Type.CONTINUE_SAME))
            throw new IllegalArgumentException("전달 가능한 재구성 신호가 아닙니다.");
        String target = signal.type == OrchestrationSignal.Type.SEND_WORK ? SIDE_WORK
                : signal.type == OrchestrationSignal.Type.SEND_CHAT ? SIDE_CHAT : sourceSide;
        long now = System.currentTimeMillis();
        long nextContinuation = signal.type == OrchestrationSignal.Type.CONTINUE_SAME
                ? continuationEpoch() == Long.MAX_VALUE ? Long.MAX_VALUE : continuationEpoch() + 1L
                : continuationEpoch();
        commit(preferences.edit().putBoolean("active", true).putBoolean("paused", false)
                .putBoolean("terminal", false).putBoolean("waitingForUser", false)
                .putBoolean("reconciling", false).putString("reconciliationPhase", RECONCILIATION_NONE)
                .putString("monitoringSide", sourceSide).putString("lastSignalSource", sourceSide)
                .putString("lastAcceptedSignal", signal.raw).putLong("lastSignalAt", now)
                .putString("currentStep", signal.step).putString("currentRound", signal.round)
                .putString("deliveryTarget", target).putString("pendingPrompt", promptFor(signal))
                .putString("stampedPrompt", "").putString("deliveryState", DELIVERY_PENDING)
                .putString("expectedSignal", "전송 완료 후 결정")
                .putLong("deliveryPreparedAt", 0L).putLong("deliveryAttemptAt", 0L)
                .putString("status", sideLabel(target) + "로 재개 복구 프롬프트 전송 대기")
                .putString("lastErrorCode", "").putString("error", "").putLong("errorAt", 0L)
                .putLong("phaseStartedAt", now).putLong("pollCountLong", 0L).putInt("pollCount", 0)
                .putString("candidateFingerprint", "").putInt("candidateStability", 0)
                .putLong("epoch", epoch() + 1L).putLong("responseStartElapsed", 0L)
                .putString("responseStartBootIdentity", "").putBoolean("softYieldDue", false)
                .putString("stopGenerationState", STOP_GENERATION_NONE).putLong("stopGenerationEpoch", 0L)
                .putLong("continuationEpoch", nextContinuation)
                .putLong("lastSignalResponseEpoch", signal.type == OrchestrationSignal.Type.CONTINUE_SAME
                        ? responseEpoch() : -1L));
    }

    public void reconciliationAmbiguous(String code, String reason) {
        commit(preferences.edit().putBoolean("active", false).putBoolean("paused", true)
                .putBoolean("reconciling", false).putString("reconciliationPhase", RECONCILIATION_NONE)
                .putString("deliveryState", DELIVERY_AMBIGUOUS).putString("status", "재개 상태 재구성 불명확 · 자동 전송 중지")
                .putString("lastErrorCode", clean(code)).putString("error", clean(reason))
                .putLong("errorAt", System.currentTimeMillis()));
    }

    public void finish(OrchestrationSignal signal, String sourceSide) {
        String nextStatus = switch (signal.type) {
            case DONE -> "완료";
            case PAUSE -> "일반 Chat 요청으로 Job 일시정지";
            case ABORTED -> "중단됨";
            default -> throw new IllegalArgumentException("종료 신호가 아닙니다.");
        };
        boolean paused = signal.type == OrchestrationSignal.Type.PAUSE;
        commit(preferences.edit().putString("lastSignalSource", sourceSide)
                .putString("lastAcceptedSignal", signal.raw).putLong("lastSignalAt", System.currentTimeMillis())
                .putBoolean("active", false).putBoolean("paused", paused)
                .putBoolean("waitingForUser", false).putString("actionId", "")
                .putBoolean("terminal", isTerminalSignal(signal.type)).putString("status", nextStatus)
                .putString("lastErrorCode", "").putString("error", "").putLong("errorAt", 0L)
                .putBoolean("reconciling", false).putString("reconciliationPhase", RECONCILIATION_NONE));
    }

    public void stop() {
        commit(preferences.edit().putBoolean("active", false).putBoolean("paused", false)
                .putBoolean("waitingForUser", false).putString("status", "사용자가 중지함")
                .putString("lastErrorCode", "").putString("error", "")
                .putBoolean("reconciling", false).putString("reconciliationPhase", RECONCILIATION_NONE));
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
    public boolean reconciling() { return preferences.getBoolean("reconciling", false); }
    public String reconciliationPhase() {
        return preferences.getString("reconciliationPhase", RECONCILIATION_NONE);
    }
    public String reconciliationSide() {
        return preferences.getString("reconciliationSide", SIDE_CHAT);
    }
    public boolean terminal() {
        if (preferences.getBoolean("terminal", false)) return true;
        OrchestrationSignal last = OrchestrationSignal.parse(lastAcceptedSignal(), runJobId());
        return last != null && isTerminalSignal(last.type);
    }
    public String monitoringSide() { return preferences.getString("monitoringSide", SIDE_CHAT); }
    public String lastSignalSource() { return preferences.getString("lastSignalSource", ""); }
    public String lastAcceptedSignal() { return preferences.getString("lastAcceptedSignal", ""); }
    public long lastSignalAt() { return preferences.getLong("lastSignalAt", 0L); }
    public String deliveryTarget() { return preferences.getString("deliveryTarget", SIDE_CHAT); }
    public String pendingPrompt() { return preferences.getString("pendingPrompt", ""); }
    /** Raw control payload for the current delivery; it never includes the transport timestamp. */
    public String rawPendingPrompt() { return pendingPrompt(); }
    /** Exact prompt used for the current delivery after timestamp stamping. */
    public String stampedPrompt() { return preferences.getString("stampedPrompt", ""); }
    public String deliveryPrompt() { return stampedPrompt(); }

    /**
     * Creates the transport envelope once for the current delivery and persists it before WebView input.
     * Synchronous commit keeps preparation/restart recovery from producing two timestamps for one delivery.
     */
    public synchronized String ensureStampedPrompt() {
        String existing = stampedPrompt();
        if (!existing.isEmpty()) return existing;
        String raw = pendingPrompt();
        if (raw.isEmpty()) return "";
        String stamped = stampPrompt(raw, System.currentTimeMillis());
        commit(preferences.edit().putString("stampedPrompt", stamped));
        return stamped;
    }

    public static String stampPrompt(String rawPrompt, long epochMillis) {
        String raw = rawPrompt == null ? "" : rawPrompt.trim();
        return raw.isEmpty() ? "" : TimestampUtil.prefix(epochMillis, raw);
    }
    public String lastDeliveredPrompt() { return preferences.getString("lastDeliveredPrompt", ""); }
    public String lastDeliveryTarget() { return preferences.getString("lastDeliveryTarget", ""); }
    public String lastDeliveryState() { return preferences.getString("lastDeliveryState", ""); }
    public String deliveryState() { return preferences.getString("deliveryState", DELIVERY_PENDING); }
    public long deliveryAttemptAt() { return preferences.getLong("deliveryAttemptAt", 0L); }
    public long lastDeliveryAt() { return preferences.getLong("lastDeliveryAt", 0L); }
    public String currentStep() { return preferences.getString("currentStep", ""); }
    public String currentRound() { return preferences.getString("currentRound", ""); }
    public String expectedSignal() { return preferences.getString("expectedSignal", ""); }
    public String status() { return preferences.getString("status", "설정 전"); }
    public String statusSummary() {
        if (reconciling()) return "재개 상태 재구성 중";
        OrchestrationSignal last = OrchestrationSignal.parse(lastAcceptedSignal(), runJobId());
        return statusSummary(active(), paused(), terminal(), waitingForUser(), deliveryState(),
                last == null ? null : last.type);
    }
    public String lastErrorCode() { return preferences.getString("lastErrorCode", ""); }
    public String error() { return preferences.getString("error", ""); }
    public long errorAt() { return preferences.getLong("errorAt", 0L); }
    public boolean schedulePreempted() { return preferences.getBoolean("schedulePreempted", false); }
    public boolean waitingForUser() { return preferences.getBoolean("waitingForUser", false); }
    public String actionId() { return preferences.getString("actionId", ""); }
    public long pollCountLong() { return preferences.getLong("pollCountLong", preferences.getInt("pollCount", 0)); }
    public int pollCount() { return (int) Math.min(pollCountLong(), Integer.MAX_VALUE); }
    public String candidateFingerprint() { return preferences.getString("candidateFingerprint", ""); }
    public int candidateStability() { return preferences.getInt("candidateStability", 0); }
    public long phaseStartedAt() { return preferences.getLong("phaseStartedAt", 0L); }
    public long epoch() { return preferences.getLong("epoch", 0L); }
    public long responseEpoch() { return preferences.getLong("responseEpoch", 0L); }
    public long responseStartElapsed() { return preferences.getLong("responseStartElapsed", 0L); }
    public String responseStartBootIdentity() {
        return preferences.getString("responseStartBootIdentity", "");
    }
    public long lastSignalResponseEpoch() {
        return preferences.getLong("lastSignalResponseEpoch", -1L);
    }
    public long continuationEpoch() { return preferences.getLong("continuationEpoch", 0L); }
    public boolean softYieldDue() { return preferences.getBoolean("softYieldDue", false); }
    public String stopGenerationState() {
        return preferences.getString("stopGenerationState", STOP_GENERATION_NONE);
    }
    public long stopGenerationEpoch() { return preferences.getLong("stopGenerationEpoch", 0L); }

    /** Starts one durable assistant-response timing epoch, preserving it across process recovery. */
    public synchronized boolean ensureResponseEpoch(String bootIdentity, long elapsedRealtime) {
        String boot = clean(bootIdentity);
        if (boot.isEmpty() || elapsedRealtime <= 0L) return false;
        if (responseStartElapsed() > 0L && boot.equals(responseStartBootIdentity())) return false;
        long next = responseEpoch() == Long.MAX_VALUE ? Long.MAX_VALUE : responseEpoch() + 1L;
        commit(preferences.edit().putLong("responseEpoch", next)
                .putLong("responseStartElapsed", elapsedRealtime)
                .putString("responseStartBootIdentity", boot)
                .putBoolean("softYieldDue", false)
                .putString("stopGenerationState", STOP_GENERATION_NONE)
                .putLong("stopGenerationEpoch", next));
        return true;
    }

    public long responseAgeMs(String bootIdentity, long elapsedRealtime) {
        if (elapsedRealtime <= 0L || !clean(bootIdentity).equals(responseStartBootIdentity())
                || responseStartElapsed() <= 0L) return 0L;
        return Math.max(0L, elapsedRealtime - responseStartElapsed());
    }

    public synchronized boolean markSoftYieldDue() {
        if (softYieldDue()) return false;
        commit(preferences.edit().putBoolean("softYieldDue", true));
        return true;
    }

    /** Durable boundary immediately before evaluating the click-capable stop-generation script. */
    public synchronized boolean markStopGenerationSubmitting() {
        if (!STOP_GENERATION_NONE.equals(stopGenerationState())
                || stopGenerationEpoch() != responseEpoch() || responseStartElapsed() <= 0L) return false;
        commit(preferences.edit().putString("stopGenerationState", STOP_GENERATION_SUBMITTING));
        return true;
    }

    public synchronized void markStopGenerationClicked() {
        commit(preferences.edit().putString("stopGenerationState", STOP_GENERATION_CLICKED));
    }

    public synchronized void markStopGenerationConfirmed() {
        commit(preferences.edit().putString("stopGenerationState", STOP_GENERATION_CONFIRMED));
    }

    public synchronized void markStopGenerationAmbiguous() {
        commit(preferences.edit().putString("stopGenerationState", STOP_GENERATION_AMBIGUOUS));
    }

    public boolean stopGenerationSubmitting() {
        return STOP_GENERATION_SUBMITTING.equals(stopGenerationState());
    }

    public boolean stopGenerationClicked() {
        return STOP_GENERATION_CLICKED.equals(stopGenerationState());
    }

    public void resetResponseTiming(String reason) {
        commit(preferences.edit().putLong("responseStartElapsed", 0L)
                .putString("responseStartBootIdentity", "")
                .putBoolean("softYieldDue", false)
                .putString("stopGenerationState", STOP_GENERATION_NONE)
                .putLong("stopGenerationEpoch", 0L));
    }

    // v0.1.14 compatibility accessors; their meanings are no longer overloaded internally.
    public String side() { return DELIVERY_WAITING_RESPONSE.equals(deliveryState()) ? monitoringSide() : deliveryTarget(); }
    public String phase() {
        if (DELIVERY_WAITING_RESPONSE.equals(deliveryState())) return PHASE_WAIT;
        if (DELIVERY_SUBMITTING.equals(deliveryState()) || DELIVERY_SUBMITTED.equals(deliveryState())) return PHASE_SUBMITTING;
        return PHASE_SUBMIT;
    }
    public String lastSignal() { return lastAcceptedSignal(); }
    public String lastStep() { return currentStep(); }
    public String lastRound() { return currentRound(); }
    public String targetUrl() {
        String side = DELIVERY_WAITING_RESPONSE.equals(deliveryState()) ? monitoringSide() : deliveryTarget();
        return SIDE_WORK.equals(side) ? runWorkUrl() : runChatUrl();
    }
    public String sideLabel() { return sideLabel(side()); }

    public static boolean isTerminalSignal(OrchestrationSignal.Type type) {
        return type == OrchestrationSignal.Type.DONE || type == OrchestrationSignal.Type.PAUSE
                || type == OrchestrationSignal.Type.ABORTED;
    }

    public static boolean isAllowedRelayUrl(String url) {
        if (!TargetParser.isSupported(url) || TargetParser.conversationId(url) == null) return false;
        URI uri = URI.create(url);
        return uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    /** Past Job IDs are audit history, not a UI gate; signal/delivery state remains the duplicate boundary. */
    public String newRunError(String candidateJobId) {
        return "";
    }

    public static String sideLabel(String side) { return SIDE_WORK.equals(side) ? "Work" : "일반 Chat"; }

    public static String statusSummary(boolean active, boolean paused, boolean terminal,
                                      boolean waitingForUser, String deliveryState,
                                      OrchestrationSignal.Type terminalSignal) {
        if (terminal) {
            if (terminalSignal == OrchestrationSignal.Type.DONE) return "완료";
            if (terminalSignal == OrchestrationSignal.Type.ABORTED) return "중단됨";
            if (terminalSignal == OrchestrationSignal.Type.PAUSE) return "일시정지";
        }
        if (waitingForUser) return "사용자 조치 필요";
        if (DELIVERY_FAILED.equals(deliveryState) || DELIVERY_AMBIGUOUS.equals(deliveryState)) return "오류";
        if (paused) return "일시정지";
        if (active) return "진행 중";
        return "대기 중";
    }

    public static String promptFor(OrchestrationSignal signal) {
        if (signal.type == OrchestrationSignal.Type.SEND_WORK)
            return "[AUTOMATION_WORK_STEP " + signal.jobId + " " + signal.step + " " + signal.round + "]";
        if (signal.type == OrchestrationSignal.Type.SEND_CHAT)
            return "[AUTOMATION_CHAT_REVIEW " + signal.jobId + " " + signal.step + " " + signal.round + "]";
        if (signal.type == OrchestrationSignal.Type.CONTINUE_SAME)
            return sameSidePrompt(signal.jobId, signal.step, signal.round);
        throw new IllegalArgumentException("전환 신호가 아닙니다.");
    }

    public static String sameSidePrompt(String jobId, String step, String round) {
        if (!JOB_ID.matcher(clean(jobId)).matches() || !clean(step).matches("S\\d{3}")
                || !clean(round).matches("R\\d{3}"))
            throw new IllegalArgumentException("Job/Step/Round 형식이 올바르지 않습니다.");
        return "[AUTOMATION_CONTINUE_SAME " + clean(jobId) + " " + clean(step) + " " + clean(round) + "]";
    }

    public static String userResolvedPrompt(String jobId, String actionId) {
        if (!JOB_ID.matcher(clean(jobId)).matches() || !JOB_ID.matcher(clean(actionId)).matches())
            throw new IllegalArgumentException("Job/Action ID 형식이 올바르지 않습니다.");
        return "[AUTOMATION_USER_RESOLVED " + clean(jobId) + " " + clean(actionId) + "]";
    }

    public String resumeBlockReason() {
        if (terminal()) return "이미 완료·일시정지·중단된 terminal 상태라 자동 재개할 수 없습니다.";
        if (waitingForUser()) return "사용자 조치 대기 상태입니다. ‘처리 완료’를 눌러 일반 Chat 재검증을 요청해 주세요.";
        return "현재 영속 상태에서 중계를 재개할 수 없습니다.";
    }

    public String userActionBlockReason() {
        if (terminal()) return "이미 terminal 상태입니다.";
        if (!waitingForUser()) return "현재 사용자 조치 대기 상태가 아닙니다.";
        return "사용자 조치 상태를 복구하지 못했습니다.";
    }

    private String currentActionFor(String state) {
        if (DELIVERY_WAITING_RESPONSE.equals(state)) return sideLabel(monitoringSide()) + " 응답 대기 중";
        if (DELIVERY_SUBMITTED.equals(state)) return sideLabel(deliveryTarget()) + " 제출 결과 확인 중";
        if (DELIVERY_SUBMITTING.equals(state)) return sideLabel(deliveryTarget()) + " 전송 결과 확인 중 · 자동 재전송 없음";
        return sideLabel(deliveryTarget()) + "로 프롬프트 전송 준비";
    }

    private static String expectedFor(String side, String step, String round) {
        String sequence = step == null || step.isEmpty() ? "S001/R001" : step + "/" + round;
        return SIDE_WORK.equals(side) ? "Work의 AR_SEND_CHAT, AR_CONTINUE_SAME " + sequence + " 대기"
                : "일반 Chat의 AR_SEND_WORK, AR_CONTINUE_SAME, AR_USER_ACTION_REQUIRED 또는 terminal 신호 대기 · 현재 " + sequence;
    }

    private void migrateLegacyState() {
        int currentSchema = preferences.getInt("schemaVersion", 0);
        if (currentSchema >= SCHEMA_VERSION) return;
        if (currentSchema >= 2) {
            String state = preferences.getString("deliveryState", DELIVERY_PENDING);
            String existingDelivered = preferences.getString("lastDeliveredPrompt", "");
            String preservedPrompt = preferences.getString("stampedPrompt", "");
            if (preservedPrompt.isEmpty() && !DELIVERY_PENDING.equals(state)) {
                preservedPrompt = existingDelivered.isEmpty()
                        ? preferences.getString("pendingPrompt", "") : existingDelivered;
            }
            commit(preferences.edit().putInt("schemaVersion", SCHEMA_VERSION)
                    .putString("stampedPrompt", preservedPrompt)
                    .putLong("responseEpoch", preferences.getLong("responseEpoch", 0L))
                    .putLong("responseStartElapsed", preferences.getLong("responseStartElapsed", 0L))
                    .putString("responseStartBootIdentity", preferences.getString("responseStartBootIdentity", ""))
                    .putLong("lastSignalResponseEpoch", preferences.getLong("lastSignalResponseEpoch", -1L))
                    .putLong("continuationEpoch", preferences.getLong("continuationEpoch", 0L))
                    .putBoolean("softYieldDue", preferences.getBoolean("softYieldDue", false))
                    .putString("stopGenerationState", preferences.getString("stopGenerationState", STOP_GENERATION_NONE))
                    .putLong("stopGenerationEpoch", preferences.getLong("stopGenerationEpoch", 0L))
                    .putBoolean("reconciling", false).putString("reconciliationPhase", RECONCILIATION_NONE)
                    .putString("reconciliationSide", SIDE_CHAT));
            return;
        }
        String legacySide = preferences.getString("side", SIDE_CHAT);
        String legacyPhase = preferences.getString("phase", PHASE_SUBMIT);
        String state = PHASE_WAIT.equals(legacyPhase) ? DELIVERY_WAITING_RESPONSE
                : PHASE_SUBMITTING.equals(legacyPhase) ? DELIVERY_AMBIGUOUS : DELIVERY_PENDING;
        String oldSignal = preferences.getString("lastSignal", "");
        String source = oldSignal.isEmpty() ? "" : opposite(legacySide);
        SharedPreferences.Editor edit = preferences.edit().putInt("schemaVersion", SCHEMA_VERSION)
                .putString("monitoringSide", PHASE_WAIT.equals(legacyPhase) ? legacySide : source.isEmpty() ? legacySide : source)
                .putString("deliveryTarget", legacySide).putString("deliveryState", state)
                .putString("lastSignalSource", source).putString("lastAcceptedSignal", oldSignal)
                .putString("lastDeliveryTarget", PHASE_WAIT.equals(legacyPhase) ? legacySide : "")
                .putString("lastDeliveredPrompt", PHASE_WAIT.equals(legacyPhase)
                        ? preferences.getString("pendingPrompt", "") : "")
                // A legacy WAIT_RESPONSE delivery was already sent without an envelope; preserve the
                // exact old prompt for DOM matching instead of generating a new string on resume.
                .putString("stampedPrompt", PHASE_WAIT.equals(legacyPhase)
                        ? preferences.getString("lastDeliveredPrompt", preferences.getString("pendingPrompt", "")) : "")
                .putString("lastDeliveryState", PHASE_WAIT.equals(legacyPhase) ? DELIVERY_WAITING_RESPONSE : "")
                .putString("currentStep", preferences.getString("lastStep", ""))
                .putString("currentRound", preferences.getString("lastRound", ""))
                .putString("lastErrorCode", "").putLong("errorAt", 0L)
                .putBoolean("schedulePreempted", false).putBoolean("waitingForUser", false)
                .putString("actionId", "").putLong("pollCountLong", preferences.getInt("pollCount", 0))
                .putBoolean("reconciling", false).putString("reconciliationPhase", RECONCILIATION_NONE)
                .putString("reconciliationSide", SIDE_CHAT);
        edit.putLong("responseEpoch", 0L).putLong("responseStartElapsed", 0L)
                .putString("responseStartBootIdentity", "").putLong("lastSignalResponseEpoch", -1L)
                .putLong("continuationEpoch", 0L).putBoolean("softYieldDue", false)
                .putString("stopGenerationState", STOP_GENERATION_NONE).putLong("stopGenerationEpoch", 0L);
        if (DELIVERY_AMBIGUOUS.equals(state)) {
            edit.putBoolean("paused", true).putString("lastErrorCode", "LEGACY_SUBMISSION_AMBIGUOUS")
                    .putString("error", "이전 버전의 제출 중 상태는 중복 방지를 위해 자동 복구하지 않습니다.")
                    .putLong("errorAt", System.currentTimeMillis());
        }
        if (DELIVERY_WAITING_RESPONSE.equals(state)) edit.putString("expectedSignal", expectedFor(legacySide,
                preferences.getString("lastStep", ""), preferences.getString("lastRound", "")));
        commit(edit);
    }

    private static String opposite(String side) { return SIDE_WORK.equals(side) ? SIDE_CHAT : SIDE_WORK; }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static void commit(SharedPreferences.Editor editor) {
        if (!editor.commit()) throw new IllegalStateException("오토런 중계 상태를 저장하지 못했습니다.");
    }
}
