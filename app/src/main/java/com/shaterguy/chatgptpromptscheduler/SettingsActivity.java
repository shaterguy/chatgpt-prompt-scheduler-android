package com.shaterguy.chatgptpromptscheduler;

import android.app.Activity;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

public final class SettingsActivity extends Activity {
    private ConfigStore store;
    private CheckBox notifySuccess;
    private CheckBox notifyFailure;
    private EditText missedGrace;
    private EditText maxRetries;
    private EditText timeout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ConfigStore(this);
        JSONObject settings = store.settings();
        ScrollView scroll = Ui.scroll(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 24));
        scroll.addView(root);
        root.addView(Ui.title(this, "설정"));
        notifySuccess = new CheckBox(this);
        notifySuccess.setText("성공 알림");
        notifySuccess.setChecked(settings.optBoolean("notifySuccess", true));
        root.addView(notifySuccess);
        notifyFailure = new CheckBox(this);
        notifyFailure.setText("실패 알림");
        notifyFailure.setChecked(settings.optBoolean("notifyFailure", true));
        root.addView(notifyFailure);
        missedGrace = field(root, "누락 실행 허용 시간(분)", String.valueOf(settings.optInt("missedGraceMinutes", 30)));
        maxRetries = field(root, "기본 재시도 횟수(0∼5)", String.valueOf(settings.optInt("maxRetries", 2)));
        timeout = field(root, "실행 제한 시간(초, 30∼300)", String.valueOf(settings.optInt("executionTimeoutSeconds", 90)));
        root.addView(Ui.actionGrid(this,
                Ui.button(this, "저장", v -> save()),
                Ui.button(this, "취소", v -> finish())));
        Ui.setContent(this, scroll);
    }

    private EditText field(LinearLayout root, String label, String value) {
        root.addView(Ui.body(this, label));
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        edit.setText(value);
        root.addView(edit);
        return edit;
    }

    private void save() {
        try {
            JSONObject settings = new JSONObject();
            settings.put("notifySuccess", notifySuccess.isChecked());
            settings.put("notifyFailure", notifyFailure.isChecked());
            settings.put("missedGraceMinutes", clamp(parse(missedGrace, 30), 0, 1440));
            settings.put("maxRetries", clamp(parse(maxRetries, 2), 0, 5));
            settings.put("executionTimeoutSeconds", clamp(parse(timeout, 90), 30, 300));
            store.saveSettings(settings);
            Toast.makeText(this, "설정을 저장했습니다.", Toast.LENGTH_LONG).show();
            finish();
        } catch (JSONException error) {
            Toast.makeText(this, "설정 저장 실패: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private int parse(EditText field, int fallback) {
        try { return Integer.parseInt(field.getText().toString().trim()); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
