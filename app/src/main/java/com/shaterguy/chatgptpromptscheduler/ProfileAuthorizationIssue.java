package com.shaterguy.chatgptpromptscheduler;

/** Diagnostic values contain categories and numbers only, never provider messages, Intents or tokens. */
final class ProfileAuthorizationIssue {
    enum Kind { GOOGLE_API, NO_RESULT, NO_TOKEN, SDK_ERROR, LAUNCH_ERROR }
    final Kind kind;
    final Integer googleStatus;
    final Integer activityResult;

    ProfileAuthorizationIssue(Kind kind, Integer googleStatus, Integer activityResult) {
        this.kind=kind;this.googleStatus=googleStatus;this.activityResult=activityResult;
    }

    String message() {
        if (googleStatus != null) {
            String advice = googleStatus == 10 ? "앱의 Google OAuth 등록 설정을 확인해 주세요."
                    : googleStatus == 7 ? "네트워크 연결을 확인해 주세요."
                    : "Google 인증 요청을 완료하지 못했습니다.";
            return advice + " (Google 오류 " + googleStatus + ")";
        }
        if (kind == Kind.NO_RESULT)
            return "Google에서 인증 결과를 전달하지 않았습니다. 다시 시도해 주세요. (결과 " + activityResult + ")";
        if (kind == Kind.NO_TOKEN) return "Google 인증 응답에서 접근 토큰을 확인하지 못했습니다. 다시 로그인해 주세요.";
        if (kind == Kind.LAUNCH_ERROR) return "Google 인증 화면을 열지 못했습니다. Google Play 서비스를 확인해 주세요.";
        return "Google 인증 응답을 처리하지 못했습니다. Google Play 서비스를 확인해 주세요.";
    }
}
