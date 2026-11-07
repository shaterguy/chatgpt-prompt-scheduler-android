package com.shaterguy.chatgptpromptscheduler;

import org.json.JSONArray;
import org.json.JSONObject;

/** Keep bounded diagnostics useful after long polling: discard routine events first. */
final class TraceRetention {
    private TraceRetention() {}

    static boolean append(JSONArray events, JSONObject event, int limit) {
        boolean dropped = events.length() >= limit;
        if (dropped) {
            int remove = -1;
            for (int i = 0; i < events.length(); i++) {
                if (routine(events.optJSONObject(i))) { remove = i; break; }
            }
            if (remove < 0 && routine(event)) return true;
            if (remove < 0) {
                for (int i = 0; i < events.length(); i++) {
                    String type = events.optJSONObject(i).optString("type");
                    if (!"RUN_FINISHED".equals(type) && !type.contains("RECOVERY")) {
                        remove = i;
                        break;
                    }
                }
            }
            events.remove(remove < 0 ? 0 : remove);
        }
        events.put(event);
        return dropped;
    }

    private static boolean routine(JSONObject event) {
        if (event == null) return false;
        String type = event.optString("type");
        if ("SCRIPT_EVALUATE".equals(type) || "WATCHDOG".equals(type)) return true;
        JSONObject data = event.optJSONObject("data");
        return "SCRIPT_RESULT".equals(type) && data != null && "RETRY".equals(data.optString("status"));
    }
}
