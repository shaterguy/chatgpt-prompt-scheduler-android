package com.shaterguy.chatgptpromptscheduler;

import java.net.URI;

/** Confirms the persisted URL of a newly submitted conversation across consecutive DOM checks. */
final class SubmissionRouteConfirmation {
    private static final String UUID = "[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}";
    private String pendingUrl = "";

    void reset() {
        pendingUrl = "";
    }

    boolean observe(Schedule schedule, String actualUrl) {
        if (!isCanonicalNewConversation(schedule, actualUrl)) {
            reset();
            return false;
        }
        if (actualUrl.equals(pendingUrl)) return true;
        pendingUrl = actualUrl;
        return false;
    }

    static boolean isCanonicalNewConversation(Schedule schedule, String actualUrl) {
        if (schedule == null || actualUrl == null
                || (!"general".equals(schedule.targetType) && !"project".equals(schedule.targetType))
                || !TargetParser.matchesVerifiedConversation(schedule.targetType,
                        schedule.targetUrl, actualUrl)) return false;
        try {
            URI uri = new URI(actualUrl);
            if (!"https".equals(uri.getScheme()) || uri.getRawUserInfo() != null
                    || uri.getPort() != -1 || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) return false;
            String raw = uri.getRawPath();
            return "general".equals(schedule.targetType)
                    ? raw.matches("/c/" + UUID)
                    : raw.matches("/g/g-p-[A-Za-z0-9_-]+/c/" + UUID);
        } catch (Exception ignored) {
            return false;
        }
    }
}
