package com.mcit.config;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.chrono.HijrahDate;
import java.time.format.DateTimeFormatter;

public class HijriDateUtil {

    // Hijri date format
    private static final DateTimeFormatter HIJRI_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Time format without milliseconds
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Converts a Gregorian LocalDateTime to a Hijri date string with time.
     * Example output: "1447-07-14 11:37:03"
     */
    public static String toHijri(LocalDateTime dateTime) {
        if (dateTime == null) return null;

        // Convert to Hijri date
        HijrahDate hijrahDate = HijrahDate.from(dateTime.atZone(ZoneId.systemDefault()));

        // Combine Hijri date with formatted time
        return hijrahDate.format(HIJRI_FORMATTER) + " " + dateTime.format(TIME_FORMATTER);
    }
}
