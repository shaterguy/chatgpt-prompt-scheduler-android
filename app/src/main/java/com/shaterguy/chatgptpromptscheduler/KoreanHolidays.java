package com.shaterguy.chatgptpromptscheduler;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class KoreanHolidays {
    // KASA almanacs, subsequent statutory changes, and the KASI 2028 calendar forecast.
    private static final Set<LocalDate> BUNDLED = dates("""
            20250101 20250127 20250128 20250129 20250130 20250301 20250303
            20250505 20250506 20250603 20250606 20250815 20251003
            20251005 20251006 20251007 20251008 20251009 20251225
            20260101 20260216 20260217 20260218 20260301 20260302
            20260501 20260505 20260524 20260525 20260603 20260606 20260717
            20260815 20260817 20260924 20260925 20260926
            20261003 20261005 20261009 20261225
            20270101 20270206 20270207 20270208 20270209 20270301
            20270501 20270503 20270505 20270513 20270606 20270717 20270719
            20270815 20270816 20270914 20270915 20270916
            20271003 20271004 20271009 20271011 20271225 20271227
            20280101 20280126 20280127 20280128 20280301 20280412
            20280501 20280502 20280505 20280606 20280717 20280815
            20281002 20281003 20281004 20281005 20281009 20281225
            """);
    private static final List<MonthDay> FIXED_WITH_SUBSTITUTE = Arrays.asList(
            MonthDay.of(3, 1), MonthDay.of(5, 1), MonthDay.of(5, 5), MonthDay.of(7, 17),
            MonthDay.of(8, 15), MonthDay.of(10, 3), MonthDay.of(10, 9), MonthDay.of(12, 25));

    private KoreanHolidays() {}

    static boolean isClosed(LocalDate date, Set<LocalDate> publishedHolidays) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
                || BUNDLED.contains(date) || publishedHolidays.contains(date)) return true;
        if (date.getYear() <= 2028) return false;
        MonthDay monthDay = MonthDay.from(date);
        return FIXED_WITH_SUBSTITUTE.contains(monthDay)
                || monthDay.equals(MonthDay.of(1, 1)) || monthDay.equals(MonthDay.of(6, 6))
                || (day == DayOfWeek.MONDAY && (FIXED_WITH_SUBSTITUTE.contains(MonthDay.from(date.minusDays(1)))
                || FIXED_WITH_SUBSTITUTE.contains(MonthDay.from(date.minusDays(2)))));
    }

    static Set<LocalDate> dates(String input) {
        Set<LocalDate> dates = new HashSet<>();
        for (String date : input.trim().split("\\s+")) {
            if (!date.isEmpty()) dates.add(LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE));
        }
        return dates;
    }

    static Set<LocalDate> parsePublicHolidayCalendar(String calendar) {
        String[] lines = calendar.replaceAll("\\r?\\n[ \\t]", "").split("\\r?\\n");
        if (lines.length < 2 || !"BEGIN:VCALENDAR".equals(lines[0])
                || !"END:VCALENDAR".equals(lines[lines.length - 1])) {
            throw new IllegalArgumentException("Invalid public holiday calendar");
        }
        Set<LocalDate> dates = new HashSet<>();
        boolean inEvent = false;
        boolean publicHoliday = false;
        boolean cancelled = false;
        LocalDate start = null;
        LocalDate end = null;
        for (String line : lines) {
            if ("BEGIN:VEVENT".equals(line)) {
                inEvent = true;
                publicHoliday = false;
                cancelled = false;
                start = null;
                end = null;
            } else if (inEvent && "END:VEVENT".equals(line)) {
                if (publicHoliday && !cancelled && start != null && end != null
                        && end.isAfter(start) && !end.isAfter(start.plusDays(14))) {
                    for (LocalDate date = start; date.isBefore(end); date = date.plusDays(1)) dates.add(date);
                }
                inEvent = false;
            } else if (inEvent) {
                if ("DESCRIPTION:공휴일".equals(line)) publicHoliday = true;
                if ("STATUS:CANCELLED".equals(line)) cancelled = true;
                if (line.startsWith("DTSTART;VALUE=DATE:")) start = parseDate(line);
                if (line.startsWith("DTEND;VALUE=DATE:")) end = parseDate(line);
            }
        }
        if (inEvent || dates.isEmpty()) throw new IllegalArgumentException("No public holidays in calendar");
        return dates;
    }

    private static LocalDate parseDate(String line) {
        String date = line.substring(line.indexOf(':') + 1);
        return date.matches("[0-9]{8}") ? LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE) : null;
    }
}
