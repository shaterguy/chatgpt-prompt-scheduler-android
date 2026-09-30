package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ProfileAuthorizationResultPolicyTest {
    private static final class Sink implements ProfileAuthorizationResultPolicy.Listener {
        String token;
        ProfileAuthorizationIssue issue;
        int deliveries;
        public void authorized(String token) { this.token=token; deliveries++; }
        public void failed(ProfileAuthorizationIssue issue) { this.issue=issue; deliveries++; }
    }
    @Test public void nonOkActivityResultStillUsesGoogleParserAsAuthority() {
        Sink sink=new Sink();int[] parses={0};
        ProfileAuthorizationResultPolicy.handle(new Object(),0,data->{parses[0]++;return "synthetic-token";},sink);
        assertEquals("Google parser must receive non-OK result data",1,parses[0]);assertEquals("synthetic-token",sink.token);assertNull(sink.issue);assertEquals(1,sink.deliveries);
    }
    @Test public void nonOkResultPreservesOnlyNumericGoogleFailure() {
        Sink sink=new Sink();
        ProfileAuthorizationResultPolicy.handle(new Object(),0,data->{throw new ProfileAuthorizationResultPolicy.GoogleStatusException(10);},sink);
        assertNull(sink.token);assertEquals(Integer.valueOf(10),sink.issue.googleStatus);assertEquals(Integer.valueOf(0),sink.issue.activityResult);
        assertTrue(sink.issue.message().contains("10"));assertEquals(1,sink.deliveries);
    }
    @Test public void googleCanceledCodeDoesNotClaimTheUserCanceled() {
        Sink sink=new Sink();
        ProfileAuthorizationResultPolicy.handle(new Object(),0,data->{throw new ProfileAuthorizationResultPolicy.GoogleStatusException(16);},sink);
        assertTrue(sink.issue.message().contains("16"));assertFalse(sink.issue.message().contains("사용자가 취소"));assertNull(sink.token);
    }
    @Test public void nullIntentDoesNotInventAnOAuthRegistrationFailure() {
        Sink sink=new Sink();int[] parses={0};
        ProfileAuthorizationResultPolicy.handle(null,0,data->{parses[0]++;return "synthetic-token";},sink);
        assertEquals(0,parses[0]);assertNull(sink.issue.googleStatus);assertEquals(ProfileAuthorizationIssue.Kind.NO_RESULT,sink.issue.kind);
        assertFalse(sink.issue.message().contains("등록"));assertNull(sink.token);
    }
    @Test public void okResultWithGoogleErrorIsStillFailure() {
        Sink sink=new Sink();
        ProfileAuthorizationResultPolicy.handle(new Object(),-1,data->{throw new ProfileAuthorizationResultPolicy.GoogleStatusException(7);},sink);
        assertNull(sink.token);assertEquals(Integer.valueOf(7),sink.issue.googleStatus);assertEquals(1,sink.deliveries);
    }
    @Test public void absentAccessTokenCannotEnableSync() {
        for(String token:new String[]{null,""}) {
            Sink sink=new Sink();ProfileAuthorizationResultPolicy.handle(new Object(),-1,data->token,sink);
            assertNull(sink.token);assertEquals(ProfileAuthorizationIssue.Kind.NO_TOKEN,sink.issue.kind);assertEquals(1,sink.deliveries);
        }
    }
    @Test public void unexpectedProviderExceptionMessageNeverEntersDiagnostics() {
        Sink sink=new Sink();
        ProfileAuthorizationResultPolicy.handle(new Object(),0,data->{throw new IllegalStateException("secret-token user@example.invalid provider-detail");},sink);
        String shown=sink.issue.message();assertFalse(shown.contains("secret-token"));assertFalse(shown.contains("example.invalid"));assertFalse(shown.contains("provider-detail"));
        assertNull(sink.issue.googleStatus);assertEquals(ProfileAuthorizationIssue.Kind.SDK_ERROR,sink.issue.kind);
    }
    @Test public void issueStoresNoProviderTextOrThrowable() {
        for(java.lang.reflect.Field field:ProfileAuthorizationIssue.class.getDeclaredFields()) {
            assertNotEquals(String.class,field.getType());assertFalse(Throwable.class.isAssignableFrom(field.getType()));
        }
    }
    @Test public void disabledAttemptCannotEnableSyncFromParserSuccess() {
        ProfileAuthorizationAttempt attemptGate=new ProfileAuthorizationAttempt();long attempt=attemptGate.begin();attemptGate.cancel();
        boolean[] enabled={false};
        ProfileAuthorizationResultPolicy.handle(new Object(),-1,data->"synthetic-token",guarded(attemptGate,attempt,enabled));
        assertFalse(enabled[0]);
    }
    @Test public void staleParserSuccessCannotFinishNewerAttempt() {
        ProfileAuthorizationAttempt attemptGate=new ProfileAuthorizationAttempt();long old=attemptGate.begin();attemptGate.cancel();long current=attemptGate.begin();
        boolean[] enabled={false};
        ProfileAuthorizationResultPolicy.handle(new Object(),-1,data->"synthetic-token",guarded(attemptGate,old,enabled));
        assertFalse(enabled[0]);assertTrue(attemptGate.current(current));
    }
    @Test public void currentOkResultCanEnableAfterSdkValidation() {
        ProfileAuthorizationAttempt attemptGate=new ProfileAuthorizationAttempt();long attempt=attemptGate.begin();boolean[] enabled={false};
        ProfileAuthorizationResultPolicy.handle(new Object(),-1,data->"synthetic-token",guarded(attemptGate,attempt,enabled));
        assertTrue(enabled[0]);assertFalse(attemptGate.current(attempt));
    }
    @Test public void androidEntryPointUsesGuardedParserBeforeEnablingSync() throws Exception {
        java.nio.file.Path file=java.nio.file.Paths.get("app/src/main/java/com/shaterguy/chatgptpromptscheduler/SettingsActivity.java");
        if(!java.nio.file.Files.exists(file))file=java.nio.file.Paths.get("..").resolve(file);
        String source=new String(java.nio.file.Files.readAllBytes(file),java.nio.charset.StandardCharsets.UTF_8);
        String result=source.substring(source.indexOf("if (requestCode >= ProfileAuthorizationSession.FIRST_REQUEST_CODE"),source.indexOf("private void authorizeProfiles()"));
        assertFalse(result.contains("resultCode == RESULT_OK"));
        assertTrue(result.indexOf("authorizationAttempt.takeConsentResult(")>=0 && result.indexOf("authorizationAttempt.takeConsentResult(")<result.indexOf("ProfileDriveAuthorization.fromIntent"));
        assertTrue(result.contains("fromIntent(this, data, resultCode, authorizationCallback(attempt))"));
        String authorized=source.substring(source.indexOf("public void authorized(String token)"),source.indexOf("public void resolution("));
        assertTrue(authorized.indexOf("authorizationAttempt.succeed(attempt,")>=0 && authorized.indexOf("authorizationAttempt.succeed(attempt,")<authorized.indexOf("profileRegistry.setSyncEnabled(true)"));
    }
    private static ProfileAuthorizationResultPolicy.Listener guarded(ProfileAuthorizationAttempt gate,long attempt,boolean[] enabled) {
        return new ProfileAuthorizationResultPolicy.Listener() {
            public void authorized(String ignored) { if(gate.finish(attempt))enabled[0]=true; }
            public void failed(ProfileAuthorizationIssue ignored) { gate.finish(attempt); }
        };
    }

}
