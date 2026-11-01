package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;
import static org.junit.Assert.*;

public final class UiDisplayPolicyTest {
    @Test public void unknownStatusAndProfileValuesAreNotMisrepresented() {
        assertEquals("REMOTE_REJECTED", UiDisplayPolicy.status("REMOTE_REJECTED"));
        assertEquals("sol-ultra-custom", UiDisplayPolicy.label("sol-ultra-custom"));
        assertEquals("실패", UiDisplayPolicy.status("FAILED"));
        assertEquals("아직 실행하지 않음", UiDisplayPolicy.status(""));
    }
    @Test public void narrowAndLargeTextLayoutsUseSingleColumn() {
        assertEquals(1, UiDisplayPolicy.actionColumns(320, 1f, 4));
        assertEquals(1, UiDisplayPolicy.actionColumns(411, 1.5f, 4));
        assertEquals(1, UiDisplayPolicy.actionColumns(840, 2f, 4));
        assertEquals(2, UiDisplayPolicy.actionColumns(411, 1f, 2));
    }
    @Test public void humanLabelsRemainDistinctAcrossStoredScheduleValues() {
        String[] raw = {"general", "project", "existing", "chat", "work", "once", "daily", "weekly", "interval"};
        java.util.Set<String> labels = new java.util.HashSet<>();
        for (String value : raw) {
            assertNotEquals(value, UiDisplayPolicy.label(value));
            labels.add(UiDisplayPolicy.label(value));
        }
        assertEquals(raw.length, labels.size());
        assertEquals("general", raw[0]);
    }
}
