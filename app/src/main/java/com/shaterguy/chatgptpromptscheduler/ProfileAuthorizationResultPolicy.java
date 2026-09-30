package com.shaterguy.chatgptpromptscheduler;

/** Google’s SDK parser is authoritative; Activity resultCode alone does not discard returned data. */
final class ProfileAuthorizationResultPolicy {
    interface Parser<T> { String accessToken(T data) throws GoogleStatusException; }
    interface Listener {
        void authorized(String token);
        void failed(ProfileAuthorizationIssue issue);
    }
    static final class GoogleStatusException extends Exception {
        final int statusCode;
        GoogleStatusException(int statusCode) { this.statusCode=statusCode; }
    }

    static <T> void handle(T data, int resultCode, Parser<T> parser, Listener listener) {
        if (data == null) {
            listener.failed(new ProfileAuthorizationIssue(ProfileAuthorizationIssue.Kind.NO_RESULT,null,resultCode));
            return;
        }
        final String token;
        try { token=parser.accessToken(data); }
        catch (GoogleStatusException error) {
            listener.failed(new ProfileAuthorizationIssue(ProfileAuthorizationIssue.Kind.GOOGLE_API,error.statusCode,resultCode));
            return;
        } catch (RuntimeException ignored) {
            listener.failed(new ProfileAuthorizationIssue(ProfileAuthorizationIssue.Kind.SDK_ERROR,null,resultCode));
            return;
        }
        if (token == null || token.isEmpty()) listener.failed(new ProfileAuthorizationIssue(ProfileAuthorizationIssue.Kind.NO_TOKEN,null,resultCode));
        else listener.authorized(token);
    }
    private ProfileAuthorizationResultPolicy() {}
}
