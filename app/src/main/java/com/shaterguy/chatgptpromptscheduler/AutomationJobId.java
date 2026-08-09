package com.shaterguy.chatgptpromptscheduler;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/** Protocol-compatible, app-owned Job IDs. */
public final class AutomationJobId {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private AutomationJobId() {}

    public static String create(Set<String> used) {
        for (int attempt = 0; attempt < 32; attempt++) {
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            StringBuilder suffix = new StringBuilder(6);
            for (int i = 0; i < 6; i++) suffix.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
            String candidate = "AR-" + format.format(new Date()) + "-" + suffix;
            if (used == null || !used.contains(candidate)) return candidate;
        }
        throw new IllegalStateException("중복되지 않는 오토런 Job ID를 생성하지 못했습니다.");
    }
}
