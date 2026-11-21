package ir.maniimani.driveclock;

import android.content.Context;
import android.content.SharedPreferences;

/** Settings and the little state shared between the screen and the receivers. */
final class Prefs {
    private Prefs() {
    }

    static final String MODE = "mode";                   // NORMAL / LOW / VERY_LOW / AUTO
    static final String NIGHT_START = "nightStart";      // hour, default 18
    static final String NIGHT_END = "nightEnd";          // hour, default 6
    static final String TRIP_START = "tripStart";        // ms: when the car was switched on
    static final String SLEEP_AT = "sleepAt";            // ms: last ENTER_SLEEP
    static final String LAST_WAKE_AT = "lastWakeAt";     // ms: last wake we handled
    static final String VISIBLE = "visible";             // the clock is on screen
    static final String PAUSED_AT = "pausedAt";          // ms: last time it left the screen
    static final String LEFT_BY_USER = "leftByUser";     // closed with Back
    static final String RESUME = "resumeAfterSleep";     // bring the clock back after wake-up

    static final String ENTER_SLEEP = "android.car.action.ENTER_SLEEP";

    static final int NORMAL = 0, LOW = 1, VERY_LOW = 2, AUTO = 3;

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences("clock", Context.MODE_PRIVATE);
    }

    /** The dimming level in effect now: 0 normal, 1 low, 2 very low. */
    static int level(SharedPreferences p, int hourNow) {
        int mode = p.getInt(MODE, AUTO);
        if (mode != AUTO) return mode;
        int s = p.getInt(NIGHT_START, 18), e = p.getInt(NIGHT_END, 6);
        boolean night;
        if (s == e) night = false;
        else if (s < e) night = hourNow >= s && hourNow < e;
        else night = hourNow >= s || hourNow < e;
        return night ? LOW : NORMAL;
    }
}
