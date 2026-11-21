package ir.maniimani.driveclock;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Full-screen ("immersive") mode. The head unit's top bar is hidden; a swipe down from the
 * top edge or a tap on the thin strip along the top shows it again for a few seconds.
 */
final class Chrome {

    private static final int HIDDEN = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    private static final int SHOWN = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
    private static final long SHOW_FOR_MS = 4000;

    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable hide = this::hide;

    Chrome(Activity a) {
        activity = a;
        // when the bar comes back for any reason (swipe, system), hide it again after a moment
        a.getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(vis -> {
            if ((vis & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                main.removeCallbacks(hide);
                main.postDelayed(hide, SHOW_FOR_MS);
            }
        });
    }

    void hide() {
        main.removeCallbacks(hide);
        activity.getWindow().getDecorView().setSystemUiVisibility(HIDDEN);
    }

    void showBriefly() {
        activity.getWindow().getDecorView().setSystemUiVisibility(SHOWN);
        main.removeCallbacks(hide);
        main.postDelayed(hide, SHOW_FOR_MS);
    }

    /** Thin invisible strip along the top edge: tap it to bring the bar back. */
    void addTopStrip(FrameLayout root, int heightPx) {
        View strip = new View(activity);
        strip.setOnClickListener(v -> showBriefly());
        root.addView(strip, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, heightPx, Gravity.TOP));
    }

    void start() {
        hide();
    }

    void stop() {
        main.removeCallbacks(hide);
    }
}
