package ir.maniimani.driveclock;

import java.util.Calendar;

/** Gregorian to Jalali (Persian) date, with Persian month and weekday names. */
final class Jalali {
    private Jalali() {
    }

    private static final String[] MONTHS = {"فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"};
    // Calendar.DAY_OF_WEEK: 1 = Sunday
    private static final String[] WEEKDAYS = {"یکشنبه", "دوشنبه", "سه\u200Cشنبه", "چهارشنبه",
            "پنجشنبه", "جمعه", "شنبه"};

    /** "پنجشنبه ۲۸ آبان ۱۴۰۵" */
    static String format(Calendar c) {
        int[] j = fromGregorian(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        return WEEKDAYS[c.get(Calendar.DAY_OF_WEEK) - 1] + " " + Text.fa(j[2]) + " "
                + MONTHS[j[1] - 1] + " " + Text.fa(j[0]);
    }

    /** @return {year, month, day} in the Jalali calendar */
    static int[] fromGregorian(int gy, int gm, int gd) {
        int[] gdm = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
        int jy = 979;
        gy -= 1600;
        int gy2 = gm > 2 ? gy + 1 : gy;
        int days = 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 - 80 + gd + gdm[gm - 1];
        jy += 33 * (days / 12053);
        days %= 12053;
        jy += 4 * (days / 1461);
        days %= 1461;
        if (days > 365) {
            jy += (days - 1) / 365;
            days = (days - 1) % 365;
        }
        int jm, jd;
        if (days < 186) {
            jm = 1 + days / 31;
            jd = 1 + days % 31;
        } else {
            jm = 7 + (days - 186) / 30;
            jd = 1 + (days - 186) % 30;
        }
        return new int[]{jy, jm, jd};
    }
}
