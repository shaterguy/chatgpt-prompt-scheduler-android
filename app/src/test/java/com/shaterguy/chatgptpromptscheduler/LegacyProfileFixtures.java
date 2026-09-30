package com.shaterguy.chatgptpromptscheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static com.shaterguy.chatgptpromptscheduler.RequestProfileEngine.*;

/** Historical profiles are test fixtures only; runtime selections come from verified snapshots. */
final class LegacyProfileFixtures {
    static List<TargetProfile> builtInProfiles() {
        List<TargetProfile> profiles = new ArrayList<>();
        profiles.add(profile(Mode.CHAT, "", "instant",
                set("model", "gpt-5-6"), remove("thinking_effort"), remove("conversation_origin"), remove("service_tier")));
        profiles.add(profile(Mode.CHAT, "", "medium",
                set("model", "gpt-5-6-thinking"), set("thinking_effort", "standard"), remove("conversation_origin"), remove("service_tier")));
        profiles.add(profile(Mode.CHAT, "", "high",
                set("model", "gpt-5-6-thinking"), set("thinking_effort", "extended"), remove("conversation_origin"), remove("service_tier")));
        profiles.add(profile(Mode.CHAT, "", "xhigh",
                set("model", "gpt-5-6-thinking"), set("thinking_effort", "max"), remove("conversation_origin"), remove("service_tier")));
        profiles.add(profile(Mode.CHAT, "", "pro",
                set("model", "gpt-5-6-pro"), set("thinking_effort", "standard"), remove("conversation_origin"), remove("service_tier")));
        profiles.add(profile(Mode.WORK, "luna", "max",
                set("model", "gpt-5.6-luna-wm"), set("thinking_effort", "max"), set("conversation_origin", "tpp"), set("service_tier", "standard")));
        profiles.add(profile(Mode.WORK, "sol", "high",
                set("model", "gpt-5.6-sol-wm"), set("thinking_effort", "extended"), set("conversation_origin", "tpp"), set("service_tier", "standard")));
        profiles.add(profile(Mode.WORK, "sol", "max",
                set("model", "gpt-5.6-sol-wm"), set("thinking_effort", "max"), set("conversation_origin", "tpp"), set("service_tier", "standard")));
        profiles.add(profile(Mode.WORK, "sol", "ultra",
                set("model", "gpt-5.6-sol-wm"), set("thinking_effort", "ultra"), set("conversation_origin", "tpp"), set("service_tier", "standard")));
        profiles.add(profile(Mode.WORK, "sol", "xhigh",
                set("model", "gpt-5.6-sol-wm"), set("thinking_effort", "xhigh"), set("conversation_origin", "tpp"), set("service_tier", "standard")));
        profiles.add(profile(Mode.WORK, "terra", "high",
                set("model", "gpt-5.6-terra-wm"), set("thinking_effort", "extended"), set("conversation_origin", "tpp"), set("service_tier", "standard")));
        profiles.add(profile(Mode.WORK, "terra", "max",
                set("model", "gpt-5.6-terra-wm"), set("thinking_effort", "max"), set("conversation_origin", "tpp"), set("service_tier", "standard")));
        profiles.add(profile(Mode.WORK, "terra", "ultra",
                set("model", "gpt-5.6-terra-wm"), set("thinking_effort", "ultra"), set("conversation_origin", "tpp"), remove("service_tier")));
        profiles.add(profile(Mode.WORK, "terra", "xhigh",
                set("model", "gpt-5.6-terra-wm"), set("thinking_effort", "xhigh"), set("conversation_origin", "tpp"), set("service_tier", "standard")));
        return Collections.unmodifiableList(profiles);
    }

    static TargetProfile profile(Mode mode, String model, String reasoning) {
        String normalizedModel = normalize(model), normalizedReasoning = normalize(reasoning);
        for (TargetProfile profile : builtInProfiles()) {
            if (profile.mode == mode && profile.model.equals(normalizedModel) && profile.reasoning.equals(normalizedReasoning)) return profile;
        }
        return null;
    }


    static void attach(Schedule schedule) throws Exception {
        Mode mode = "work".equals(schedule.experience) ? Mode.WORK : Mode.CHAT;
        TargetProfile target = profile(mode, mode == Mode.WORK ? schedule.workModel : "",
                mode == Mode.WORK ? schedule.reasoningEffort : schedule.chatReasoning);
        if (target == null) throw new IllegalArgumentException("FIXTURE_UNREGISTERED");
        org.json.JSONObject signal = new org.json.JSONObject().put("reasoning", target.reasoning);
        if (mode == Mode.WORK) signal.put("model", target.model);
        org.json.JSONObject request = new org.json.JSONObject();
        org.json.JSONArray operations = new org.json.JSONArray();
        for (Operation operation : target.operations) {
            org.json.JSONObject op = new org.json.JSONObject().put("op", operation.kind.name()).put("path", operation.path);
            if (operation.kind == OperationKind.SET) {
                op.put("value", operation.value);request.put(operation.path, operation.value);
            }
            operations.put(op);
        }
        org.json.JSONObject entry = new org.json.JSONObject().put("signal", signal).put("request", request)
                .put("operations", operations).put("builtIn", true)
                .put("fingerprint", RequestProfileRegistry.fingerprint(mode, target.operations));
        String document = new org.json.JSONObject().put("schema", mode == Mode.CHAT
                ? "selfrun-chat-profile-registry-v1" : "selfrun-work-profile-registry-v1")
                .put("registrySchemaVersion", 1).put("appVersion", "fixture")
                .put("profiles", new org.json.JSONArray().put(entry)).toString();
        RequestProfileRegistry registry = new RequestProfileRegistry(ProfileRegistrySyncTest.memoryPreferences());
        registry.replaceCanonicalSnapshot(mode, document, "fixture");
        registry.attach(schedule);
    }

    private static TargetProfile profile(Mode mode, String model, String reasoning, Operation... operations) {
        return new TargetProfile(mode, model, reasoning, List.of(operations));
    }
    private static Operation set(String path, String value) { return Operation.set(path, value); }
    private static Operation remove(String path) { return Operation.remove(path); }
}
