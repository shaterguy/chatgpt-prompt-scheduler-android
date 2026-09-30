package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/** Runs only in the test APK's isolated emulator/application data. Never contacts Google. */
@RunWith(AndroidJUnit4.class)
public final class AutoOnlyProfileUiAndroidTest {
    private Context context;
    private SharedPreferences profiles, config;
    private Map<String, ?> savedProfiles, savedConfig;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        profiles = context.getSharedPreferences("scheduler_request_profile_registry_v1", Context.MODE_PRIVATE);
        config = context.getSharedPreferences("scheduler_config", Context.MODE_PRIVATE);
        savedProfiles = profiles.getAll(); savedConfig = config.getAll();
        assertTrue(profiles.edit().clear().putBoolean("drive_sync_enabled", false).commit());
        assertTrue(config.edit().clear().commit());
    }
    @After public void restorePreferences() { restore(profiles, savedProfiles); restore(config, savedConfig); }

    @Test public void settingsHasNoManualCaptureOrImportControlsAndShowsUpdateNeeded() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                String text = visibleText(activity.getWindow().getDecorView());
                assertFalse(text.contains("모델·추론 캡처"));
                assertFalse(text.contains("설정파일 가져오기"));
                assertTrue(text.contains("Google 로그인 · 목록 업데이트"));
                assertTrue(text.contains("업데이트 필요"));
                activity.onActivityResult(2101, -1, new Intent());
                assertEquals(0, new RequestProfileRegistry(context).count(RequestProfileEngine.Mode.CHAT));
            });
        }
    }

    @Test public void legacyChatInheritRemainsAnExplicitNativeChoice() {
        Schedule schedule = new Schedule();schedule.chatReasoning="inherit";schedule.prompt="keep native";schedule.enabled=false;
        new ConfigStore(context).saveSchedule(schedule);
        Intent intent = new Intent(context, ScheduleEditorActivity.class).putExtra("scheduleId", schedule.id);
        try (ActivityScenario<ScheduleEditorActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                List<String> values = field(activity, "chatReasoningValues");
                Spinner spinner = field(activity, "chatReasoning");
                assertEquals("keep", values.get(spinner.getSelectedItemPosition()));
                assertEquals(List.of("", "keep"), values);
            });
        }
    }

    @Test public void obsoleteSelectionCannotBeSavedAsTheFirstAutomaticModel() throws Exception {
        new RequestProfileRegistry(context).replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK, workDocument(), "v4");
        Schedule schedule = new Schedule();schedule.experience="work";schedule.workModel="manual-old";
        schedule.reasoningEffort="high";schedule.prompt="preserved prompt";schedule.enabled=false;
        new ConfigStore(context).saveSchedule(schedule);
        String before = config.getString("config_json", "");
        Intent intent = new Intent(context, ScheduleEditorActivity.class).putExtra("scheduleId", schedule.id);
        try (ActivityScenario<ScheduleEditorActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                List<String> models = field(activity, "workModelValues");
                Spinner model = field(activity, "workModel");
                assertFalse(models.contains("manual-old"));
                assertTrue(models.contains("automatic"));
                assertEquals("", models.get(model.getSelectedItemPosition()));
                assertFalse(((android.widget.ListAdapter)model.getAdapter()).isEnabled(0));
                ((EditText)field(activity, "name")).setText("must not be saved yet");
                invokeSave(activity);
                assertEquals(before, config.getString("config_json", ""));
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                List<String> models = field(activity, "workModelValues");
                Spinner model = field(activity, "workModel");
                assertEquals("", models.get(model.getSelectedItemPosition()));
                invokeSave(activity);
                assertEquals(before, config.getString("config_json", ""));
            });
        }
    }

    @Test public void changingToAutomaticModelRequiresOneOfItsExactReasoningChoices() throws Exception {
        new RequestProfileRegistry(context).replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK, workDocument(), "v4");
        Schedule schedule = new Schedule();schedule.experience="work";schedule.workModel="manual-old";
        schedule.reasoningEffort="high";schedule.prompt="preserved prompt";schedule.enabled=false;
        new ConfigStore(context).saveSchedule(schedule);
        String before = config.getString("config_json", "");
        Intent intent = new Intent(context, ScheduleEditorActivity.class).putExtra("scheduleId", schedule.id);
        try (ActivityScenario<ScheduleEditorActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                List<String> models = field(activity, "workModelValues");
                ((Spinner)field(activity, "workModel")).setSelection(models.indexOf("automatic"));
            });
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                List<String> efforts = field(activity, "workReasoningValues");
                assertEquals(List.of("", "high"), efforts);
                assertEquals(0, ((Spinner)field(activity, "reasoningEffort")).getSelectedItemPosition());
                invokeSave(activity);assertEquals(before, config.getString("config_json", ""));
                ((Spinner)field(activity, "reasoningEffort")).setSelection(efforts.indexOf("high"));
            });
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(AutoOnlyProfileUiAndroidTest::invokeSave);
        }
        Schedule saved = new ConfigStore(context).findSchedule(schedule.id);
        assertEquals("automatic", saved.workModel);assertEquals("high", saved.reasoningEffort);
        assertEquals("preserved prompt", saved.prompt);assertFalse(saved.enabled);
    }

    private static String workDocument() throws Exception {
        List<RequestProfileEngine.Operation> ops = List.of(RequestProfileEngine.Operation.set("model", "automatic-model"),
                RequestProfileEngine.Operation.set("thinking_effort", "high"), RequestProfileEngine.Operation.set("conversation_origin", "tpp"),
                RequestProfileEngine.Operation.remove("service_tier"));
        JSONArray operations = new JSONArray();
        for (RequestProfileEngine.Operation op : ops) {
            JSONObject value = new JSONObject().put("op", op.kind.name()).put("path", op.path);
            if (op.value != null) value.put("value", op.value);operations.put(value);
        }
        JSONObject profile = new JSONObject().put("signal", new JSONObject().put("model", "automatic").put("reasoning", "high"))
                .put("request", new JSONObject().put("model", "automatic-model").put("thinking_effort", "high").put("conversation_origin", "tpp"))
                .put("operations", operations).put("builtIn", true).put("fingerprint", RequestProfileRegistry.fingerprint(RequestProfileEngine.Mode.WORK, ops));
        return new JSONObject().put("schema", "selfrun-work-profile-registry-v1").put("registrySchemaVersion", 1)
                .put("appVersion", "fixture").put("profiles", new JSONArray().put(profile)).toString();
    }
    @SuppressWarnings("unchecked") private static <T> T field(Object owner, String name) {
        try { Field field = owner.getClass().getDeclaredField(name);field.setAccessible(true);return (T)field.get(owner); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private static void invokeSave(ScheduleEditorActivity activity) {
        try { Method method = ScheduleEditorActivity.class.getDeclaredMethod("save");method.setAccessible(true);method.invoke(activity); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private static String visibleText(View view) {
        StringBuilder out = new StringBuilder();
        if (view instanceof TextView) out.append(((TextView)view).getText()).append('\n');
        if (view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++)out.append(visibleText(((ViewGroup)view).getChildAt(i)));
        return out.toString();
    }
    private static void restore(SharedPreferences prefs, Map<String, ?> values) {
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (Map.Entry<String, ?> item : values.entrySet()) {
            Object v=item.getValue();String k=item.getKey();
            if(v instanceof String)editor.putString(k,(String)v);else if(v instanceof Boolean)editor.putBoolean(k,(Boolean)v);
            else if(v instanceof Long)editor.putLong(k,(Long)v);else if(v instanceof Integer)editor.putInt(k,(Integer)v);
            else if(v instanceof Float)editor.putFloat(k,(Float)v);
        }
        assertTrue(editor.commit());
    }
}
