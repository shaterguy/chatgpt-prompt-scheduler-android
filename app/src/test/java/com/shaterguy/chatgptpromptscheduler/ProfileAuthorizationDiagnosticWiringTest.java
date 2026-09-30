package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public final class ProfileAuthorizationDiagnosticWiringTest {
    private String source(String file)throws Exception{Path path=Path.of("app/src/main/java/com/shaterguy/chatgptpromptscheduler",file);if(!Files.exists(path))path=Path.of("..").resolve(path);return new String(Files.readAllBytes(path),java.nio.charset.StandardCharsets.UTF_8);}
    @Test public void manualAndAutomaticAuthorizationUseExplicitBoundedMode()throws Exception{
        String settings=source("SettingsActivity.java");assertFalse("Normal button must not force CONSENT",settings.contains("request(this, true,"));
        assertTrue(settings.contains("request(this, authorizationAttempt.mode(),"));
        String sync=source("ProfileRegistrySync.java");assertTrue(sync.contains("ProfileAuthorizationSession.Mode.DEFAULT"));
        String auth=source("ProfileDriveAuthorization.java");assertTrue(auth.contains("mode == ProfileAuthorizationSession.Mode.PREVIOUS_CONSENT"));
        assertFalse(auth.contains("drive.file"));assertFalse(auth.contains("setAccount("));assertFalse(auth.contains("setResourceParameters("));
    }
    @Test public void diagnosticsRequireExplicitUiActionsAndNeverPersist()throws Exception{
        String settings=source("SettingsActivity.java");assertTrue("A report must be explicitly copyable",settings.contains("진단 내용 복사"));
        assertTrue(settings.contains("diagnostics.setVisibility(android.view.View.GONE)"));
        assertTrue(settings.contains("authorizationAttempt.selectMode("));assertTrue(settings.contains("setPrimaryClip("));
        String toggle=settings.substring(settings.indexOf("comparison.setOnCheckedChangeListener"),settings.indexOf("diagnostics.addView(comparison)"));assertFalse(toggle.contains("authorizeProfiles("));assertFalse(toggle.contains("ProfileDriveAuthorization.request("));
        String state=source("ProfileAuthorizationSession.java");assertFalse(state.contains("SharedPreferences"));assertFalse(state.contains("java.io"));assertFalse(state.contains("android."));assertFalse(state.contains("getAccessToken"));
        assertTrue(settings.contains("authorizationAttempt.nextRequestCode()"));assertFalse(settings.contains("putString(\"google"));
    }
}
