package com.shaterguy.chatgptpromptscheduler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public final class CapturedRequestProfileTest {
    private static final String SESSION = "capture-test-session";

    private static JSONObject envelope(String mode) throws JSONException {
        return new JSONObject().put("captureId", SESSION).put("mode", mode)
                .put("operations", new JSONArray()
                        .put(new JSONObject().put("op", "SET").put("path", "model").put("value", "captured-model"))
                        .put(new JSONObject().put("op", "SET").put("path", "thinking_effort").put("value", "extended"))
                        .put(new JSONObject().put("op", "REMOVE").put("path", "conversation_origin"))
                        .put(new JSONObject().put("op", "REMOVE").put("path", "service_tier")));
    }

    @Test public void chatCaptureRoundTripsThroughExistingRegistryAndPreservesPayload() throws Exception {
        CapturedRequestProfile capture = CapturedRequestProfile.parse(envelope("chat").toString(), RequestProfileEngine.Mode.CHAT, SESSION);
        String portable = capture.portableJson("", " Captured-High ", "fixture");
        RequestProfileEngine.TargetProfile target = RequestProfileRegistry.parseRegistryText(portable, RequestProfileEngine.Mode.CHAT).get(0);
        assertEquals("captured-high", target.reasoning);
        assertTrue(capture.matches(target));
        Map<String, Object> nativeBody = new LinkedHashMap<>();
        nativeBody.put("messages", List.of(Map.of("text", "private-test-prompt")));
        nativeBody.put("conversation_id", "keep-conversation");
        nativeBody.put("model", "previous-model");
        nativeBody.put("service_tier", "previous-tier");
        Map<String, Object> applied = RequestProfileEngine.apply(nativeBody, target);
        assertEquals("captured-model", applied.get("model"));
        assertEquals("extended", applied.get("thinking_effort"));
        assertFalse(applied.containsKey("service_tier"));
        assertTrue(RequestProfileEngine.nonControlEquivalent(nativeBody, applied));
        assertFalse(portable.contains("private-test-prompt"));
    }

    @Test public void workCaptureRetainsExplicitRemoveAndNormalizedAliases() throws Exception {
        CapturedRequestProfile capture = CapturedRequestProfile.parse(envelope("work").toString(), RequestProfileEngine.Mode.WORK, SESSION);
        RequestProfileEngine.TargetProfile target = RequestProfileRegistry.parseRegistryText(
                capture.portableJson(" New-Model ", " High ", "fixture"), RequestProfileEngine.Mode.WORK).get(0);
        assertEquals("new-model", target.model);
        assertEquals("high", target.reasoning);
        assertTrue(capture.matches(target));
        assertTrue(capture.actualCombination().contains("REMOVE"));
    }

    @Test public void rejectsSessionAndModeMismatch() throws Exception {
        String raw = envelope("chat").toString();
        assertThrows(JSONException.class, () -> CapturedRequestProfile.parse(raw, RequestProfileEngine.Mode.CHAT, "older-session"));
        assertThrows(JSONException.class, () -> CapturedRequestProfile.parse(raw, RequestProfileEngine.Mode.WORK, SESSION));
    }

    @Test public void rejectsPromptOrHeaderFieldsInCaptureEnvelope() throws Exception {
        JSONObject value = envelope("chat").put("messages", new JSONArray());
        assertThrows(JSONException.class, () -> CapturedRequestProfile.parse(value.toString(), RequestProfileEngine.Mode.CHAT, SESSION));
    }

    @Test public void rejectsDuplicateAndUnknownControlPaths() throws Exception {
        JSONObject duplicate = envelope("chat");
        duplicate.getJSONArray("operations").getJSONObject(3).put("path", "model");
        assertThrows(JSONException.class, () -> CapturedRequestProfile.parse(duplicate.toString(), RequestProfileEngine.Mode.CHAT, SESSION));
        JSONObject unknown = envelope("chat");
        unknown.getJSONArray("operations").getJSONObject(3).put("path", "messages");
        assertThrows(JSONException.class, () -> CapturedRequestProfile.parse(unknown.toString(), RequestProfileEngine.Mode.CHAT, SESSION));
    }

    @Test public void rejectsMissingModelAndNonStringControl() throws Exception {
        JSONObject noModel = envelope("chat");
        noModel.getJSONArray("operations").put(0, new JSONObject().put("op", "REMOVE").put("path", "model"));
        assertThrows(JSONException.class, () -> CapturedRequestProfile.parse(noModel.toString(), RequestProfileEngine.Mode.CHAT, SESSION));
        JSONObject wrongType = envelope("chat");
        wrongType.getJSONArray("operations").getJSONObject(0).put("value", 7);
        assertThrows(JSONException.class, () -> CapturedRequestProfile.parse(wrongType.toString(), RequestProfileEngine.Mode.CHAT, SESSION));
    }

    @Test public void rejectsOversizedCaptureAndControl() throws Exception {
        assertThrows(JSONException.class, () -> CapturedRequestProfile.parse("x".repeat(8193), RequestProfileEngine.Mode.CHAT, SESSION));
        JSONObject value = envelope("chat");
        value.getJSONArray("operations").getJSONObject(0).put("value", "x".repeat(129));
        assertThrows(JSONException.class, () -> CapturedRequestProfile.parse(value.toString(), RequestProfileEngine.Mode.CHAT, SESSION));
    }

    @Test public void preservesRegistryReservedSignalValidation() throws Exception {
        CapturedRequestProfile chat = CapturedRequestProfile.parse(envelope("chat").toString(), RequestProfileEngine.Mode.CHAT, SESSION);
        assertThrows(JSONException.class, () -> chat.portableJson("", "keep", "fixture"));
        assertThrows(JSONException.class, () -> chat.portableJson("", "bad name", "fixture"));
        CapturedRequestProfile work = CapturedRequestProfile.parse(envelope("work").toString(), RequestProfileEngine.Mode.WORK, SESSION);
        assertThrows(JSONException.class, () -> work.portableJson("inherit", "high", "fixture"));
    }

    @Test public void duplicateComparisonIgnoresOperationOrderButNotRemove() throws Exception {
        CapturedRequestProfile chat = CapturedRequestProfile.parse(envelope("chat").toString(), RequestProfileEngine.Mode.CHAT, SESSION);
        List<RequestProfileEngine.Operation> reversed = new java.util.ArrayList<>(chat.operations);
        java.util.Collections.reverse(reversed);
        assertTrue(chat.matches(new RequestProfileEngine.TargetProfile(RequestProfileEngine.Mode.CHAT, "", "alias", reversed)));
        reversed.set(0, RequestProfileEngine.Operation.set("service_tier", "standard"));
        assertFalse(chat.matches(new RequestProfileEngine.TargetProfile(RequestProfileEngine.Mode.CHAT, "", "alias", reversed)));
    }

    @Test public void trustedOriginsRejectLookalikesUserInfoAndAlternatePorts() {
        assertTrue(CapturedRequestProfile.trustedPage("https://chatgpt.com/c/test"));
        assertTrue(CapturedRequestProfile.trustedPage("https://www.chatgpt.com:443/"));
        for (String bad : List.of("http://chatgpt.com", "https://chatgpt.com.evil.test", "https://evil.test@chatgpt.com",
                "https://chatgpt.com:444", "file:///chatgpt.com", "javascript:alert(1)", "https://chatgpt.com./")) {
            assertFalse(bad, CapturedRequestProfile.trustedPage(bad));
        }
    }
}
