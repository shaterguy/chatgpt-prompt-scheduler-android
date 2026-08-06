package com.shaterguy.chatgptpromptscheduler;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimestampUtil {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd | HH:mm:ss").withZone(ZoneId.of("Asia/Seoul"));
    private TimestampUtil() {}
    public static String prefix(long epochMillis, String prompt) {
        return "[" + FORMAT.format(Instant.ofEpochMilli(epochMillis)) + "]\n" + prompt;
    }
}
