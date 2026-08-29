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
        return RequestProfileEngine.apply(nativeRequest(), new RequestProfileEngine.TargetProfile(mode, model, reasoning));
    }

    @Test public void chatProfilesMatchSelfRunRegistryIncludingPro() {
        Map<String, String> models = Map.of(
                "instant", "gpt-5-6",
                "medium", "gpt-5-6-thinking",
                "high", "gpt-5-6-thinking",
                "xhigh", "gpt-5-6-thinking",
                "pro", "gpt-5-6-pro");
        Map<String, String> efforts = Map.of(
                "medium", "standard", "high", "extended", "xhigh", "max", "pro", "standard");
        for (String reasoning : models.keySet()) {
            Map<String, Object> request = nativeRequest();
            request.put("model", "old");
            request.put("thinking_effort", "old");
            request.put("conversation_origin", "tpp");
            request.put("service_tier", "standard");
            Map<String, Object> output = RequestProfileEngine.apply(
                    request, new RequestProfileEngine.TargetProfile(RequestProfileEngine.Mode.CHAT, "", reasoning));
            assertEquals(models.get(reasoning), output.get("model"));
            if ("instant".equals(reasoning)) assertFalse(output.containsKey("thinking_effort"));
            else assertEquals(efforts.get(reasoning), output.get("thinking_effort"));
            assertFalse(output.containsKey("conversation_origin"));
            assertFalse(output.containsKey("service_tier"));
        }
    }

    @Test public void workProfilesAreExactRegistryCombinationsOnly() {
        String[][] supported = {
                {"luna", "max", "gpt-5.6-luna-wm", "max"},
                {"sol", "high", "gpt-5.6-sol-wm", "extended"},
                {"sol", "max", "gpt-5.6-sol-wm", "max"},
                {"sol", "ultra", "gpt-5.6-sol-wm", "ultra"},
                {"sol", "xhigh", "gpt-5.6-sol-wm", "xhigh"},
                {"terra", "high", "gpt-5.6-terra-wm", "extended"},
                {"terra", "max", "gpt-5.6-terra-wm", "max"},
                {"terra", "ultra", "gpt-5.6-terra-wm", "ultra"},
                {"terra", "xhigh", "gpt-5.6-terra-wm", "xhigh"}
        };
        for (String[] profile : supported) {
            Map<String, Object> output = apply(RequestProfileEngine.Mode.WORK, profile[0], profile[1]);
            assertEquals(profile[2], output.get("model"));
            assertEquals(profile[3], output.get("thinking_effort"));
            assertEquals("tpp", output.get("conversation_origin"));
            if ("terra".equals(profile[0]) && "ultra".equals(profile[1])) assertFalse(output.containsKey("service_tier"));
            else assertEquals("standard", output.get("service_tier"));
        }
        assertThrows(IllegalArgumentException.class, () -> apply(RequestProfileEngine.Mode.WORK, "terra", "medium"));
        assertThrows(IllegalArgumentException.class, () -> apply(RequestProfileEngine.Mode.WORK, "sol", "light"));
        assertThrows(IllegalArgumentException.class, () -> apply(RequestProfileEngine.Mode.WORK, "luna", "high"));
        assertThrows(IllegalArgumentException.class, () -> apply(RequestProfileEngine.Mode.WORK, "luna", "ultra"));
    }

    @Test public void importedExactOperationsCanDefineFutureRegisteredCombination() {
        RequestProfileEngine.TargetProfile target = new RequestProfileEngine.TargetProfile(
                RequestProfileEngine.Mode.WORK, "future", "deep", List.of(
                RequestProfileEngine.Operation.set("model", "future-model"),
                RequestProfileEngine.Operation.set("thinking_effort", "future-effort"),
                RequestProfileEngine.Operation.set("conversation_origin", "tpp"),
                RequestProfileEngine.Operation.remove("service_tier")));
        Map<String, Object> output = RequestProfileEngine.apply(nativeRequest(), target);
        assertEquals("future-model", output.get("model"));
        assertEquals("future-effort", output.get("thinking_effort"));
        assertEquals("tpp", output.get("conversation_origin"));
        assertFalse(output.containsKey("service_tier"));
    }

    @Test public void unsupportedIncompleteAndInvalidProfilesFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> apply(RequestProfileEngine.Mode.CHAT, "", "keep"));
        assertThrows(IllegalArgumentException.class, () -> apply(RequestProfileEngine.Mode.WORK, "inherit", "high"));
        assertThrows(IllegalArgumentException.class, () -> apply(RequestProfileEngine.Mode.WORK, "sol", "inherit"));
        assertThrows(IllegalArgumentException.class, () -> RequestProfileEngine.plan(
                new RequestProfileEngine.TargetProfile(RequestProfileEngine.Mode.WORK, "sol", "high", "future-profile")));
        assertThrows(IllegalArgumentException.class, () -> RequestProfileEngine.plan(
                new RequestProfileEngine.TargetProfile(RequestProfileEngine.Mode.WORK, "future", "deep", List.of(
                        RequestProfileEngine.Operation.set("model", "future-model")))));
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

    @Test public void transientRegistryAttachmentOverridesBuiltInMapping() {
        Schedule schedule = new Schedule();
        schedule.targetType = "general";
        schedule.experience = "chat";
        schedule.chatReasoning = "future";
        schedule.resolvedRequestProfile = new RequestProfileEngine.TargetProfile(
                RequestProfileEngine.Mode.CHAT, "", "future", List.of(
                RequestProfileEngine.Operation.set("model", "future-chat"),
                RequestProfileEngine.Operation.remove("thinking_effort"),
                RequestProfileEngine.Operation.remove("conversation_origin"),
                RequestProfileEngine.Operation.remove("service_tier")));
        assertSame(schedule.resolvedRequestProfile, RequestProfileEngine.forSchedule(schedule));
    }

    @Test public void priorControlStateNeverInfluencesNextAbsoluteTarget() {
        Map<String, Object> work = apply(RequestProfileEngine.Mode.WORK, "sol", "max");
        Map<String, Object> chat = RequestProfileEngine.apply(
                work, new RequestProfileEngine.TargetProfile(RequestProfileEngine.Mode.CHAT, "", "instant"));
        Map<String, Object> terra = RequestProfileEngine.apply(
                chat, new RequestProfileEngine.TargetProfile(RequestProfileEngine.Mode.WORK, "terra", "high"));
        assertEquals("gpt-5.6-terra-wm", terra.get("model"));
        assertEquals("extended", terra.get("thinking_effort"));
        assertEquals("tpp", terra.get("conversation_origin"));
        assertEquals("standard", terra.get("service_tier"));
    }

    @Test public void dataPlaneAndExactAllowlistRemainInvariant() {
        Map<String, Object> before = nativeRequest();
        Map<String, Object> after = RequestProfileEngine.apply(
                before, new RequestProfileEngine.TargetProfile(RequestProfileEngine.Mode.WORK, "sol", "high"));
        assertEquals(Set.of("model", "thinking_effort", "conversation_origin", "service_tier"), RequestProfileEngine.CONTROL_PATHS);
        assertTrue(RequestProfileEngine.nonControlEquivalent(before, after));
        assertSame(before.get("messages"), after.get("messages"));
        assertSame(before.get("conversation_id"), after.get("conversation_id"));
        assertSame(before.get("parent_message_id"), after.get("parent_message_id"));
    }

    @Test public void unknownConversationSchemaFailsClosed() {
        Map<String, Object> missing = new LinkedHashMap<>();
        missing.put("action", "next");
        assertThrows(IllegalArgumentException.class, () -> RequestProfileEngine.apply(
                missing, new RequestProfileEngine.TargetProfile(RequestProfileEngine.Mode.CHAT, "", "high")));
        Map<String, Object> wrong = new LinkedHashMap<>();
        wrong.put("messages", Map.of());
        assertThrows(IllegalArgumentException.class, () -> RequestProfileEngine.apply(
                wrong, new RequestProfileEngine.TargetProfile(RequestProfileEngine.Mode.CHAT, "", "high")));
    }

    @Test public void legacyScheduleJsonSchemaRemainsStable() throws Exception {
        Schedule baseline = new Schedule();
        JSONObject legacy = baseline.toJson();
        legacy.remove("chatReasoning");
        Schedule restoredChat = Schedule.fromJson(legacy);
        assertEquals("keep", restoredChat.chatReasoning);

        JSONObject legacyWork = baseline.toJson();
        legacyWork.put("experience", "work");
        legacyWork.remove("workModel");
        legacyWork.remove("reasoningEffort");
        Schedule restoredWork = Schedule.fromJson(legacyWork);
        assertEquals("inherit", restoredWork.workModel);
        assertEquals("inherit", restoredWork.reasoningEffort);

        assertEquals(1, ConfigStore.SCHEMA_VERSION);
        assertEquals(Set.of("id", "name", "targetType", "targetUrl", "experience", "workModel",
                        "reasoningEffort", "chatReasoning", "prompt", "recurrence", "intervalMinutes",
                        "weekdays", "times", "enabled", "retryCount", "lastRunAt", "nextRunAt", "lastStatus"),
                jsonKeys(baseline.toJson()));
    }

    private static Set<String> jsonKeys(JSONObject object) {
        Set<String> keys = new java.util.HashSet<>();
        for (java.util.Iterator<String> iterator = object.keys(); iterator.hasNext();) keys.add(iterator.next());
        return keys;
    }
}
