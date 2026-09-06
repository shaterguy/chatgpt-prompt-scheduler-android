package com.shaterguy.chatgptpromptscheduler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** SelfRun capture format adapted to the scheduler's existing validated registry. */
final class CapturedRequestProfile {
    static final int MAX_MESSAGE_CHARS = 8192;
    static final List<String> CONTROL_PATHS = List.of(
            "model", "thinking_effort", "conversation_origin", "service_tier");
    final RequestProfileEngine.Mode mode;
    final List<RequestProfileEngine.Operation> operations;

    private CapturedRequestProfile(RequestProfileEngine.Mode mode,
                                   List<RequestProfileEngine.Operation> operations) {
        this.mode = mode;
        this.operations = Collections.unmodifiableList(new ArrayList<>(operations));
    }

    static boolean trustedPage(String url) {
        try {
            URI uri = new URI(url == null ? "" : url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && ("chatgpt.com".equalsIgnoreCase(uri.getHost())
                    || "www.chatgpt.com".equalsIgnoreCase(uri.getHost()));
        } catch (Exception ignored) { return false; }
    }

    static CapturedRequestProfile parse(String raw, RequestProfileEngine.Mode expectedMode,
                                        String expectedCaptureId) throws JSONException {
        if (raw == null || raw.length() > MAX_MESSAGE_CHARS || expectedMode == null
                || expectedCaptureId == null || expectedCaptureId.isEmpty())
            throw new JSONException("캡처 세션 또는 결과 크기가 올바르지 않습니다.");
        JSONObject root = new JSONObject(raw);
        exactKeys(root, Set.of("captureId", "mode", "operations"));
        if (!expectedCaptureId.equals(root.opt("captureId"))
                || !expectedMode.name().toLowerCase(Locale.ROOT).equals(root.opt("mode")))
            throw new JSONException("현재 캡처 세션과 일치하지 않습니다.");
        JSONArray array = root.optJSONArray("operations");
        if (array == null || array.length() != CONTROL_PATHS.size())
            throw new JSONException("모델/추론 제어 필드가 완전하지 않습니다.");
        ArrayList<RequestProfileEngine.Operation> out = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject op = array.optJSONObject(i);
            if (op == null || !(op.opt("path") instanceof String) || !(op.opt("op") instanceof String))
                throw new JSONException("캡처 operation이 올바르지 않습니다.");
            String path = op.getString("path"), kind = op.getString("op");
            if (!CONTROL_PATHS.contains(path) || !seen.add(path))
                throw new JSONException("허용되지 않거나 중복된 제어 필드입니다.");
            if ("SET".equals(kind)) {
                exactKeys(op, Set.of("op", "path", "value"));
                Object value = op.opt("value");
                if (!(value instanceof String) || ((String) value).isEmpty() || ((String) value).length() > 128)
                    throw new JSONException("스케쥴러에서 지원하지 않는 제어 값입니다.");
                out.add(RequestProfileEngine.Operation.set(path, (String) value));
            } else if ("REMOVE".equals(kind)) {
                exactKeys(op, Set.of("op", "path"));
                out.add(RequestProfileEngine.Operation.remove(path));
            } else throw new JSONException("지원하지 않는 캡처 operation입니다.");
        }
        RequestProfileEngine.validateOperations(out);
        boolean hasModel = false;
        for (RequestProfileEngine.Operation op : out)
            if ("model".equals(op.path) && op.kind == RequestProfileEngine.OperationKind.SET) hasModel = true;
        if (!hasModel) throw new JSONException("실제 요청의 모델 값을 확인하지 못했습니다.");
        return new CapturedRequestProfile(expectedMode, out);
    }

    String portableJson(String modelSignal, String reasoningSignal, String appVersion) throws JSONException {
        JSONObject signal = new JSONObject();
        if (mode == RequestProfileEngine.Mode.WORK) signal.put("model", normalizeSignal(modelSignal));
        signal.put("reasoning", normalizeSignal(reasoningSignal));
        JSONObject request = new JSONObject();
        JSONArray ops = new JSONArray();
        for (String path : CONTROL_PATHS) {
            for (RequestProfileEngine.Operation operation : operations) {
                if (!path.equals(operation.path)) continue;
                JSONObject op = new JSONObject().put("op", operation.kind.name()).put("path", path);
                if (operation.kind == RequestProfileEngine.OperationKind.SET) {
                    op.put("value", operation.value);
                    request.put(path, operation.value);
                }
                ops.put(op);
            }
        }
        JSONObject profile = new JSONObject().put("signal", signal).put("request", request).put("operations", ops);
        JSONObject root = new JSONObject()
                .put("schema", mode == RequestProfileEngine.Mode.CHAT
                        ? "selfrun-chat-profile-registry-v1" : "selfrun-work-profile-registry-v1")
                .put("registrySchemaVersion", 1).put("appVersion", appVersion)
                .put("profiles", new JSONArray().put(profile));
        String json = root.toString();
        // Reuse the import boundary: capture must never bypass its signal/operation validation.
        RequestProfileRegistry.parseRegistryText(json, mode);
        return json;
    }

    String actualCombination() {
        StringBuilder out = new StringBuilder();
        for (String path : CONTROL_PATHS) {
            for (RequestProfileEngine.Operation op : operations) {
                if (!path.equals(op.path)) continue;
                if (out.length() > 0) out.append('\n');
                out.append(path).append(" = ").append(op.kind == RequestProfileEngine.OperationKind.SET
                        ? op.value : "[필드 없음 / REMOVE]");
            }
        }
        return out.toString();
    }

    boolean matches(RequestProfileEngine.TargetProfile profile) {
        if (profile == null || profile.mode != mode || profile.operations.size() != operations.size()) return false;
        for (RequestProfileEngine.Operation left : operations) {
            boolean found = false;
            for (RequestProfileEngine.Operation right : profile.operations) {
                if (left.path.equals(right.path) && left.kind == right.kind
                        && java.util.Objects.equals(left.value, right.value)) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    RequestProfileEngine.TargetProfile findDuplicate(RequestProfileRegistry registry) {
        if (mode == RequestProfileEngine.Mode.CHAT) {
            for (String reasoning : registry.chatReasonings()) {
                RequestProfileEngine.TargetProfile profile = registry.find(mode, "", reasoning);
                if (matches(profile)) return profile;
            }
        } else {
            for (String model : registry.workModels()) {
                for (String reasoning : registry.workReasoningsForModel(model)) {
                    RequestProfileEngine.TargetProfile profile = registry.find(mode, model, reasoning);
                    if (matches(profile)) return profile;
                }
            }
        }
        return null;
    }

    static String signalLabel(RequestProfileEngine.TargetProfile profile) {
        return profile.mode == RequestProfileEngine.Mode.CHAT ? profile.reasoning
                : profile.model + " / " + profile.reasoning;
    }

    static String normalizeSignal(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }

    private static void exactKeys(JSONObject object, Set<String> expected) throws JSONException {
        if (object.length() != expected.size()) throw new JSONException("캡처 결과에 예상하지 않은 필드가 있습니다.");
        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext()) if (!expected.contains(keys.next()))
            throw new JSONException("허용되지 않은 캡처 필드입니다.");
    }
}
