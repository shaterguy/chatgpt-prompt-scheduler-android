package com.shaterguy.chatgptpromptscheduler;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import static org.junit.Assert.*;

public final class ProfileRegistrySyncTest {
    @Test public void canonicalSnapshotReplacesRemovedModelsAndSurvivesRecreation() throws Exception {
        SharedPreferences prefs = memoryPreferences();
        RequestProfileRegistry registry = new RequestProfileRegistry(prefs);
        registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK, document("old", "low"), "v1");
        registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK, document("new", "high"), "v2");
        assertEquals(List.of("new"), registry.workModels());
        assertNull(registry.find(RequestProfileEngine.Mode.WORK, "old", "low"));
        RequestProfileRegistry recreated = new RequestProfileRegistry(prefs);
        assertEquals(List.of("new"), recreated.workModels());
        assertEquals("v2", recreated.sourceVersion(RequestProfileEngine.Mode.WORK));
        assertThrows(IllegalStateException.class, () -> recreated.importWork(document("manual", "low")));
    }

    @Test public void invalidReplacementKeepsLastGoodSnapshotAndVersion() throws Exception {
        RequestProfileRegistry registry = new RequestProfileRegistry(memoryPreferences());
        registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK, document("good", "high"), "v1");
        JSONObject bad = new JSONObject(document("bad", "low"));
        bad.getJSONArray("profiles").getJSONObject(0).put("fingerprint", "0".repeat(64));
        assertThrows(Exception.class, () -> registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK, bad.toString(), "v2"));
        assertEquals(List.of("good"), registry.workModels());
        assertEquals("v1", registry.sourceVersion(RequestProfileEngine.Mode.WORK));
        registry.recordSyncFailure(RequestProfileEngine.Mode.WORK, "AUTH_REQUIRED");
        assertEquals(List.of("good"), registry.workModels());
    }

    @Test public void absentCanonicalSelectionNeverFallsBackToBuiltIn() throws Exception {
        RequestProfileRegistry registry = new RequestProfileRegistry(memoryPreferences());
        registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK, document("new", "high"), "v1");
        Schedule schedule = new Schedule(); schedule.experience="work";schedule.workModel="terra";schedule.reasoningEffort="ultra";
        registry.attach(schedule);
        assertThrows(IllegalArgumentException.class, () -> RequestProfileEngine.forSchedule(schedule));
        schedule.workModel="inherit";schedule.reasoningEffort="inherit";registry.attach(schedule);
        assertNull(RequestProfileEngine.forSchedule(schedule));
        schedule.targetType="existing";registry.attach(schedule);
        assertNull(RequestProfileEngine.forSchedule(schedule));
    }

    @Test public void chatAndWorkUpdatesAreIndependentAndPortableSettingsUnchanged() throws Exception {
        RequestProfileRegistry registry = new RequestProfileRegistry(memoryPreferences());
        List<String> originalChat = registry.chatReasonings();
        Schedule schedule = new Schedule();schedule.experience="work";schedule.workModel="new";schedule.reasoningEffort="high";
        String before = schedule.toJson().toString();
        registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK, document("new", "high"), "v1");
        registry.attach(schedule);
        assertNotNull(RequestProfileEngine.forSchedule(schedule));
        assertEquals(before, schedule.toJson().toString());
        assertEquals(originalChat, registry.chatReasonings());
        assertFalse(registry.syncEnabled());registry.setSyncEnabled(true);assertTrue(registry.syncEnabled());
    }

    @Test public void failedDiskCommitRestoresLiveLastGoodSnapshotAndVersion() throws Exception {
        boolean[] failWrites = {false};
        RequestProfileRegistry registry = new RequestProfileRegistry(memoryPreferences(failWrites));
        registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK, document("old", "high"), "v1");
        failWrites[0] = true;
        assertThrows(IllegalStateException.class, () -> registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK, document("new", "high"), "v2"));
        assertEquals(List.of("old"), registry.workModels());
        assertEquals("v1", registry.sourceVersion(RequestProfileEngine.Mode.WORK));
    }

    @Test public void initialOptInNeverClaimsBundledDefaultsAreCanonical() throws Exception {
        SharedPreferences prefs = memoryPreferences();
        RequestProfileRegistry registry = new RequestProfileRegistry(prefs);
        assertFalse(registry.workModels().isEmpty());
        registry.setSyncEnabled(true);
        assertTrue(registry.workModels().isEmpty());
        registry.recordSyncFailure(RequestProfileEngine.Mode.WORK, "AUTH_REQUIRED");
        assertTrue(new RequestProfileRegistry(prefs).workModels().isEmpty());
        registry.setSyncEnabled(false);
        assertFalse(registry.workModels().isEmpty());
    }

    @Test public void canonicalFingerprintIsOrderIndependentButRequired() throws Exception {
        JSONObject root = new JSONObject(document("new", "high"));
        JSONObject profile = root.getJSONArray("profiles").getJSONObject(0);
        JSONArray ops = profile.getJSONArray("operations");
        profile.put("operations", new JSONArray().put(ops.get(3)).put(ops.get(2)).put(ops.get(1)).put(ops.get(0)));
        RequestProfileRegistry registry = new RequestProfileRegistry(memoryPreferences());
        registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK, root.toString(), "v1");
        profile.remove("fingerprint");
        assertThrows(Exception.class, () -> registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK, root.toString(), "v2"));
    }

    static String document(String model, String reasoning) throws Exception {
        List<RequestProfileEngine.Operation> ops = List.of(RequestProfileEngine.Operation.set("model", model+"-wm"),
                RequestProfileEngine.Operation.set("thinking_effort", reasoning), RequestProfileEngine.Operation.set("conversation_origin", "tpp"),
                RequestProfileEngine.Operation.remove("service_tier"));
        JSONArray operations = new JSONArray();
        for (RequestProfileEngine.Operation op: ops) {
            JSONObject value=new JSONObject().put("op",op.kind.name()).put("path",op.path);
            if(op.value!=null)value.put("value",op.value);operations.put(value);
        }
        JSONObject entry = new JSONObject().put("signal",new JSONObject().put("model",model).put("reasoning",reasoning))
                .put("request",new JSONObject().put("model",model+"-wm").put("thinking_effort",reasoning).put("conversation_origin","tpp"))
                .put("operations",operations).put("fingerprint",RequestProfileRegistry.fingerprint(RequestProfileEngine.Mode.WORK,ops)).put("builtIn",false);
        return new JSONObject().put("schema","selfrun-work-profile-registry-v1").put("registrySchemaVersion",1)
                .put("appVersion","test").put("profiles",new JSONArray().put(entry)).toString();
    }

    static SharedPreferences memoryPreferences() { return memoryPreferences(new boolean[]{false}); }

    static SharedPreferences memoryPreferences(boolean[] failWrites) {
        Map<String,Object> values=new HashMap<>();
        Object[] editor=new Object[1];Map<String,Object> pending=new HashMap<>();
        editor[0]=Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class[]{SharedPreferences.Editor.class},(p,m,a)->{
            String name=m.getName();
            if(name.startsWith("put")){pending.put((String)a[0],a[1]);return editor[0];}
            if(name.equals("remove")){pending.put((String)a[0],null);return editor[0];}
            if(name.equals("commit")||name.equals("apply")){for(String k:pending.keySet()){Object v=pending.get(k);if(v==null)values.remove(k);else values.put(k,v);}pending.clear();return name.equals("commit")?!failWrites[0]:null;}
            throw new UnsupportedOperationException(name);
        });
        return (SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class[]{SharedPreferences.class},(p,m,a)->{
            if(m.getName().equals("edit"))return editor[0];
            if(m.getName().equals("contains"))return values.containsKey((String)a[0]);
            if(m.getName().startsWith("get"))return values.getOrDefault((String)a[0],a[1]);
            throw new UnsupportedOperationException(m.getName());
        });
    }
}
