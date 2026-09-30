package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Persistent, strictly validated SelfRun-compatible Chat/Work request profile registry. */
final class RequestProfileRegistry {
    static final int MAX_PROFILE_FILE_BYTES = 262_144;
    static final int MAX_PROFILES_PER_MODE = 64;
    private static final String PREFS = "scheduler_request_profile_registry_v1";
    private static final String KEY_CHAT = "chat_profiles";
    private static final String KEY_WORK = "work_profiles";
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9][a-z0-9._:-]{0,79}");
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> TOP_KEYS = Set.of("schema", "registrySchemaVersion", "appVersion", "profiles");
    private static final Set<String> PROFILE_KEYS = Set.of("signal", "request", "operations", "fingerprint", "builtIn");
    private static final Set<String> OP_KEYS = Set.of("op", "path", "value");
    private final SharedPreferences prefs;
    private static final Object STORAGE_LOCK = new Object();
    private static final Map<RequestProfileEngine.Mode, String> TRANSIENT_SYNC_ERRORS = new java.util.concurrent.ConcurrentHashMap<>();

    static final class ImportResult {
        final int added, updated, unchanged, total;
        ImportResult(int added, int updated, int unchanged, int total) {
            this.added = added; this.updated = updated; this.unchanged = unchanged; this.total = total;
        }
    }

    RequestProfileRegistry(Context context) {
        this(context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    RequestProfileRegistry(SharedPreferences preferences) {
        prefs = preferences;
        synchronized (STORAGE_LOCK) { ensureBuiltIns(); }
    }

    synchronized ImportResult importChat(String raw) throws JSONException { return merge(RequestProfileEngine.Mode.CHAT, parseRegistryText(raw, RequestProfileEngine.Mode.CHAT)); }
    synchronized ImportResult importWork(String raw) throws JSONException { return merge(RequestProfileEngine.Mode.WORK, parseRegistryText(raw, RequestProfileEngine.Mode.WORK)); }

    synchronized void attach(Schedule schedule) {
        if (schedule == null) return;
        schedule.resolvedRequestProfile = null;
        schedule.requestProfileRegistryResolved = true;
        if ("existing".equals(schedule.targetType)) return;
        String experience = Schedule.normalizedExperience(schedule.targetType, schedule.experience);
        RequestProfileEngine.Mode mode = "work".equals(experience) ? RequestProfileEngine.Mode.WORK : RequestProfileEngine.Mode.CHAT;
        String model = mode == RequestProfileEngine.Mode.WORK ? Schedule.normalizedWorkModel(experience, schedule.workModel) : "";
        String reasoning = mode == RequestProfileEngine.Mode.WORK
                ? Schedule.normalizedReasoningEffort(experience, schedule.reasoningEffort)
                : Schedule.normalizedChatReasoning(experience, schedule.chatReasoning);
        schedule.resolvedRequestProfile = find(mode, model, reasoning);
    }

    synchronized RequestProfileEngine.TargetProfile find(RequestProfileEngine.Mode mode, String model, String reasoning) {
        String key = RequestProfileEngine.key(mode, model, reasoning);
        for (RequestProfileEngine.TargetProfile profile : profiles(mode)) if (RequestProfileEngine.key(profile).equals(key)) return profile;
        return null;
    }

    synchronized List<String> chatReasonings() {
        ArrayList<String> out = new ArrayList<>();
        for (RequestProfileEngine.TargetProfile profile : profiles(RequestProfileEngine.Mode.CHAT)) if (!out.contains(profile.reasoning)) out.add(profile.reasoning);
        return out;
    }

    synchronized List<String> workModels() {
        ArrayList<String> out = new ArrayList<>();
        for (RequestProfileEngine.TargetProfile profile : profiles(RequestProfileEngine.Mode.WORK)) if (!out.contains(profile.model)) out.add(profile.model);
        return out;
    }

    synchronized List<String> workReasoningsForModel(String model) {
        String normalized = RequestProfileEngine.normalize(model);
        ArrayList<String> out = new ArrayList<>();
        for (RequestProfileEngine.TargetProfile profile : profiles(RequestProfileEngine.Mode.WORK)) {
            if (profile.model.equals(normalized) && !out.contains(profile.reasoning)) out.add(profile.reasoning);
        }
        return out;
    }

    synchronized int count(RequestProfileEngine.Mode mode) { return profiles(mode).size(); }

    static List<RequestProfileEngine.TargetProfile> parseRegistryText(String raw, RequestProfileEngine.Mode expectedMode) throws JSONException {
        if (raw == null || raw.isBlank()) throw new JSONException("설정파일이 비어 있습니다.");
        if (raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PROFILE_FILE_BYTES) throw new JSONException("설정파일이 너무 큽니다.");
        JSONObject root = new JSONObject(raw);
        requireExactKeys(root, TOP_KEYS, "최상위");
        String expectedSchema = expectedMode == RequestProfileEngine.Mode.CHAT
                ? "selfrun-chat-profile-registry-v1" : "selfrun-work-profile-registry-v1";
        if (!expectedSchema.equals(root.optString("schema", ""))) throw new JSONException("설정파일 모드가 일치하지 않습니다.");
        if (root.optInt("registrySchemaVersion", -1) != 1) throw new JSONException("지원하지 않는 registrySchemaVersion입니다.");
        if (!(root.opt("appVersion") instanceof String)) throw new JSONException("appVersion이 없습니다.");
        JSONArray source = root.optJSONArray("profiles");
        if (source == null || source.length() < 1 || source.length() > MAX_PROFILES_PER_MODE) throw new JSONException("profiles 개수가 올바르지 않습니다.");

        ArrayList<RequestProfileEngine.TargetProfile> out = new ArrayList<>();
        HashSet<String> seenProfiles = new HashSet<>();
        for (int i = 0; i < source.length(); i++) {
            JSONObject profile = source.optJSONObject(i);
            if (profile == null) throw new JSONException("profiles[" + i + "]가 객체가 아닙니다.");
            requireAllowedKeys(profile, PROFILE_KEYS, "profiles[" + i + "]");
            if (!profile.has("signal") || !profile.has("request") || !profile.has("operations")) throw new JSONException("필수 profile 필드가 없습니다.");
            if (profile.has("fingerprint") && !FINGERPRINT.matcher(profile.optString("fingerprint", "")).matches()) throw new JSONException("fingerprint 형식이 올바르지 않습니다.");
            if (profile.has("builtIn") && !(profile.opt("builtIn") instanceof Boolean)) throw new JSONException("builtIn 형식이 올바르지 않습니다.");

            JSONObject signal = profile.optJSONObject("signal");
            JSONObject request = profile.optJSONObject("request");
            JSONArray operations = profile.optJSONArray("operations");
            if (signal == null || request == null || operations == null) throw new JSONException("profile 객체 형식이 올바르지 않습니다.");
            String model = "", reasoning;
            if (expectedMode == RequestProfileEngine.Mode.CHAT) {
                requireExactKeys(signal, Set.of("reasoning"), "Chat signal");
                reasoning = token(signal.optString("reasoning", ""), "reasoning");
                if ("keep".equals(reasoning) || "inherit".equals(reasoning)) throw new JSONException("예약어 reasoning은 등록할 수 없습니다.");
            } else {
                requireExactKeys(signal, Set.of("model", "reasoning"), "Work signal");
                model = token(signal.optString("model", ""), "model");
                reasoning = token(signal.optString("reasoning", ""), "reasoning");
                if ("inherit".equals(model) || "inherit".equals(reasoning)) throw new JSONException("예약어 profile은 등록할 수 없습니다.");
            }
            requireAllowedKeys(request, RequestProfileEngine.CONTROL_PATHS, "request");
            if (operations.length() != RequestProfileEngine.CONTROL_PATHS.size()) throw new JSONException("operations는 control path마다 정확히 하나씩 필요합니다.");
            ArrayList<RequestProfileEngine.Operation> parsedOps = new ArrayList<>();
            LinkedHashSet<String> seenPaths = new LinkedHashSet<>();
            for (int opIndex = 0; opIndex < operations.length(); opIndex++) {
                JSONObject op = operations.optJSONObject(opIndex);
                if (op == null) throw new JSONException("operation이 객체가 아닙니다.");
                requireAllowedKeys(op, OP_KEYS, "operation");
                String kind = op.optString("op", "");
                String path = op.optString("path", "");
                if (!RequestProfileEngine.CONTROL_PATHS.contains(path) || !seenPaths.add(path)) throw new JSONException("control path가 허용되지 않거나 중복되었습니다: " + path);
                if ("SET".equals(kind)) {
                    requireExactKeys(op, Set.of("op", "path", "value"), "SET operation");
                    Object valueObject = op.opt("value");
                    if (!(valueObject instanceof String)) throw new JSONException("SET value는 문자열이어야 합니다.");
                    String value = (String) valueObject;
                    if (value.isEmpty() || value.length() > 128) throw new JSONException("SET value 길이가 올바르지 않습니다.");
                    if (!request.has(path) || !value.equals(request.optString(path, null))) throw new JSONException("request와 SET operation이 일치하지 않습니다: " + path);
                    parsedOps.add(RequestProfileEngine.Operation.set(path, value));
                } else if ("REMOVE".equals(kind)) {
                    requireExactKeys(op, Set.of("op", "path"), "REMOVE operation");
                    if (request.has(path)) throw new JSONException("request와 REMOVE operation이 일치하지 않습니다: " + path);
                    parsedOps.add(RequestProfileEngine.Operation.remove(path));
                } else throw new JSONException("지원하지 않는 operation입니다: " + kind);
            }
            if (!seenPaths.equals(RequestProfileEngine.CONTROL_PATHS)) throw new JSONException("control path 집합이 완전하지 않습니다.");
            RequestProfileEngine.TargetProfile parsed = new RequestProfileEngine.TargetProfile(expectedMode, model, reasoning, parsedOps);
            RequestProfileEngine.validateOperations(parsed.operations);
            if (profile.has("fingerprint") && !fingerprint(expectedMode, parsed.operations).equals(profile.getString("fingerprint")))
                throw new JSONException("fingerprint 검증에 실패했습니다.");
            if (!seenProfiles.add(RequestProfileEngine.key(parsed))) throw new JSONException("중복 profile 조합입니다.");
            out.add(parsed);
        }
        return Collections.unmodifiableList(out);
    }

    private ImportResult merge(RequestProfileEngine.Mode mode, List<RequestProfileEngine.TargetProfile> incoming) throws JSONException {
        synchronized (STORAGE_LOCK) {
        if (syncEnabled() || prefs.contains(rawKey(mode))) throw new IllegalStateException("Drive에서 관리하는 프로필은 공식 문서에서 수정해 주세요.");
        LinkedHashMap<String, RequestProfileEngine.TargetProfile> merged = new LinkedHashMap<>();
        for (RequestProfileEngine.TargetProfile profile : profiles(mode)) merged.put(RequestProfileEngine.key(profile), profile);
        int added = 0, updated = 0, unchanged = 0;
        for (RequestProfileEngine.TargetProfile profile : incoming) {
            String key = RequestProfileEngine.key(profile);
            RequestProfileEngine.TargetProfile prior = merged.get(key);
            if (prior == null) { added++; merged.put(key, profile); }
            else if (sameOperations(prior.operations, profile.operations)) unchanged++;
            else { updated++; merged.put(key, profile); }
        }
        if (merged.size() > MAX_PROFILES_PER_MODE) throw new JSONException("등록 가능한 profile 수를 초과합니다.");
        JSONArray stored = toStoredArray(new ArrayList<>(merged.values()));
        String key = mode == RequestProfileEngine.Mode.CHAT ? KEY_CHAT : KEY_WORK;
        if (!prefs.edit().putString(key, stored.toString()).commit()) throw new IllegalStateException("프로필 레지스트리를 저장하지 못했습니다.");
        return new ImportResult(added, updated, unchanged, incoming.size());
        }
    }

    private synchronized List<RequestProfileEngine.TargetProfile> profiles(RequestProfileEngine.Mode mode) {
        synchronized (STORAGE_LOCK) {
        if (prefs.contains(rawKey(mode))) {
            try { return parseCanonicalRegistry(prefs.getString(rawKey(mode), ""), mode); }
            catch (Exception invalidCache) { return Collections.emptyList(); }
        }
        if (syncEnabled()) return Collections.emptyList();
        String key = mode == RequestProfileEngine.Mode.CHAT ? KEY_CHAT : KEY_WORK;
        String stored = prefs.getString(key, null);
        ArrayList<RequestProfileEngine.TargetProfile> out = new ArrayList<>();
        if (stored != null) {
            try {
                JSONArray array = new JSONArray(stored);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject value = array.optJSONObject(i);
                    if (value != null) out.add(fromStored(value));
                }
            } catch (Throwable ignored) { out.clear(); }
        }
        return out;
        }
    }

    private void ensureBuiltIns() {
        LinkedHashMap<String, RequestProfileEngine.TargetProfile> chat = mapByKey(profiles(RequestProfileEngine.Mode.CHAT));
        LinkedHashMap<String, RequestProfileEngine.TargetProfile> work = mapByKey(profiles(RequestProfileEngine.Mode.WORK));
        boolean changed = false;
        for (RequestProfileEngine.TargetProfile profile : RequestProfileEngine.builtInProfiles()) {
            if (prefs.contains(rawKey(profile.mode))) continue;
            LinkedHashMap<String, RequestProfileEngine.TargetProfile> target = profile.mode == RequestProfileEngine.Mode.CHAT ? chat : work;
            if (!target.containsKey(RequestProfileEngine.key(profile))) { target.put(RequestProfileEngine.key(profile), profile); changed = true; }
        }
        if (changed || prefs.getString(KEY_CHAT, null) == null || prefs.getString(KEY_WORK, null) == null) {
            SharedPreferences.Editor editor = prefs.edit();
            try {
                if (!prefs.contains(rawKey(RequestProfileEngine.Mode.CHAT)) && !syncEnabled())
                    editor.putString(KEY_CHAT, toStoredArray(new ArrayList<>(chat.values())).toString());
                if (!prefs.contains(rawKey(RequestProfileEngine.Mode.WORK)) && !syncEnabled())
                    editor.putString(KEY_WORK, toStoredArray(new ArrayList<>(work.values())).toString());
            } catch (JSONException error) { throw new IllegalStateException(error); }
            if (!editor.commit()) throw new IllegalStateException("기본 프로필 레지스트리를 저장하지 못했습니다.");
        }
    }

    private static LinkedHashMap<String, RequestProfileEngine.TargetProfile> mapByKey(List<RequestProfileEngine.TargetProfile> values) {
        LinkedHashMap<String, RequestProfileEngine.TargetProfile> out = new LinkedHashMap<>();
        for (RequestProfileEngine.TargetProfile profile : values) out.put(RequestProfileEngine.key(profile), profile);
        return out;
    }

    private static JSONArray toStoredArray(List<RequestProfileEngine.TargetProfile> profiles) throws JSONException {
        JSONArray array = new JSONArray();
        for (RequestProfileEngine.TargetProfile profile : profiles) {
            JSONObject object = new JSONObject();
            object.put("mode", profile.mode.name()); object.put("model", profile.model); object.put("reasoning", profile.reasoning);
            JSONArray operations = new JSONArray();
            for (RequestProfileEngine.Operation operation : profile.operations) {
                JSONObject op = new JSONObject();
                op.put("op", operation.kind.name()); op.put("path", operation.path);
                if (operation.kind == RequestProfileEngine.OperationKind.SET) op.put("value", operation.value);
                operations.put(op);
            }
            object.put("operations", operations); array.put(object);
        }
        return array;
    }

    private static RequestProfileEngine.TargetProfile fromStored(JSONObject object) throws JSONException {
        RequestProfileEngine.Mode mode = RequestProfileEngine.Mode.valueOf(object.getString("mode"));
        String model = object.optString("model", ""), reasoning = object.getString("reasoning");
        JSONArray operations = object.getJSONArray("operations");
        ArrayList<RequestProfileEngine.Operation> parsed = new ArrayList<>();
        for (int i = 0; i < operations.length(); i++) {
            JSONObject op = operations.getJSONObject(i);
            if ("SET".equals(op.getString("op"))) parsed.add(RequestProfileEngine.Operation.set(op.getString("path"), op.getString("value")));
            else parsed.add(RequestProfileEngine.Operation.remove(op.getString("path")));
        }
        RequestProfileEngine.TargetProfile profile = new RequestProfileEngine.TargetProfile(mode, model, reasoning, parsed);
        RequestProfileEngine.validateOperations(profile.operations);
        return profile;
    }

    private static boolean sameOperations(List<RequestProfileEngine.Operation> left, List<RequestProfileEngine.Operation> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            RequestProfileEngine.Operation a = left.get(i), b = right.get(i);
            if (a.kind != b.kind || !a.path.equals(b.path) || !java.util.Objects.equals(a.value, b.value)) return false;
        }
        return true;
    }


    boolean syncEnabled() { return prefs.getBoolean("drive_sync_enabled", false); }

    void setSyncEnabled(boolean enabled) {
        synchronized (STORAGE_LOCK) {
            boolean previous = syncEnabled();
            if (!prefs.edit().putBoolean("drive_sync_enabled", enabled).commit()) {
                prefs.edit().putBoolean("drive_sync_enabled", previous).commit();
                throw new IllegalStateException("자동 업데이트 설정을 저장하지 못했습니다.");
            }
        }
    }

    String sourceVersion(RequestProfileEngine.Mode mode) { return prefs.getString(versionKey(mode), ""); }

    boolean hasCanonicalSnapshot(RequestProfileEngine.Mode mode) {
        if (!prefs.contains(rawKey(mode))) return false;
        try { return !parseCanonicalRegistry(prefs.getString(rawKey(mode), ""), mode).isEmpty(); }
        catch (Exception invalidCache) { return false; }
    }

    void replaceCanonicalSnapshot(RequestProfileEngine.Mode mode, String raw, String version) throws JSONException {
        replaceCanonicalSnapshot(mode, raw, version, null);
    }

    boolean replaceCanonicalSnapshot(RequestProfileEngine.Mode mode, String raw, String version,
                                     ProfileSyncOperation operation) throws JSONException {
        // Validation/serialization are outside both the disk boundary and cancellation monitor.
        List<RequestProfileEngine.TargetProfile> replacement = parseCanonicalRegistry(raw, mode);
        String cleaned = cleanText(raw), stored = toStoredArray(replacement).toString();
        boolean publishing = false;
        try {
            synchronized (STORAGE_LOCK) {
                if (operation != null && (!syncEnabled() || !operation.beginPublication())) return false;
                publishing = operation != null;
                String effectiveKey = mode == RequestProfileEngine.Mode.CHAT ? KEY_CHAT : KEY_WORK;
                String checkedKey = "checked_" + mode.name(), errorKey = "error_" + mode.name();
                Map<String, Object> previous = new LinkedHashMap<>();
                previous.put(rawKey(mode), prefs.getString(rawKey(mode), null));
                previous.put(versionKey(mode), prefs.getString(versionKey(mode), null));
                previous.put(effectiveKey, prefs.getString(effectiveKey, null));
                previous.put(checkedKey, prefs.contains(checkedKey) ? prefs.getLong(checkedKey, 0L) : null);
                previous.put(errorKey, prefs.getString(errorKey, null));
                SharedPreferences.Editor editor = prefs.edit().putString(rawKey(mode), cleaned)
                        .putString(versionKey(mode), version == null ? "" : version)
                        .putString(mode == RequestProfileEngine.Mode.CHAT ? KEY_CHAT : KEY_WORK, stored)
                        .putLong("checked_" + mode.name(), System.currentTimeMillis()).remove("error_" + mode.name());
                if (!editor.commit()) {
                    // Android may publish preference changes in memory before disk commit reports failure.
                    // Restore the prior live snapshot while the shared lock excludes registry readers.
                    SharedPreferences.Editor rollback = prefs.edit();
                    for (Map.Entry<String, Object> entry : previous.entrySet()) {
                        if (entry.getValue() == null) rollback.remove(entry.getKey());
                        else if (entry.getValue() instanceof Long) rollback.putLong(entry.getKey(), (Long)entry.getValue());
                        else rollback.putString(entry.getKey(), (String)entry.getValue());
                    }
                    rollback.commit();
                    throw new IllegalStateException("공식 프로필을 저장하지 못했습니다.");
                }
                TRANSIENT_SYNC_ERRORS.remove(mode);
                return true;
            }
        } finally {
            if (publishing) operation.endPublication();
        }
    }

    void recordSyncSuccess(RequestProfileEngine.Mode mode) { recordSyncMetadata(mode, null, null, false); }
    boolean recordSyncSuccess(RequestProfileEngine.Mode mode, ProfileSyncOperation operation) {
        return recordSyncMetadata(mode, null, operation, true);
    }
    void recordSyncFailure(RequestProfileEngine.Mode mode, String code) { recordSyncMetadata(mode, code, null, false); }
    boolean recordSyncFailure(RequestProfileEngine.Mode mode, String code, ProfileSyncOperation operation) {
        return recordSyncMetadata(mode, code, operation, true);
    }
    void recordTransientSyncFailure(RequestProfileEngine.Mode mode, String code) {
        TRANSIENT_SYNC_ERRORS.put(mode, safeSyncError(code));
    }

    private static String safeSyncError(String code) {
        return Set.of("AUTH_REQUIRED", "AUTH_FAILED", "NETWORK", "ACCESS_DENIED", "DOCUMENT_INVALID", "SYNC_TIMEOUT", "PERSISTENCE")
                .contains(code) ? code : "DOCUMENT_INVALID";
    }

    private boolean recordSyncMetadata(RequestProfileEngine.Mode mode, String code,
                                       ProfileSyncOperation operation, boolean requireEnabled) {
        boolean publishing = false;
        try {
            synchronized (STORAGE_LOCK) {
                if (requireEnabled && !syncEnabled()) return false;
                if (operation != null && !operation.beginPublication()) return false;
                publishing = operation != null;
                if (code == null) {
                    if (!prefs.edit().putLong("checked_" + mode.name(), System.currentTimeMillis()).remove("error_" + mode.name()).commit())
                        throw new IllegalStateException("프로필 확인 결과를 저장하지 못했습니다.");
                    TRANSIENT_SYNC_ERRORS.remove(mode);
                } else {
                    // Only fixed categories enter preferences, never provider responses or tokens.
                    String safe = safeSyncError(code);
                    prefs.edit().putString("error_" + mode.name(), safe).commit();
                }
                return true;
            }
        } finally {
            if (publishing) operation.endPublication();
        }
    }

    String syncStatusText() {
        StringBuilder text = new StringBuilder(syncEnabled() ? "Drive 자동 업데이트 켜짐" : "Drive 자동 업데이트 꺼짐");
        for (RequestProfileEngine.Mode mode : RequestProfileEngine.Mode.values()) {
            text.append("\n").append(mode == RequestProfileEngine.Mode.CHAT ? "Chat" : "Work").append(": ");
            if (hasCanonicalSnapshot(mode)) {
                long checked = prefs.getLong("checked_" + mode.name(), 0L);
                text.append(count(mode)).append("개 · 마지막 확인 ")
                        .append(checked == 0L ? "없음" : java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(new java.util.Date(checked)));
                if (TRANSIENT_SYNC_ERRORS.containsKey(mode) || !prefs.getString("error_" + mode.name(), "").isEmpty()) text.append(" · 확인 실패, 마지막 정상 목록 유지");
            } else text.append(syncEnabled() ? "아직 공식 목록을 받지 못했습니다" : "로컬 목록 사용");
        }
        return text.toString();
    }

    static List<RequestProfileEngine.TargetProfile> parseCanonicalRegistry(String raw, RequestProfileEngine.Mode mode) throws JSONException {
        String cleaned = cleanText(raw);
        List<RequestProfileEngine.TargetProfile> result = parseRegistryText(cleaned, mode);
        JSONObject root = new JSONObject(cleaned);
        if (root.getString("appVersion").length() > 128) throw new JSONException("appVersion이 너무 깁니다.");
        JSONArray entries = root.getJSONArray("profiles");
        Set<String> fingerprints = new HashSet<>();
        Map<String, String> models = new LinkedHashMap<>(), reasonings = new LinkedHashMap<>();
        for (int i = 0; i < result.size(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            if (!entry.has("fingerprint") || !entry.has("builtIn")) throw new JSONException("공식 프로필 검증 필드가 없습니다.");
            RequestProfileEngine.TargetProfile profile = result.get(i);
            if (!fingerprints.add(fingerprint(mode, profile.operations))) throw new JSONException("중복 fingerprint입니다.");
            String model = operationValue(profile, "model");
            if (!model.startsWith("SET|")) throw new JSONException("모델 값이 없습니다.");
            if (mode == RequestProfileEngine.Mode.WORK) {
                String prior = models.putIfAbsent(profile.model, model);
                if (prior != null && !prior.equals(model)) throw new JSONException("Work 모델 신호가 충돌합니다.");
                String effort = operationValue(profile, "thinking_effort");
                prior = reasonings.putIfAbsent(profile.reasoning, effort);
                if (prior != null && !prior.equals(effort)) throw new JSONException("Work 추론 신호가 충돌합니다.");
            }
        }
        return result;
    }

    private static String operationValue(RequestProfileEngine.TargetProfile profile, String path) {
        for (RequestProfileEngine.Operation op : profile.operations) if (path.equals(op.path)) return op.kind.name() + "|" + (op.value == null ? "" : op.value);
        return "";
    }

    static String fingerprint(RequestProfileEngine.Mode mode, List<RequestProfileEngine.Operation> operations) {
        RequestProfileEngine.validateOperations(operations);
        StringBuilder source = new StringBuilder("selfrun-profile-registry-v1\n").append(mode.name()).append('\n');
        for (String path : List.of("model", "thinking_effort", "conversation_origin", "service_tier")) {
            for (RequestProfileEngine.Operation op : operations) if (path.equals(op.path)) {
                source.append(op.kind.name()).append('|').append(path).append('|');
                if (op.value != null) source.append(op.value);
                source.append('\n');
            }
        }
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(source.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte value : digest) out.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String cleanText(String raw) {
        String text = raw == null ? "" : raw.trim();
        return text.startsWith("\ufeff") ? text.substring(1).trim() : text;
    }
    private static String rawKey(RequestProfileEngine.Mode mode) { return "canonical_raw_" + mode.name(); }
    private static String versionKey(RequestProfileEngine.Mode mode) { return "canonical_version_" + mode.name(); }

    private static String token(String value, String field) throws JSONException {
        String normalized = RequestProfileEngine.normalize(value);
        if (!TOKEN.matcher(normalized).matches()) throw new JSONException(field + " 값이 올바르지 않습니다.");
        return normalized;
    }

    private static void requireExactKeys(JSONObject object, Set<String> expected, String location) throws JSONException {
        Set<String> actual = keys(object);
        if (!actual.equals(expected)) throw new JSONException(location + " 필드 구성이 올바르지 않습니다: " + actual);
    }

    private static void requireAllowedKeys(JSONObject object, Set<String> allowed, String location) throws JSONException {
        Set<String> actual = keys(object);
        if (!allowed.containsAll(actual)) throw new JSONException(location + "에 허용되지 않은 필드가 있습니다: " + actual);
    }

    private static Set<String> keys(JSONObject object) {
        HashSet<String> out = new HashSet<>();
        for (java.util.Iterator<String> iterator = object.keys(); iterator.hasNext();) out.add(iterator.next());
        return out;
    }
}
