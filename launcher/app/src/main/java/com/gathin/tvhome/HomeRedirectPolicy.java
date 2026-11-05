package com.gathin.tvhome;

/** Pure policy, deliberately limited to the reported Google TV launcher activity. */
final class HomeRedirectPolicy {
    static final String STOCK_PACKAGE = "com.google.android.apps.tv.launcherx";
    static final String STOCK_HOME = STOCK_PACKAGE + ".home.HomeActivity";
    static final long COOLDOWN_MS = 1500;

    static boolean shouldRedirect(boolean enabled, boolean unlocked, boolean interactive,
                                  boolean keyguardLocked, String pkg, String activity,
                                  long now, long lastLaunch) {
        return enabled && unlocked && interactive && !keyguardLocked
                && STOCK_PACKAGE.equals(pkg) && STOCK_HOME.equals(activity)
                && (lastLaunch < 0 || now - lastLaunch >= COOLDOWN_MS);
    }
    private HomeRedirectPolicy() { }
}
