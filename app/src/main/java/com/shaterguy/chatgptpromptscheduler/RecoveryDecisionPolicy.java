package com.shaterguy.chatgptpromptscheduler;

/**
 * Shared, side-effect-free recovery vocabulary and decision policy used by
 * scheduled execution and the Protocol 3 relay.
 */
public final class RecoveryDecisionPolicy {
    public enum TargetIntent {
        GLOBAL_NEW_CHAT,
        PROJECT_NEW_CHAT,
        FIXED_CONVERSATION,
        AUTORUN_CHAT_FIXED,
        AUTORUN_WORK_FIXED
    }

    public enum ObservedLocation {
        EXPECTED_CONVERSATION,
        PROJECT_NEW_CHAT,
        PROJECT_ROOT,
        GLOBAL_NEW_CHAT,
        DIFFERENT_CONVERSATION,
        UNKNOWN
    }

    public enum NetworkState { OK, RATE_LIMIT, TRANSIENT_ERROR }

    public enum UiReadiness {
        READY,
        LOADING,
        COMPOSER_MISSING,
        MODE_PENDING,
        MODEL_PENDING,
        REASONING_PENDING
    }

    public enum SendState { NOT_STARTED, SUBMITTING, DOM_CONFIRMED, AMBIGUOUS }

    public enum Decision {
        CONTINUE,
        RATE_LIMIT_WAIT,
        UI_WAIT,
        RESTORE_TARGET,
        RECONCILE_SEND,
        TARGET_CHANGED,
        FAIL
    }

    private RecoveryDecisionPolicy() {}

    public static TargetIntent targetIntent(String targetType, boolean autorun, String side) {
        if (autorun) {
            return "WORK".equalsIgnoreCase(side) ? TargetIntent.AUTORUN_WORK_FIXED
                    : TargetIntent.AUTORUN_CHAT_FIXED;
        }
        if ("project".equalsIgnoreCase(targetType)) return TargetIntent.PROJECT_NEW_CHAT;
        if ("general".equalsIgnoreCase(targetType)) return TargetIntent.GLOBAL_NEW_CHAT;
        return TargetIntent.FIXED_CONVERSATION;
    }

    public static boolean isFixed(TargetIntent intent) {
        return intent == TargetIntent.FIXED_CONVERSATION
                || intent == TargetIntent.AUTORUN_CHAT_FIXED
                || intent == TargetIntent.AUTORUN_WORK_FIXED;
    }

    public static boolean isRateLimitHttp(int statusCode) {
        return statusCode == 429;
    }

    /** Android WebViewClient.ERROR_TOO_MANY_REQUESTS is -15 on supported SDKs. */
    public static boolean isRateLimitWebViewError(int errorCode) {
        return errorCode == -15;
    }

    public static boolean isUiWaitStatus(String status) {
        return "UI_WAIT".equals(status) || "LOADING".equals(status)
                || "COMPOSER_WAIT".equals(status) || "MODE_WAIT".equals(status)
                || "MODEL_WAIT".equals(status) || "REASONING_WAIT".equals(status);
    }

    public static Decision decide(TargetIntent intent, ObservedLocation location,
                                  NetworkState network, UiReadiness readiness,
                                  SendState sendState) {
        if (network == NetworkState.RATE_LIMIT) return Decision.RATE_LIMIT_WAIT;
        if (location == ObservedLocation.DIFFERENT_CONVERSATION) return Decision.TARGET_CHANGED;
        if (isFixed(intent) && location != ObservedLocation.EXPECTED_CONVERSATION) {
            return sendState == SendState.SUBMITTING || sendState == SendState.DOM_CONFIRMED
                    || sendState == SendState.AMBIGUOUS
                    ? Decision.RECONCILE_SEND : Decision.RESTORE_TARGET;
        }
        if (readiness != UiReadiness.READY) return Decision.UI_WAIT;
        if (sendState == SendState.SUBMITTING || sendState == SendState.AMBIGUOUS)
            return Decision.RECONCILE_SEND;
        return Decision.CONTINUE;
    }

    public static ObservedLocation classify(String targetType, String expectedUrl, String actualUrl) {
        if ("existing".equalsIgnoreCase(targetType)) {
            TargetParser.ConversationTargetState state =
                    TargetParser.classifyConversationTarget(expectedUrl, actualUrl);
            if (state == TargetParser.ConversationTargetState.MATCH)
                return ObservedLocation.EXPECTED_CONVERSATION;
            if (state == TargetParser.ConversationTargetState.DIFFERENT)
                return TargetParser.conversationId(actualUrl) == null
                        ? classifyTransientLocation(expectedUrl, actualUrl)
                        : ObservedLocation.DIFFERENT_CONVERSATION;
            return classifyTransientLocation(expectedUrl, actualUrl);
        }
        if ("project".equalsIgnoreCase(targetType)) {
            if (TargetParser.matchesTarget("project", expectedUrl, actualUrl)) {
                return TargetParser.isProjectHome(actualUrl)
                        ? ObservedLocation.PROJECT_ROOT : ObservedLocation.PROJECT_NEW_CHAT;
            }
            return TargetParser.conversationId(actualUrl) == null
                    ? classifyTransientLocation(expectedUrl, actualUrl)
                    : ObservedLocation.DIFFERENT_CONVERSATION;
        }
        if (TargetParser.matchesTarget("general", expectedUrl, actualUrl))
            return ObservedLocation.GLOBAL_NEW_CHAT;
        return TargetParser.conversationId(actualUrl) == null
                ? classifyTransientLocation(expectedUrl, actualUrl)
                : ObservedLocation.DIFFERENT_CONVERSATION;
    }

    private static ObservedLocation classifyTransientLocation(String expectedUrl, String actualUrl) {
        if (TargetParser.isProjectHome(actualUrl)) return ObservedLocation.PROJECT_ROOT;
        if (TargetParser.isProjectNewChatSurface(expectedUrl, actualUrl))
            return ObservedLocation.PROJECT_NEW_CHAT;
        if (TargetParser.isGlobalNewChatSurface(actualUrl)) return ObservedLocation.GLOBAL_NEW_CHAT;
        return ObservedLocation.UNKNOWN;
    }
}
