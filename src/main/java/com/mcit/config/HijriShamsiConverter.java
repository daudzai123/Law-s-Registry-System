package com.mcit.config;

import com.ibm.icu.util.PersianCalendar;
import com.ibm.icu.util.ULocale;

import java.time.LocalDate;
import java.util.Date;
import java.util.GregorianCalendar;

public class HijriShamsiConverter {

    // Hijri Shamsi (Solar Hijri) → Gregorian
    public static LocalDate shamsiToGregorian(int shYear, int shMonth, int shDay) {
        ULocale locale = new ULocale("fa_IR@calendar=persian"); // Persian (Solar Hijri)
        PersianCalendar shamsiCal = new PersianCalendar(locale);

        // months are 0-based in ICU
        shamsiCal.set(shYear, shMonth - 1, shDay);

        Date gregorianDate = shamsiCal.getTime();
        java.util.Calendar gCal = new GregorianCalendar();
        gCal.setTime(gregorianDate);

        return LocalDate.of(
                gCal.get(java.util.Calendar.YEAR),
                gCal.get(java.util.Calendar.MONTH) + 1,
                gCal.get(java.util.Calendar.DAY_OF_MONTH)
        );
    }

    // Gregorian → Hijri Shamsi (Solar Hijri)
    public static String gregorianToShamsi(LocalDate gregorianDate) {
        ULocale locale = new ULocale("fa_IR@calendar=persian");
        PersianCalendar shamsiCal = new PersianCalendar(locale);

        java.util.Calendar gCal = new GregorianCalendar(
                gregorianDate.getYear(),
                gregorianDate.getMonthValue() - 1,
                gregorianDate.getDayOfMonth()
        );
        shamsiCal.setTime(gCal.getTime());

        int shYear = shamsiCal.get(PersianCalendar.EXTENDED_YEAR);
        int shMonth = shamsiCal.get(PersianCalendar.MONTH) + 1; // 0-based
        int shDay = shamsiCal.get(PersianCalendar.DATE);

        return String.format("%04d-%02d-%02d", shYear, shMonth, shDay);
    }
}
