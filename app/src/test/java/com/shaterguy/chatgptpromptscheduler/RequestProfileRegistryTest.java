package com.shaterguy.chatgptpromptscheduler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public final class RequestProfileRegistryTest {
    @Test public void parsesChatRegistryAndPreservesProOperations() throws Exception {
        JSONObject profile = profile(
                new JSONObject().put("reasoning", "pro"),
                new JSONObject().put("model", "gpt-5-6-pro").put("thinking_effort", "standard"),
                new JSONArray()
                        .put(set("model", "gpt-5-6-pro"))
                        .put(set("thinking_effort", "standard"))
                        .put(remove("conversation_origin"))
                        .put(remove("service_tier")));
        List<RequestProfileEngine.TargetProfile> parsed = RequestProfileRegistry.parseRegistryText(
                registry("selfrun-chat-profile-registry-v1", profile).toString(), RequestProfileEngine.Mode.CHAT);
        assertEquals(1, parsed.size());
        RequestProfileEngine.TargetProfile target = parsed.get(0);
        assertEquals("pro", target.reasoning);
        assertEquals(4, target.operations.size());
        assertEquals(RequestProfileEngine.OperationKind.REMOVE, target.operations.get(3).kind);
        assertEquals("service_tier", target.operations.get(3).path);
    }

    @Test public void parsesWorkRegistryAndPreservesRemovedServiceTier() throws Exception {
        JSONObject profile = profile(
                new JSONObject().put("model", "terra").put("reasoning", "ultra"),
                new JSONObject().put("model", "gpt-5.6-terra-wm")
                        .put("thinking_effort", "ultra").put("conversation_origin", "tpp"),
                new JSONArray()
                        .put(set("model", "gpt-5.6-terra-wm"))
                        .put(set("thinking_effort", "ultra"))
                        .put(set("conversation_origin", "tpp"))
                        .put(remove("service_tier")));
        List<RequestProfileEngine.TargetProfile> parsed = RequestProfileRegistry.parseRegistryText(
                registry("selfrun-work-profile-registry-v1", profile).toString(), RequestProfileEngine.Mode.WORK);
        assertEquals("terra", parsed.get(0).model);
        assertEquals("ultra", parsed.get(0).reasoning);
        assertEquals(RequestProfileEngine.OperationKind.REMOVE, parsed.get(0).operations.get(3).kind);
    }

    @Test public void rejectsWrongModeUnknownControlPathAndPartialOperations() throws Exception {
        JSONObject chat = profile(
                new JSONObject().put("reasoning", "instant"),
                new JSONObject().put("model", "gpt-5-6"),
                new JSONArray().put(set("model", "gpt-5-6")).put(remove("thinking_effort"))
                        .put(remove("conversation_origin")).put(remove("service_tier")));
        assertThrows(JSONException.class, () -> RequestProfileRegistry.parseRegistryText(
                registry("selfrun-chat-profile-registry-v1", chat).toString(), RequestProfileEngine.Mode.WORK));

        JSONObject unknownPath = profile(
                new JSONObject().put("reasoning", "instant"),
                new JSONObject().put("model", "gpt-5-6"),
                new JSONArray().put(set("model", "gpt-5-6")).put(remove("thinking_effort"))
                        .put(remove("conversation_origin")).put(remove("unexpected")));
        assertThrows(JSONException.class, () -> RequestProfileRegistry.parseRegistryText(
                registry("selfrun-chat-profile-registry-v1", unknownPath).toString(), RequestProfileEngine.Mode.CHAT));

        JSONObject partial = profile(
                new JSONObject().put("reasoning", "instant"),
                new JSONObject().put("model", "gpt-5-6"),
                new JSONArray().put(set("model", "gpt-5-6")).put(remove("thinking_effort"))
                        .put(remove("conversation_origin")));
        assertThrows(JSONException.class, () -> RequestProfileRegistry.parseRegistryText(
                registry("selfrun-chat-profile-registry-v1", partial).toString(), RequestProfileEngine.Mode.CHAT));
    }

    @Test public void rejectsRequestOperationMismatchAndDuplicateCombination() throws Exception {
        JSONObject mismatch = profile(
                new JSONObject().put("reasoning", "medium"),
                new JSONObject().put("model", "gpt-5-6-thinking").put("thinking_effort", "standard"),
                new JSONArray().put(set("model", "gpt-5-6-thinking")).put(set("thinking_effort", "extended"))
                        .put(remove("conversation_origin")).put(remove("service_tier")));
        assertThrows(JSONException.class, () -> RequestProfileRegistry.parseRegistryText(
                registry("selfrun-chat-profile-registry-v1", mismatch).toString(), RequestProfileEngine.Mode.CHAT));

        JSONObject duplicate = profile(
                new JSONObject().put("reasoning", "instant"),
                new JSONObject().put("model", "gpt-5-6"),
                new JSONArray().put(set("model", "gpt-5-6")).put(remove("thinking_effort"))
                        .put(remove("conversation_origin")).put(remove("service_tier")));
        JSONObject root = baseRegistry("selfrun-chat-profile-registry-v1");
        root.put("profiles", new JSONArray().put(duplicate).put(new JSONObject(duplicate.toString())));
        assertThrows(JSONException.class, () -> RequestProfileRegistry.parseRegistryText(
                root.toString(), RequestProfileEngine.Mode.CHAT));
    }

    @Test public void rejectsUnexpectedTopLevelAndProfileFields() throws Exception {
        JSONObject profile = profile(
                new JSONObject().put("reasoning", "instant"),
                new JSONObject().put("model", "gpt-5-6"),
                new JSONArray().put(set("model", "gpt-5-6")).put(remove("thinking_effort"))
                        .put(remove("conversation_origin")).put(remove("service_tier")));
        JSONObject root = registry("selfrun-chat-profile-registry-v1", profile).put("extra", true);
        assertThrows(JSONException.class, () -> RequestProfileRegistry.parseRegistryText(
                root.toString(), RequestProfileEngine.Mode.CHAT));
        profile.put("extra", true);
        assertThrows(JSONException.class, () -> RequestProfileRegistry.parseRegistryText(
                registry("selfrun-chat-profile-registry-v1", profile).toString(), RequestProfileEngine.Mode.CHAT));
    }

    private static JSONObject registry(String schema, JSONObject profile) throws Exception {
        return baseRegistry(schema).put("profiles", new JSONArray().put(profile));
    }

    private static JSONObject baseRegistry(String schema) throws Exception {
        return new JSONObject().put("schema", schema).put("registrySchemaVersion", 1)
                .put("appVersion", "2.1.0-dev5").put("profiles", new JSONArray());
    }

    private static JSONObject profile(JSONObject signal, JSONObject request, JSONArray operations) throws Exception {
        return new JSONObject().put("signal", signal).put("request", request).put("operations", operations)
                .put("builtIn", true);
    }

    private static JSONObject set(String path, String value) throws Exception {
        return new JSONObject().put("op", "SET").put("path", path).put("value", value);
    }

    private static JSONObject remove(String path) throws Exception {
        return new JSONObject().put("op", "REMOVE").put("path", path);
    }
}
