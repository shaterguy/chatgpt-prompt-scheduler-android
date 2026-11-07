package com.shaterguy.chatgptpromptscheduler;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class TraceRetentionTest {
    @Test public void pollingOverflowRetainsSetupRecoveryAndTerminal() throws Exception {
        JSONArray events = new JSONArray();
        int dropped = 0;
        TraceRetention.append(events, event("RUN_STARTED", ""), 120);
        TraceRetention.append(events, event("NATIVE_PREFERENCE_RESULT", "PREPARED"), 120);
        for (int i = 0; i < 160; i++) {
            if (TraceRetention.append(events, event("SCRIPT_EVALUATE", ""), 120)) dropped++;
            if (TraceRetention.append(events, event("SCRIPT_RESULT", "RETRY"), 120)) dropped++;
            if (i == 80 && TraceRetention.append(events, event("COMPOSER_RECOVERY", ""), 120)) dropped++;
        }
        JSONObject finished = event("RUN_FINISHED", "EXECUTION_TIMEOUT");
        if (TraceRetention.append(events, finished, 120)) dropped++;
        finished.getJSONObject("data").put("traceDropped", dropped);
        assertEquals(120, events.length());
        assertEquals(204, dropped);
        assertTrue(contains(events, "RUN_STARTED"));
        assertTrue(contains(events, "NATIVE_PREFERENCE_RESULT"));
        assertTrue(contains(events, "COMPOSER_RECOVERY"));
        assertSame(finished, events.getJSONObject(events.length() - 1));
        assertEquals(dropped, finished.getJSONObject("data").getInt("traceDropped"));
    }

    @Test public void routineEventDoesNotDisplaceAnAllSignificantBuffer() throws Exception {
        JSONArray events = new JSONArray();
        TraceRetention.append(events, event("COMPOSER_RECOVERY", ""), 2);
        TraceRetention.append(events, event("RUN_FINISHED", ""), 2);
        assertTrue(TraceRetention.append(events, event("WATCHDOG", ""), 2));
        assertEquals(2, events.length());
        assertTrue(contains(events, "COMPOSER_RECOVERY"));
        assertTrue(contains(events, "RUN_FINISHED"));
    }

    private static JSONObject event(String type, String status) throws Exception {
        return new JSONObject().put("type", type).put("data", new JSONObject().put("status", status));
    }

    private static boolean contains(JSONArray events, String type) {
        for (int i = 0; i < events.length(); i++) {
            if (type.equals(events.optJSONObject(i).optString("type"))) return true;
        }
        return false;
    }
}
