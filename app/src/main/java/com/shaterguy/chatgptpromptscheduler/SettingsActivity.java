package com.shaterguy.chatgptpromptscheduler;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class SettingsActivity extends Activity {
    private static final int REQUEST_EXPORT = 1001;
    private static final int REQUEST_IMPORT = 1002;
    private ProfileAuthorizationSession authorizationAttempt;
    private TextView diagnosticStatus;
    private ConfigStore store;
    private RequestProfileRegistry profileRegistry;
    private ProjectCatalog projectCatalog;
    private CheckBox notifySuccess;
    private CheckBox notifyFailure;
    private EditText missedGrace;
    private EditText maxRetries;
    private EditText timeout;
    private TextView profileStatus;
    private String googleAuthorizationStatus = "";
    private TextView projectStatus;
    private TextView readinessStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authorizationAttempt = new ProfileAuthorizationSession(savedInstanceState == null ? ProfileAuthorizationSession.FIRST_REQUEST_CODE
                : savedInstanceState.getInt("nextConsentRequestCode", ProfileAuthorizationSession.FIRST_REQUEST_CODE));
        store = new ConfigStore(this);
        profileRegistry = new RequestProfileRegistry(this);
        projectCatalog = new ProjectCatalog(this);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        if (profileStatus != null) profileStatus.setText(profileStatusText());
        if (projectStatus != null) projectStatus.setText(projectStatusText());
        if (readinessStatus != null) readinessStatus.setText(readinessStatusText());
        ProfileRegistrySync.refresh(this, result -> updateProfileStatus());
    }

    @Override protected void onDestroy() {
        // Consent state intentionally does not survive Activity/process recreation.
        authorizationAttempt.cancel(ProfileAuthorizationSession.Outcome.DESTROYED, android.os.SystemClock.elapsedRealtime());
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        // Only the next result routing code survives recreation, never a pending authorization.
        state.putInt("nextConsentRequestCode", authorizationAttempt.nextRequestCode());
        super.onSaveInstanceState(state);
    }

    private void buildUi() {
        JSONObject settings = store.settings();
        ScrollView scroll = Ui.scroll(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 24));
        scroll.addView(root);
        root.addView(Ui.title(this, "설정"));

        root.addView(Ui.section(this, "실행 준비 상태"));
        readinessStatus = Ui.body(this, readinessStatusText());
        root.addView(readinessStatus);
        root.addView(Ui.body(this, "프로젝트 등록은 로그인/세션 화면에서 관리합니다."));
        root.addView(Ui.actionGrid(this,
                Ui.button(this, "알람 권한", v -> requestExactAlarm()),
                Ui.button(this, "절전 제외", v -> requestBatteryExemption()),
                Ui.button(this, "로그인/세션", v -> startActivity(new Intent(this, LoginActivity.class)))));

        root.addView(Ui.section(this, "모델 · 추론 프로필"));
        root.addView(Ui.body(this,
                "자동으로 확인한 공식 목록의 모델·추론 조합만 선택할 수 있습니다. 예전 수동 목록은 더 이상 사용하지 않습니다. 기존 예약과 프롬프트는 유지되며, 목록에 없는 조합은 다시 선택해야 합니다."));
        root.addView(Ui.body(this, "Google 로그인·동의 후 공식 Chat/Work 문서가 바뀌면 모델과 추론 목록을 자동으로 갱신합니다. Drive 메타데이터와 Docs 읽기 전용 권한을 요청하며, 앱은 지정된 두 문서만 읽습니다. 동기화 실패 시 마지막 정상 목록을 유지합니다."));
        root.addView(Ui.actionGrid(this,
                Ui.button(this, "Google 로그인 · 목록 업데이트", v -> authorizeProfiles()),
                Ui.button(this, "자동 업데이트 끄기", v -> {
                    try {
                        authorizationAttempt.cancel(ProfileAuthorizationSession.Outcome.DISABLED, android.os.SystemClock.elapsedRealtime());
                        updateDiagnosticStatus();
                        profileRegistry.setSyncEnabled(false); updateProfileStatus();
                        toast("자동 업데이트를 껐습니다. 마지막 목록은 유지됩니다. Google 계정의 앱 접근 권한은 계정 설정에서 관리할 수 있습니다.");
                    } catch (IllegalStateException error) { toast("저장 공간을 확인해 주세요. 이전 설정은 유지됩니다."); }
                })));
        profileStatus = Ui.body(this, profileStatusText());
        root.addView(profileStatus);
        addAuthorizationDiagnostics(root);

        root.addView(Ui.section(this, "프로젝트 목록"));
        projectStatus = Ui.body(this, projectStatusText());
        root.addView(projectStatus);
        root.addView(Ui.button(this, "프로젝트 목록 전체 삭제", v -> confirmClearProjects()));

        root.addView(Ui.section(this, "실행 설정"));
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
        root.addView(Ui.section(this, "백업 및 진단"));
        root.addView(Ui.actionGrid(this,
                Ui.button(this, "설정 내보내기", v -> exportConfig()),
                Ui.button(this, "설정 가져오기", v -> importConfig()),
                Ui.button(this, "실행 기록", v -> startActivity(new Intent(this, LogsActivity.class))),
                Ui.button(this, "알람 재구축", v -> {
                    AlarmEngine.rebuildAll(this);
                    toast("알람을 다시 등록했습니다.");
                })));
        root.addView(Ui.actionGrid(this,
                Ui.button(this, "저장", v -> save()),
                Ui.button(this, "취소", v -> finish())));
        Ui.setContent(this, scroll);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode >= ProfileAuthorizationSession.FIRST_REQUEST_CODE && requestCode <= 65535) {
            long attempt = authorizationAttempt.takeConsentResult(requestCode, resultCode, data != null, android.os.SystemClock.elapsedRealtime());
            if (attempt == 0L) {
                toast("로그인 요청이 취소되었거나 화면이 다시 열렸습니다. 목록 업데이트가 필요하면 Google 로그인 버튼을 다시 눌러 주세요.");
                return;
            }
            updateDiagnosticStatus();
            // Google’s parser handles both successful and failed returned Intents.
            ProfileDriveAuthorization.fromIntent(this, data, resultCode, authorizationCallback(attempt));
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT) {
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                output.write(store.exportPortable().toString(2).getBytes(StandardCharsets.UTF_8));
                toast("설정 JSON을 저장했습니다.");
            } catch (Exception error) { toast("내보내기 실패: " + error.getMessage()); }
        } else if (requestCode == REQUEST_IMPORT) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
                StringBuilder contents = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) contents.append(line).append('\n');
                JSONObject incoming = new JSONObject(contents.toString());
                int count = incoming.optJSONArray("schedules") == null ? 0 : incoming.optJSONArray("schedules").length();
                new AlertDialog.Builder(this).setTitle("설정 가져오기")
                        .setMessage("현재 예약과 설정을 교체하고 " + count
                                + "개 예약을 가져옵니다. 로그인 쿠키와 실행 기록은 변경하지 않습니다.")
                        .setNegativeButton("취소", null).setPositiveButton("교체", (dialog, which) -> {
                            try {
                                int imported = store.importPortable(incoming);
                                AlarmEngine.rebuildAll(this);
                                toast(imported + "개 예약을 가져왔습니다.");
                                buildUi(); // Import replaces settings; reload form from the new snapshot.
                            } catch (Exception error) { toast("가져오기 실패: " + error.getMessage()); }
                        }).show();
            } catch (Exception error) { toast("파일 읽기 실패: " + error.getMessage()); }
        }
    }

    private void authorizeProfiles() {
        long attempt = authorizationAttempt.begin(android.os.SystemClock.elapsedRealtime());
        if (attempt == 0L) return;
        googleAuthorizationStatus = "";
        recordGoogleEnvironment(attempt);
        updateProfileStatus(); updateDiagnosticStatus();
        try { ProfileDriveAuthorization.request(this, authorizationAttempt.mode(), authorizationCallback(attempt)); }
        catch (RuntimeException error) {
            authorizationCallback(attempt).failed(new ProfileAuthorizationIssue(ProfileAuthorizationIssue.Kind.SDK_ERROR,null,null));
        }
    }

    private ProfileDriveAuthorization.Callback authorizationCallback(long attempt) {
        return new ProfileDriveAuthorization.Callback() {
            public void authorized(String token) {
                recordGoogleEnvironment(attempt);
                if (!authorizationAttempt.succeed(attempt, android.os.SystemClock.elapsedRealtime()) || isFinishing() || isDestroyed()) return;
                updateDiagnosticStatus();
                googleAuthorizationStatus = "";
                try { profileRegistry.setSyncEnabled(true); }
                catch (IllegalStateException error) { toast("설정을 저장하지 못했습니다. 저장 공간을 확인해 주세요."); return; }
                updateProfileStatus();
                ProfileRegistrySync.refreshWithToken(SettingsActivity.this, token, result -> {
                    if (isFinishing() || isDestroyed()) return;
                    updateProfileStatus();
                    toast(result.failed ? "일부 목록을 확인하지 못했습니다. 문서 접근 권한과 연결을 확인해 주세요. 마지막 정상 목록은 유지됩니다."
                            : "공식 모델·추론 목록을 확인했습니다.");
                });
            }
            public void resolution(android.app.PendingIntent pendingIntent) {
                if (isFinishing() || isDestroyed() || !authorizationAttempt.current(attempt)) return;
                recordGoogleEnvironment(attempt);
                int requestCode = authorizationAttempt.launchConsent(attempt, android.os.SystemClock.elapsedRealtime());
                if (requestCode == 0) { failed(new ProfileAuthorizationIssue(ProfileAuthorizationIssue.Kind.LAUNCH_ERROR,null,null)); return; }
                updateDiagnosticStatus();
                try { startIntentSenderForResult(pendingIntent.getIntentSender(), requestCode, null, 0, 0, 0); }
                catch (android.content.IntentSender.SendIntentException | RuntimeException error) {
                    failed(new ProfileAuthorizationIssue(ProfileAuthorizationIssue.Kind.LAUNCH_ERROR,null,null));
                }
            }
            public void failed(ProfileAuthorizationIssue issue) {
                recordGoogleEnvironment(attempt);
                if (!authorizationAttempt.fail(attempt, issue, android.os.SystemClock.elapsedRealtime()) || isFinishing() || isDestroyed()) return;
                updateDiagnosticStatus();
                googleAuthorizationStatus = issue.message();
                updateProfileStatus();
                toast(googleAuthorizationStatus + " 저장된 목록은 유지됩니다.");
            }
        };
    }

    private void addAuthorizationDiagnostics(LinearLayout root) {
        LinearLayout diagnostics = new LinearLayout(this);
        diagnostics.setOrientation(LinearLayout.VERTICAL);
        diagnostics.setVisibility(android.view.View.GONE);
        root.addView(Ui.button(this, "Google 연결 진단", v -> diagnostics.setVisibility(
                diagnostics.getVisibility() == android.view.View.VISIBLE ? android.view.View.GONE : android.view.View.VISIBLE)));
        diagnostics.addView(Ui.body(this, "기본 요청으로 먼저 확인하세요. 비교가 필요할 때만 아래 옵션을 선택한 뒤 Google 로그인 버튼을 직접 누르세요. 모드를 바꾸면 진행 중인 요청은 취소됩니다. 최근 4회 기록은 이 설정 화면에만 남으며, 계정이나 토큰은 포함하지 않습니다. 기존 권한을 재사용했는지는 확인할 수 없습니다."));
        CheckBox comparison = new CheckBox(this);
        comparison.setText("이전 동의 요청 방식과 비교");
        comparison.setOnCheckedChangeListener((button, checked) -> {
            authorizationAttempt.selectMode(checked ? ProfileAuthorizationSession.Mode.PREVIOUS_CONSENT
                    : ProfileAuthorizationSession.Mode.DEFAULT, android.os.SystemClock.elapsedRealtime());
            updateDiagnosticStatus();
        });
        diagnostics.addView(comparison);
        diagnosticStatus = Ui.body(this, authorizationAttempt.report(android.os.SystemClock.elapsedRealtime()));
        diagnostics.addView(diagnosticStatus);
        diagnostics.addView(Ui.button(this, "진단 내용 복사", v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null) { toast("클립보드를 사용할 수 없습니다."); return; }
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Google 연결 진단",
                    ProfileAuthorizationEnvironment.report(this) + authorizationAttempt.report(android.os.SystemClock.elapsedRealtime())));
            toast("계정·토큰을 제외한 연결 진단을 복사했습니다.");
        }));
        root.addView(diagnostics);
    }

    private void recordGoogleEnvironment(long attempt) {
        if (!authorizationAttempt.current(attempt)) return;
        authorizationAttempt.observeGoogle(attempt, ProfileAuthorizationEnvironment.googleVersion(this),
                ProfileAuthorizationEnvironment.googleAvailability(this));
    }

    private void updateDiagnosticStatus() {
        if (!isFinishing() && !isDestroyed() && diagnosticStatus != null)
            diagnosticStatus.setText(authorizationAttempt.report(android.os.SystemClock.elapsedRealtime()));
    }

    private void updateProfileStatus() {
        if (!isFinishing() && !isDestroyed() && profileStatus != null) profileStatus.setText(profileStatusText());
    }

    private void confirmClearProjects() {
        int count = projectCatalog.entries().size();
        if (count == 0) { toast("삭제할 프로젝트가 없습니다."); return; }
        new AlertDialog.Builder(this)
                .setTitle("프로젝트 목록 전체 삭제")
                .setMessage("등록된 프로젝트 " + count + "개의 주소와 저장 이름을 모두 삭제합니다. 예약 작업, 로그인 세션, 실행 기록은 삭제하지 않습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("전체 삭제", (dialog, which) -> {
                    try {
                        int cleared = projectCatalog.clearAll();
                        if (projectStatus != null) projectStatus.setText(projectStatusText());
                        toast(cleared + "개 프로젝트를 삭제했습니다. 다시 방문하면 처음부터 등록됩니다.");
                    } catch (RuntimeException error) { toast("프로젝트 삭제 실패: " + error.getMessage()); }
                }).show();
    }

    private String readinessStatusText() {
        AlarmManager alarms = getSystemService(AlarmManager.class);
        boolean exact = Build.VERSION.SDK_INT < 31 || (alarms != null && alarms.canScheduleExactAlarms());
        PowerManager power = getSystemService(PowerManager.class);
        boolean battery = power != null && power.isIgnoringBatteryOptimizations(getPackageName());
        return (exact ? "✓" : "✕") + " 정확한 알람 권한\n"
                + (battery ? "✓" : "✕") + " 배터리 최적화 제외";
    }

    private void requestExactAlarm() {
        if (Build.VERSION.SDK_INT >= 31) {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        } else toast("이 Android 버전에서는 별도 권한이 필요하지 않습니다.");
    }

    private void requestBatteryExemption() {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()));
        try { startActivity(intent); }
        catch (Exception error) { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
    }

    private void exportConfig() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "chatgpt-prompt-scheduler-settings.json");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void importConfig() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json")
                .addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    private String profileStatusText() {
        return "현재 등록: Chat " + profileRegistry.count(RequestProfileEngine.Mode.CHAT)
                + "개 · Work " + profileRegistry.count(RequestProfileEngine.Mode.WORK) + "개\n" + profileRegistry.syncStatusText()
                + (googleAuthorizationStatus.isEmpty() ? "" : "\nGoogle 연결: " + googleAuthorizationStatus);
    }

    private String projectStatusText() { return "등록된 프로젝트: " + projectCatalog.entries().size() + "개"; }

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
            JSONObject settings = new JSONObject(store.settings().toString());
            settings.put("notifySuccess", notifySuccess.isChecked());
            settings.put("notifyFailure", notifyFailure.isChecked());
            settings.put("missedGraceMinutes", clamp(parse(missedGrace, 30), 0, 1440));
            settings.put("maxRetries", clamp(parse(maxRetries, 2), 0, 5));
            settings.put("executionTimeoutSeconds", clamp(parse(timeout, 90), 30, 300));
            store.saveSettings(settings);
            toast("설정을 저장했습니다.");
            finish();
        } catch (JSONException error) { toast("설정 저장 실패: " + error.getMessage()); }
    }

    private int parse(EditText field, int fallback) {
        try { return Integer.parseInt(field.getText().toString().trim()); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
