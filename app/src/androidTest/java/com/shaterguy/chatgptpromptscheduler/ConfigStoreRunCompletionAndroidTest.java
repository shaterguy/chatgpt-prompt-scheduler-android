package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class ConfigStoreRunCompletionAndroidTest {
    @Test public void manualCompletionKeepsUserToggleAndEditedFields() {
        Context context = ApplicationProvider.getApplicationContext();
        ConfigStore store = new ConfigStore(context);
        Schedule schedule = new Schedule();
        schedule.enabled = false;
        schedule.recurrence = "daily";
        store.saveSchedule(schedule);
        try {
            Schedule staleInFlight = store.findSchedule(schedule.id);
            assertNotNull(staleInFlight);
            assertFalse(staleInFlight.enabled);

            Schedule edited = store.findSchedule(schedule.id);
            assertNotNull(edited);
            edited.enabled = true;
            edited.prompt = "changed while manual run is active";
            store.saveSchedule(edited);
            Schedule completed = store.recordRunCompletion(staleInFlight.id, 1234L, "VERIFIED", false);

            assertNotNull(completed);
            Schedule persisted = store.findSchedule(schedule.id);
            assertNotNull(persisted);
            assertTrue(persisted.enabled);
            assertEquals("changed while manual run is active", persisted.prompt);
            assertEquals(1234L, persisted.lastRunAt);
            assertEquals("VERIFIED", persisted.lastStatus);
        } finally {
            store.deleteSchedule(schedule.id);
        }
    }

    @Test public void deletingScheduleDuringRunDoesNotRecreateIt() {
        Context context = ApplicationProvider.getApplicationContext();
        ConfigStore store = new ConfigStore(context);
        Schedule schedule = new Schedule();
        store.saveSchedule(schedule);
        store.deleteSchedule(schedule.id);
        assertNull(store.recordRunCompletion(schedule.id, 4321L, "VERIFIED", false));
        assertNull(store.findSchedule(schedule.id));
    }

    @Test public void portableSchemaOneRetainsOptionalRecurrenceFieldsAndMigratesLegacy() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        ConfigStore store = new ConfigStore(context);
        org.json.JSONObject backup = new org.json.JSONObject(store.exportPortable().toString());
        try {
            Schedule schedule = new Schedule();
            schedule.intervalMinutes = 120;
            schedule.intervalUnit = "hours";
            schedule.dailyIntervalDays = 3;
            schedule.lastRunAt = 1234L;
            schedule.lastOccurrenceAt = 1000L;
            org.json.JSONObject portable = ConfigStore.defaultRoot();
            portable.put("schedules", new org.json.JSONArray().put(schedule.toJson()));
            assertEquals(1, store.importPortable(portable));
            org.json.JSONObject exported = store.exportPortable();
            assertEquals(1, exported.getInt("schemaVersion"));
            assertEquals(1, store.importPortable(new org.json.JSONObject(exported.toString())));
            Schedule restored = new ConfigStore(context).findSchedule(schedule.id);
            assertEquals("hours", restored.intervalUnit);
            assertEquals(120, restored.intervalMinutes);
            assertEquals(3, restored.dailyIntervalDays);
            assertEquals(1000L, restored.lastOccurrenceAt);

            org.json.JSONObject legacy = schedule.toJson();
            legacy.put("intervalMinutes", 90);
            legacy.remove("intervalUnit");
            legacy.remove("dailyIntervalDays");
            legacy.remove("lastOccurrenceAt");
            portable.put("schedules", new org.json.JSONArray().put(legacy));
            assertEquals(1, store.importPortable(portable));
            restored = new ConfigStore(context).findSchedule(schedule.id);
            assertEquals("minutes", restored.intervalUnit);
            assertEquals(90, restored.intervalMinutes);
            assertEquals(1, restored.dailyIntervalDays);
            assertEquals(1234L, restored.lastOccurrenceAt);
        } finally {
            store.importPortable(backup);
        }
    }

    @Test public void editedScheduleMergesCompletionFromAnotherStoreInstance() {
        Context context = ApplicationProvider.getApplicationContext();
        ConfigStore editorStore = new ConfigStore(context);
        ConfigStore executionStore = new ConfigStore(context);
        Schedule initial = new Schedule();
        initial.enabled = false;
        editorStore.saveSchedule(initial);
        try {
            Schedule staleEditor = editorStore.findSchedule(initial.id);
            Schedule enabled = executionStore.findSchedule(initial.id);
            enabled.enabled = true;
            executionStore.saveSchedule(enabled);
            long finished = java.time.LocalDateTime.parse("2026-11-17T10:10")
                    .atZone(Recurrence.KST).toInstant().toEpochMilli();
            executionStore.recordRunCompletion(initial.id, finished, "TIMEOUT", false, finished - 60_000L);
            staleEditor.recurrence = "interval";
            staleEditor.intervalMinutes = 120;
            staleEditor.intervalUnit = "hours";
            editorStore.saveEditedSchedule(staleEditor, false, finished + 30 * 60_000L, java.util.Set.of());
            Schedule persisted = new ConfigStore(context).findSchedule(initial.id);
            assertTrue(persisted.enabled);
            assertEquals(finished, persisted.lastRunAt);
            assertEquals(finished - 60_000L, persisted.lastOccurrenceAt);
            assertEquals("TIMEOUT", persisted.lastStatus);
            assertEquals(finished + 120 * 60_000L, persisted.nextRunAt);
            assertEquals("hours", persisted.intervalUnit);
        } finally {
            editorStore.deleteSchedule(initial.id);
        }
    }
}
