package com.shaterguy.chatgptpromptscheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Exact request-control profile selected for a scheduled ChatGPT conversation. */
final class RequestProfileEngine {
    static final String PROFILE_VERSION = "chatgpt-request-profile-registry-v2@2026-08-30";
    static final Set<String> CONTROL_PATHS = Set.of(
            "model", "thinking_effort", "conversation_origin", "service_tier");

    enum Mode { CHAT, WORK }
    enum OperationKind { SET, REMOVE }

    static final class TargetProfile {
        final Mode mode;
        final String model;
        final String reasoning;
        final String profileVersion;
        final List<Operation> operations;

        TargetProfile(Mode mode, String model, String reasoning) {
            this(mode, model, reasoning, PROFILE_VERSION, List.of());
        }

        TargetProfile(Mode mode, String model, String reasoning, String profileVersion) {
            this(mode, model, reasoning, profileVersion, List.of());
        }

        TargetProfile(Mode mode, String model, String reasoning, List<Operation> operations) {
            this(mode, model, reasoning, PROFILE_VERSION, operations);
        }

        TargetProfile(Mode mode, String model, String reasoning, String profileVersion, List<Operation> operations) {
            this.mode = Objects.requireNonNull(mode, "mode");
            this.model = normalize(model);
            this.reasoning = normalize(reasoning);
            this.profileVersion = Objects.requireNonNull(profileVersion, "profileVersion");
            this.operations = Collections.unmodifiableList(new ArrayList<>(operations == null ? List.of() : operations));
        }
    }

    static final class Operation {
        final OperationKind kind;
        final String path;
        final String value;

        private Operation(OperationKind kind, String path, String value) {
            if (!CONTROL_PATHS.contains(path)) throw new IllegalArgumentException("CONTROL_PATH_NOT_ALLOWLISTED");
            this.kind = Objects.requireNonNull(kind, "kind");
            this.path = path;
            this.value = value;
        }

        static Operation set(String path, String value) {
            if (value == null || value.isEmpty() || value.length() > 128) throw new IllegalArgumentException("CONTROL_VALUE_INVALID");
            return new Operation(OperationKind.SET, path, value);
        }

        static Operation remove(String path) { return new Operation(OperationKind.REMOVE, path, null); }
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

    /** Returns null when the schedule intentionally inherits the current native ChatGPT profile. */
    static TargetProfile forSchedule(Schedule schedule) {
        Objects.requireNonNull(schedule, "schedule");
        if ("existing".equals(schedule.targetType)) return null;
        String experience = Schedule.normalizedExperience(schedule.targetType, schedule.experience);
        Mode mode;
        String model, reasoning;
        if ("chat".equals(experience)) {
            mode = Mode.CHAT;
            model = "";
            reasoning = Schedule.normalizedChatReasoning(experience, schedule.chatReasoning);
            if ("keep".equals(reasoning) || "inherit".equals(reasoning)) return null;
        } else if ("work".equals(experience)) {
            mode = Mode.WORK;
            model = Schedule.normalizedWorkModel(experience, schedule.workModel);
            reasoning = Schedule.normalizedReasoningEffort(experience, schedule.reasoningEffort);
            if ("inherit".equals(model) && "inherit".equals(reasoning)) return null;
        } else throw new IllegalArgumentException("PROFILE_MODE_UNSUPPORTED");
        TargetProfile target = schedule.resolvedRequestProfile;
        if (!schedule.requestProfileRegistryResolved || target == null
                || !key(mode, model, reasoning).equals(key(target)))
            throw new IllegalArgumentException("PROFILE_UNREGISTERED");
        plan(target);
        return target;
    }

    static ProfilePlan plan(TargetProfile target) {
        Objects.requireNonNull(target, "target");
        if (!PROFILE_VERSION.equals(target.profileVersion)) throw new IllegalArgumentException("PROFILE_VERSION_UNSUPPORTED");
        validateOperations(target.operations);
        return new ProfilePlan(target, target.operations);
    }

    static void validateOperations(List<Operation> operations) {
        if (operations == null || operations.size() != CONTROL_PATHS.size()) throw new IllegalArgumentException("CONTROL_OPERATION_COUNT_INVALID");
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Operation operation : operations) {
            if (operation == null || !seen.add(operation.path)) throw new IllegalArgumentException("CONTROL_OPERATION_DUPLICATE");
            if (operation.kind == OperationKind.SET && (operation.value == null || operation.value.isEmpty() || operation.value.length() > 128))
                throw new IllegalArgumentException("CONTROL_VALUE_INVALID");
            if (operation.kind == OperationKind.REMOVE && operation.value != null) throw new IllegalArgumentException("REMOVE_VALUE_FORBIDDEN");
        }
        if (!seen.equals(CONTROL_PATHS)) throw new IllegalArgumentException("CONTROL_OPERATION_SET_INCOMPLETE");
    }

    static Map<String, Object> apply(Map<String, Object> nativeRequest, TargetProfile target) {
        validateSubmissionSchema(nativeRequest);
        Map<String, Object> before = new LinkedHashMap<>(nativeRequest), after = new LinkedHashMap<>(nativeRequest);
        for (Operation operation : plan(target).operations) {
            if (operation.kind == OperationKind.SET) after.put(operation.path, operation.value); else after.remove(operation.path);
        }
        if (!nonControlEquivalent(before, after)) throw new IllegalStateException("DATA_PLANE_CHANGED");
        return after;
    }

    static void validateSubmissionSchema(Map<String, Object> request) {
        if (request == null || !(request.get("messages") instanceof List<?>)) throw new IllegalArgumentException("CONVERSATION_SCHEMA_UNKNOWN");
    }

    static boolean nonControlEquivalent(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> left = new LinkedHashMap<>(before), right = new LinkedHashMap<>(after);
        CONTROL_PATHS.forEach(left::remove); CONTROL_PATHS.forEach(right::remove);
        return left.equals(right);
    }

    static String key(TargetProfile profile) { return key(profile.mode, profile.model, profile.reasoning); }
    static String key(Mode mode, String model, String reasoning) { return mode.name() + "|" + normalize(model) + "|" + normalize(reasoning); }
    static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(); }
}
