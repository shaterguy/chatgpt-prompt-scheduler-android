package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ProfileAuthorizationSessionTest {
    @Test public void defaultAndModeSelectionNeverStartAuthorization() {
        ProfileAuthorizationSession session=new ProfileAuthorizationSession(3000);
        assertEquals(ProfileAuthorizationSession.Mode.DEFAULT,session.mode());
        assertEquals(0,session.size());
        session.selectMode(ProfileAuthorizationSession.Mode.PREVIOUS_CONSENT,20);
        assertEquals(0,session.size());assertEquals(ProfileAuthorizationSession.Mode.PREVIOUS_CONSENT,session.mode());
    }
    @Test public void modeChangeRejectsDelayedSuccessAndResolution() {
        ProfileAuthorizationSession session=new ProfileAuthorizationSession(3000);long old=session.begin(100);
        session.selectMode(ProfileAuthorizationSession.Mode.PREVIOUS_CONSENT,110);
        assertFalse(session.succeed(old,120));assertEquals(0,session.launchConsent(old,120));
        assertTrue(session.report(120).contains("outcome=MODE_CHANGED"));
        long current=session.begin(130);assertTrue(current>old);assertTrue(session.succeed(current,140));
    }
    @Test public void disableRejectsSuccessFailureAndResolution() {
        ProfileAuthorizationSession session=new ProfileAuthorizationSession(3000);long old=session.begin(100);
        session.cancel(ProfileAuthorizationSession.Outcome.DISABLED,110);
        assertFalse(session.succeed(old,120));assertFalse(session.fail(old,issue(8),120));assertEquals(0,session.launchConsent(old,120));
        assertTrue(session.report(120).contains("outcome=DISABLED"));
    }
    @Test public void destroyedAndRecreatedSessionRejectsOldResultEvenAfterNewLaunch() {
        ProfileAuthorizationSession old=new ProfileAuthorizationSession(3000);long first=old.begin(100);int firstCode=old.launchConsent(first,110);
        int nextCode=old.nextRequestCode();old.cancel(ProfileAuthorizationSession.Outcome.DESTROYED,120);
        ProfileAuthorizationSession fresh=new ProfileAuthorizationSession(nextCode);long current=fresh.begin(200);int currentCode=fresh.launchConsent(current,210);
        assertNotEquals(firstCode,currentCode);assertEquals(0,fresh.takeConsentResult(firstCode,-1,true,220));
        assertEquals(current,fresh.takeConsentResult(currentCode,-1,true,230));assertTrue(fresh.succeed(current,240));
    }
    @Test public void stateSavedBeforeDelayedResolutionCannotReuseItsRoutingCode() {
        ProfileAuthorizationSession old=new ProfileAuthorizationSession(3000);long first=old.begin(100);
        int savedBeforeResolution=old.nextRequestCode();int firstCode=old.launchConsent(first,120);
        old.cancel(ProfileAuthorizationSession.Outcome.DESTROYED,130);
        ProfileAuthorizationSession fresh=new ProfileAuthorizationSession(savedBeforeResolution);
        long current=fresh.begin(200);int currentCode=fresh.launchConsent(current,220);
        assertNotEquals(firstCode,currentCode);assertEquals(0,fresh.takeConsentResult(firstCode,-1,true,230));
        assertEquals(current,fresh.takeConsentResult(currentCode,-1,true,240));assertTrue(fresh.succeed(current,250));
    }
    @Test public void staleFailureCannotAlterNewAttempt() {
        ProfileAuthorizationSession session=new ProfileAuthorizationSession(3000);long old=session.begin(100);session.cancel(ProfileAuthorizationSession.Outcome.DISABLED,110);
        long current=session.begin(120);assertFalse(session.fail(old,issue(8),130));
        assertTrue(session.current(current));assertFalse(session.report(130).contains("google=8"));assertTrue(session.succeed(current,140));
    }
    @Test public void consentFailureRecordsOnlyNumericResultAndOrigin() {
        ProfileAuthorizationSession session=new ProfileAuthorizationSession(3000);long attempt=session.begin(100);int request=session.launchConsent(attempt,140);
        assertEquals(attempt,session.takeConsentResult(request,0,true,190));assertTrue(session.fail(attempt,issue(8),200));
        String report=session.report(900);assertTrue(report.contains("phase=RESOLUTION_RESULT"));assertTrue(report.contains("outcome=GOOGLE_API"));
        assertTrue(report.contains("google=8"));assertTrue(report.contains("activity=0"));assertTrue(report.contains("data=true"));assertTrue(report.contains("duration_ms=100"));
    }
    @Test public void directResultDoesNotAssertFreshOrCachedGrant() {
        ProfileAuthorizationSession session=new ProfileAuthorizationSession(3000);long attempt=session.begin(100);assertTrue(session.succeed(attempt,120));
        String report=session.report(130);assertTrue(report.contains("phase=DIRECT_RESULT"));assertTrue(report.contains("grant_source=UNKNOWN"));
        assertFalse(report.contains("fresh=true"));assertFalse(report.contains("cached=true"));
    }
    @Test public void fourAttemptBoundAndDuplicateTapDoNotLoseCurrentAttempt() {
        ProfileAuthorizationSession session=new ProfileAuthorizationSession(3000);
        for(int i=0;i<6;i++){long attempt=session.begin(i*100);assertEquals(0,session.begin(i*100+1));assertTrue(session.succeed(attempt,i*100+10));}
        assertEquals(4,session.size());String report=session.report(1000);assertFalse(report.contains("attempt=1 "));assertFalse(report.contains("attempt=2 "));assertTrue(report.contains("attempt=6 "));
    }
    @Test public void resultIsConsumedOnceAndCannotBindCancelledAttempt() {
        ProfileAuthorizationSession session=new ProfileAuthorizationSession(3000);long old=session.begin(100);int oldCode=session.launchConsent(old,110);
        session.selectMode(ProfileAuthorizationSession.Mode.PREVIOUS_CONSENT,120);long current=session.begin(130);int newCode=session.launchConsent(current,140);
        assertEquals(0,session.takeConsentResult(oldCode,-1,true,150));assertEquals(current,session.takeConsentResult(newCode,-1,true,160));
        assertEquals(0,session.takeConsentResult(newCode,-1,true,170));assertTrue(session.succeed(current,180));
    }
    @Test public void diagnosticsStateCannotStoreOpaqueProviderObjectsOrText() {
        for(java.lang.reflect.Field field:ProfileAuthorizationSession.Record.class.getDeclaredFields()){
            Class<?> type=field.getType();assertTrue(type.isPrimitive()||type.isEnum()||type==Integer.class||type==Boolean.class);
        }
        for(java.lang.reflect.Method method:ProfileAuthorizationSession.class.getDeclaredMethods())for(Class<?> type:method.getParameterTypes()){
            assertNotEquals(String.class,type);assertFalse(Throwable.class.isAssignableFrom(type));
        }
    }
    @Test public void pendingAndInvalidClockRemainBoundedAndNonnegative() {
        ProfileAuthorizationSession session=new ProfileAuthorizationSession(3000);session.begin(100);
        assertTrue(session.report(50).contains("duration_ms=0"));assertTrue(session.report(200).contains("outcome=PENDING"));assertTrue(session.report(200).length()<4096);
    }
    @Test public void exhaustedRequestCodesFailClosedWithoutReusingOldCodes() {
        ProfileAuthorizationSession session=new ProfileAuthorizationSession(65536);long attempt=session.begin(100);
        assertEquals(0,session.launchConsent(attempt,110));assertTrue(session.fail(attempt,new ProfileAuthorizationIssue(ProfileAuthorizationIssue.Kind.LAUNCH_ERROR,null,null),120));
        assertTrue(session.report(130).contains("outcome=LAUNCH_ERROR"));
    }
    @Test public void googleMetadataIsNumericAndStaleCallbacksCannotChangeIt() {
        ProfileAuthorizationSession session=new ProfileAuthorizationSession(3000);long attempt=session.begin(100);
        session.observeGoogle(attempt,10001,0);session.observeGoogle(attempt,10002,2);assertTrue(session.fail(attempt,issue(8),200));
        session.observeGoogle(attempt,99999,99);String report=session.report(300);
        assertTrue(report.contains("gms_start=10001 gms_end=10002 availability_start=0 availability_end=2"));assertFalse(report.contains("99999"));
    }
    private static ProfileAuthorizationIssue issue(int code){return new ProfileAuthorizationIssue(ProfileAuthorizationIssue.Kind.GOOGLE_API,code,null);}
}
