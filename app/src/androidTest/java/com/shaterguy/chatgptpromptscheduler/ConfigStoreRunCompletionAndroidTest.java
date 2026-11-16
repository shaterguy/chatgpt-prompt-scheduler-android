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
}
