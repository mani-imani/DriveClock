package ir.maniimani.driveclock;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * DriveClock: a big dim clock on pure black, instead of turning the screen off.
 * Tap the screen for the controls: normal / low / very low / automatic (night hours).
 */
public class MainActivity extends Activity {

    /** True while the clock is on screen (read by ShowService). */
    static volatile boolean sVisible;

    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;

    private static final int C_TEXT = Color.rgb(236, 236, 238);
    private static final int C_TEXT_2 = Color.rgb(142, 142, 150);
    private static final int C_TEXT_3 = Color.rgb(96, 96, 104);
    private static final int C_SURFACE = Color.rgb(14, 14, 16);
    private static final int C_STROKE = Color.rgb(34, 34, 38);
    private static final int C_RIPPLE = Color.argb(46, 255, 255, 255);

    /** For normal / low / very low: the dark overlay, and the screen brightness we ask for. */
    private static final float[] DIM_ALPHA = {0f, 0.45f, 0.78f};
    private static final float[] BRIGHTNESS = {WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE, 0.12f, 0.03f};
    private static final long CONTROLS_MS = 6000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private Chrome chrome;
    private Typeface fMedium, fBold, fBlack;

    private FrameLayout root;
    private LinearLayout center, controls, nightRow;
    private TextView timeView, dateView, tripView, startLabel, endLabel;
    private final TextView[] modeButtons = new TextView[4];
    private View dim;
    private boolean controlsShown;
    private int shownLevel = -1;
    private int lastW = -1, lastH = -1;

    // ================================================================== lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        prefs = Prefs.get(this);
        chrome = new Chrome(this);
        fMedium = font("vazirmatn_Medium");
        fBold = font("vazirmatn_Bold");
        fBlack = font("vazirmatn_Black");
        buildUi();
        setContentView(root);
        refreshControls();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sVisible = true;
        prefs.edit().putBoolean(Prefs.VISIBLE, true).putBoolean(Prefs.LEFT_BY_USER, false).commit();
        chrome.start();
        shownLevel = -1;
        main.post(tick);
    }

    @Override
    protected void onPause() {
        sVisible = false;
        prefs.edit().putBoolean(Prefs.VISIBLE, false)
                .putLong(Prefs.PAUSED_AT, System.currentTimeMillis()).commit();
        main.removeCallbacks(tick);
        main.removeCallbacks(hideTask);
        chrome.stop();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) chrome.hide();
    }

    @Override
    public void onBackPressed() {
        if (controlsShown) {
            hideControls();
            return;
        }
        // closed on purpose: don't bring the clock back after the next ignition
        prefs.edit().putBoolean(Prefs.LEFT_BY_USER, true).commit();
        finish();
    }

    // ================================================================== clock

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            main.postDelayed(this, 1000 - System.currentTimeMillis() % 1000 + 20);
        }
    };

    private void render() {
        Calendar c = new GregorianCalendar();
        String time = Text.time(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        if (!time.contentEquals(timeView.getText())) timeView.setText(time);
        String date = Jalali.format(c);
        if (!date.contentEquals(dateView.getText())) dateView.setText(date);

        long now = c.getTimeInMillis();
        long start = prefs.getLong(Prefs.TRIP_START, 0);
        if (start <= 0 || start > now || now - start > 24 * 3600_000L) {
            start = now;
            prefs.edit().putLong(Prefs.TRIP_START, now).apply();
        }
        String trip = Text.drive(now - start);
        if (!trip.contentEquals(tripView.getText())) tripView.setText(trip);
        tripView.setVisibility(trip.isEmpty() ? View.INVISIBLE : View.VISIBLE);

        applyLevel();
    }

    /** Dark overlay + a request to the system for lower screen brightness. */
    private void applyLevel() {
        int level = Prefs.level(prefs, new GregorianCalendar().get(Calendar.HOUR_OF_DAY));
        if (level == shownLevel) return;
        boolean instant = shownLevel == -1;
        shownLevel = level;
        dim.animate().cancel();
        dim.animate().alpha(DIM_ALPHA[level]).setDuration(instant ? 0 : 700).start();
        // Some head units ignore this (their backlight is driven by the board); the overlay
        // above always works.
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = BRIGHTNESS[level];
        getWindow().setAttributes(lp);
    }

    // ================================================================== controls

    private void toggleControls() {
        if (controlsShown) hideControls();
        else showControls();
    }

    private void showControls() {
        controlsShown = true;
        refreshControls();
        controls.animate().cancel();
        controls.setVisibility(View.VISIBLE);
        controls.setAlpha(0f);
        controls.setTranslationY(dp(16));
        controls.animate().alpha(shownLevel > 0 ? 0.8f : 1f).translationY(0).setDuration(240)
                .setInterpolator(new DecelerateInterpolator()).start();
        scheduleHide();
    }

    private void hideControls() {
        main.removeCallbacks(hideTask);
        if (!controlsShown) return;
        controlsShown = false;
        controls.animate().cancel();
        controls.animate().alpha(0f).translationY(dp(16)).setDuration(200).withEndAction(() -> {
            if (!controlsShown) controls.setVisibility(View.GONE);
        }).start();
    }

    private final Runnable hideTask = this::hideControls;

    private void scheduleHide() {
        main.removeCallbacks(hideTask);
        main.postDelayed(hideTask, CONTROLS_MS);
    }

    private void setMode(int mode) {
        prefs.edit().putInt(Prefs.MODE, mode).apply();
        refreshControls();
        applyLevel();
        scheduleHide();
    }

    private void bump(boolean start, int delta) {
        String key = start ? Prefs.NIGHT_START : Prefs.NIGHT_END;
        int v = (prefs.getInt(key, start ? 18 : 6) + delta + 24) % 24;
        prefs.edit().putInt(key, v).apply();
        refreshControls();
        applyLevel();
        scheduleHide();
    }

    private void refreshControls() {
        int mode = prefs.getInt(Prefs.MODE, Prefs.AUTO);
        for (int i = 0; i < modeButtons.length; i++) stylePill(modeButtons[i], i == mode);
        nightRow.setVisibility(mode == Prefs.AUTO ? View.VISIBLE : View.GONE);
        startLabel.setText(Text.hour(prefs.getInt(Prefs.NIGHT_START, 18)));
        endLabel.setText(Text.hour(prefs.getInt(Prefs.NIGHT_END, 6)));
    }

    // ================================================================== UI

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setOnClickListener(v -> toggleControls());

        center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        root.addView(center, new FrameLayout.LayoutParams(MATCH, MATCH));

        timeView = text(fBlack, 120, C_TEXT);
        timeView.setTextDirection(View.TEXT_DIRECTION_LTR);
        center.addView(timeView, new LinearLayout.LayoutParams(WRAP, WRAP));

        dateView = text(fMedium, 20, C_TEXT_2);
        dateView.setTextDirection(View.TEXT_DIRECTION_RTL);
        center.addView(dateView, new LinearLayout.LayoutParams(WRAP, WRAP));

        tripView = text(fMedium, 14, C_TEXT_3);
        tripView.setTextDirection(View.TEXT_DIRECTION_RTL);
        center.addView(tripView, new LinearLayout.LayoutParams(WRAP, WRAP));

        // dark overlay: touches pass through to the root
        dim = new View(this);
        dim.setBackgroundColor(Color.BLACK);
        dim.setAlpha(0f);
        dim.setClickable(false);
        dim.setFocusable(false);
        root.addView(dim, new FrameLayout.LayoutParams(MATCH, MATCH));

        // controls panel (above the overlay, so it stays usable at night)
        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER_HORIZONTAL);
        controls.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        controls.setPadding(dp(14), dp(14), dp(14), dp(14));
        controls.setBackground(round(Color.argb(240, 10, 10, 12), C_STROKE, dp(30)));
        controls.setClickable(true);
        controls.setVisibility(View.GONE);

        LinearLayout modes = new LinearLayout(this);
        modes.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        String[] names = {"عادی", "کم", "خیلی کم", "خودکار"};
        for (int i = 0; i < names.length; i++) {
            final int mode = i;
            TextView pill = pill(names[i], v -> setMode(mode));
            modeButtons[i] = pill;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            if (i > 0) lp.setMarginStart(dp(8));
            modes.addView(pill, lp);
        }
        controls.addView(modes, new LinearLayout.LayoutParams(WRAP, WRAP));

        nightRow = new LinearLayout(this);
        nightRow.setGravity(Gravity.CENTER);
        nightRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        nightRow.addView(label("شب از"), new LinearLayout.LayoutParams(WRAP, WRAP));
        nightRow.addView(stepper(true), new LinearLayout.LayoutParams(WRAP, WRAP));
        nightRow.addView(label("تا"), new LinearLayout.LayoutParams(WRAP, WRAP));
        nightRow.addView(stepper(false), new LinearLayout.LayoutParams(WRAP, WRAP));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(WRAP, WRAP);
        nl.topMargin = dp(12);
        controls.addView(nightRow, nl);

        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cl.bottomMargin = dp(30);
        root.addView(controls, cl);

        chrome.addTopStrip(root, dp(14));

        root.addOnLayoutChangeListener((v, l, tp, r, bt, ol, ot, orr, ob) -> {
            int w = r - l, h = bt - tp;
            if (w != lastW || h != lastH) {
                lastW = w;
                lastH = h;
                v.post(() -> fit(w, h));
            }
        });
    }

    /** Everything scales with the screen: the clock is the biggest thing that fits. */
    private void fit(int w, int h) {
        float clock = Math.min(h * 0.40f, w * 0.30f);
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_PX, clock);
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_PX, clock * 0.15f);
        tripView.setTextSize(TypedValue.COMPLEX_UNIT_PX, clock * 0.105f);
        topMargin(dateView, Math.round(clock * 0.05f));
        topMargin(tripView, Math.round(clock * 0.07f));
    }

    private LinearLayout stepper(final boolean isStart) {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        l.addView(circle("−", v -> bump(isStart, -1)), new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView value = text(fMedium, 18, C_TEXT);
        value.setTextDirection(View.TEXT_DIRECTION_LTR);
        value.setMinWidth(dp(76));
        if (isStart) startLabel = value;
        else endLabel = value;
        l.addView(value, new LinearLayout.LayoutParams(WRAP, WRAP));
        l.addView(circle("+", v -> bump(isStart, 1)), new LinearLayout.LayoutParams(dp(42), dp(42)));
        return l;
    }

    private TextView label(String s) {
        TextView t = text(fMedium, 14, C_TEXT_2);
        t.setText(s);
        t.setPadding(dp(10), 0, dp(10), 0);
        return t;
    }

    private TextView pill(String label, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTypeface(fBold);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), dp(10), dp(18), dp(10));
        t.setOnClickListener(l);
        return t;
    }

    private void stylePill(TextView t, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(100));
        if (selected) {
            bg.setColor(C_TEXT);
            t.setTextColor(Color.BLACK);
        } else {
            bg.setColor(C_SURFACE);
            bg.setStroke(Math.max(1, dp(1)), C_STROKE);
            t.setTextColor(C_TEXT);
        }
        t.setBackground(new RippleDrawable(
                ColorStateList.valueOf(selected ? Color.argb(60, 0, 0, 0) : C_RIPPLE), bg, null));
    }

    private TextView circle(String label, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTypeface(fBold);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        t.setTextColor(C_TEXT);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(C_SURFACE);
        g.setStroke(Math.max(1, dp(1)), C_STROKE);
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(C_RIPPLE), g, null));
        t.setOnClickListener(l);
        return t;
    }

    // ================================================================== helpers

    private TextView text(Typeface face, float sp, int color) {
        TextView t = new TextView(this);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        return t;
    }

    private GradientDrawable round(int fill, int stroke, int radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(Math.max(1, dp(1)), stroke);
        g.setCornerRadius(radiusPx);
        return g;
    }

    private void topMargin(View v, int px) {
        ((LinearLayout.LayoutParams) v.getLayoutParams()).topMargin = px;
        v.requestLayout();
    }

    private Typeface font(String name) {
        try {
            return Typeface.createFromAsset(getAssets(), "fonts/" + name + ".ttf");
        } catch (Throwable t) {
            return Typeface.DEFAULT;
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
