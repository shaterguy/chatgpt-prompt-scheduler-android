package com.shaterguy.chatgptpromptscheduler;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class ScheduleViewAndSettingsAndroidTest {
    @Test public void mainExposesListSelectorsButNotOperationalMenuItems() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View screen = activity.getWindow().getDecorView();
                assertEquals(2, countSpinners(screen));
                assertTrue(hasExactText(screen, "보기"));
                assertTrue(hasExactText(screen, "정렬"));
                assertTrue(hasExactText(screen, "설정"));
                assertFalse(hasExactText(screen, "설정 내보내기"));
                assertFalse(hasExactText(screen, "알람 재구축"));
                assertFalse(hasExactText(screen, "실행 준비 상태"));
            });
        }
    }

    @Test public void settingsOwnsPermissionsProfilesProjectsBackupsAndLogs() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                View screen = activity.getWindow().getDecorView();
                for (String label : new String[] {
                        "실행 준비 상태", "알람 권한", "절전 제외", "로그인/세션",
                        "모델 · 추론 프로필", "프로젝트 목록", "실행 설정",
                        "백업 및 진단", "설정 내보내기", "설정 가져오기",
                        "실행 기록", "알람 재구축"
                }) {
                    assertTrue("Missing settings menu: " + label, hasExactText(screen, label));
                }
            });
        }
    }

    private static int countSpinners(View view) {
        int count = view instanceof Spinner ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) count += countSpinners(group.getChildAt(i));
        }
        return count;
    }

    private static boolean hasExactText(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasExactText(group.getChildAt(i), value)) return true;
            }
        }
        return false;
    }
}
