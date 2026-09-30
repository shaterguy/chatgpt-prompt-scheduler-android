package com.shaterguy.chatgptpromptscheduler;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.Assert.*;

public final class AutoOnlyProfilesTest {
    @Test public void freshInstallHasNoBundledSelectionsEvenWhenSyncIsDisabled() {
        SharedPreferences prefs = ProfileRegistrySyncTest.memoryPreferences();
        RequestProfileRegistry registry = new RequestProfileRegistry(prefs);
        assertTrue(registry.chatReasonings().isEmpty());
        assertTrue(registry.workModels().isEmpty());
        assertFalse(prefs.contains("chat_profiles"));
        assertFalse(prefs.contains("work_profiles"));
        assertTrue(registry.syncStatusText().contains("업데이트 필요"));
    }

    @Test public void legacyListsAreQuarantinedEvenWhenTheyContainCanonicalMetadata() throws Exception {
        SharedPreferences prefs = ProfileRegistrySyncTest.memoryPreferences();
        JSONObject portable = new JSONObject(ProfileRegistrySyncTest.document("manual", "high"));
        JSONObject entry = portable.getJSONArray("profiles").getJSONObject(0);
        JSONObject legacy = new JSONObject().put("mode", "WORK").put("model", "manual")
                .put("reasoning", "high").put("operations", entry.getJSONArray("operations"))
                .put("fingerprint", entry.getString("fingerprint")).put("builtIn", false);
        String original = new JSONArray().put(legacy).toString();
        prefs.edit().putString("work_profiles", original).putString("chat_profiles", "[]")
                .putString("config_json", "untouched-schedule-and-prompt-fixture").commit();
        RequestProfileRegistry registry = new RequestProfileRegistry(prefs);
        assertTrue(registry.workModels().isEmpty());
        assertTrue(registry.chatReasonings().isEmpty());
        assertEquals(original, prefs.getString("work_profiles", null));
        assertEquals("untouched-schedule-and-prompt-fixture", prefs.getString("config_json", null));
        registry.setSyncEnabled(true);
        registry.recordSyncFailure(RequestProfileEngine.Mode.WORK, "NETWORK");
        registry.setSyncEnabled(false);
        assertTrue(new RequestProfileRegistry(prefs).workModels().isEmpty());
    }

    @Test public void validatedAutomaticDataRetainsBuiltInFlagAndSurvivesFailureAndRecreation() throws Exception {
        SharedPreferences prefs = ProfileRegistrySyncTest.memoryPreferences();
        JSONObject canonical = new JSONObject(ProfileRegistrySyncTest.document("automatic", "high"));
        canonical.getJSONArray("profiles").getJSONObject(0).put("builtIn", true);
        prefs.edit().putString("canonical_raw_WORK", canonical.toString())
                .putString("canonical_version_WORK", "existing-v3").putString("work_profiles", "legacy-quarantine").commit();
        RequestProfileRegistry registry = new RequestProfileRegistry(prefs);
        assertEquals(List.of("automatic"), registry.workModels());
        registry.recordSyncFailure(RequestProfileEngine.Mode.WORK, "NETWORK");
        registry.setSyncEnabled(false);
        RequestProfileRegistry reopened = new RequestProfileRegistry(prefs);
        assertEquals(List.of("automatic"), reopened.workModels());
        assertEquals("existing-v3", reopened.sourceVersion(RequestProfileEngine.Mode.WORK));
        assertEquals("legacy-quarantine", prefs.getString("work_profiles", null));
        assertTrue(reopened.chatReasonings().isEmpty());
    }

    @Test public void corruptAutomaticCacheCannotReviveOldManualList() throws Exception {
        SharedPreferences prefs = ProfileRegistrySyncTest.memoryPreferences();
        prefs.edit().putString("canonical_raw_WORK", "not-json").putString("work_profiles", "[]").commit();
        RequestProfileRegistry registry = new RequestProfileRegistry(prefs);
        assertTrue(registry.workModels().isEmpty());
        assertTrue(registry.syncStatusText().contains("업데이트 필요"));
    }

    @Test public void automaticReplacementNeverRewritesLegacyQuarantine() throws Exception {
        SharedPreferences prefs = ProfileRegistrySyncTest.memoryPreferences();
        prefs.edit().putString("work_profiles", "quarantined-original").commit();
        RequestProfileRegistry registry = new RequestProfileRegistry(prefs);
        registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK,
                ProfileRegistrySyncTest.document("automatic", "high"), "v4");
        assertEquals("quarantined-original", prefs.getString("work_profiles", null));
        assertEquals(List.of("automatic"), registry.workModels());
    }

    @Test public void unattachedExplicitSchedulesNeverUseBundledFallbacks() {
        Schedule schedule = new Schedule(); schedule.chatReasoning = "high";
        assertThrows(IllegalArgumentException.class, () -> RequestProfileEngine.forSchedule(schedule));
        schedule.experience = "work"; schedule.workModel = "sol"; schedule.reasoningEffort = "high";
        assertThrows(IllegalArgumentException.class, () -> RequestProfileEngine.forSchedule(schedule));
        assertThrows(IllegalArgumentException.class, () -> RequestProfileEngine.plan(
                new RequestProfileEngine.TargetProfile(RequestProfileEngine.Mode.WORK, "sol", "high")));
    }

    @Test public void explicitWorkModelCannotSilentlyBecomeNativeInheritance() {
        Schedule schedule = new Schedule(); schedule.experience = "work";
        schedule.workModel = "sol"; schedule.reasoningEffort = "inherit";
        assertThrows(IllegalArgumentException.class, () -> RequestProfileEngine.forSchedule(schedule));
        assertTrue(RequestProfileScript.activate(schedule).contains("REQUEST_PROFILE_INVALID"));
        schedule.workModel = "inherit"; schedule.reasoningEffort = "high";
        assertThrows(IllegalArgumentException.class, () -> RequestProfileEngine.forSchedule(schedule));
        schedule.reasoningEffort = "inherit";
        assertNull(RequestProfileEngine.forSchedule(schedule));
        schedule.targetType = "existing"; schedule.workModel = "old-manual";
        assertNull(RequestProfileEngine.forSchedule(schedule));
    }

    @Test public void manualIngressAndCaptureEntryPointsAreRemoved() throws Exception {
        for (java.lang.reflect.Method method : RequestProfileRegistry.class.getDeclaredMethods()) {
            assertNotEquals("importChat", method.getName());
            assertNotEquals("importWork", method.getName());
        }
        String settings = source("SettingsActivity.java");
        for (String removed : List.of("startCapture(", "pickProfile(", "readBounded(",
                "REQUEST_CHAT_PROFILE", "REQUEST_WORK_PROFILE", "CaptureDialog")) assertFalse(removed, settings.contains(removed));
        assertFalse(Files.exists(sourcePath("RequestProfileCaptureDialog.java")));
    }

    @Test public void pickerCannotAppendOldSelectionsOrNormalizeMissingChoiceToDefault() throws Exception {
        String editor = source("ScheduleEditorActivity.java");
        assertFalse(editor.contains("chatReasoningValues.add(current)"));
        assertFalse(editor.contains("workModelValues.add(current)"));
        assertFalse(editor.contains("workReasoningValues.add(current)"));
        assertFalse(editor.contains("Schedule.normalizedChatReasoning(\"chat\", requested)"));
        assertFalse(editor.contains("Schedule.normalizedWorkModel(\"work\", requested)"));
        assertFalse(editor.contains("Schedule.normalizedReasoningEffort(\"work\", requested)"));
    }

    @Test public void scriptContainsNoBuiltInModelSetters() {
        String script = RequestProfileScript.documentStartScript();
        assertFalse(script.contains("setChatReasoning"));
        assertFalse(script.contains("setWorkModel"));
        assertFalse(script.contains("setWorkReasoning"));
        assertFalse(script.contains("gpt-5.6-sol-wm"));
        assertTrue(script.contains("configure"));
    }

    private static Path sourcePath(String file) {
        Path path = Path.of("app/src/main/java/com/shaterguy/chatgptpromptscheduler", file);
        return Files.exists(path.getParent()) ? path : Path.of("..").resolve(path);
    }
    private static String source(String file) throws Exception { return new String(Files.readAllBytes(sourcePath(file)), java.nio.charset.StandardCharsets.UTF_8); }
}
