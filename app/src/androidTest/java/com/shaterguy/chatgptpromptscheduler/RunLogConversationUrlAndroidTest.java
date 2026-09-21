package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class RunLogConversationUrlAndroidTest {
    private static final String PREFS = "scheduler_logs";

    @Test public void conversationUrlRoundTripsAndLegacySchemaTwoRemainsReadable() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        assertTrue(preferences.edit().clear().commit());
        try {
            RunLogStore store = new RunLogStore(context);
            String targetUrl = "https://chatgpt.com/g/g-p-6a507cce80cc81919eeb9ba553b6ad9e/project";
            String conversationUrl = "https://chatgpt.com/g/g-p-6a507cce80cc81919eeb9ba553b6ad9e/c/conversation_1";
            store.append("run-new", "schedule-1", "IFRS 뉴스", "VERIFIED", "ok",
                    100L, 200L, targetUrl, conversationUrl, true, new JSONArray(), new JSONObject());

            JSONObject saved = store.find("run-new");
            assertNotNull(saved);
            assertEquals(targetUrl, saved.getString("targetUrl"));
            assertEquals(conversationUrl, saved.getString("conversationUrl"));
            JSONObject exported = store.exportAll();
            assertEquals(conversationUrl,
                    exported.getJSONArray("logs").getJSONObject(0).getString("conversationUrl"));

            JSONObject legacy = new JSONObject()
                    .put("schemaVersion", 2)
                    .put("runId", "run-legacy")
                    .put("scheduleId", "schedule-old")
                    .put("scheduleName", "legacy")
                    .put("success", true)
                    .put("status", "VERIFIED")
                    .put("detail", "")
                    .put("startedAt", 1L)
                    .put("finishedAt", 2L)
                    .put("durationMs", 1L)
                    .put("targetUrl", "https://chatgpt.com/")
                    .put("environment", new JSONObject())
                    .put("events", new JSONArray());
            assertTrue(preferences.edit()
                    .putString("logs", new JSONArray().put(legacy).toString())
                    .putString("logs_backup", "[]")
                    .commit());

            JSONObject restored = new RunLogStore(context).find("run-legacy");
            assertNotNull(restored);
            assertEquals("", restored.optString("conversationUrl", ""));
            assertEquals("https://chatgpt.com/", restored.getString("targetUrl"));
        } finally {
            preferences.edit().clear().commit();
        }
    }
}
