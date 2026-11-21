package ir.maniimani.driveclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Ignition off/on.
 *
 * When the car is switched off, the head unit force-stops every installed app, so regular
 * broadcasts like BOOT_COMPLETED never reach us. The vendor's own wake-up broadcasts are
 * sent so that they do reach stopped apps.
 *
 *  - ENTER_SLEEP: note whether the clock was on screen (and not closed with Back).
 *  - wake-up / boot: start a new "trip" (unless the car was off for under 10 minutes) and,
 *    if the clock was on screen, ask ShowService to bring it back.
 */
public class SleepReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        SharedPreferences p = Prefs.get(context);
        long now = System.currentTimeMillis();

        if (Prefs.ENTER_SLEEP.equals(intent.getAction())) {
            boolean onScreen = p.getBoolean(Prefs.VISIBLE, false)
                    || now - p.getLong(Prefs.PAUSED_AT, 0) < 30_000;
            boolean leftByUser = p.getBoolean(Prefs.LEFT_BY_USER, false);
            p.edit().putLong(Prefs.SLEEP_AT, now)
                    .putBoolean(Prefs.RESUME, onScreen && !leftByUser)
                    .commit();
            return;
        }

        // woke up or booted. EXIT_SLEEP, CAR_BOOT_COMPLETED and BOOT_COMPLETED often arrive
        // together, so handle only the first of a burst.
        long lastWake = p.getLong(Prefs.LAST_WAKE_AT, 0);
        boolean sameWake = lastWake > 0 && now >= lastWake && now - lastWake < 120_000;
        if (!sameWake) {
            long sleepAt = p.getLong(Prefs.SLEEP_AT, 0);
            boolean quickRestart = sleepAt > 0 && now >= sleepAt && now - sleepAt < 10 * 60_000L
                    && p.getLong(Prefs.TRIP_START, 0) > 0;
            SharedPreferences.Editor e = p.edit()
                    .putLong(Prefs.LAST_WAKE_AT, now)
                    .putLong(Prefs.SLEEP_AT, 0);
            if (!quickRestart) e.putLong(Prefs.TRIP_START, now);
            e.commit();
        }
        if (p.getBoolean(Prefs.RESUME, false)) ShowService.start(context);
    }
}
