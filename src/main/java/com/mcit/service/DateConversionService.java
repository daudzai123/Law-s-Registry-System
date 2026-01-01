package com.mcit.service;

import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.ULocale;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;

@Service
public class DateConversionService {

    public String toHijriQamariAuto(String date) {

        int year = Integer.parseInt(date.substring(0, 4));

        if (year > 1700) {
            return gregorianToHijriQamari(date);
        }

        if (year >= 1300 && year <= 1500) {
            return shamsiToHijriQamari(date);
        }

        return date; // assume Hijri-Qamari
    }

    private String gregorianToHijriQamari(String date) {

        LocalDate gregorian = LocalDate.parse(date);
        HijrahDate hijri = HijrahDate.from(gregorian);

        return format(hijri);
    }

    private String shamsiToHijriQamari(String date) {

        String[] parts = date.split("-");
        int y = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]) - 1; // ICU months are 0-based
        int d = Integer.parseInt(parts[2]);

        // Persian (Hijri-Shamsi) calendar
        Calendar shamsiCal = Calendar.getInstance(new ULocale("fa_IR@calendar=persian"));
        shamsiCal.set(y, m, d);

        // Convert to Gregorian
        Calendar gregorianCal = Calendar.getInstance(ULocale.ENGLISH);
        gregorianCal.setTime(shamsiCal.getTime());

        // Convert to Hijri-Qamari
        Calendar hijriCal = Calendar.getInstance(new ULocale("ar_SA@calendar=islamic"));
        hijriCal.setTime(gregorianCal.getTime());

        int hy = hijriCal.get(Calendar.YEAR);
        int hm = hijriCal.get(Calendar.MONTH) + 1;
        int hd = hijriCal.get(Calendar.DAY_OF_MONTH);

        return String.format("%04d-%02d-%02d", hy, hm, hd);
    }


    private String format(HijrahDate hijri) {

        int y = hijri.get(ChronoField.YEAR);
        int m = hijri.get(ChronoField.MONTH_OF_YEAR);
        int d = hijri.get(ChronoField.DAY_OF_MONTH);

        return String.format("%04d-%02d-%02d", y, m, d);
    }
}
