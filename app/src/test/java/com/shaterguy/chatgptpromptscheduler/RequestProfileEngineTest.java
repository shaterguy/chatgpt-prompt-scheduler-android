package com.shaterguy.chatgptpromptscheduler;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public final class RequestProfileEngineTest {
    private static Map<String, Object> nativeRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("action", "next");
        request.put("messages", new ArrayList<>(List.of(
                Map.of("author", "user", "content", Map.of("content_type", "text", "parts", List.of("opaque"))))));
        request.put("conversation_id", "opaque-conversation");
        request.put("parent_message_id", "opaque-parent");
        request.put("client_prepare_state", "sent");
        request.put("supports_buffering", true);
        return request;
    }

    private static Map<String, Object> apply(RequestProfileEngine.Mode mode, String model, String reasoning) {
        return RequestProfileEngine.apply(
                nativeRequest(), new RequestProfileEngine.TargetProfile(mode, model, reasoning));
    }

    @Test public void chatMappingsAreAbsoluteAndRemoveWorkControls() {
        Map<String, String> models = Map.of(
                "instant", "gpt-5-6",
                "medium", "gpt-5-6-thinking",
                "high", "gpt-5-6-thinking",
                "xhigh", "gpt-5-6-thinking");
        Map<String, String> efforts = Map.of(
                "medium", "standard", "high", "extended", "xhigh", "max");
        for (String reasoning : models.keySet()) {
            Map<String, Object> request = nativeRequest();
            request.put("model", "old");
            request.put("thinking_effort", "old");
            request.put("conversation_origin", "tpp");
            request.put("service_tier", "standard");
            Map<String, Object> output = RequestProfileEngine.apply(
                    request, new RequestProfileEngine.TargetProfile(
                            RequestProfileEngine.Mode.CHAT, "", reasoning));
            assertEquals(models.get(reasoning), output.get("model"));
            if ("instant".equals(reasoning)) assertFalse(output.containsKey("thinking_effort"));
            else assertEquals(efforts.get(reasoning), output.get("thinking_effort"));
            assertFalse(output.containsKey("conversation_origin"));
            assertFalse(output.containsKey("service_tier"));
        }
    }

    @Test public void everySupportedWorkFactorMapsToCapturedPayloadValues() {
        Map<String, String> models = Map.of(
                "sol", "gpt-5.6-sol-wm",
                "terra", "gpt-5.6-terra-wm",
                "luna", "gpt-5.6-luna-wm");
        Map<String, String> efforts = Map.of(
                "light", "min",
                "medium", "standard",
                "high", "extended",
                "xhigh", "xhigh",
                "max", "max",
                "ultra", "ultra");
        for (String model : models.keySet()) {
            for (String reasoning : efforts.keySet()) {
                if ("luna".equals(model) && "ultra".equals(reasoning)) continue;
                Map<String, Object> output = apply(RequestProfileEngine.Mode.WORK, model, reasoning);
                assertEquals(models.get(model), output.get("model"));
                assertEquals(efforts.get(reasoning), output.get("thinking_effort"));
                assertEquals("tpp", output.get("conversation_origin"));
                assertEquals("standard", output.get("service_tier"));
            }
        }
    }

    @Test public void unsupportedAndIncompleteProfilesFailClosed() {
        assertThrows(IllegalArgumentException.class, () ->
                apply(RequestProfileEngine.Mode.CHAT, "", "pro"));
        assertThrows(IllegalArgumentException.class, () ->
                apply(RequestProfileEngine.Mode.CHAT, "", "keep"));
        assertThrows(IllegalArgumentException.class, () ->
                apply(RequestProfileEngine.Mode.WORK, "inherit", "high"));
        assertThrows(IllegalArgumentException.class, () ->
                apply(RequestProfileEngine.Mode.WORK, "sol", "inherit"));
        assertThrows(IllegalArgumentException.class, () ->
                apply(RequestProfileEngine.Mode.WORK, "luna", "ultra"));
        assertThrows(IllegalArgumentException.class, () -> RequestProfileEngine.plan(
                new RequestProfileEngine.TargetProfile(
                        RequestProfileEngine.Mode.WORK, "sol", "high", "future-profile")));
    }

    @Test public void existingConversationUsesNativeInheritedProfileOnly() {
        Schedule existing = new Schedule();
        existing.targetType = "existing";
        existing.targetUrl = "https://chatgpt.com/c/opaque";
        existing.experience = "work";
        existing.workModel = "sol";
        existing.reasoningEffort = "max";
        assertNull(RequestProfileEngine.forSchedule(existing));
    }

    @Test public void selectableSchedulesRequireExplicitSupportedProfiles() {
        Schedule chat = new Schedule();
        chat.targetType = "general";
        chat.experience = "chat";
        chat.chatReasoning = "keep";
        assertThrows(IllegalArgumentException.class, () -> RequestProfileEngine.forSchedule(chat));
        chat.chatReasoning = "medium";
        assertEquals(RequestProfileEngine.Mode.CHAT, RequestProfileEngine.forSchedule(chat).mode);

        Schedule work = new Schedule();
        work.targetType = "project";
        work.experience = "work";
        work.workModel = "inherit";
        work.reasoningEffort = "high";
        assertThrows(IllegalArgumentException.class, () -> RequestProfileEngine.forSchedule(work));
        work.workModel = "terra";
        assertEquals("terra", RequestProfileEngine.forSchedule(work).model);
    }

    @Test public void priorControlStateNeverInfluencesNextAbsoluteTarget() {
        Map<String, Object> work = apply(RequestProfileEngine.Mode.WORK, "sol", "max");
        Map<String, Object> chat = RequestProfileEngine.apply(
                work, new RequestProfileEngine.TargetProfile(
                        RequestProfileEngine.Mode.CHAT, "", "instant"));
        Map<String, Object> terra = RequestProfileEngine.apply(
                chat, new RequestProfileEngine.TargetProfile(
                        RequestProfileEngine.Mode.WORK, "terra", "light"));
        assertEquals("gpt-5.6-terra-wm", terra.get("model"));
        assertEquals("min", terra.get("thinking_effort"));
        assertEquals("tpp", terra.get("conversation_origin"));
        assertEquals("standard", terra.get("service_tier"));
    }

    @Test public void dataPlaneAndExactAllowlistRemainInvariant() {
        Map<String, Object> before = nativeRequest();
        Map<String, Object> after = RequestProfileEngine.apply(
                before, new RequestProfileEngine.TargetProfile(
                        RequestProfileEngine.Mode.WORK, "sol", "high"));
        assertEquals(Set.of("model", "thinking_effort", "conversation_origin", "service_tier"),
                RequestProfileEngine.CONTROL_PATHS);
        assertTrue(RequestProfileEngine.nonControlEquivalent(before, after));
        assertSame(before.get("messages"), after.get("messages"));
        assertSame(before.get("conversation_id"), after.get("conversation_id"));
        assertSame(before.get("parent_message_id"), after.get("parent_message_id"));
    }

    @Test public void unknownConversationSchemaFailsClosed() {
        Map<String, Object> missing = new LinkedHashMap<>();
        missing.put("action", "next");
        assertThrows(IllegalArgumentException.class, () -> RequestProfileEngine.apply(
                missing, new RequestProfileEngine.TargetProfile(
                        RequestProfileEngine.Mode.CHAT, "", "high")));
        Map<String, Object> wrong = new LinkedHashMap<>();
        wrong.put("messages", Map.of());
        assertThrows(IllegalArgumentException.class, () -> RequestProfileEngine.apply(
                wrong, new RequestProfileEngine.TargetProfile(
                        RequestProfileEngine.Mode.CHAT, "", "high")));
    }

    @Test public void legacyScheduleJsonStillLoadsAndNormalizesWithoutSchemaChanges() throws Exception {
        Schedule baseline = new Schedule();
        JSONObject legacy = baseline.toJson();
        legacy.remove("chatReasoning");
        Schedule restoredChat = Schedule.fromJson(legacy);
        assertEquals("keep", restoredChat.chatReasoning);
        assertThrows(IllegalArgumentException.class, () ->
                RequestProfileEngine.forSchedule(restoredChat));

        JSONObject legacyWork = baseline.toJson();
        legacyWork.put("experience", "work");
        legacyWork.remove("workModel");
        legacyWork.remove("reasoningEffort");
        Schedule restoredWork = Schedule.fromJson(legacyWork);
        assertEquals("inherit", restoredWork.workModel);
        assertEquals("inherit", restoredWork.reasoningEffort);
        assertThrows(IllegalArgumentException.class, () ->
                RequestProfileEngine.forSchedule(restoredWork));

        assertEquals(1, ConfigStore.SCHEMA_VERSION);
        assertEquals(Set.of("id", "name", "targetType", "targetUrl", "experience", "workModel",
                        "reasoningEffort", "chatReasoning", "prompt", "recurrence", "intervalMinutes",
                        "weekdays", "times", "enabled", "retryCount", "lastRunAt", "nextRunAt", "lastStatus"),
                jsonKeys(baseline.toJson()));
    }

    private static Set<String> jsonKeys(JSONObject object) {
        Set<String> keys = new java.util.HashSet<>();
        for (java.util.Iterator<String> iterator = object.keys(); iterator.hasNext();) {
            keys.add(iterator.next());
        }
        return keys;
    }
}
