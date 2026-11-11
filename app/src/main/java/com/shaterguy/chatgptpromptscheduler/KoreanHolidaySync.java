package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class KoreanHolidaySync {
    private static final String CALENDAR_URL = "https://calendar.google.com/calendar/ical/"
            + "ko.south_korea.official%23holiday%40group.v.calendar.google.com/public/basic.ics";
    private static final String DATES = "dates";
    private static final String LAST_ATTEMPT = "lastAttempt";
    private static final String LAST_SUCCESS = "lastSuccess";
    private static final long REFRESH_MS = 60 * 60_000L;
    private static final long RETRY_MS = 10 * 60_000L;
    private static final int MAX_CALENDAR_CHARS = 256 * 1024;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    interface Callback {
        void onFinished(boolean changed);
    }

    private KoreanHolidaySync() {}

    static Set<LocalDate> cached(Context context) {
        Set<LocalDate> dates = new HashSet<>();
        for (String date : preferences(context).getStringSet(DATES, Collections.emptySet())) {
            dates.add(LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE));
        }
        return dates;
    }

    static void refresh(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        executor.execute(() -> {
            boolean changed = updateCache(app);
            new Handler(Looper.getMainLooper()).post(() -> callback.onFinished(changed));
        });
    }

    private static boolean updateCache(Context context) {
        SharedPreferences prefs = preferences(context);
        long now = System.currentTimeMillis();
        if (recent(now, prefs.getLong(LAST_SUCCESS, 0), REFRESH_MS)
                || recent(now, prefs.getLong(LAST_ATTEMPT, 0), RETRY_MS)) return false;
        prefs.edit().putLong(LAST_ATTEMPT, now).apply();
        try {
            Set<LocalDate> dates = KoreanHolidays.parsePublicHolidayCalendar(download());
            Set<String> encoded = new HashSet<>();
            for (LocalDate date : dates) encoded.add(date.format(DateTimeFormatter.BASIC_ISO_DATE));
            boolean changed = !encoded.equals(prefs.getStringSet(DATES, Collections.emptySet()));
            prefs.edit().putStringSet(DATES, encoded).putLong(LAST_SUCCESS, now).apply();
            return changed;
        } catch (IOException | RuntimeException unavailable) {
            return false;
        }
    }

    private static boolean recent(long now, long timestamp, long window) {
        return timestamp > 0 && now >= timestamp && now - timestamp < window;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("korean_public_holidays", Context.MODE_PRIVATE);
    }

    private static String download() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(CALENDAR_URL).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        connection.setUseCaches(false);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Public holiday calendar is unavailable");
            }
            try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                StringBuilder calendar = new StringBuilder();
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    if (calendar.length() + count > MAX_CALENDAR_CHARS) throw new IOException("Calendar is too large");
                    calendar.append(buffer, 0, count);
                }
                return calendar.toString();
            }
        } finally {
            connection.disconnect();
        }
    }
}
