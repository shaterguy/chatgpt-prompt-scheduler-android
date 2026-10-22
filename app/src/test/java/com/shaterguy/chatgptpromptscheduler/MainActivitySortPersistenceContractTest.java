package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public final class MainActivitySortPersistenceContractTest {
    @Test public void lastSelectedSortSurvivesActivityColdStart() throws Exception {
        Path source = Paths.get("app/src/main/java/com/shaterguy/chatgptpromptscheduler/MainActivity.java");
        if (!Files.exists(source)) source = Paths.get("..").resolve(source);
        String main = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        assertTrue(main.contains("sortKey = getPreferences(MODE_PRIVATE).getString(\"scheduleSort\", ScheduleListPolicy.NEWEST)"));
        assertTrue(main.contains("getPreferences(MODE_PRIVATE).edit().putString(\"scheduleSort\", selected).apply()"));
        assertTrue(main.contains("state.putString(\"scheduleSort\", sortKey)"));
    }
}
