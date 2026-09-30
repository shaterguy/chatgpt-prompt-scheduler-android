package com.shaterguy.chatgptpromptscheduler;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertThrows;

public final class CanonicalProfileIntegrityTest {
    @Test public void rejectsWellFormedButIncorrectFingerprint() throws Exception {
        JSONObject entry = new JSONObject()
                .put("signal", new JSONObject().put("reasoning", "instant"))
                .put("request", new JSONObject().put("model", "gpt-5-6"))
                .put("operations", new JSONArray()
                        .put(new JSONObject().put("op", "SET").put("path", "model").put("value", "gpt-5-6"))
                        .put(new JSONObject().put("op", "REMOVE").put("path", "thinking_effort"))
                        .put(new JSONObject().put("op", "REMOVE").put("path", "conversation_origin"))
                        .put(new JSONObject().put("op", "REMOVE").put("path", "service_tier")))
                .put("fingerprint", "0".repeat(64)).put("builtIn", true);
        String raw = new JSONObject().put("schema", "selfrun-chat-profile-registry-v1")
                .put("registrySchemaVersion", 1).put("appVersion", "4.0.5")
                .put("profiles", new JSONArray().put(entry)).toString();
        assertThrows(org.json.JSONException.class, () -> RequestProfileRegistry.parseRegistryText(raw, RequestProfileEngine.Mode.CHAT));
    }
}
