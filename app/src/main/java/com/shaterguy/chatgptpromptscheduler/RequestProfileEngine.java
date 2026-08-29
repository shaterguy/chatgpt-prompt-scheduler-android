package com.shaterguy.chatgptpromptscheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Absolute, capture-calibrated ChatGPT request profile for scheduled conversations. */
final class RequestProfileEngine {
    static final String PROFILE_VERSION = "chatgpt-request-snapshot-calibration-v1@2026-08-28";
    static final Set<String> CONTROL_PATHS = Set.of(
            "model", "thinking_effort", "conversation_origin", "service_tier");

    enum Mode { CHAT, WORK }
    enum OperationKind { SET, REMOVE }

    static final class TargetProfile {
        final Mode mode;
        final String model;
        final String reasoning;
        final String profileVersion;

        TargetProfile(Mode mode, String model, String reasoning) {
            this(mode, model, reasoning, PROFILE_VERSION);
        }

        TargetProfile(Mode mode, String model, String reasoning, String profileVersion) {
            this.mode = Objects.requireNonNull(mode, "mode");
            this.model = normalize(model);
            this.reasoning = normalize(reasoning);
            this.profileVersion = Objects.requireNonNull(profileVersion, "profileVersion");
        }
    }

    static final class Operation {
        final OperationKind kind;
        final String path;
        final String value;

        private Operation(OperationKind kind, String path, String value) {
            if (!CONTROL_PATHS.contains(path)) {
                throw new IllegalArgumentException("CONTROL_PATH_NOT_ALLOWLISTED");
            }
            this.kind = kind;
            this.path = path;
            this.value = value;
        }

        static Operation set(String path, String value) {
            if (value == null) throw new IllegalArgumentException("CONTROL_VALUE_MISSING");
            return new Operation(OperationKind.SET, path, value);
        }

        static Operation remove(String path) {
            return new Operation(OperationKind.REMOVE, path, null);
        }
    }

    static final class ProfilePlan {
        final TargetProfile target;
        final List<Operation> operations;

        ProfilePlan(TargetProfile target, List<Operation> operations) {
            this.target = target;
            this.operations = Collections.unmodifiableList(new ArrayList<>(operations));
        }
    }

    private RequestProfileEngine() {}

    /**
     * Returns null only for existing conversations, whose native/inherited profile must remain
     * completely untouched. Every selectable new-conversation target must be explicit.
     */
    static TargetProfile forSchedule(Schedule schedule) {
        Objects.requireNonNull(schedule, "schedule");
        if ("existing".equals(schedule.targetType)) return null;
        String experience = Schedule.normalizedExperience(schedule.targetType, schedule.experience);
        if ("chat".equals(experience)) {
            String reasoning = Schedule.normalizedChatReasoning(experience, schedule.chatReasoning);
            TargetProfile target = new TargetProfile(Mode.CHAT, "", reasoning);
            plan(target);
            return target;
        }
        if ("work".equals(experience)) {
            String model = Schedule.normalizedWorkModel(experience, schedule.workModel);
            String reasoning = Schedule.normalizedReasoningEffort(experience, schedule.reasoningEffort);
            TargetProfile target = new TargetProfile(Mode.WORK, model, reasoning);
            plan(target);
            return target;
        }
        throw new IllegalArgumentException("PROFILE_MODE_UNSUPPORTED");
    }

    static ProfilePlan plan(TargetProfile target) {
        Objects.requireNonNull(target, "target");
        if (!PROFILE_VERSION.equals(target.profileVersion)) {
            throw new IllegalArgumentException("PROFILE_VERSION_UNSUPPORTED");
        }
        return switch (target.mode) {
            case CHAT -> chatPlan(target);
            case WORK -> workPlan(target);
        };
    }

    private static ProfilePlan chatPlan(TargetProfile target) {
        List<Operation> operations = new ArrayList<>();
        switch (target.reasoning) {
            case "instant" -> {
                operations.add(Operation.set("model", "gpt-5-6"));
                operations.add(Operation.remove("thinking_effort"));
            }
            case "medium" -> {
                operations.add(Operation.set("model", "gpt-5-6-thinking"));
                operations.add(Operation.set("thinking_effort", "standard"));
            }
            case "high" -> {
                operations.add(Operation.set("model", "gpt-5-6-thinking"));
                operations.add(Operation.set("thinking_effort", "extended"));
            }
            case "xhigh" -> {
                operations.add(Operation.set("model", "gpt-5-6-thinking"));
                operations.add(Operation.set("thinking_effort", "max"));
            }
            case "pro" -> throw new IllegalArgumentException("CHAT_PRO_UNCAPTURED");
            case "keep", "inherit", "" -> throw new IllegalArgumentException("CHAT_PROFILE_INCOMPLETE");
            default -> throw new IllegalArgumentException("CHAT_PROFILE_UNSUPPORTED");
        }
        operations.add(Operation.remove("conversation_origin"));
        operations.add(Operation.remove("service_tier"));
        return new ProfilePlan(target, operations);
    }

    private static ProfilePlan workPlan(TargetProfile target) {
        String model = switch (target.model) {
            case "sol" -> "gpt-5.6-sol-wm";
            case "terra" -> "gpt-5.6-terra-wm";
            case "luna" -> "gpt-5.6-luna-wm";
            case "inherit", "" -> throw new IllegalArgumentException("WORK_MODEL_INCOMPLETE");
            default -> throw new IllegalArgumentException("WORK_MODEL_UNSUPPORTED");
        };
        String effort = switch (target.reasoning) {
            case "light" -> "min";
            case "medium" -> "standard";
            case "high" -> "extended";
            case "xhigh" -> "xhigh";
            case "max" -> "max";
            case "ultra" -> "ultra";
            case "inherit", "" -> throw new IllegalArgumentException("WORK_REASONING_INCOMPLETE");
            default -> throw new IllegalArgumentException("WORK_REASONING_UNSUPPORTED");
        };
        if ("gpt-5.6-luna-wm".equals(model) && "ultra".equals(effort)) {
            throw new IllegalArgumentException("LUNA_ULTRA_UNSUPPORTED");
        }
        return new ProfilePlan(target, List.of(
                Operation.set("model", model),
                Operation.set("thinking_effort", effort),
                Operation.set("conversation_origin", "tpp"),
                Operation.set("service_tier", "standard")));
    }

    static Map<String, Object> apply(Map<String, Object> nativeRequest, TargetProfile target) {
        validateSubmissionSchema(nativeRequest);
        Map<String, Object> before = new LinkedHashMap<>(nativeRequest);
        Map<String, Object> after = new LinkedHashMap<>(nativeRequest);
        for (Operation operation : plan(target).operations) {
            if (operation.kind == OperationKind.SET) after.put(operation.path, operation.value);
            else after.remove(operation.path);
        }
        if (!nonControlEquivalent(before, after)) {
            throw new IllegalStateException("DATA_PLANE_CHANGED");
        }
        return after;
    }

    static void validateSubmissionSchema(Map<String, Object> request) {
        if (request == null || !(request.get("messages") instanceof List<?>)) {
            throw new IllegalArgumentException("CONVERSATION_SCHEMA_UNKNOWN");
        }
    }

    static boolean nonControlEquivalent(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> left = new LinkedHashMap<>(before);
        Map<String, Object> right = new LinkedHashMap<>(after);
        CONTROL_PATHS.forEach(left::remove);
        CONTROL_PATHS.forEach(right::remove);
        return left.equals(right);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
