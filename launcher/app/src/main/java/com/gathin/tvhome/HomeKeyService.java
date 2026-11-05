package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** User-enabled remote helper. Firmware may consume HOME before accessibility sees it. */
public class HomeKeyService extends AccessibilityService {
    static final String PREFS = "home_handling";
    static final String REDIRECT = "redirect_google_home";
    private int[] settingsScancodes = new int[0];
    private long lastLaunch = -1;

    private boolean unlocked() {
        UserManager users = getSystemService(UserManager.class);
        return users != null && users.isUserUnlocked();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        Log.i(HomeDiagnostics.TAG, "HomeKeyService connected; filterKeys="
                + ((getServiceInfo().flags & android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0)
                + "; unlocked=" + unlocked());
    }

    private boolean launch(Intent intent, String reason) {
        try {
            startActivity(intent);
            Log.i(HomeDiagnostics.TAG, "launch requested: " + reason + " (verify onResume)");
            return true;
        } catch (ActivityNotFoundException | SecurityException failure) {
            Log.w(HomeDiagnostics.TAG, "launch failed: " + reason, failure);
            return false;
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (!unlocked()) return false;
        int code = event.getKeyCode();
        boolean home = code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_ALL_APPS;
        boolean settings = code == KeyEvent.KEYCODE_SETTINGS;
        if (!home) for (int scan : settingsScancodes) settings |= event.getScanCode() == scan;
        if (!home && !settings) return false;
        Log.i(HomeDiagnostics.TAG, "key=" + KeyEvent.keyCodeToString(code)
                + " action=" + event.getAction() + " repeat=" + event.getRepeatCount()
                + " canceled=" + event.isCanceled());
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            if (home) {
                lastLaunch = SystemClock.uptimeMillis();
                return launch(HomeDiagnostics.homeIntent(this), "remote key");
            }
            return launch(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "settings key");
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || !unlocked()) return;
        String pkg = event.getPackageName() == null ? "" : event.getPackageName().toString();
        String cls = event.getClassName() == null ? "" : event.getClassName().toString();
        if (!HomeRedirectPolicy.STOCK_PACKAGE.equals(pkg)) return;
        boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(REDIRECT, false);
        PowerManager power = getSystemService(PowerManager.class);
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        long now = SystemClock.uptimeMillis();
        boolean redirect = HomeRedirectPolicy.shouldRedirect(enabled, true,
                power != null && power.isInteractive(), keyguard == null || keyguard.isKeyguardLocked(),
                pkg, cls, now, lastLaunch);
        Log.i(HomeDiagnostics.TAG, "stock window=" + cls + " fallback=" + enabled + " redirect=" + redirect);
        if (redirect) {
            // Bound retries even when firmware refuses the request; no delayed or periodic relaunch.
            lastLaunch = now;
            launch(HomeDiagnostics.homeIntent(this), "opt-in Google TV Home fallback");
        }
    }

    @Override public void onInterrupt() {
        Log.i(HomeDiagnostics.TAG, "HomeKeyService interrupted");
    }
    @Override public void onDestroy() {
        Log.i(HomeDiagnostics.TAG, "HomeKeyService destroyed");
        super.onDestroy();
    }
}
