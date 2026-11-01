package com.shaterguy.chatgptpromptscheduler;

/** Presentation only: never converts persisted model or schedule values. */
public final class UiDisplayPolicy {
    private UiDisplayPolicy() {}

    public static int actionColumns(int widthDp, float fontScale, int actionCount) {
        if (actionCount < 2 || widthDp < 360 || fontScale >= 1.25f) return 1;
        int max = widthDp >= 600 ? 4 : 2;
        int fitting = Math.max(1, (int) ((widthDp - 72) / (140 * Math.max(1f, fontScale))));
        return Math.max(1, Math.min(actionCount, Math.min(max, fitting)));
    }

    public static String label(String value) {
        if (value == null) return "";
        return switch (value) {
            case "general" -> "일반 새 대화";
            case "project" -> "프로젝트";
            case "existing" -> "기존 대화";
            case "chat" -> "일반 Chat";
            case "work" -> "Work";
            case "once" -> "한 번";
            case "daily" -> "매일";
            case "weekly" -> "매주";
            case "interval" -> "분 간격";
            default -> value;
        };
    }

    public static String status(String value) {
        if (value == null || value.isBlank()) return "아직 실행하지 않음";
        return switch (value) {
            case "NEVER" -> "아직 실행하지 않음";
            case "READY", "NEW", "IDLE" -> "실행 대기";
            case "QUEUED", "PENDING" -> "대기열에 등록";
            case "RUNNING" -> "실행 중";
            case "VERIFIED", "SUCCESS", "DONE" -> "완료";
            case "FAILED", "FAILURE", "ERROR" -> "실패";
            case "AUTH_REQUIRED" -> "로그인 필요";
            case "TIMEOUT", "TIMED_OUT" -> "시간 초과";
            case "CANCELLED" -> "취소됨";
            case "SKIPPED" -> "실행 건너뜀";
            default -> value;
        };
    }
}
