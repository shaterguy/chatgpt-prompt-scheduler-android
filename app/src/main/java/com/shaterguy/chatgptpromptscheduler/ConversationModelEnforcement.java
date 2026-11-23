package com.shaterguy.chatgptpromptscheduler;

import org.json.JSONException;
import org.json.JSONObject;

/** A fallback run must show that its actual conversation request carried the selected model. */
final class ConversationModelEnforcement {
    private ConversationModelEnforcement() {}

    static boolean matches(JSONObject result, RequestProfileEngine.TargetProfile profile) {
        if (result == null || profile == null) return false;
        JSONObject outer = result.optJSONObject("diagnostics");
        JSONObject engine = outer == null ? null : outer.optJSONObject("requestProfile");
        JSONObject patch = engine == null ? null : engine.optJSONObject("lastPatch");
        JSONObject after = patch == null ? null : patch.optJSONObject("after");
        if (engine == null || engine.optInt("patchCount", 0) < 1 || after == null) return false;
        try {
            JSONObject expected = new JSONObject(NativeProfilePreparationScript.profileJson(profile));
            if (!expected.optString("model").equals(after.optString("model"))) return false;
            String effort = expected.optString("thinking_effort", "");
            return expected.isNull("thinking_effort") ? after.isNull("thinking_effort")
                    : effort.equals(after.optString("thinking_effort"));
        } catch (JSONException invalid) {
            return false;
        }
    }
}
