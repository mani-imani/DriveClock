package ir.maniimani.driveclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Lives about ten seconds after the car wakes up. If the clock was on screen when the car
 * was switched off, it brings the clock back, unless music is playing by then: the music
 * player (for example DrivePlay, which also reopens itself after wake-up) comes first.
 */
public class ShowService extends Service {

    private static final String CHANNEL = "wake";
    private static final long CHECK_AFTER_MS = 10_000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean scheduled;

    static void start(Context c) {
        try {
            c.startForegroundService(new Intent(c, ShowService.class));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "نمایش ساعت", NotificationManager.IMPORTANCE_MIN);
        ch.setShowBadge(false);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_clock)
                .setContentTitle("DriveClock")
                .setShowWhen(false)
                .build();
        startForeground(1, n);
        if (!scheduled) {
            scheduled = true;
            main.postDelayed(this::showIfAppropriate, CHECK_AFTER_MS);
        }
        return START_NOT_STICKY;
    }

    private void showIfAppropriate() {
        SharedPreferences p = Prefs.get(this);
        if (p.getBoolean(Prefs.RESUME, false)) {
            p.edit().putBoolean(Prefs.RESUME, false).apply();
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (!am.isMusicActive() && !MainActivity.sVisible) {
                try {
                    startActivity(new Intent(this, MainActivity.class).addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                } catch (Throwable ignored) {
                }
            }
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
