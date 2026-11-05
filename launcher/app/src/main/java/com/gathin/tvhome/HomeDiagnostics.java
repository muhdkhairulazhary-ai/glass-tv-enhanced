package com.gathin.tvhome;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.util.Log;

/** Local diagnostics: no intent extras, URLs, window text or uploaded logs. */
final class HomeDiagnostics {
    static final String TAG = "TvHome.Home";

    static Intent homeIntent(Context context) {
        return new Intent(context, MainActivity.class)
                .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    static void activity(Activity activity, String event, Intent intent) {
        Log.i(TAG, event + " task=" + activity.getTaskId() + " root=" + activity.isTaskRoot()
                + " action=" + (intent == null ? null : intent.getAction())
                + " home=" + (intent != null && intent.hasCategory(Intent.CATEGORY_HOME))
                + " flags=" + (intent == null ? "0" : Integer.toHexString(intent.getFlags())));
    }

    static String selection(Context context) {
        StringBuilder info = new StringBuilder("package=").append(context.getPackageName())
                .append(" sdk=").append(Build.VERSION.SDK_INT);
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager roles = context.getSystemService(RoleManager.class);
            info.append(" HOME-role=").append(roles != null && roles.isRoleHeld(RoleManager.ROLE_HOME));
        }
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        info.append(" resolved=").append(component(context.getPackageManager().resolveActivity(home, 0)));
        int count = 0;
        for (ResolveInfo candidate : context.getPackageManager().queryIntentActivities(home, 0)) {
            if (++count > 16) break;
            info.append("\nHOME ").append(component(candidate)).append(" priority=").append(candidate.priority);
        }
        String result = info.toString();
        Log.i(TAG, result);
        return result;
    }

    private static String component(ResolveInfo info) {
        return info == null || info.activityInfo == null ? "none"
                : info.activityInfo.packageName + "/" + info.activityInfo.name;
    }
    private HomeDiagnostics() { }
}
