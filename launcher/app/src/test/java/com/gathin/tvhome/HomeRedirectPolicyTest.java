package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class HomeRedirectPolicyTest {
    private boolean decide(boolean enabled, boolean unlocked, boolean interactive, boolean locked,
                           String pkg, String activity, long now, long last) {
        return HomeRedirectPolicy.shouldRedirect(enabled, unlocked, interactive, locked, pkg, activity, now, last);
    }
    @Test public void onlyExactStockHomeIsEligible() {
        String pkg = HomeRedirectPolicy.STOCK_PACKAGE, cls = HomeRedirectPolicy.STOCK_HOME;
        assertTrue(decide(true, true, true, false, pkg, cls, 100, -1));
        assertFalse(decide(true, true, true, false, pkg, "android.app.Dialog", 100, -1));
        assertFalse(decide(true, true, true, false, pkg, pkg + ".settings.SettingsActivity", 100, -1));
        assertFalse(decide(true, true, true, false, "com.klevico.monet", cls, 100, -1));
        assertFalse(decide(true, true, true, false, null, null, 100, -1));
    }
    @Test public void requiresConsentAndAwakeUnlockedDevice() {
        String p = HomeRedirectPolicy.STOCK_PACKAGE, c = HomeRedirectPolicy.STOCK_HOME;
        assertFalse(decide(false, true, true, false, p, c, 100, -1));
        assertFalse(decide(true, false, true, false, p, c, 100, -1));
        assertFalse(decide(true, true, false, false, p, c, 100, -1));
        assertFalse(decide(true, true, true, true, p, c, 100, -1));
    }
    @Test public void repeatedEventsCannotImmediatelyRelaunch() {
        String p = HomeRedirectPolicy.STOCK_PACKAGE, c = HomeRedirectPolicy.STOCK_HOME;
        assertFalse(decide(true, true, true, false, p, c, 1000, 1000));
        assertFalse(decide(true, true, true, false, p, c, 2499, 1000));
        assertTrue(decide(true, true, true, false, p, c, 2500, 1000));
        assertFalse(decide(true, true, true, false, p, c, 500, 1000));
    }
}
