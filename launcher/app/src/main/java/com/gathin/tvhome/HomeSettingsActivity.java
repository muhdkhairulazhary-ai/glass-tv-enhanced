package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

/** User-controlled HOME setup and escape switch; never modifies another package. */
public class HomeSettingsActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        showOptions();
    }

    private void showOptions() {
        boolean fallback = getSharedPreferences(HomeKeyService.PREFS, MODE_PRIVATE)
                .getBoolean(HomeKeyService.REDIRECT, false);
        new AlertDialog.Builder(this)
                .setTitle(R.string.home_controls)
                .setItems(new String[]{getString(R.string.home_choose), getString(R.string.home_accessibility),
                        getString(fallback ? R.string.home_fallback_off : R.string.home_fallback_on),
                        getString(R.string.home_diagnostics)}, (dialog, which) -> {
                    if (which == 0) chooseHome();
                    else if (which == 1) open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    else if (which == 2) {
                        if (fallback) setFallback(false);
                        else new AlertDialog.Builder(this).setTitle(R.string.home_fallback_on)
                                .setMessage(R.string.home_fallback_warning)
                                .setPositiveButton(android.R.string.ok, (d, w) -> setFallback(true))
                                .setNegativeButton(android.R.string.cancel, (d, w) -> finish())
                                .setOnCancelListener(d -> finish()).show();
                    } else new AlertDialog.Builder(this).setTitle(R.string.home_diagnostics)
                            .setMessage(HomeDiagnostics.selection(this))
                            .setPositiveButton(android.R.string.ok, (d, w) -> finish())
                            .setOnCancelListener(d -> finish()).show();
                }).setNegativeButton(android.R.string.cancel, (d, w) -> finish())
                .setOnCancelListener(d -> finish()).show();
    }

    private void chooseHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager roles = getSystemService(RoleManager.class);
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)
                    && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                try {
                    startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 1);
                    return;
                } catch (ActivityNotFoundException | SecurityException unavailable) {
                    Log.w(HomeDiagnostics.TAG, "HOME role UI unavailable; trying settings", unavailable);
                }
            }
        }
        open(new Intent(Settings.ACTION_HOME_SETTINGS));
    }

    private void setFallback(boolean enabled) {
        getSharedPreferences(HomeKeyService.PREFS, MODE_PRIVATE).edit()
                .putBoolean(HomeKeyService.REDIRECT, enabled).apply();
        Log.i(HomeDiagnostics.TAG, "user set Google Home fallback=" + enabled);
        finish();
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
        finish();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        HomeDiagnostics.selection(this);
        finish();
    }
}
